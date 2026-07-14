package com.sdgclaw

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BridgeSetupActivity — guides the user through the four steps required to
 * connect SDG Claw to the Termux WebSocket bridge:
 *
 *   1. Node.js installed in Termux
 *   2. `ws` npm package installed
 *   3. server.js is running (bridge process active)
 *   4. WebSocket connection established (ws://127.0.0.1:8765)
 *
 * The activity shows the current state of each step via an icon + badge chip
 * and a "Retry Connection" button that triggers a fresh connection attempt.
 *
 * Step 4 (WebSocket connected) is the only step the app can verify directly;
 * steps 1–3 remain as visual guides whose state is inferred from step 4.
 */
class BridgeSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BridgeSetupActivity"

        /** Delay (ms) before probing connection state after a retry tap. */
        private const val PROBE_DELAY_MS = 1_500L
    }

    // ── Views ──────────────────────────────────────────────────────────────

    private lateinit var btnBack: ImageButton

    // Step 1 — Node.js
    private lateinit var iconNodeJs: ImageView
    private lateinit var badgeNodeJs: TextView
    private lateinit var stepNodeJs: LinearLayout

    // Step 2 — ws package
    private lateinit var iconWsPackage: ImageView
    private lateinit var badgeWsPackage: TextView
    private lateinit var stepWsPackage: LinearLayout

    // Step 3 — server.js running
    private lateinit var iconServerRunning: ImageView
    private lateinit var badgeServerRunning: TextView
    private lateinit var stepServerRunning: LinearLayout

    // Step 4 — WebSocket connected
    private lateinit var iconWsConnected: ImageView
    private lateinit var badgeWsConnected: TextView
    private lateinit var stepWsConnected: LinearLayout

    private lateinit var btnRetry: TextView

    // ── Bridge reference ───────────────────────────────────────────────────

    private val bridge: TermuxBridge?
        get() = (application as? SDGClawApplication)?.termuxBridge

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bridge_setup)

        initViews()
        setupListeners()
        refreshStepStates()
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time the user comes back (e.g. after switching to Termux).
        refreshStepStates()
    }

    // ── View binding ───────────────────────────────────────────────────────

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)

        iconNodeJs = findViewById(R.id.iconNodeJs)
        badgeNodeJs = findViewById(R.id.badgeNodeJs)
        stepNodeJs = findViewById(R.id.stepNodeJs)

        iconWsPackage = findViewById(R.id.iconWsPackage)
        badgeWsPackage = findViewById(R.id.badgeWsPackage)
        stepWsPackage = findViewById(R.id.stepWsPackage)

        iconServerRunning = findViewById(R.id.iconServerRunning)
        badgeServerRunning = findViewById(R.id.badgeServerRunning)
        stepServerRunning = findViewById(R.id.stepServerRunning)

        iconWsConnected = findViewById(R.id.iconWsConnected)
        badgeWsConnected = findViewById(R.id.badgeWsConnected)
        stepWsConnected = findViewById(R.id.stepWsConnected)

        btnRetry = findViewById(R.id.btnRetryConnection)
    }

    // ── Listeners ──────────────────────────────────────────────────────────

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnRetry.setOnClickListener {
            onRetryTapped()
        }
    }

    // ── Step state logic ───────────────────────────────────────────────────

    /**
     * Render the current step states based on the live bridge connection.
     *
     * Steps 1–3 are prerequisites that the app cannot directly probe; their
     * state is inferred:
     *   - All show DONE when the WebSocket connection succeeds (step 4 = DONE).
     *   - Steps 1–3 show PENDING until step 4 is confirmed; they are never
     *     individually marked ERROR because we cannot tell which one failed.
     *   - Step 4 mirrors the real bridge [isConnected] flag.
     */
    private fun refreshStepStates() {
        val connected = bridge?.isConnected() ?: false

        if (connected) {
            // All four steps verified — show every row as done.
            applyStepDone(iconNodeJs, badgeNodeJs, stepNodeJs)
            applyStepDone(iconWsPackage, badgeWsPackage, stepWsPackage)
            applyStepDone(iconServerRunning, badgeServerRunning, stepServerRunning)
            applyStepDone(iconWsConnected, badgeWsConnected, stepWsConnected)
        } else {
            // Connection not established — prerequisite steps are pending;
            // step 4 (the one we can verify) is explicitly in error.
            applyStepPending(iconNodeJs, badgeNodeJs, stepNodeJs)
            applyStepPending(iconWsPackage, badgeWsPackage, stepWsPackage)
            applyStepPending(iconServerRunning, badgeServerRunning, stepServerRunning)
            applyStepError(iconWsConnected, badgeWsConnected, stepWsConnected)
        }
    }

    // ── Retry ──────────────────────────────────────────────────────────────

    private fun onRetryTapped() {
        // Disable button briefly to prevent rapid tapping.
        btnRetry.isEnabled = false

        // Kick off a fresh connection attempt via the application-owned bridge.
        bridge?.connect()

        // Wait a moment then re-read state and re-enable the button.
        lifecycleScope.launch {
            delay(PROBE_DELAY_MS)
            refreshStepStates()
            btnRetry.isEnabled = true
        }
    }

    // ── Step appearance helpers ────────────────────────────────────────────

    /**
     * Render a step row in the PENDING state:
     * hollow-circle icon, gray badge labelled "PENDING".
     */
    private fun applyStepPending(icon: ImageView, badge: TextView, row: View) {
        icon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_step_pending))

        badge.text = getString(R.string.badge_pending)
        badge.setTextColor(ContextCompat.getColor(this, R.color.badge_pending_text))
        badge.backgroundTintList = null          // let the bg_badge_pending drawable colour show
        badge.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_pending)

        row.alpha = 1f
    }

    /**
     * Render a step row in the DONE state:
     * green-check icon, green badge labelled "DONE".
     */
    private fun applyStepDone(icon: ImageView, badge: TextView, row: View) {
        icon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_step_done))

        badge.text = getString(R.string.badge_done)
        badge.setTextColor(ContextCompat.getColor(this, R.color.badge_done_text))
        badge.backgroundTintList = null
        badge.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_done)

        row.alpha = 1f
    }

    /**
     * Render a step row in the ERROR state:
     * red-X icon, red badge labelled "ERROR".
     */
    private fun applyStepError(icon: ImageView, badge: TextView, row: View) {
        icon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_step_error))

        badge.text = getString(R.string.badge_error)
        badge.setTextColor(ContextCompat.getColor(this, R.color.badge_error_text))
        badge.backgroundTintList = null
        badge.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_error)

        row.alpha = 1f
    }
}
