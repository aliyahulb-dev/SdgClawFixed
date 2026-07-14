package com.sdgclaw

import android.app.Application
import android.util.Log
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * SDGClawApplication — singleton Application class.
 *
 * Owns the [TermuxBridge] lifecycle so that a single WebSocket connection is
 * shared across all Activities.  Activities retrieve the bridge via:
 *
 *     (application as SDGClawApplication).bridge
 *
 * A [CoroutineScope] backed by [SupervisorJob] is exposed for app-wide
 * background work that outlives any individual Activity.
 */
class SDGClawApplication : Application() {

    companion object {
        private const val TAG = "SDGClawApplication"
    }

    // ── App-wide coroutine scope ───────────────────────────────────────────
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Termux bridge (single shared instance) ────────────────────────────
    lateinit var bridge: TermuxBridge
        private set

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created.")

        bridge = TermuxBridge()

        // Set up bridge callbacks before attempting to connect
        bridge.onConnected = {
            Log.d(TAG, "Bridge connected.")
        }
        bridge.onDisconnected = {
            Log.d(TAG, "Bridge disconnected.")
        }
        bridge.onError = { err ->
            Log.e(TAG, "Bridge error: $err")
        }

        // Attempt initial connection; bridge will auto-reconnect on failure
        bridge.connect()
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application terminating — disconnecting bridge.")
        bridge.disconnect()
    }
}
