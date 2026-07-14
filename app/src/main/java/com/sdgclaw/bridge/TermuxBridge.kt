package com.sdgclaw.bridge

import android.util.Log
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OkHttp WebSocket client that maintains a persistent connection to the
 * Termux Node.js bridge at `ws://127.0.0.1:8765`.
 *
 * Features:
 * - Auto-reconnect (3 s delay) on disconnect or error
 * - 30 s ping interval to keep the connection alive
 * - Outbound message queue flushed on (re)connect
 * - Coroutine [Channel]-based response delivery for callers
 * - [isConnected] for synchronous status checks (used by BridgeSetupActivity)
 */
class TermuxBridge {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG              = "TermuxBridge"
        private const val BRIDGE_URL       = "ws://127.0.0.1:8765"
        private const val RECONNECT_DELAY  = 3_000L  // ms
        private const val PING_INTERVAL    = 30_000L // ms
        private const val CONNECT_TIMEOUT  = 10_000L // ms (for isConnected probe)
    }

    // ── OkHttp client ─────────────────────────────────────────────────────────
    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // no read timeout — keep alive
        .build()

    // ── State ─────────────────────────────────────────────────────────────────
    @Volatile private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)

    /** Messages queued while the socket is disconnected; flushed on connect. */
    private val pendingMessages = ArrayDeque<String>()

    /** Pending coroutine responses keyed by request id. */
    private val pendingResponses = ConcurrentHashMap<String, Channel<JSONObject>>()

    // ── Callbacks (set by owner) ───────────────────────────────────────────────
    var onConnected:    (() -> Unit)?           = null
    var onDisconnected: (() -> Unit)?           = null
    var onMessage:      ((JSONObject) -> Unit)? = null
    var onError:        ((String) -> Unit)?     = null

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns `true` if the WebSocket handshake has completed and the socket
     *  is currently open.  Safe to call from any thread. */
    fun isConnected(): Boolean = connected.get() && webSocket != null

    /** Initiate the WebSocket connection (idempotent — safe to call multiple times). */
    fun connect() {
        if (connected.get()) return
        Log.d(TAG, "Connecting to $BRIDGE_URL…")
        val request = Request.Builder().url(BRIDGE_URL).build()
        client.newWebSocket(request, BridgeListener())
    }

    /** Disconnect gracefully and stop auto-reconnect. */
    fun disconnect() {
        Log.d(TAG, "Disconnecting…")
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        connected.set(false)
    }

    /**
     * Send a raw JSON string.
     * If the socket is not currently connected the message is queued and will
     * be sent automatically after the next successful reconnect.
     */
    fun sendMessage(json: String) {
        val ws = webSocket
        if (connected.get() && ws != null) {
            ws.send(json)
        } else {
            Log.d(TAG, "Socket not ready — queuing message.")
            synchronized(pendingMessages) { pendingMessages.addLast(json) }
        }
    }

    /**
     * Send a tool-execution request and suspend until the bridge replies or
     * the [timeoutMs] elapses.
     *
     * @param type       Message type: `"execute_command"`, `"read_file"`, etc.
     * @param payload    JSONObject containing type-specific fields.
     * @param timeoutMs  How long to wait for a response (default 30 s).
     * @return The bridge's response [JSONObject], or a synthetic error object.
     */
    suspend fun sendRequest(
        type: String,
        payload: JSONObject,
        timeoutMs: Long = 30_000L,
    ): JSONObject {
        val id = UUID.randomUUID().toString()
        val msg = JSONObject().apply {
            put("type", type)
            put("id", id)
            put("payload", payload)
        }

        val channel = Channel<JSONObject>(Channel.UNLIMITED)
        pendingResponses[id] = channel

        sendMessage(msg.toString())

        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                channel.receive()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Request $id timed out after ${timeoutMs}ms")
            JSONObject().apply {
                put("type", "error")
                put("id", id)
                put("error", "Request timed out after ${timeoutMs}ms")
            }
        } finally {
            pendingResponses.remove(id)
            channel.close()
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Flush any queued messages now that the socket is open. */
    private fun flushPendingMessages(ws: WebSocket) {
        synchronized(pendingMessages) {
            while (pendingMessages.isNotEmpty()) {
                ws.send(pendingMessages.removeFirst())
            }
        }
    }

    /** Schedule a reconnect attempt after [RECONNECT_DELAY] ms. */
    private fun scheduleReconnect() {
        Log.d(TAG, "Scheduling reconnect in ${RECONNECT_DELAY}ms…")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            connect()
        }, RECONNECT_DELAY)
    }

    // ── WebSocketListener ─────────────────────────────────────────────────────

    private inner class BridgeListener : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened.")
            webSocket = ws
            connected.set(true)
            flushPendingMessages(ws)
            onConnected?.invoke()
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.v(TAG, "← $text")
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message: $text", e)
                return
            }

            // Route to a waiting coroutine if there is one
            val id = json.optString("id", "")
            if (id.isNotEmpty()) {
                val ch = pendingResponses[id]
                if (ch != null) {
                    ch.trySend(json)
                    return
                }
            }

            // Otherwise deliver via the general callback
            onMessage?.invoke(json)
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            onMessage(ws, bytes.utf8())
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            ws.close(code, reason)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            connected.set(false)
            webSocket = null
            onDisconnected?.invoke()
            if (code != 1000) scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            connected.set(false)
            webSocket = null
            onError?.invoke(t.message ?: "Unknown error")
            onDisconnected?.invoke()
            scheduleReconnect()
        }
    }
}
