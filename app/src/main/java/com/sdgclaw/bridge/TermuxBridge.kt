package com.sdgclaw.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TermuxBridge - WebSocket client connecting to Node.js server in Termux
 * Server runs at ws://127.0.0.1:8765 (started via: node ~/sdgclaw-bridge/server.js)
 */
class TermuxBridge(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "TermuxBridge"
        private const val WS_URL = "ws://127.0.0.1:8765"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val PING_INTERVAL_MS = 30000L
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var isConnected = false
    private var reconnectJob: kotlinx.coroutines.Job? = null
    
    // Message channels for communication with app
    private val incomingMessages = Channel<String>(Channel.UNLIMITED)
    private val outgoingMessages = Channel<String>(Channel.UNLIMITED)
    
    // Connection state callbacks
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: ((String?) -> Unit)? = null
    private var onMessageCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    // Message queue for when not connected
    private val pendingMessages = mutableListOf<String>()
    
    /**
     * Connect to the Termux WebSocket bridge
     */
    fun connect() {
        if (isConnecting || isConnected) {
            Log.d(TAG, "Already connecting or connected")
            return
        }
        
        isConnecting = true
        Log.d(TAG, "Connecting to $WS_URL")
        
        val request = Request.Builder().url(WS_URL).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully")
                isConnecting = false
                isConnected = true
                
                // Send any pending messages
                pendingMessages.forEach { sendRaw(it) }
                pendingMessages.clear()
                
                // Start listening for outgoing messages
                coroutineScope.launch { processOutgoingMessages(webSocket) }
                
                onConnectedCallback?.invoke()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                incomingMessages.trySend(text)
                onMessageCallback?.invoke(text)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Received binary message: ${bytes.size} bytes")
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, "Client closing")
                handleDisconnect("Server closing: $reason")
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                handleDisconnect(t.message)
                onErrorCallback?.invoke(t.message ?: "Unknown error")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                handleDisconnect(reason)
            }
        })
        
        // Start listening for incoming messages
        coroutineScope.launch { processIncomingMessages() }
    }
    
    /**
     * Disconnect from the bridge
     */
    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        isConnected = false
        isConnecting = false
    }
    
    /**
     * Send a message to the bridge
     */
    fun send(message: String) {
        if (isConnected) {
            outgoingMessages.trySend(message)
        } else {
            pendingMessages.add(message)
            // Try to reconnect if not already trying
            if (!isConnecting) {
                scheduleReconnect()
            }
        }
    }
    
    /**
     * Send JSON message to bridge
     */
    fun sendJson(json: JSONObject) {
        send(json.toString())
    }
    
    // Callbacks
    fun setOnConnected(callback: () -> Unit) {
        onConnectedCallback = callback
    }
    
    fun setOnDisconnected(callback: (String?) -> Unit) {
        onDisconnectedCallback = callback
    }
    
    fun setOnMessage(callback: (String) -> Unit) {
        onMessageCallback = callback
    }
    
    fun setOnError(callback: (String) -> Unit) {
        onErrorCallback = callback
    }
    
    // State getters
    fun isConnected(): Boolean = isConnected
    fun isConnecting(): Boolean = isConnecting
    
    // Incoming message channel for consumers
    fun getIncomingMessages(): ReceiveChannel<String> = incomingMessages
    
    // Private methods
    
    private fun sendRaw(message: String) {
        webSocket?.send(message)
    }
    
    private fun handleDisconnect(reason: String?) {
        isConnected = false
        isConnecting = false
        webSocket = null
        onDisconnectedCallback?.invoke(reason)
        scheduleReconnect()
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(RECONNECT_DELAY_MS)
            if (!isConnected && !isConnecting) {
                connect()
            }
        }
    }
    
    private suspend fun processIncomingMessages() {
        for (message in incomingMessages) {
            // Messages are also sent via callback
            // Consumers can also receive from the channel directly
        }
    }
    
    private suspend fun processOutgoingMessages(ws: WebSocket) {
        for (message in outgoingMessages) {
            if (isConnected) {
                ws.send(message)
            } else {
                // Put back in queue if disconnected
                outgoingMessages.trySend(message)
                break
            }
        }
    }
    
    // Test connection method for MainActivity
    fun testConnection() {
        if (isConnected) {
            val testMsg = JSONObject().apply {
                put("type", "test")
                put("timestamp", System.currentTimeMillis())
                put("client", "SDGClaw-Android")
            }
            sendJson(testMsg)
            Log.d(TAG, "Test message sent")
        } else {
            Log.w(TAG, "Cannot send test - not connected")
        }
    }
}