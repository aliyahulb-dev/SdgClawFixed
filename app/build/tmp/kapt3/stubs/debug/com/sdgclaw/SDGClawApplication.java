package com.sdgclaw;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0007\u001a\u00020\u0004J\b\u0010\b\u001a\u0004\u0018\u00010\u0006J\b\u0010\t\u001a\u00020\nH\u0016J\b\u0010\u000b\u001a\u00020\nH\u0016R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2 = {"Lcom/sdgclaw/SDGClawApplication;", "Landroid/app/Application;", "()V", "coroutineScope", "Lkotlinx/coroutines/CoroutineScope;", "termuxBridge", "Lcom/sdgclaw/bridge/TermuxBridge;", "getCoroutineScope", "getTermuxBridge", "onCreate", "", "onTerminate", "app_debug"})
public final class SDGClawApplication extends android.app.Application {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope coroutineScope = null;
    @org.jetbrains.annotations.Nullable()
    private com.sdgclaw.bridge.TermuxBridge termuxBridge;
    
    public SDGClawApplication() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.sdgclaw.bridge.TermuxBridge getTermuxBridge() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.CoroutineScope getCoroutineScope() {
        return null;
    }
    
    @java.lang.Override()
    public void onTerminate() {
    }
}