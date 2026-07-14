package com.sdgclaw.bridge

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * TermuxBridge — OkHttp WebSocket client that maintains a persistent connection
 * to the Node.js bridge server running in Termux on ws://127.0.0.1:8765.
 *
 * Features:
 * - Auto-reconnect (3 s delay)
 * - Ping interval 30 s
 * - Coroutine-based [executeCommand] — suspends until the response arrives
 * - Message queue for fire-and-forget sends while disconnected
 */
class TermuxBridge {

    companion object {
        private const val TAG    = "TermuxBridge"
        private const val URL    = "ws://127.0.0.1:8765"
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val TOOL_TIMEOUT_MS    = 30_000L
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // keep-alive
        .build()

    private var webSocket: WebSocket? = null

    @Volatile var isConnected: Boolean = false
        private set

    /** Pending coroutine continuations keyed by request id. */
    private val pending = ConcurrentHashMap<String, kotlinx.coroutines.CancellableContinuation<String>>()

    // ── External callbacks ─────────────────────────────────────────────────────
    private var onConnected: (() -> Unit)?    = null
    private var onDisconnected: (() -> Unit)? = null
    private var onMessage: ((String) -> Unit)? = null

    fun setOnConnected(cb: () -> Unit)    { onConnected = cb }
    fun setOnDisconnected(cb: () -> Unit) { onDisconnected = cb }
    fun setOnMessage(cb: (String) -> Unit) { onMessage = cb }

    // ── Connect / disconnect ───────────────────────────────────────────────────

    fun connect() {
        val request = Request.Builder().url(URL).build()
        webSocket = http.newWebSocket(request, listener)
        Log.d(TAG, "Connecting to $URL")
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
    }

    // ── Tool execution ─────────────────────────────────────────────────────────

    /**
     * Execute a named tool on the Termux bridge and suspend until the response
     * arrives (or [TOOL_TIMEOUT_MS] elapses).
     */
    suspend fun executeCommand(toolName: String, args: JSONObject): String {
        if (!isConnected) {
            throw IllegalStateException("TermuxBridge is not connected")
        }

        val id = UUID.randomUUID().toString()
        val payload = JSONObject().apply {
            put("id",   id)
            put("tool", toolName)
            put("args", args)
        }.toString()

        return withTimeout(TOOL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                pending[id] = cont
                val sent = webSocket?.send(payload) ?: false
                if (!sent) {
                    pending.remove(id)
                    cont.resumeWithException(RuntimeException("WebSocket send failed"))
                }
                cont.invokeOnCancellation { pending.remove(id) }
            }
        }
    }

    // ── WebSocket listener ─────────────────────────────────────────────────────

    private val listener = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket connected")
            isConnected = true
            onConnected?.invoke()
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "Received: $text")
            onMessage?.invoke(text)

            // Try to match a pending tool-call response
            try {
                val json = JSONObject(text)
                val id   = json.optString("id")
                if (id.isNotEmpty()) {
                    pending.remove(id)?.let { cont ->
                        val error = json.optString("error", "")
                        if (error.isNotEmpty()) {
                            cont.resumeWithException(RuntimeException(error))
                        } else {
                            cont.resume(json.optString("result", text))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse bridge message as JSON: $text")
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            isConnected = false
            onDisconnected?.invoke()
            // Resume all pending continuations with an error
            pending.values.forEach { it.resumeWithException(t) }
            pending.clear()
            // Schedule reconnect
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ connect() }, RECONNECT_DELAY_MS)
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(1000, null)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closed: $reason")
            isConnected = false
            onDisconnected?.invoke()
        }
    }
}
