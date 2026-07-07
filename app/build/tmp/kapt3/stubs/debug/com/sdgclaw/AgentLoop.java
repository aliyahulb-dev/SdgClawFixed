package com.sdgclaw;

/**
 * AgentLoop - Core agent execution loop
 * Handles the conversation flow between user, LLM, and tools
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000v\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0010$\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0017\u0018\u0000 ;2\u00020\u0001:\u00039:;B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\u001a\u0010 \u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u00010\"0!H\u0002J\u0006\u0010#\u001a\u00020\u0017J\f\u0010$\u001a\b\u0012\u0004\u0012\u00020\r0%J\f\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00100!J\b\u0010\'\u001a\u00020\rH\u0002J\u0010\u0010(\u001a\u00020\u00172\u0006\u0010)\u001a\u00020\rH\u0002J\u000e\u0010*\u001a\u00020\u0017H\u0082@\u00a2\u0006\u0002\u0010+J\u000e\u0010,\u001a\u00020\u0017H\u0082@\u00a2\u0006\u0002\u0010+J\u000e\u0010-\u001a\u00020\u00172\u0006\u0010.\u001a\u00020\rJ\u001a\u0010/\u001a\u00020\u00172\u0012\u00100\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u00170\u0016J\u001a\u00101\u001a\u00020\u00172\u0012\u00100\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u00170\u0016J\u001a\u00102\u001a\u00020\u00172\u0012\u00100\u001a\u000e\u0012\u0004\u0012\u00020\u001a\u0012\u0004\u0012\u00020\u00170\u0016J \u00103\u001a\u00020\u00172\u0018\u00100\u001a\u0014\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u001d\u0012\u0004\u0012\u00020\u00170\u001cJ \u00104\u001a\u00020\u00172\u0018\u00100\u001a\u0014\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u00170\u001cJ\u0010\u00105\u001a\u00020\u00172\u0006\u00106\u001a\u00020\u001aH\u0002J\u0006\u00107\u001a\u00020\u0017J\u0006\u00108\u001a\u00020\u0017R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0015\u001a\u0010\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u0017\u0018\u00010\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0018\u001a\u0010\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u0017\u0018\u00010\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0019\u001a\u0010\u0012\u0004\u0012\u00020\u001a\u0012\u0004\u0012\u00020\u0017\u0018\u00010\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\"\u0010\u001b\u001a\u0016\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u001d\u0012\u0004\u0012\u00020\u0017\u0018\u00010\u001cX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\"\u0010\u001e\u001a\u0016\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u0017\u0018\u00010\u001cX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\r0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006<"}, d2 = {"Lcom/sdgclaw/AgentLoop;", "", "coroutineScope", "Lkotlinx/coroutines/CoroutineScope;", "llmClient", "Lcom/sdgclaw/LLMClient;", "toolRegistry", "Lcom/sdgclaw/ToolRegistry;", "termuxBridge", "Lcom/sdgclaw/bridge/TermuxBridge;", "(Lkotlinx/coroutines/CoroutineScope;Lcom/sdgclaw/LLMClient;Lcom/sdgclaw/ToolRegistry;Lcom/sdgclaw/bridge/TermuxBridge;)V", "agentResponses", "Lkotlinx/coroutines/channels/Channel;", "", "conversationHistory", "", "Lcom/sdgclaw/AgentLoop$ChatMessage;", "currentIteration", "", "isRunning", "", "onAgentResponse", "Lkotlin/Function1;", "", "onError", "onStateChange", "Lcom/sdgclaw/AgentLoop$AgentState;", "onToolCall", "Lkotlin/Function2;", "Lorg/json/JSONObject;", "onToolResult", "userMessages", "buildLLMMessages", "", "", "clearHistory", "getAgentResponses", "Lkotlinx/coroutines/channels/ReceiveChannel;", "getHistory", "getSystemPrompt", "handleError", "error", "processUserMessages", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "runAgentTurn", "sendUserMessage", "message", "setOnAgentResponse", "callback", "setOnError", "setOnStateChange", "setOnToolCall", "setOnToolResult", "setState", "state", "start", "stop", "AgentState", "ChatMessage", "Companion", "app_debug"})
public final class AgentLoop {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope coroutineScope = null;
    @org.jetbrains.annotations.NotNull()
    private final com.sdgclaw.LLMClient llmClient = null;
    @org.jetbrains.annotations.NotNull()
    private final com.sdgclaw.ToolRegistry toolRegistry = null;
    @org.jetbrains.annotations.NotNull()
    private final com.sdgclaw.bridge.TermuxBridge termuxBridge = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "AgentLoop";
    private static final int MAX_ITERATIONS = 10;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.channels.Channel<java.lang.String> userMessages = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.channels.Channel<java.lang.String> agentResponses = null;
    private boolean isRunning = false;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<com.sdgclaw.AgentLoop.ChatMessage> conversationHistory;
    private int currentIteration = 0;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onAgentResponse;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function2<? super java.lang.String, ? super org.json.JSONObject, kotlin.Unit> onToolCall;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function2<? super java.lang.String, ? super java.lang.String, kotlin.Unit> onToolResult;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onError;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super com.sdgclaw.AgentLoop.AgentState, kotlin.Unit> onStateChange;
    @org.jetbrains.annotations.NotNull()
    public static final com.sdgclaw.AgentLoop.Companion Companion = null;
    
    public AgentLoop(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope coroutineScope, @org.jetbrains.annotations.NotNull()
    com.sdgclaw.LLMClient llmClient, @org.jetbrains.annotations.NotNull()
    com.sdgclaw.ToolRegistry toolRegistry, @org.jetbrains.annotations.NotNull()
    com.sdgclaw.bridge.TermuxBridge termuxBridge) {
        super();
    }
    
    public final void start() {
    }
    
    public final void stop() {
    }
    
    public final void sendUserMessage(@org.jetbrains.annotations.NotNull()
    java.lang.String message) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.channels.ReceiveChannel<java.lang.String> getAgentResponses() {
        return null;
    }
    
    public final void setOnAgentResponse(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> callback) {
    }
    
    public final void setOnToolCall(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super java.lang.String, ? super org.json.JSONObject, kotlin.Unit> callback) {
    }
    
    public final void setOnToolResult(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function2<? super java.lang.String, ? super java.lang.String, kotlin.Unit> callback) {
    }
    
    public final void setOnError(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> callback) {
    }
    
    public final void setOnStateChange(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.sdgclaw.AgentLoop.AgentState, kotlin.Unit> callback) {
    }
    
    private final java.lang.Object processUserMessages(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object runAgentTurn(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.util.List<java.util.Map<java.lang.String, java.lang.Object>> buildLLMMessages() {
        return null;
    }
    
    private final java.lang.String getSystemPrompt() {
        return null;
    }
    
    private final void handleError(java.lang.String error) {
    }
    
    private final void setState(com.sdgclaw.AgentLoop.AgentState state) {
    }
    
    public final void clearHistory() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.sdgclaw.AgentLoop.ChatMessage> getHistory() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\b\u00a8\u0006\t"}, d2 = {"Lcom/sdgclaw/AgentLoop$AgentState;", "", "(Ljava/lang/String;I)V", "IDLE", "THINKING", "CALLING_TOOL", "WAITING_FOR_TOOL", "RESPONDING", "ERROR", "app_debug"})
    public static enum AgentState {
        /*public static final*/ IDLE /* = new IDLE() */,
        /*public static final*/ THINKING /* = new THINKING() */,
        /*public static final*/ CALLING_TOOL /* = new CALLING_TOOL() */,
        /*public static final*/ WAITING_FOR_TOOL /* = new WAITING_FOR_TOOL() */,
        /*public static final*/ RESPONDING /* = new RESPONDING() */,
        /*public static final*/ ERROR /* = new ERROR() */;
        
        AgentState() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.sdgclaw.AgentLoop.AgentState> getEntries() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u000f\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010\u0010\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J5\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\t\u00a8\u0006\u0018"}, d2 = {"Lcom/sdgclaw/AgentLoop$ChatMessage;", "", "role", "", "content", "toolCallId", "toolName", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "getContent", "()Ljava/lang/String;", "getRole", "getToolCallId", "getToolName", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class ChatMessage {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String role = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String content = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String toolCallId = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String toolName = null;
        
        public ChatMessage(@org.jetbrains.annotations.NotNull()
        java.lang.String role, @org.jetbrains.annotations.NotNull()
        java.lang.String content, @org.jetbrains.annotations.Nullable()
        java.lang.String toolCallId, @org.jetbrains.annotations.Nullable()
        java.lang.String toolName) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getRole() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getContent() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getToolCallId() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getToolName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.sdgclaw.AgentLoop.ChatMessage copy(@org.jetbrains.annotations.NotNull()
        java.lang.String role, @org.jetbrains.annotations.NotNull()
        java.lang.String content, @org.jetbrains.annotations.Nullable()
        java.lang.String toolCallId, @org.jetbrains.annotations.Nullable()
        java.lang.String toolName) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/sdgclaw/AgentLoop$Companion;", "", "()V", "MAX_ITERATIONS", "", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}