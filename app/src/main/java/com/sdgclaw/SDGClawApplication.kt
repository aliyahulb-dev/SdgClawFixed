package com.sdgclaw

import android.app.Application
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SDGClawApplication : Application() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var termuxBridge: TermuxBridge? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize Termux Bridge
        termuxBridge = TermuxBridge(this, coroutineScope)

        // Auto-connect to bridge on app start
        termuxBridge?.connect()
    }

    fun getTermuxBridge(): TermuxBridge? = termuxBridge

    fun getCoroutineScope(): CoroutineScope = coroutineScope

    override fun onTerminate() {
        termuxBridge?.disconnect()
        super.onTerminate()
    }
}
