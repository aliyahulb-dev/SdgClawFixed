package com.sdgclaw;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u000b\u0018\u00002\u00020\u0001:\u0003()*B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u000fH\u0002J\b\u0010\u001b\u001a\u00020\u0019H\u0002J\b\u0010\u001c\u001a\u00020\u0019H\u0002J\b\u0010\u001d\u001a\u00020\u0019H\u0002J\u0012\u0010\u001e\u001a\u00020\u00192\b\u0010\u001f\u001a\u0004\u0018\u00010 H\u0014J\b\u0010!\u001a\u00020\u0019H\u0014J\b\u0010\"\u001a\u00020\u0019H\u0002J\b\u0010#\u001a\u00020\u0019H\u0002J\b\u0010$\u001a\u00020\u0019H\u0002J\b\u0010%\u001a\u00020\u0019H\u0002J\b\u0010&\u001a\u00020\u0019H\u0002J\b\u0010\'\u001a\u00020\u0019H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0017X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006+"}, d2 = {"Lcom/sdgclaw/ChatActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "adapter", "Lcom/sdgclaw/ChatActivity$MessageAdapter;", "agentLoop", "Lcom/sdgclaw/AgentLoop;", "btnSend", "Landroid/widget/ImageButton;", "etMessage", "Landroid/widget/EditText;", "llmClient", "Lcom/sdgclaw/LLMClient;", "messages", "", "Lcom/sdgclaw/ChatActivity$Message;", "rvMessages", "Landroidx/recyclerview/widget/RecyclerView;", "termuxBridge", "Lcom/sdgclaw/bridge/TermuxBridge;", "toolRegistry", "Lcom/sdgclaw/ToolRegistry;", "typingIndicator", "Landroid/widget/LinearLayout;", "addMessage", "", "message", "hideTyping", "initAgent", "initViews", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "sendMessage", "setupAgentCallbacks", "setupInput", "setupRecyclerView", "setupToolbar", "showTyping", "Message", "MessageAdapter", "Type", "app_debug"})
public final class ChatActivity extends androidx.appcompat.app.AppCompatActivity {
    private androidx.recyclerview.widget.RecyclerView rvMessages;
    private android.widget.EditText etMessage;
    private android.widget.ImageButton btnSend;
    private android.widget.LinearLayout typingIndicator;
    private com.sdgclaw.ChatActivity.MessageAdapter adapter;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.sdgclaw.ChatActivity.Message> messages = null;
    @org.jetbrains.annotations.Nullable()
    private com.sdgclaw.AgentLoop agentLoop;
    @org.jetbrains.annotations.Nullable()
    private com.sdgclaw.LLMClient llmClient;
    @org.jetbrains.annotations.Nullable()
    private com.sdgclaw.ToolRegistry toolRegistry;
    @org.jetbrains.annotations.Nullable()
    private com.sdgclaw.bridge.TermuxBridge termuxBridge;
    
    public ChatActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void initViews() {
    }
    
    private final void setupToolbar() {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void setupInput() {
    }
    
    private final void initAgent() {
    }
    
    private final void setupAgentCallbacks() {
    }
    
    private final void sendMessage() {
    }
    
    private final void addMessage(com.sdgclaw.ChatActivity.Message message) {
    }
    
    private final void showTyping() {
    }
    
    private final void hideTyping() {
    }
    
    @java.lang.Override()
    protected void onDestroy() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0007H\u00c6\u0003J\'\u0010\u0012\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0019"}, d2 = {"Lcom/sdgclaw/ChatActivity$Message;", "", "type", "Lcom/sdgclaw/ChatActivity$Type;", "content", "", "timestamp", "", "(Lcom/sdgclaw/ChatActivity$Type;Ljava/lang/String;J)V", "getContent", "()Ljava/lang/String;", "getTimestamp", "()J", "getType", "()Lcom/sdgclaw/ChatActivity$Type;", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Message {
        @org.jetbrains.annotations.NotNull()
        private final com.sdgclaw.ChatActivity.Type type = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String content = null;
        private final long timestamp = 0L;
        
        public Message(@org.jetbrains.annotations.NotNull()
        com.sdgclaw.ChatActivity.Type type, @org.jetbrains.annotations.NotNull()
        java.lang.String content, long timestamp) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.sdgclaw.ChatActivity.Type getType() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getContent() {
            return null;
        }
        
        public final long getTimestamp() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.sdgclaw.ChatActivity.Type component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        public final long component3() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.sdgclaw.ChatActivity.Message copy(@org.jetbrains.annotations.NotNull()
        com.sdgclaw.ChatActivity.Type type, @org.jetbrains.annotations.NotNull()
        java.lang.String content, long timestamp) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0011B\u0013\u0012\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\u0002\u0010\u0006J\b\u0010\u0007\u001a\u00020\bH\u0016J\u0018\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u00022\u0006\u0010\f\u001a\u00020\bH\u0016J\u0018\u0010\r\u001a\u00020\u00022\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\bH\u0016R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0012"}, d2 = {"Lcom/sdgclaw/ChatActivity$MessageAdapter;", "Landroidx/recyclerview/widget/RecyclerView$Adapter;", "Lcom/sdgclaw/ChatActivity$MessageAdapter$ViewHolder;", "messages", "", "Lcom/sdgclaw/ChatActivity$Message;", "(Ljava/util/List;)V", "getItemCount", "", "onBindViewHolder", "", "holder", "position", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "ViewHolder", "app_debug"})
    public static final class MessageAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<com.sdgclaw.ChatActivity.MessageAdapter.ViewHolder> {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.sdgclaw.ChatActivity.Message> messages = null;
        
        public MessageAdapter(@org.jetbrains.annotations.NotNull()
        java.util.List<com.sdgclaw.ChatActivity.Message> messages) {
            super();
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public com.sdgclaw.ChatActivity.MessageAdapter.ViewHolder onCreateViewHolder(@org.jetbrains.annotations.NotNull()
        android.view.ViewGroup parent, int viewType) {
            return null;
        }
        
        @java.lang.Override()
        public void onBindViewHolder(@org.jetbrains.annotations.NotNull()
        com.sdgclaw.ChatActivity.MessageAdapter.ViewHolder holder, int position) {
        }
        
        @java.lang.Override()
        public int getItemCount() {
            return 0;
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u000f\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\bR\u0011\u0010\u000b\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\bR\u0011\u0010\r\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0011\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u0011\u0010\u0013\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0010R\u0011\u0010\u0015\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0010R\u0011\u0010\u0017\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0010R\u0011\u0010\u0019\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0010R\u0011\u0010\u001b\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0010\u00a8\u0006\u001d"}, d2 = {"Lcom/sdgclaw/ChatActivity$MessageAdapter$ViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "view", "Landroid/view/View;", "(Landroid/view/View;)V", "llAssistantMessage", "Landroid/widget/LinearLayout;", "getLlAssistantMessage", "()Landroid/widget/LinearLayout;", "llToolMessage", "getLlToolMessage", "llUserMessage", "getLlUserMessage", "tvAssistantMessage", "Landroid/widget/TextView;", "getTvAssistantMessage", "()Landroid/widget/TextView;", "tvAssistantTime", "getTvAssistantTime", "tvSystemMessage", "getTvSystemMessage", "tvToolMessage", "getTvToolMessage", "tvToolTime", "getTvToolTime", "tvUserMessage", "getTvUserMessage", "tvUserTime", "getTvUserTime", "app_debug"})
        public static final class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            @org.jetbrains.annotations.NotNull()
            private final android.widget.LinearLayout llUserMessage = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvUserMessage = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvUserTime = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.LinearLayout llAssistantMessage = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvAssistantMessage = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvAssistantTime = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.LinearLayout llToolMessage = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvToolMessage = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvToolTime = null;
            @org.jetbrains.annotations.NotNull()
            private final android.widget.TextView tvSystemMessage = null;
            
            public ViewHolder(@org.jetbrains.annotations.NotNull()
            android.view.View view) {
                super(null);
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.LinearLayout getLlUserMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvUserMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvUserTime() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.LinearLayout getLlAssistantMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvAssistantMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvAssistantTime() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.LinearLayout getLlToolMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvToolMessage() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvToolTime() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final android.widget.TextView getTvSystemMessage() {
                return null;
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/sdgclaw/ChatActivity$Type;", "", "(Ljava/lang/String;I)V", "USER", "ASSISTANT", "TOOL", "SYSTEM", "app_debug"})
    public static enum Type {
        /*public static final*/ USER /* = new USER() */,
        /*public static final*/ ASSISTANT /* = new ASSISTANT() */,
        /*public static final*/ TOOL /* = new TOOL() */,
        /*public static final*/ SYSTEM /* = new SYSTEM() */;
        
        Type() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.sdgclaw.ChatActivity.Type> getEntries() {
            return null;
        }
    }
}