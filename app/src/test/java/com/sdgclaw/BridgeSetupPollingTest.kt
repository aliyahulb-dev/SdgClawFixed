package com.sdgclaw

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the 2-second polling state machine logic used in
 * [BridgeSetupActivity].
 *
 * These tests exercise the pure logic of the polling loop (condition evaluation,
 * timeout, state transitions) without requiring an Android runtime.
 *
 * State machine under test:
 *   IDLE → POLLING → DONE      (condition returns true before timeout)
 *              └──→ TIMED_OUT  (timeout elapses first)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeSetupPollingTest {

    // ── Mirror of the activity's PollState enum ───────────────────────────
    private enum class PollState { IDLE, POLLING, DONE, TIMED_OUT }

    // ── Minimal polling engine extracted from BridgeSetupActivity ────────

    private val POLL_INTERVAL_MS = 2_000L
    private val POLL_TIMEOUT_MS  = 120_000L  // 2 minutes

    /**
     * Minimal replication of the polling coroutine from [BridgeSetupActivity].
     * Returns the final [PollState] after the loop exits.
     *
     * @param scope       Coroutine scope in which the loop runs.
     * @param condition   Suspend lambda that returns `true` when satisfied.
     * @param onTick      Called on every poll tick with seconds remaining.
     * @param onDone      Called when condition is satisfied.
     * @param onTimeout   Called when timeout elapses.
     */
    private fun startPollingEngine(
        scope: TestScope,
        condition: suspend () -> Boolean,
        onTick: (Long) -> Unit = {},
        onDone: () -> Unit = {},
        onTimeout: () -> Unit = {},
    ): Pair<Job, () -> PollState> {
        var state = PollState.POLLING
        val job = scope.launch {
            val startTime = scope.testScheduler.currentTime
            while (isActive) {
                val elapsed = scope.testScheduler.currentTime - startTime
                if (elapsed >= POLL_TIMEOUT_MS) {
                    state = PollState.TIMED_OUT
                    onTimeout()
                    break
                }
                val satisfied = try { condition() } catch (_: Exception) { false }
                if (satisfied) {
                    state = PollState.DONE
                    onDone()
                    break
                }
                val remaining = ((POLL_TIMEOUT_MS - elapsed) / 1000).coerceAtLeast(0)
                onTick(remaining)
                delay(POLL_INTERVAL_MS)
            }
        }
        return job to { state }
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    /**
     * When the condition becomes true before the timeout the state should
     * transition to DONE.
     */
    @Test
    fun `poll transitions to DONE when condition satisfied before timeout`() = runTest {
        var conditionTrue = false
        var doneCallbackFired = false

        val (job, getState) = startPollingEngine(
            scope     = this,
            condition = { conditionTrue },
            onDone    = { doneCallbackFired = true }
        )

        // Advance past one poll interval — condition still false
        advanceTimeBy(POLL_INTERVAL_MS + 1)
        assertEquals(PollState.POLLING, getState())

        // Satisfy the condition then advance another interval
        conditionTrue = true
        advanceTimeBy(POLL_INTERVAL_MS + 1)

        job.join()
        assertEquals(PollState.DONE, getState())
        assertTrue("onDone callback must have fired", doneCallbackFired)
    }

    /**
     * When the condition is never satisfied and the timeout elapses the state
     * should transition to TIMED_OUT.
     */
    @Test
    fun `poll transitions to TIMED_OUT when condition never satisfied`() = runTest {
        var timeoutCallbackFired = false

        val (job, getState) = startPollingEngine(
            scope     = this,
            condition = { false },          // never satisfied
            onTimeout = { timeoutCallbackFired = true }
        )

        // Advance past the full timeout window
        advanceTimeBy(POLL_TIMEOUT_MS + POLL_INTERVAL_MS + 1)

        job.join()
        assertEquals(PollState.TIMED_OUT, getState())
        assertTrue("onTimeout callback must have fired", timeoutCallbackFired)
    }

    /**
     * The polling loop should fire an onTick callback every [POLL_INTERVAL_MS].
     */
    @Test
    fun `poll fires tick callback on each interval`() = runTest {
        val ticks = mutableListOf<Long>()

        val (job, _) = startPollingEngine(
            scope     = this,
            condition = { false },
            onTick    = { remaining -> ticks.add(remaining) }
        )

        // Advance 5 intervals
        advanceTimeBy(POLL_INTERVAL_MS * 5 + 1)
        job.cancel()
        job.join()

        // Should have recorded at least 4 ticks (first check is instant,
        // then each subsequent delay fires another tick)
        assertTrue("Expected at least 4 ticks, got ${ticks.size}", ticks.size >= 4)
    }

    /**
     * Cancelling the job while POLLING should stop the loop immediately
     * without transitioning to DONE or TIMED_OUT.
     */
    @Test
    fun `cancelling poll job stops the loop`() = runTest {
        var doneCallbackFired    = false
        var timeoutCallbackFired = false

        val (job, getState) = startPollingEngine(
            scope     = this,
            condition = { false },
            onDone    = { doneCallbackFired = true },
            onTimeout = { timeoutCallbackFired = true }
        )

        advanceTimeBy(POLL_INTERVAL_MS * 3 + 1)
        job.cancel()
        job.join()

        assertFalse("onDone must not fire when cancelled", doneCallbackFired)
        assertFalse("onTimeout must not fire when cancelled", timeoutCallbackFired)
        assertFalse("Job must not be active after cancel", job.isActive)
    }

    /**
     * If the condition throws an exception the loop should treat the tick as
     * unsatisfied and continue rather than crashing.
     */
    @Test
    fun `exception in condition is swallowed and loop continues`() = runTest {
        var callCount = 0

        val (job, getState) = startPollingEngine(
            scope     = this,
            condition = {
                callCount++
                if (callCount < 3) throw RuntimeException("transient error")
                true   // succeeds on third call
            }
        )

        // Advance enough for condition to be called 3+ times
        advanceTimeBy(POLL_INTERVAL_MS * 4 + 1)
        job.join()

        assertEquals(PollState.DONE, getState())
        assertTrue("Condition must have been called at least 3 times", callCount >= 3)
    }

    /**
     * Verifies that the first condition check happens immediately (no initial
     * delay) — i.e. if the condition is already true at t=0 the loop exits
     * without advancing the virtual clock.
     */
    @Test
    fun `condition satisfied immediately resolves without waiting`() = runTest {
        val (job, getState) = startPollingEngine(
            scope     = this,
            condition = { true }   // immediately true
        )

        // Advance just a tiny bit so the coroutine runs
        advanceTimeBy(1)
        job.join()

        assertEquals(PollState.DONE, getState())
    }

    /**
     * Remaining-seconds reported via onTick should decrease monotonically.
     */
    @Test
    fun `remaining seconds decrease monotonically on each tick`() = runTest {
        val remainingValues = mutableListOf<Long>()

        val (job, _) = startPollingEngine(
            scope     = this,
            condition = { false },
            onTick    = { remaining -> remainingValues.add(remaining) }
        )

        advanceTimeBy(POLL_INTERVAL_MS * 6 + 1)
        job.cancel()
        job.join()

        // Verify monotone non-increasing sequence
        for (i in 1 until remainingValues.size) {
            assertTrue(
                "Remaining seconds must be non-increasing: ${remainingValues[i - 1]} → ${remainingValues[i]}",
                remainingValues[i] <= remainingValues[i - 1]
            )
        }
    }
}
