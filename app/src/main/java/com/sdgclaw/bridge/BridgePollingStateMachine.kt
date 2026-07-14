package com.sdgclaw.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BridgePollingStateMachine — monitors bridge connectivity and triggers
 * automatic recovery via [TermuxDeepLinkHelper] when the bridge is unreachable.
 *
 * States:
 *  [PollingState.DISCONNECTED]  — no connection; recovery not yet attempted (or exhausted)
 *  [PollingState.CONNECTING]    — WebSocket connect attempt in progress
 *  [PollingState.CONNECTED]     — bridge is reachable and responding
 *  [PollingState.RECOVERING]    — deep-link fired; waiting for bridge to come up
 *
 * Expose [status] as a [StateFlow] for UI observation.
 */
class BridgePollingStateMachine(
    private val context: Context,
    private val bridge: TermuxBridge,
    private val coroutineScope: CoroutineScope,
    private val pollIntervalConnectedMs: Long   = 10_000L,
    private val pollIntervalRecoveringMs: Long  = 3_000L,
    private val maxRecoveryAttempts: Int        = 3,
    private val bridgeStartCommand: String      =
        "cd ~/sdgclaw-bridge && node server.js &"
) {

    companion object {
        private const val TAG = "BridgePollingFSM"
    }

    enum class PollingState { DISCONNECTED, CONNECTING, CONNECTED, RECOVERING }

    data class PollingStatus(
        val state: PollingState,
        val lastCheckedMs: Long,
        val recoveryAttempts: Int
    )

    private val _status = MutableStateFlow(
        PollingStatus(
            state            = PollingState.DISCONNECTED,
            lastCheckedMs    = 0L,
            recoveryAttempts = 0
        )
    )
    val status: StateFlow<PollingStatus> get() = _status

    private var pollingJob: Job? = null

    // ── Public API ─────────────────────────────────────────────────────────────

    fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = coroutineScope.launch { pollingLoop() }
        Log.i(TAG, "Polling started")
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        Log.i(TAG, "Polling stopped")
    }

    // ── Polling loop ───────────────────────────────────────────────────────────

    private suspend fun pollingLoop() {
        var recoveryAttempts = 0

        while (coroutineScope.isActive) {
            val now = System.currentTimeMillis()

            if (bridge.isConnected) {
                // Happy path
                emit(PollingState.CONNECTED, now, 0)
                recoveryAttempts = 0
                delay(pollIntervalConnectedMs)
                continue
            }

            // Bridge not connected
            emit(PollingState.CONNECTING, now, recoveryAttempts)

            // Give the bridge a moment to self-reconnect
            delay(pollIntervalRecoveringMs)

            if (bridge.isConnected) {
                emit(PollingState.CONNECTED, System.currentTimeMillis(), 0)
                recoveryAttempts = 0
                delay(pollIntervalConnectedMs)
                continue
            }

            // Still not connected — attempt recovery
            if (recoveryAttempts < maxRecoveryAttempts) {
                recoveryAttempts++
                Log.w(TAG, "Bridge unreachable — firing deep link (attempt $recoveryAttempts)")
                emit(PollingState.RECOVERING, System.currentTimeMillis(), recoveryAttempts)

                withContext(Dispatchers.Main) {
                    TermuxDeepLinkHelper.runCommand(context, bridgeStartCommand)
                }

                // Re-connect the WebSocket from our side too
                bridge.connect()

                delay(pollIntervalRecoveringMs * 2)
            } else {
                // Exhausted — surface DISCONNECTED and wait longer before retrying
                Log.e(TAG, "Max recovery attempts ($maxRecoveryAttempts) exhausted")
                emit(PollingState.DISCONNECTED, System.currentTimeMillis(), recoveryAttempts)
                delay(pollIntervalConnectedMs * 3)
                // Reset counter so user can trigger recovery again after a long wait
                recoveryAttempts = 0
            }
        }
    }

    private fun emit(state: PollingState, timeMs: Long, attempts: Int) {
        _status.value = PollingStatus(state, timeMs, attempts)
    }
}
