package com.sdgclaw;

/**
 * ToolRegistry - Manages available tools for the agent
 * Tools can be local (Kotlin functions) or remote (via Termux bridge)
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010#\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\f\u0018\u0000 %2\u00020\u0001:\u0003%&\'B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\u000f\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010\u0011\u001a\u00020\tH\u0002J\u001e\u0010\u0012\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010\u0011\u001a\u00020\tH\u0086@\u00a2\u0006\u0002\u0010\u0013J\f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00070\u0015J\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u0010\u001a\u00020\u0007J\f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\t0\u0015J(\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u00072\u0006\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u00072\b\u0010\u001e\u001a\u0004\u0018\u00010\u0007J\b\u0010\u001f\u001a\u00020\u0019H\u0002J2\u0010 \u001a\u00020\u00192\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010!\u001a\u00020\u00072\u0006\u0010\"\u001a\u00020\t2\u0012\u0010#\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\n0\bJ\u001e\u0010$\u001a\u00020\u00192\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010!\u001a\u00020\u00072\u0006\u0010\"\u001a\u00020\tR&\u0010\u0005\u001a\u001a\u0012\u0004\u0012\u00020\u0007\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\n0\b0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00070\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\r\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u000e0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006("}, d2 = {"Lcom/sdgclaw/ToolRegistry;", "", "termuxBridge", "Lcom/sdgclaw/bridge/TermuxBridge;", "(Lcom/sdgclaw/bridge/TermuxBridge;)V", "localTools", "", "", "Lkotlin/Function1;", "Lorg/json/JSONObject;", "Lcom/sdgclaw/ToolRegistry$ToolResult;", "remoteTools", "", "toolDefinitions", "Lcom/sdgclaw/ToolRegistry$ToolDefinition;", "executeRemoteTool", "name", "arguments", "executeTool", "(Ljava/lang/String;Lorg/json/JSONObject;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAvailableTools", "", "getToolDefinition", "getToolDefinitions", "handleToolResult", "", "requestId", "success", "", "output", "error", "registerBuiltinTools", "registerLocalTool", "description", "parameters", "executor", "registerRemoteTool", "Companion", "ToolDefinition", "ToolResult", "app_debug"})
public final class ToolRegistry {
    @org.jetbrains.annotations.NotNull()
    private final com.sdgclaw.bridge.TermuxBridge termuxBridge = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ToolRegistry";
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, kotlin.jvm.functions.Function1<org.json.JSONObject, com.sdgclaw.ToolRegistry.ToolResult>> localTools = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Set<java.lang.String> remoteTools = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, com.sdgclaw.ToolRegistry.ToolDefinition> toolDefinitions = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.sdgclaw.ToolRegistry.Companion Companion = null;
    
    public ToolRegistry(@org.jetbrains.annotations.NotNull()
    com.sdgclaw.bridge.TermuxBridge termuxBridge) {
        super();
    }
    
    /**
     * Register a local tool (executed in Kotlin)
     */
    public final void registerLocalTool(@org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.NotNull()
    java.lang.String description, @org.jetbrains.annotations.NotNull()
    org.json.JSONObject parameters, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super org.json.JSONObject, com.sdgclaw.ToolRegistry.ToolResult> executor) {
    }
    
    /**
     * Register a remote tool (executed via Termux bridge)
     */
    public final void registerRemoteTool(@org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.NotNull()
    java.lang.String description, @org.jetbrains.annotations.NotNull()
    org.json.JSONObject parameters) {
    }
    
    /**
     * Execute a tool by name
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object executeTool(@org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.NotNull()
    org.json.JSONObject arguments, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    /**
     * Execute tool via Termux bridge (async, returns immediately)
     * For synchronous result, the bridge would need request/response correlation
     */
    private final java.lang.String executeRemoteTool(java.lang.String name, org.json.JSONObject arguments) {
        return null;
    }
    
    /**
     * Get all tool definitions for LLM function calling
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<org.json.JSONObject> getToolDefinitions() {
        return null;
    }
    
    /**
     * Get tool definition by name
     */
    @org.jetbrains.annotations.Nullable()
    public final com.sdgclaw.ToolRegistry.ToolDefinition getToolDefinition(@org.jetbrains.annotations.NotNull()
    java.lang.String name) {
        return null;
    }
    
    /**
     * List all available tools
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> getAvailableTools() {
        return null;
    }
    
    /**
     * Register built-in tools
     */
    private final void registerBuiltinTools() {
    }
    
    /**
     * Handle tool result from Termux bridge (call this when bridge receives tool response)
     */
    public final void handleToolResult(@org.jetbrains.annotations.NotNull()
    java.lang.String requestId, boolean success, @org.jetbrains.annotations.NotNull()
    java.lang.String output, @org.jetbrains.annotations.Nullable()
    java.lang.String error) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/sdgclaw/ToolRegistry$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\b\b\u0002\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tJ\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0013\u001a\u00020\bH\u00c6\u0003J1\u0010\u0014\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\bH\u00c6\u0001J\u0013\u0010\u0015\u001a\u00020\b2\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001J\t\u0010\u0019\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000bR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u001a"}, d2 = {"Lcom/sdgclaw/ToolRegistry$ToolDefinition;", "", "name", "", "description", "parameters", "Lorg/json/JSONObject;", "isLocal", "", "(Ljava/lang/String;Ljava/lang/String;Lorg/json/JSONObject;Z)V", "getDescription", "()Ljava/lang/String;", "()Z", "getName", "getParameters", "()Lorg/json/JSONObject;", "component1", "component2", "component3", "component4", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class ToolDefinition {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String description = null;
        @org.jetbrains.annotations.NotNull()
        private final org.json.JSONObject parameters = null;
        private final boolean isLocal = false;
        
        public ToolDefinition(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        java.lang.String description, @org.jetbrains.annotations.NotNull()
        org.json.JSONObject parameters, boolean isLocal) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getDescription() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final org.json.JSONObject getParameters() {
            return null;
        }
        
        public final boolean isLocal() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final org.json.JSONObject component3() {
            return null;
        }
        
        public final boolean component4() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.sdgclaw.ToolRegistry.ToolDefinition copy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        java.lang.String description, @org.jetbrains.annotations.NotNull()
        org.json.JSONObject parameters, boolean isLocal) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000e\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0005H\u00c6\u0003J\u000b\u0010\u000f\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J)\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00032\b\u0010\u0012\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0005H\u00d6\u0001R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0016"}, d2 = {"Lcom/sdgclaw/ToolRegistry$ToolResult;", "", "success", "", "output", "", "error", "(ZLjava/lang/String;Ljava/lang/String;)V", "getError", "()Ljava/lang/String;", "getOutput", "getSuccess", "()Z", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class ToolResult {
        private final boolean success = false;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String output = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String error = null;
        
        public ToolResult(boolean success, @org.jetbrains.annotations.NotNull()
        java.lang.String output, @org.jetbrains.annotations.Nullable()
        java.lang.String error) {
            super();
        }
        
        public final boolean getSuccess() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getOutput() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getError() {
            return null;
        }
        
        public final boolean component1() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.sdgclaw.ToolRegistry.ToolResult copy(boolean success, @org.jetbrains.annotations.NotNull()
        java.lang.String output, @org.jetbrains.annotations.Nullable()
        java.lang.String error) {
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
}