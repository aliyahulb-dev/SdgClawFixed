package com.sdgclaw

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * BridgeSetupActivity — Step-by-step guided setup wizard for the Termux WebSocket bridge.
 *
 * Steps:
 *  0  Install Termux (from F-Droid)
 *  1  Install Node.js in Termux
 *  2  Copy setup.sh asset to device storage (auto-performed on arrival)
 *  3  Launch Termux and run setup.sh via deep-link; 2 s polling loop verifies file copied
 *  4  Start the bridge server; 2 s polling loop verifies WebSocket connection
 *
 * Polling state machine:
 *  - IDLE        → not polling
 *  - POLLING     → coroutine running, checking every POLL_INTERVAL_MS
 *  - DONE        → condition met, polling stopped
 *  - TIMED_OUT   → POLL_TIMEOUT_MS elapsed without condition, polling stopped
 *
 * Deep-link:
 *  Intent(Intent.ACTION_VIEW, Uri.parse("termux://run-script")) fires on step 3 to
 *  open Termux; on step 4 the user starts the server manually (or we deep-link again).
 */
class BridgeSetupActivity : AppCompatActivity() {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "BridgeSetupActivity"

        /** Bridge script destination on internal app storage (scoped, no permission needed). */
        private const val SCRIPT_DIR_NAME  = "sdgclaw-setup"
        private const val SCRIPT_FILE_NAME = "setup.sh"

        /** Polling configuration. */
        private const val POLL_INTERVAL_MS = 2_000L
        private const val POLL_TIMEOUT_MS  = 120_000L  // 2 minutes

