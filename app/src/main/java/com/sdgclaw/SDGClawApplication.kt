package com.sdgclaw

import android.app.Application
import android.util.Log
import com.sdgclaw.bridge.BridgePollingStateMachine
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * SDGClawApplication — singleton Application class.
 *
 * Owns the [TermuxBridge] and [BridgePollingStateMachine] lifecycles;
 * exposes them to Activities via typed cast of [getApplication()].
 */
class SDGClawApplication : Application() {

    companion object {
        private const val TAG = "SDGClawApplication"
    }

    /** Application-scoped coroutine scope (survives Activity recreation). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var termuxBridge: TermuxBridge
    lateinit var pollingStateMachine: BridgePollingStateMachine
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate — initialising bridge")

        termuxBridge = TermuxBridge()
        termuxBridge.connect()

        pollingStateMachine = BridgePollingStateMachine(
            context      = this,
            bridge       = termuxBridge,
            coroutineScope = appScope
        )
        pollingStateMachine.start()
    }

    /** Retrieve the singleton [TermuxBridge] instance. */
    fun getTermuxBridge(): TermuxBridge = termuxBridge
}
