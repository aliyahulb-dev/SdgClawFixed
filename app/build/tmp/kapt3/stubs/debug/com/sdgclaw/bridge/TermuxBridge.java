package com.sdgclaw.bridge;

/**
 * TermuxBridge - WebSocket client connecting to Node.js server in Termux
 * Server runs at ws://127.0.0.1:8765 (started via: node ~/sdgclaw-bridge/server.js)
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010!\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\t\u0018\u0000 52\u00020\u0001:\u00015B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010\u001d\u001a\u00020\u0011J\u0006\u0010\u001e\u001a\u00020\u0011J\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\t0 J\u0012\u0010!\u001a\u00020\u00112\b\u0010\"\u001a\u0004\u0018\u00010\tH\u0002J\u0006\u0010\n\u001a\u00020\u000bJ\u0006\u0010\f\u001a\u00020\u000bJ\u000e\u0010#\u001a\u00020\u0011H\u0082@\u00a2\u0006\u0002\u0010$J\u0016\u0010%\u001a\u00020\u00112\u0006\u0010&\u001a\u00020\u001cH\u0082@\u00a2\u0006\u0002\u0010\'J\b\u0010(\u001a\u00020\u0011H\u0002J\u000e\u0010)\u001a\u00020\u00112\u0006\u0010*\u001a\u00020\tJ\u000e\u0010+\u001a\u00020\u00112\u0006\u0010,\u001a\u00020-J\u0010\u0010.\u001a\u00020\u00112\u0006\u0010*\u001a\u00020\tH\u0002J\u0014\u0010/\u001a\u00020\u00112\f\u00100\u001a\b\u0012\u0004\u0012\u00020\u00110\u0010J\u001c\u00101\u001a\u00020\u00112\u0014\u00100\u001a\u0010\u0012\u0006\u0012\u0004\u0018\u00010\t\u0012\u0004\u0012\u00020\u00110\u0013J\u001a\u00102\u001a\u00020\u00112\u0012\u00100\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u00110\u0013J\u001a\u00103\u001a\u00020\u00112\u0012\u00100\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u00110\u0013J\u0006\u00104\u001a\u00020\u0011R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000f\u001a\n\u0012\u0004\u0012\u00020\u0011\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0012\u001a\u0012\u0012\u0006\u0012\u0004\u0018\u00010\t\u0012\u0004\u0012\u00020\u0011\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0014\u001a\u0010\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u0011\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0015\u001a\u0010\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u0011\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\t0\u0018X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0019\u001a\u0004\u0018\u00010\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001b\u001a\u0004\u0018\u00010\u001cX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00066"}, d2 = {"Lcom/sdgclaw/bridge/TermuxBridge;", "", "context", "Landroid/content/Context;", "coroutineScope", "Lkotlinx/coroutines/CoroutineScope;", "(Landroid/content/Context;Lkotlinx/coroutines/CoroutineScope;)V", "incomingMessages", "Lkotlinx/coroutines/channels/Channel;", "", "isConnected", "", "isConnecting", "okHttpClient", "Lokhttp3/OkHttpClient;", "onConnectedCallback", "Lkotlin/Function0;", "", "onDisconnectedCallback", "Lkotlin/Function1;", "onErrorCallback", "onMessageCallback", "outgoingMessages", "pendingMessages", "", "reconnectJob", "Lkotlinx/coroutines/Job;", "webSocket", "Lokhttp3/WebSocket;", "connect", "disconnect", "getIncomingMessages", "Lkotlinx/coroutines/channels/ReceiveChannel;", "handleDisconnect", "reason", "processIncomingMessages", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "processOutgoingMessages", "ws", "(Lokhttp3/WebSocket;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "scheduleReconnect", "send", "message", "sendJson", "json", "Lorg/json/JSONObject;", "sendRaw", "setOnConnected", "callback", "setOnDisconnected", "setOnError", "setOnMessage", "testConnection", "Companion", "app_debug"})
public final class TermuxBridge {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope coroutineScope = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "TermuxBridge";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String WS_URL = "ws://127.0.0.1:8765";
    private static final long RECONNECT_DELAY_MS = 3000L;
    private static final long PING_INTERVAL_MS = 30000L;
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient okHttpClient = null;
    @org.jetbrains.annotations.Nullable()
    private okhttp3.WebSocket webSocket;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job reconnectJob;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.channels.Channel<java.lang.String> incomingMessages = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.channels.Channel<java.lang.String> outgoingMessages = null;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onConnectedCallback;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onDisconnectedCallback;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onMessageCallback;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onErrorCallback;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> pendingMessages = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.sdgclaw.bridge.TermuxBridge.Companion Companion = null;
    
    public TermuxBridge(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope coroutineScope) {
        super();
    }
    
    /**
     * Connect to the Termux WebSocket bridge
     */
    public final void connect() {
    }
    
    /**
     * Disconnect from the bridge
     */
    public final void disconnect() {
    }
    
    /**
     * Send a message to the bridge
     */
    public final void send(@org.jetbrains.annotations.NotNull()
    java.lang.String message) {
    }
    
    /**
     * Send JSON message to bridge
     */
    public final void sendJson(@org.jetbrains.annotations.NotNull()
    org.json.JSONObject json) {
    }
    
    public final void setOnConnected(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> callback) {
    }
    
    public final void setOnDisconnected(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> callback) {
    }
    
    public final void setOnMessage(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> callback) {
    }
    
    public final void setOnError(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> callback) {
    }
    
    public final boolean isConnected() {
        return false;
    }
    
    public final boolean isConnecting() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.channels.ReceiveChannel<java.lang.String> getIncomingMessages() {
        return null;
    }
    
    private final void sendRaw(java.lang.String message) {
    }
    
    private final void handleDisconnect(java.lang.String reason) {
    }
    
    private final void scheduleReconnect() {
    }
    
    private final java.lang.Object processIncomingMessages(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object processOutgoingMessages(okhttp3.WebSocket ws, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void testConnection() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/sdgclaw/bridge/TermuxBridge$Companion;", "", "()V", "PING_INTERVAL_MS", "", "RECONNECT_DELAY_MS", "TAG", "", "WS_URL", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}