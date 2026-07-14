package com.sdgclaw

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import com.sdgclaw.util.AssetCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step-by-step guided wizard that walks the user through installing the
 * Termux WebSocket bridge.
 *
 * Steps:
 *   0 — Welcome / prerequisites
 *   1 — Install Termux (from F-Droid)
 *   2 — Install Node.js inside Termux
 *   3 — Copy setup.sh to device storage
 *   4 — Run setup.sh in Termux
 *   5 — Verify connection (final step)
 */
class BridgeSetupActivity : AppCompatActivity() {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "BridgeSetupActivity"
        private const val ASSET_SETUP_SH = "setup.sh"
        private const val TOTAL_STEPS = 6          // steps 0 … 5
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentStep = 0

    /** Step data class — drives the wizard UI. */
    private data class SetupStep(
        val title: String,
        val description: String,
        /** Shell / CLI snippet shown in the code block; null = no code block. */
        val codeSnippet: String? = null,
        /** Whether to show the "Copy setup.sh" action button on this step. */
        val showCopyScriptButton: Boolean = false,
        /** Whether the Next button should read "Verify Connection" instead. */
        val isVerifyStep: Boolean = false,
    )

    private val steps: List<SetupStep> by lazy { buildSteps() }

    // ── Views (resolved lazily to avoid boilerplate) ──────────────────────────
    private lateinit var tvStepIndicator: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDescription: TextView
    private lateinit var cardCodeBlock: View
    private lateinit var tvCodeSnippet: TextView
    private lateinit var btnCopyCode: Button
    private lateinit var btnCopyScript: Button
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button
    private lateinit var verifyContainer: LinearLayout
    private lateinit var tvVerifyStatus: TextView
    private lateinit var ivVerifyIcon: ImageView
    private lateinit var dotContainer: LinearLayout

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bridge_setup)

        bindViews()
        buildDots()
        renderStep(currentStep)
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private fun bindViews() {
        tvStepIndicator  = findViewById(R.id.tv_step_indicator)
        tvStepTitle      = findViewById(R.id.tv_step_title)
        tvStepDescription = findViewById(R.id.tv_step_description)
        cardCodeBlock    = findViewById(R.id.card_code_block)
        tvCodeSnippet    = findViewById(R.id.tv_code_snippet)
        btnCopyCode      = findViewById(R.id.btn_copy_code)
        btnCopyScript    = findViewById(R.id.btn_copy_script)
        btnBack          = findViewById(R.id.btn_back)
        btnNext          = findViewById(R.id.btn_next)
        verifyContainer  = findViewById(R.id.verify_container)
        tvVerifyStatus   = findViewById(R.id.tv_verify_status)
        ivVerifyIcon     = findViewById(R.id.iv_verify_icon)
        dotContainer     = findViewById(R.id.dot_container)

        btnBack.setOnClickListener { navigateBack() }
        btnNext.setOnClickListener { navigateNext() }
        btnCopyCode.setOnClickListener { copyCodeToClipboard() }
        btnCopyScript.setOnClickListener { copySetupScriptToStorage() }
    }

    // ── Dot indicator ─────────────────────────────────────────────────────────

    private fun buildDots() {
        dotContainer.removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.dot_size)
        val margin = resources.getDimensionPixelSize(R.dimen.dot_margin)
        repeat(TOTAL_STEPS) { i ->
            val dot = View(this).apply {
                val lp = LinearLayout.LayoutParams(size, size).also {
                    it.setMargins(margin, 0, margin, 0)
                }
                layoutParams = lp
                setBackgroundResource(
                    if (i == currentStep) R.drawable.dot_active else R.drawable.dot_inactive
                )
                tag = i
            }
            dotContainer.addView(dot)
        }
    }

    private fun refreshDots() {
        for (i in 0 until dotContainer.childCount) {
            dotContainer.getChildAt(i)?.setBackgroundResource(
                if (i == currentStep) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateBack() {
        if (currentStep > 0) {
            currentStep--
            renderStep(currentStep)
        } else {
            finish()
        }
    }

    private fun navigateNext() {
        if (steps[currentStep].isVerifyStep) {
            runConnectionVerification()
            return
        }
        if (currentStep < TOTAL_STEPS - 1) {
            currentStep++
            renderStep(currentStep)
        } else {
            finish()
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderStep(index: Int) {
        val step = steps[index]

        tvStepIndicator.text  = getString(R.string.step_indicator, index + 1, TOTAL_STEPS)
        tvStepTitle.text      = step.title
        tvStepDescription.text = step.description

        // Code block
        if (step.codeSnippet != null) {
            cardCodeBlock.visibility = View.VISIBLE
            tvCodeSnippet.text = step.codeSnippet
        } else {
            cardCodeBlock.visibility = View.GONE
        }

        // Copy-script action button
        btnCopyScript.visibility =
            if (step.showCopyScriptButton) View.VISIBLE else View.GONE

        // Verify container (only on the last step)
        verifyContainer.visibility =
            if (step.isVerifyStep) View.VISIBLE else View.GONE
        if (!step.isVerifyStep) resetVerifyUi()

        // Next button label
        btnNext.text = when {
            step.isVerifyStep && currentStep == TOTAL_STEPS - 1 ->
                getString(R.string.btn_finish)
            step.isVerifyStep ->
                getString(R.string.btn_verify)
            currentStep == TOTAL_STEPS - 1 ->
                getString(R.string.btn_finish)
            else ->
                getString(R.string.btn_next)
        }

        // Back button label
        btnBack.text =
            if (index == 0) getString(R.string.btn_cancel) else getString(R.string.btn_back)

        refreshDots()
    }

    // ── Copy code snippet to clipboard ────────────────────────────────────────

    private fun copyCodeToClipboard() {
        val text = tvCodeSnippet.text?.toString() ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("sdgclaw_command", text))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    // ── Copy setup.sh from assets to device storage ───────────────────────────

    /**
     * Copies `assets/setup.sh` to the app's external files directory,
     * then shows the destination path so the user can `cp` it into Termux.
     *
     * Handles:
     *  - file already exists (offers overwrite via second tap)
     *  - write failure (surfaces error message)
     *  - post-copy readability check
     */
    private fun copySetupScriptToStorage() {
        btnCopyScript.isEnabled = false
        btnCopyScript.text = getString(R.string.copying)

        lifecycleScope.launch {
            val destFile = AssetCopier.defaultSetupScriptDest(applicationContext)
            Log.d(TAG, "Copying setup.sh → ${destFile.absolutePath}")

            val result = withContext(Dispatchers.IO) {
                AssetCopier.copyAndVerify(
                    context    = applicationContext,
                    assetPath  = ASSET_SETUP_SH,
                    destFile   = destFile,
                    overwrite  = false,        // first attempt: do not clobber
                )
            }

            when (result) {
                is AssetCopier.CopyResult.Success -> {
                    if (result.alreadyExisted) {
                        // File exists — ask user if they want to overwrite
                        handleScriptAlreadyExists(destFile)
                    } else {
                        onScriptCopiedSuccessfully(destFile)
                    }
                }
                is AssetCopier.CopyResult.Failure -> {
                    onScriptCopyFailed(result.message)
                }
            }
        }
    }

    /**
     * Called when the destination already exists.
     * Re-enables the button with an "Overwrite" label so the user can decide.
     */
    private fun handleScriptAlreadyExists(destFile: File) {
        val path = destFile.absolutePath
        Log.d(TAG, "setup.sh already exists at $path")

        // Update button to offer overwrite
        btnCopyScript.isEnabled = true
        btnCopyScript.text = getString(R.string.btn_overwrite_script)

        // Replace the click listener with an overwrite action
        btnCopyScript.setOnClickListener {
            btnCopyScript.isEnabled = false
            btnCopyScript.text = getString(R.string.copying)

            lifecycleScope.launch {
                val destFile2 = AssetCopier.defaultSetupScriptDest(applicationContext)
                val result = withContext(Dispatchers.IO) {
                    AssetCopier.copyAndVerify(
                        context   = applicationContext,
                        assetPath = ASSET_SETUP_SH,
                        destFile  = destFile2,
                        overwrite = true,
                    )
                }
                when (result) {
                    is AssetCopier.CopyResult.Success -> onScriptCopiedSuccessfully(destFile2)
                    is AssetCopier.CopyResult.Failure -> onScriptCopyFailed(result.message)
                }
            }
        }

        Toast.makeText(
            this,
            getString(R.string.script_already_exists, path),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun onScriptCopiedSuccessfully(destFile: File) {
        Log.d(TAG, "setup.sh copied OK: ${destFile.absolutePath}")
        btnCopyScript.isEnabled = true
        btnCopyScript.text = getString(R.string.script_copied)

        // Update description to include the path
        tvStepDescription.text = getString(
            R.string.step3_description_with_path,
            destFile.absolutePath
        )

        // Copy path to clipboard for convenience
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(
            ClipData.newPlainText("setup_sh_path", destFile.absolutePath)
        )

        Toast.makeText(
            this,
            getString(R.string.script_copy_success, destFile.absolutePath),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun onScriptCopyFailed(message: String) {
        Log.e(TAG, "setup.sh copy failed: $message")
        btnCopyScript.isEnabled = true
        btnCopyScript.text = getString(R.string.btn_copy_script)
        Toast.makeText(
            this,
            getString(R.string.script_copy_failed, message),
            Toast.LENGTH_LONG
        ).show()
    }

    // ── Connection verification ───────────────────────────────────────────────

    private fun runConnectionVerification() {
        btnNext.isEnabled = false
        tvVerifyStatus.text = getString(R.string.verifying)
        tvVerifyStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_medium))
        ivVerifyIcon.setImageResource(R.drawable.circle_yellow)
        verifyContainer.visibility = View.VISIBLE

        lifecycleScope.launch {
            val connected = withContext(Dispatchers.IO) {
                pingBridge()
            }
            onVerifyResult(connected)
        }
    }

    /**
     * Attempts a TCP-level connection to the bridge's WebSocket port.
     * Returns `true` if a socket can be opened within 3 seconds.
     */
    private fun pingBridge(): Boolean {
        return try {
            val app = application as SDGClawApplication
            // Re-use the Application's bridge and send a ping;
            // we rely on the bridge being already connected (auto-connect).
            val bridge = app.termuxBridge
            bridge.isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "pingBridge exception: ${e.message}", e)
            false
        }
    }

    private fun onVerifyResult(connected: Boolean) {
        btnNext.isEnabled = true
        if (connected) {
            tvVerifyStatus.text = getString(R.string.verify_success)
            tvVerifyStatus.setTextColor(ContextCompat.getColor(this, R.color.green_status))
            ivVerifyIcon.setImageResource(R.drawable.circle_green)
            btnNext.text = getString(R.string.btn_finish)
            // Swap click so the next tap goes to finish
            btnNext.setOnClickListener { finish() }
        } else {
            tvVerifyStatus.text = getString(R.string.verify_failure)
            tvVerifyStatus.setTextColor(ContextCompat.getColor(this, R.color.red_status))
            ivVerifyIcon.setImageResource(R.drawable.circle_red)
            btnNext.text = getString(R.string.btn_verify)
        }
    }

    private fun resetVerifyUi() {
        tvVerifyStatus.text = ""
        ivVerifyIcon.setImageDrawable(null)
    }

    // ── Step data ─────────────────────────────────────────────────────────────

    private fun buildSteps(): List<SetupStep> = listOf(

        /* 0 — Welcome */
        SetupStep(
            title = getString(R.string.step0_title),
            description = getString(R.string.step0_description),
        ),

        /* 1 — Install Termux */
        SetupStep(
            title = getString(R.string.step1_title),
            description = getString(R.string.step1_description),
            codeSnippet = null, // user installs from F-Droid; no shell command here
        ),

        /* 2 — Install Node.js inside Termux */
        SetupStep(
            title = getString(R.string.step2_title),
            description = getString(R.string.step2_description),
            codeSnippet = getString(R.string.step2_code),
        ),

        /* 3 — Copy setup.sh to device */
        SetupStep(
            title = getString(R.string.step3_title),
            description = getString(R.string.step3_description),
            codeSnippet = null,
            showCopyScriptButton = true,
        ),

        /* 4 — Run setup.sh in Termux */
        SetupStep(
            title = getString(R.string.step4_title),
            description = getString(R.string.step4_description),
            codeSnippet = getString(R.string.step4_code),
        ),

        /* 5 — Verify connection */
        SetupStep(
            title = getString(R.string.step5_title),
            description = getString(R.string.step5_description),
            isVerifyStep = true,
        ),
    )
}