        /** Total wizard steps (0-indexed internally, 1-indexed in UI). */
        private const val TOTAL_STEPS = 5
    }

    // ── Polling state ─────────────────────────────────────────────────────────
    private enum class PollState { IDLE, POLLING, DONE, TIMED_OUT }

    // ── Step data ─────────────────────────────────────────────────────────────
    private data class SetupStep(
        val title: String,
        val description: String,
        val codeSnippet: String? = null,
        val isScriptCopyStep: Boolean = false,
        val isDeepLinkStep: Boolean = false,
        val isFinalStep: Boolean = false,
        val pollCondition: (suspend () -> Boolean)? = null
    )

    // ── UI references (inflated lazily from setContentView) ───────────────────
    private lateinit var tvStepCounter: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDescription: TextView
    private lateinit var tvCodeSnippet: TextView
    private lateinit var cardCodeSnippet: View
    private lateinit var btnCopyCode: Button
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button
    private lateinit var tvScriptPath: TextView
    private lateinit var cardScriptPath: View
    private lateinit var tvPollStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var ivConnectionIcon: ImageView
    private lateinit var cardConnectionStatus: View
    private lateinit var stepIndicatorContainer: LinearLayout

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentStep = 0
    private var scriptDestFile: File? = null
    private var pollJob: Job? = null
    private var pollState = PollState.IDLE
    /** Tracks which steps have been confirmed/checked off. */
    private val completedSteps = mutableSetOf<Int>()

    // ── Bridge reference ──────────────────────────────────────────────────────
    private val bridge: TermuxBridge? get() =
        (application as? SDGClawApplication)?.bridge

    // ── Steps definition ──────────────────────────────────────────────────────
    private val steps: List<SetupStep> by lazy {
        listOf(
            /* 0 */ SetupStep(
                title       = getString(R.string.setup_step1_title),
                description = getString(R.string.setup_step1_desc),
                codeSnippet = null
            ),
            /* 1 */ SetupStep(
                title       = getString(R.string.setup_step2_title),
                description = getString(R.string.setup_step2_desc),
                codeSnippet = getString(R.string.setup_step2_code)
            ),
            /* 2 */ SetupStep(
                title            = getString(R.string.setup_step3_title),
                description      = getString(R.string.setup_step3_desc),
                isScriptCopyStep = true
            ),
            /* 3 */ SetupStep(
                title         = getString(R.string.setup_step4_title),
                description   = getString(R.string.setup_step4_desc),
                codeSnippet   = getString(R.string.setup_step4_code),
                isDeepLinkStep = true,
                pollCondition  = { isScriptDeployed() }
            ),
            /* 4 */ SetupStep(
                title         = getString(R.string.setup_step5_title),
                description   = getString(R.string.setup_step5_desc),
                codeSnippet   = getString(R.string.setup_step5_code),
                isDeepLinkStep = true,
                isFinalStep   = true,
                pollCondition  = { bridge?.isConnected() == true }
            )
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bridge_setup)

        bindViews()
        buildStepIndicator()
        updateStep(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }

    // ── View binding (manual — no ViewBinding plugin required) ────────────────

    private fun bindViews() {
        tvStepCounter          = findViewById(R.id.tv_step_counter)
        tvStepTitle            = findViewById(R.id.tv_step_title)
        tvStepDescription      = findViewById(R.id.tv_step_description)
        tvCodeSnippet          = findViewById(R.id.tv_code_snippet)
        cardCodeSnippet        = findViewById(R.id.card_code_snippet)
        btnCopyCode            = findViewById(R.id.btn_copy_code)
        btnBack                = findViewById(R.id.btn_back)
        btnNext                = findViewById(R.id.btn_next)
        tvScriptPath           = findViewById(R.id.tv_script_path)
        cardScriptPath         = findViewById(R.id.card_script_path)
        tvPollStatus           = findViewById(R.id.tv_poll_status)
        tvConnectionStatus     = findViewById(R.id.tv_connection_status)
        ivConnectionIcon       = findViewById(R.id.iv_connection_icon)
        cardConnectionStatus   = findViewById(R.id.card_connection_status)
        stepIndicatorContainer = findViewById(R.id.step_indicator_container)

        btnBack.setOnClickListener { navigateBack() }
        btnNext.setOnClickListener { navigateNext() }
        btnCopyCode.setOnClickListener { copyCodeToClipboard() }
    }

    // ── Step indicator dots ────────────────────────────────────────────────────

    private fun buildStepIndicator() {
        stepIndicatorContainer.removeAllViews()
        val sizePx  = (10 * resources.displayMetrics.density).toInt()
        val gapPx   = (8  * resources.displayMetrics.density).toInt()
        repeat(TOTAL_STEPS) { index ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                    if (index > 0) it.leftMargin = gapPx
                }
                setBackgroundResource(
                    if (index == currentStep) R.drawable.circle_green
                    else                      R.drawable.circle_gray
                )
                tag = "dot_$index"
            }
            stepIndicatorContainer.addView(dot)
        }
    }

    private fun refreshStepIndicator() {
        for (i in 0 until TOTAL_STEPS) {
            val dot = stepIndicatorContainer.findViewWithTag<View>("dot_$i") ?: continue
            dot.setBackgroundResource(
                when {
                    i == currentStep         -> R.drawable.circle_green
                    i in completedSteps      -> R.drawable.circle_green
                    else                     -> R.drawable.circle_gray
                }
            )
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateBack() {
        stopPolling()
        if (currentStep > 0) updateStep(currentStep - 1) else finish()
    }

    private fun navigateNext() {
        val step = steps[currentStep]
        when {
            step.isFinalStep && pollState == PollState.DONE -> {
                // All done — return to MainActivity signalling success
                setResult(RESULT_OK)
                finish()
            }
            step.isFinalStep -> {
                // Final step but not connected yet — trigger verify
                startPolling(currentStep)
            }
            else -> {
                completedSteps.add(currentStep)
                updateStep(currentStep + 1)
            }
        }
    }

    // ── Step rendering ────────────────────────────────────────────────────────

    private fun updateStep(index: Int) {
        stopPolling()
        currentStep = index
        val step = steps[index]

        // Counter
        tvStepCounter.text = getString(R.string.setup_step_counter, index + 1, TOTAL_STEPS)

        // Title & description
        tvStepTitle.text       = step.title
        tvStepDescription.text = step.description

        // Code snippet card
        if (step.codeSnippet != null) {
            cardCodeSnippet.visibility = View.VISIBLE
            tvCodeSnippet.text         = step.codeSnippet
        } else {
            cardCodeSnippet.visibility = View.GONE
        }

        // Script path card (shown after copy on step 2)
        cardScriptPath.visibility = View.GONE

        // Poll status
        tvPollStatus.visibility = View.GONE
        tvPollStatus.text       = ""

        // Connection status card (only on final step)
        cardConnectionStatus.visibility = if (step.isFinalStep) View.VISIBLE else View.GONE
        if (step.isFinalStep) resetConnectionStatus()

        // Navigation buttons
        btnBack.text = if (index == 0) getString(R.string.setup_btn_cancel) else getString(R.string.setup_btn_back)
        btnNext.text = when {
            step.isFinalStep  -> getString(R.string.setup_btn_verify)
            index == TOTAL_STEPS - 1 -> getString(R.string.setup_btn_finish)
            else              -> getString(R.string.setup_btn_next)
        }

        // Auto-actions on arrive
        when {
            step.isScriptCopyStep -> performScriptCopy()
            step.isDeepLinkStep   -> {
                // Auto-trigger deep link when step 3 is the run-script step
                if (index == 3) fireTermuxDeepLink()
                if (step.pollCondition != null) startPolling(index)
            }
        }

        refreshStepIndicator()
    }

    // ── Script copy ───────────────────────────────────────────────────────────

    private fun performScriptCopy() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { copySetupScript() }
            if (result != null) {
                scriptDestFile = result
                cardScriptPath.visibility = View.VISIBLE
                tvScriptPath.text = result.absolutePath
                completedSteps.add(currentStep)
                refreshStepIndicator()
                tvPollStatus.visibility = View.VISIBLE
                tvPollStatus.text       = getString(R.string.setup_script_copied)
            } else {
                tvPollStatus.visibility = View.VISIBLE
                tvPollStatus.text       = getString(R.string.setup_script_copy_failed)
            }
        }
    }

    /**
     * Copies `assets/setup.sh` to the app's internal files directory under
     * `sdgclaw-setup/setup.sh`.  No external-storage permission needed.
     * Returns the destination [File] on success, null on failure.
     */
    private fun copySetupScript(): File? {
        return try {
            val dir  = File(filesDir, SCRIPT_DIR_NAME).also { it.mkdirs() }
            val dest = File(dir, SCRIPT_FILE_NAME)
            assets.open(SCRIPT_FILE_NAME).use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
                }
            }
            Log.d(TAG, "setup.sh copied to ${dest.absolutePath}")
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy setup.sh", e)
            null
        }
    }

    // ── Termux deep-link ──────────────────────────────────────────────────────

    /**
     * Fires `termux://run-script` deep-link to open Termux.
     * Falls back to opening Termux via package name if the URI scheme is not handled.
     *
     * The Termux:API `termux://run-script` URI can carry the script path as a query
     * parameter; however, since Termux's built-in URI handler varies by version we
     * just open Termux and let the user run the displayed command.
     */
    private fun fireTermuxDeepLink() {
        val scriptPath = scriptDestFile?.absolutePath
            ?: File(File(filesDir, SCRIPT_DIR_NAME), SCRIPT_FILE_NAME).absolutePath

        // Build URI — Termux handles `termux://run-script?path=...` on some versions
        val uri = Uri.parse("termux://run-script").buildUpon()
            .appendQueryParameter("path", scriptPath)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(packageManager) != null) {
            Log.d(TAG, "Firing Termux deep-link: $uri")
            startActivity(intent)
        } else {
            // Fallback: open Termux app directly
            Log.d(TAG, "Deep-link unresolved — falling back to package launch")
            val fallback = packageManager.getLaunchIntentForPackage("com.termux")
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback)
            } else {
                Toast.makeText(this, getString(R.string.setup_termux_not_installed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Polling state machine ─────────────────────────────────────────────────

    /**
     * Starts a coroutine that evaluates [steps[stepIndex].pollCondition] every
     * [POLL_INTERVAL_MS] ms.  Stops automatically after [POLL_TIMEOUT_MS] ms or
     * when the condition returns `true`.
     *
     * State transitions:
     *   IDLE → POLLING → DONE      (condition satisfied)
     *              └──→ TIMED_OUT  (timeout elapsed)
     */
    private fun startPolling(stepIndex: Int) {
        val condition = steps[stepIndex].pollCondition ?: return
        if (pollState == PollState.POLLING) return   // already running

        pollState = PollState.POLLING
        updatePollStatusUi()

        val isFinal = steps[stepIndex].isFinalStep
        val startTime = System.currentTimeMillis()

        pollJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime

                // Check timeout
                if (elapsed >= POLL_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        pollState = PollState.TIMED_OUT
                        updatePollStatusUi()
                        if (isFinal) updateConnectionStatusUi(connected = false, timedOut = true)
                        Log.w(TAG, "Polling timed out for step $stepIndex after ${elapsed}ms")
                    }
                    break
                }

                // Evaluate condition
                val satisfied = try { condition() } catch (e: Exception) {
                    Log.e(TAG, "Poll condition threw", e)
                    false
                }

                if (satisfied) {
                    withContext(Dispatchers.Main) {
                        pollState = PollState.DONE
                        completedSteps.add(stepIndex)
                        refreshStepIndicator()
                        updatePollStatusUi()
                        if (isFinal) updateConnectionStatusUi(connected = true, timedOut = false)

                        // Auto-advance Next button label
                        btnNext.text = if (isFinal)
                            getString(R.string.setup_btn_finish)
                        else
                            getString(R.string.setup_btn_next)

                        Log.d(TAG, "Poll condition satisfied for step $stepIndex (${elapsed}ms)")
                    }
                    break
                }

                // Update elapsed UI on main thread
                withContext(Dispatchers.Main) {
                    val remaining = ((POLL_TIMEOUT_MS - elapsed) / 1000).coerceAtLeast(0)
                    tvPollStatus.visibility = View.VISIBLE
                    tvPollStatus.text = getString(R.string.setup_polling, remaining)
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        if (pollState == PollState.POLLING) pollState = PollState.IDLE
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private fun updatePollStatusUi() {
        tvPollStatus.visibility = View.VISIBLE
        tvPollStatus.text = when (pollState) {
            PollState.IDLE      -> ""
            PollState.POLLING   -> getString(R.string.setup_polling, POLL_TIMEOUT_MS / 1000)
            PollState.DONE      -> getString(R.string.setup_poll_done)
            PollState.TIMED_OUT -> getString(R.string.setup_poll_timeout)
        }
    }

    private fun resetConnectionStatus() {
        ivConnectionIcon.setImageResource(R.drawable.circle_gray)
        tvConnectionStatus.text = getString(R.string.setup_connection_waiting)
        cardConnectionStatus.visibility = View.VISIBLE
    }

    private fun updateConnectionStatusUi(connected: Boolean, timedOut: Boolean) {
        cardConnectionStatus.visibility = View.VISIBLE
        if (connected) {
            ivConnectionIcon.setImageResource(R.drawable.circle_green)
            tvConnectionStatus.text = getString(R.string.setup_connection_success)
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.green_connected))
        } else {
            ivConnectionIcon.setImageResource(R.drawable.circle_red)
            tvConnectionStatus.text = if (timedOut)
                getString(R.string.setup_connection_timeout)
            else
                getString(R.string.setup_connection_failed)
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        }
    }

    // ── Clipboard helper ──────────────────────────────────────────────────────

    private fun copyCodeToClipboard() {
        val code = tvCodeSnippet.text?.toString() ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SDG Claw setup command", code))
        Toast.makeText(this, getString(R.string.setup_copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    // ── Poll condition helpers ────────────────────────────────────────────────

    /**
     * Returns true if the setup script has been successfully copied to internal storage.
     * Used as the poll condition for step 3 (the copy/deploy step).
     */
    private fun isScriptDeployed(): Boolean {
        val dest = File(File(filesDir, SCRIPT_DIR_NAME), SCRIPT_FILE_NAME)
        return dest.exists() && dest.length() > 0
    }
}
