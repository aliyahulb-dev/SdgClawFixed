package com.sdgclaw

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"

        /** SharedPreferences key for the system prompt. */
        const val KEY_SYSTEM_PROMPT = "system_prompt"

        /** Soft character limit; a warning is shown above this threshold. */
        private const val SYSTEM_PROMPT_WARN_THRESHOLD = 1800

        /** Hard counter maximum shown in the TextInputLayout counter. */
        private const val SYSTEM_PROMPT_MAX = 2000

        // ── Canned prompts ────────────────────────────────────────────────

        private const val PRESET_DEFAULT =
            "You are SDG Claw, a capable AI agent running on an Android device. " +
            "You have access to a Termux shell via a WebSocket bridge, which lets you " +
            "execute commands, read and write files, and perform system operations. " +
            "Always think step-by-step before acting. Prefer safe, reversible actions. " +
            "When in doubt, ask the user for clarification before proceeding."

        private const val PRESET_CODE =
            "You are an expert software-engineering assistant specialised in Android, " +
            "Kotlin, and shell scripting. Help the user write, debug, and refactor code. " +
            "When providing code samples use idiomatic Kotlin. Explain your reasoning " +
            "clearly and highlight any potential pitfalls or security considerations. " +
            "Use the available Termux tools to inspect files or run build commands when " +
            "it would help answer the user's question."

        private const val PRESET_FILES =
            "You are a file-management assistant with full access to the Android file " +
            "system via the Termux bridge. Help the user navigate directories, read and " +
            "write files, move or rename items, search for content, and manage storage. " +
            "Always confirm destructive operations (delete, overwrite) with the user " +
            "before executing them. Present directory listings in a clean, readable " +
            "format and summarise large file contents rather than dumping them verbatim."
    }

    // ── Bridge / status ───────────────────────────────────────────────────
    private lateinit var tvBridgeStatus: TextView
    private lateinit var btnTestBridge: Button

    // ── System prompt ─────────────────────────────────────────────────────
    private lateinit var tilSystemPrompt: TextInputLayout
    private lateinit var etSystemPrompt: TextInputEditText
    private lateinit var tvSystemPromptWarning: TextView
    private lateinit var btnPresetDefault: Button
    private lateinit var btnPresetCode: Button
    private lateinit var btnPresetFiles: Button

    // ── OpenAI ────────────────────────────────────────────────────────────
    private lateinit var etOpenAIKey: TextInputEditText
    private lateinit var etOpenAIModel: TextInputEditText
    private lateinit var btnTestOpenAI: Button

    // ── Anthropic ─────────────────────────────────────────────────────────
    private lateinit var etAnthropicKey: TextInputEditText
    private lateinit var etAnthropicModel: TextInputEditText
    private lateinit var btnTestAnthropic: Button

    // ── Google ────────────────────────────────────────────────────────────
    private lateinit var etGoogleKey: TextInputEditText
    private lateinit var etGoogleModel: TextInputEditText
    private lateinit var btnTestGoogle: Button

    // ── Custom ────────────────────────────────────────────────────────────
    private lateinit var etCustomName: TextInputEditText
    private lateinit var etCustomUrl: TextInputEditText
    private lateinit var etCustomKey: TextInputEditText
    private lateinit var etCustomModel: TextInputEditText
    private lateinit var btnTestCustom: Button

    // ── Active provider ───────────────────────────────────────────────────
    private lateinit var rgActiveProvider: RadioGroup
    private lateinit var rbOpenAI: RadioButton
    private lateinit var rbAnthropic: RadioButton
    private lateinit var rbGoogle: RadioButton
    private lateinit var rbCustom: RadioButton

    // ── Save ──────────────────────────────────────────────────────────────
    private lateinit var btnSaveSettings: Button

    private var llmClient: LLMClient? = null
    private var termuxBridge: TermuxBridge? = null

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupToolbar()
        loadSettings()
        setupListeners()
        checkBridgeStatus()
    }

    // ─────────────────────────────────────────────────────────────────────
    // View init
    // ─────────────────────────────────────────────────────────────────────

    private fun initViews() {
        tvBridgeStatus = findViewById(R.id.tvBridgeStatus)
        btnTestBridge  = findViewById(R.id.btnTestBridge)

        // System prompt
        tilSystemPrompt      = findViewById(R.id.tilSystemPrompt)
        etSystemPrompt       = findViewById(R.id.etSystemPrompt)
        tvSystemPromptWarning = findViewById(R.id.tvSystemPromptWarning)
        btnPresetDefault     = findViewById(R.id.btnPresetDefault)
        btnPresetCode        = findViewById(R.id.btnPresetCode)
        btnPresetFiles       = findViewById(R.id.btnPresetFiles)

        // OpenAI
        etOpenAIKey   = findViewById(R.id.etOpenAIKey)
        etOpenAIModel = findViewById(R.id.etOpenAIModel)
        btnTestOpenAI = findViewById(R.id.btnTestOpenAI)

        // Anthropic
        etAnthropicKey   = findViewById(R.id.etAnthropicKey)
        etAnthropicModel = findViewById(R.id.etAnthropicModel)
        btnTestAnthropic = findViewById(R.id.btnTestAnthropic)

        // Google
        etGoogleKey   = findViewById(R.id.etGoogleKey)
        etGoogleModel = findViewById(R.id.etGoogleModel)
        btnTestGoogle = findViewById(R.id.btnTestGoogle)

        // Custom
        etCustomName  = findViewById(R.id.etCustomName)
        etCustomUrl   = findViewById(R.id.etCustomUrl)
        etCustomKey   = findViewById(R.id.etCustomKey)
        etCustomModel = findViewById(R.id.etCustomModel)
        btnTestCustom = findViewById(R.id.btnTestCustom)

        // Active provider
        rgActiveProvider = findViewById(R.id.rgActiveProvider)
        rbOpenAI         = findViewById(R.id.rbOpenAI)
        rbAnthropic      = findViewById(R.id.rbAnthropic)
        rbGoogle         = findViewById(R.id.rbGoogle)
        rbCustom         = findViewById(R.id.rbCustom)

        btnSaveSettings = findViewById(R.id.btnSaveSettings)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Load / save settings
    // ─────────────────────────────────────────────────────────────────────

    private fun loadSettings() {
        val prefs = getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE)

        // System prompt
        val savedPrompt = prefs.getString(KEY_SYSTEM_PROMPT, "") ?: ""
        etSystemPrompt.setText(savedPrompt)
        updateSystemPromptWarning(savedPrompt.length)

        // OpenAI
        etOpenAIKey.setText(prefs.getString("openai_key", ""))
        etOpenAIModel.setText(prefs.getString("openai_model", "gpt-4o-mini"))

        // Anthropic
        etAnthropicKey.setText(prefs.getString("anthropic_key", ""))
        etAnthropicModel.setText(prefs.getString("anthropic_model", "claude-3-5-sonnet-20241022"))

        // Google
        etGoogleKey.setText(prefs.getString("google_key", ""))
        etGoogleModel.setText(prefs.getString("google_model", "gemini-1.5-flash"))

        // Custom
        etCustomName.setText(prefs.getString("custom_name", "Custom"))
        etCustomUrl.setText(prefs.getString("custom_url", ""))
        etCustomKey.setText(prefs.getString("custom_key", ""))
        etCustomModel.setText(prefs.getString("custom_model", ""))

        // Active provider
        val activeProvider = prefs.getString("active_provider", "openai")
        when (activeProvider) {
            "openai"    -> rbOpenAI.isChecked    = true
            "anthropic" -> rbAnthropic.isChecked = true
            "google"    -> rbGoogle.isChecked    = true
            "custom"    -> rbCustom.isChecked    = true
        }

        // Initialize LLMClient
        llmClient = LLMClient(this)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE).edit()

        // ── System prompt ──────────────────────────────────────────────
        val promptText = etSystemPrompt.text?.toString()?.trim() ?: ""
        prefs.putString(KEY_SYSTEM_PROMPT, promptText)
        Log.d(TAG, "Saving system prompt (${promptText.length} chars)")

        // ── LLM providers ──────────────────────────────────────────────
        updateProviderFromUI(LLMClient.ProviderType.OPENAI)
        updateProviderFromUI(LLMClient.ProviderType.ANTHROPIC)
        updateProviderFromUI(LLMClient.ProviderType.GOOGLE)
        updateProviderFromUI(LLMClient.ProviderType.CUSTOM)

        // ── Active provider ────────────────────────────────────────────
        val activeProvider = when (rgActiveProvider.checkedRadioButtonId) {
            R.id.rbOpenAI    -> "openai"
            R.id.rbAnthropic -> "anthropic"
            R.id.rbGoogle    -> "google"
            R.id.rbCustom    -> "custom"
            else             -> "openai"
        }
        prefs.putString("active_provider", activeProvider)

        val providerType = when (activeProvider) {
            "openai"    -> LLMClient.ProviderType.OPENAI
            "anthropic" -> LLMClient.ProviderType.ANTHROPIC
            "google"    -> LLMClient.ProviderType.GOOGLE
            "custom"    -> LLMClient.ProviderType.CUSTOM
            else        -> LLMClient.ProviderType.OPENAI
        }
        llmClient?.setActiveProvider(providerType)

        prefs.apply()
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Listeners
    // ─────────────────────────────────────────────────────────────────────

    private fun setupListeners() {
        // Bridge
        btnTestBridge.setOnClickListener { testBridgeConnection() }

        // ── System prompt character counter & warning ──────────────────
        etSystemPrompt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                updateSystemPromptWarning(len)
            }
        })

        // ── Preset buttons ─────────────────────────────────────────────
        btnPresetDefault.setOnClickListener {
            etSystemPrompt.setText(PRESET_DEFAULT)
            etSystemPrompt.setSelection(PRESET_DEFAULT.length)
            Toast.makeText(this, "Default Agent preset loaded", Toast.LENGTH_SHORT).show()
        }

        btnPresetCode.setOnClickListener {
            etSystemPrompt.setText(PRESET_CODE)
            etSystemPrompt.setSelection(PRESET_CODE.length)
            Toast.makeText(this, "Code Assistant preset loaded", Toast.LENGTH_SHORT).show()
        }

        btnPresetFiles.setOnClickListener {
            etSystemPrompt.setText(PRESET_FILES)
            etSystemPrompt.setSelection(PRESET_FILES.length)
            Toast.makeText(this, "File Manager preset loaded", Toast.LENGTH_SHORT).show()
        }

        // ── Provider tests ─────────────────────────────────────────────
        btnTestOpenAI.setOnClickListener    { testProvider(LLMClient.ProviderType.OPENAI) }
        btnTestAnthropic.setOnClickListener { testProvider(LLMClient.ProviderType.ANTHROPIC) }
        btnTestGoogle.setOnClickListener    { testProvider(LLMClient.ProviderType.GOOGLE) }
        btnTestCustom.setOnClickListener    { testProvider(LLMClient.ProviderType.CUSTOM) }

        // ── Save ───────────────────────────────────────────────────────
        btnSaveSettings.setOnClickListener { saveSettings() }

        // Active provider change: handled at save time; no extra action needed here.
        rgActiveProvider.setOnCheckedChangeListener { _, _ -> }
    }

    // ─────────────────────────────────────────────────────────────────────
    // System-prompt helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Shows or hides the amber warning text depending on how close the user
     * is to the [SYSTEM_PROMPT_MAX] soft limit.
     */
    private fun updateSystemPromptWarning(charCount: Int) {
        if (charCount >= SYSTEM_PROMPT_WARN_THRESHOLD) {
            tvSystemPromptWarning.visibility = View.VISIBLE
        } else {
            tvSystemPromptWarning.visibility = View.GONE
        }

        // Tint the counter red when the hard cap is reached
        if (charCount >= SYSTEM_PROMPT_MAX) {
            tilSystemPrompt.setCounterTextColor(
                android.content.res.ColorStateList.valueOf(0xFFF44336.toInt())
            )
        } else {
            tilSystemPrompt.setCounterTextColor(
                android.content.res.ColorStateList.valueOf(0xFF9E9E9E.toInt())
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Bridge
    // ─────────────────────────────────────────────────────────────────────

    private fun checkBridgeStatus() {
        val app = application as SDGClawApplication
        termuxBridge = app.getTermuxBridge()

        val connected = termuxBridge?.isConnected() == true
        tvBridgeStatus.text = if (connected) "Status: Connected ✓" else "Status: Disconnected ✗"
        tvBridgeStatus.setTextColor(
            if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        )
    }

    private fun testBridgeConnection() {
        termuxBridge?.testConnection()
        checkBridgeStatus()
        Toast.makeText(this, "Test message sent to bridge", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Provider testing
    // ─────────────────────────────────────────────────────────────────────

    private fun testProvider(type: LLMClient.ProviderType) {
        val btn = when (type) {
            LLMClient.ProviderType.OPENAI    -> btnTestOpenAI
            LLMClient.ProviderType.ANTHROPIC -> btnTestAnthropic
            LLMClient.ProviderType.GOOGLE    -> btnTestGoogle
            LLMClient.ProviderType.CUSTOM    -> btnTestCustom
        }

        val originalText = btn.text.toString()
        btn.text = "Testing…"
        btn.isEnabled = false

        // Flush UI fields into the client before testing
        updateProviderFromUI(type)

        lifecycleScope.launch {
            val result = llmClient?.testProvider(type)

            runOnUiThread {
                btn.text = originalText
                btn.isEnabled = true

                result?.fold(
                    onSuccess = { response ->
                        Toast.makeText(
                            this@SettingsActivity,
                            "${type.name} OK: $response",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@SettingsActivity,
                            "${type.name} Error: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun updateProviderFromUI(type: LLMClient.ProviderType) {
        val config = when (type) {
            LLMClient.ProviderType.OPENAI -> LLMClient.ProviderConfig(
                type         = type,
                name         = "OpenAI",
                apiKey       = etOpenAIKey.text.toString().trim(),
                defaultModel = etOpenAIModel.text.toString().trim(),
                enabled      = etOpenAIKey.text.toString().isNotBlank()
            )
            LLMClient.ProviderType.ANTHROPIC -> LLMClient.ProviderConfig(
                type         = type,
                name         = "Anthropic",
                apiKey       = etAnthropicKey.text.toString().trim(),
                defaultModel = etAnthropicModel.text.toString().trim(),
                enabled      = etAnthropicKey.text.toString().isNotBlank()
            )
            LLMClient.ProviderType.GOOGLE -> LLMClient.ProviderConfig(
                type         = type,
                name         = "Google Gemini",
                apiKey       = etGoogleKey.text.toString().trim(),
                defaultModel = etGoogleModel.text.toString().trim(),
                enabled      = etGoogleKey.text.toString().isNotBlank()
            )
            LLMClient.ProviderType.CUSTOM -> LLMClient.ProviderConfig(
                type         = type,
                name         = etCustomName.text.toString().trim(),
                apiKey       = etCustomKey.text.toString().trim(),
                baseUrl      = etCustomUrl.text.toString().trim(),
                defaultModel = etCustomModel.text.toString().trim(),
                enabled      = etCustomUrl.text.toString().isNotBlank()
                            && etCustomKey.text.toString().isNotBlank()
            )
        }

        llmClient?.updateProvider(config)
    }
}
