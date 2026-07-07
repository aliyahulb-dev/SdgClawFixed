package com.sdgclaw

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var tvBridgeStatus: TextView
    private lateinit var btnTestBridge: Button
    
    // OpenAI
    private lateinit var etOpenAIKey: TextInputEditText
    private lateinit var etOpenAIModel: TextInputEditText
    private lateinit var btnTestOpenAI: Button
    
    // Anthropic
    private lateinit var etAnthropicKey: TextInputEditText
    private lateinit var etAnthropicModel: TextInputEditText
    private lateinit var btnTestAnthropic: Button
    
    // Google
    private lateinit var etGoogleKey: TextInputEditText
    private lateinit var etGoogleModel: TextInputEditText
    private lateinit var btnTestGoogle: Button
    
    // Custom
    private lateinit var etCustomName: TextInputEditText
    private lateinit var etCustomUrl: TextInputEditText
    private lateinit var etCustomKey: TextInputEditText
    private lateinit var etCustomModel: TextInputEditText
    private lateinit var btnTestCustom: Button
    
    // Active Provider
    private lateinit var rgActiveProvider: RadioGroup
    private lateinit var rbOpenAI: RadioButton
    private lateinit var rbAnthropic: RadioButton
    private lateinit var rbGoogle: RadioButton
    private lateinit var rbCustom: RadioButton
    
    // Save
    private lateinit var btnSaveSettings: Button
    
    private var llmClient: LLMClient? = null
    private var termuxBridge: TermuxBridge? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        initViews()
        setupToolbar()
        loadSettings()
        setupListeners()
        checkBridgeStatus()
    }
    
    private fun initViews() {
        tvBridgeStatus = findViewById(R.id.tvBridgeStatus)
        btnTestBridge = findViewById(R.id.btnTestBridge)
        
        etOpenAIKey = findViewById(R.id.etOpenAIKey)
        etOpenAIModel = findViewById(R.id.etOpenAIModel)
        btnTestOpenAI = findViewById(R.id.btnTestOpenAI)
        
        etAnthropicKey = findViewById(R.id.etAnthropicKey)
        etAnthropicModel = findViewById(R.id.etAnthropicModel)
        btnTestAnthropic = findViewById(R.id.btnTestAnthropic)
        
        etGoogleKey = findViewById(R.id.etGoogleKey)
        etGoogleModel = findViewById(R.id.etGoogleModel)
        btnTestGoogle = findViewById(R.id.btnTestGoogle)
        
        etCustomName = findViewById(R.id.etCustomName)
        etCustomUrl = findViewById(R.id.etCustomUrl)
        etCustomKey = findViewById(R.id.etCustomKey)
        etCustomModel = findViewById(R.id.etCustomModel)
        btnTestCustom = findViewById(R.id.btnTestCustom)
        
        rgActiveProvider = findViewById(R.id.rgActiveProvider)
        rbOpenAI = findViewById(R.id.rbOpenAI)
        rbAnthropic = findViewById(R.id.rbAnthropic)
        rbGoogle = findViewById(R.id.rbGoogle)
        rbCustom = findViewById(R.id.rbCustom)
        
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE)
        
        etOpenAIKey.setText(prefs.getString("openai_key", ""))
        etOpenAIModel.setText(prefs.getString("openai_model", "gpt-4o-mini"))
        
        etAnthropicKey.setText(prefs.getString("anthropic_key", ""))
        etAnthropicModel.setText(prefs.getString("anthropic_model", "claude-3-5-sonnet-20241022"))
        
        etGoogleKey.setText(prefs.getString("google_key", ""))
        etGoogleModel.setText(prefs.getString("google_model", "gemini-1.5-flash"))
        
        etCustomName.setText(prefs.getString("custom_name", "Custom"))
        etCustomUrl.setText(prefs.getString("custom_url", ""))
        etCustomKey.setText(prefs.getString("custom_key", ""))
        etCustomModel.setText(prefs.getString("custom_model", ""))
        
        // Active provider
        val activeProvider = prefs.getString("active_provider", "openai")
        when (activeProvider) {
            "openai" -> rbOpenAI.isChecked = true
            "anthropic" -> rbAnthropic.isChecked = true
            "google" -> rbGoogle.isChecked = true
            "custom" -> rbCustom.isChecked = true
        }
        
        // Initialize LLMClient
        llmClient = LLMClient(this)
    }
    
    private fun setupListeners() {
        // Bridge test
        btnTestBridge.setOnClickListener {
            testBridgeConnection()
        }
        
        // Provider tests
        btnTestOpenAI.setOnClickListener { testProvider(LLMClient.ProviderType.OPENAI) }
        btnTestAnthropic.setOnClickListener { testProvider(LLMClient.ProviderType.ANTHROPIC) }
        btnTestGoogle.setOnClickListener { testProvider(LLMClient.ProviderType.GOOGLE) }
        btnTestCustom.setOnClickListener { testProvider(LLMClient.ProviderType.CUSTOM) }
        
        // Save settings
        btnSaveSettings.setOnClickListener { saveSettings() }
        
        // Active provider change
        rgActiveProvider.setOnCheckedChangeListener { _, checkedId ->
            // Just update UI, save on button click
        }
    }
    
    private fun checkBridgeStatus() {
        val app = application as SDGClawApplication
        termuxBridge = app.getTermuxBridge()
        
        val connected = termuxBridge?.isConnected() == true
        tvBridgeStatus.text = if (connected) "Status: Connected ✓" else "Status: Disconnected ✗"
        tvBridgeStatus.setTextColor(if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
    }
    
    private fun testBridgeConnection() {
        termuxBridge?.testConnection()
        checkBridgeStatus()
        Toast.makeText(this, "Test message sent to bridge", Toast.LENGTH_SHORT).show()
    }
    
    private fun testProvider(type: LLMClient.ProviderType) {
        val originalText = when (type) {
            LLMClient.ProviderType.OPENAI -> btnTestOpenAI.text.toString()
            LLMClient.ProviderType.ANTHROPIC -> btnTestAnthropic.text.toString()
            LLMClient.ProviderType.GOOGLE -> btnTestGoogle.text.toString()
            LLMClient.ProviderType.CUSTOM -> btnTestCustom.text.toString()
        }
        
        val btn = when (type) {
            LLMClient.ProviderType.OPENAI -> btnTestOpenAI
            LLMClient.ProviderType.ANTHROPIC -> btnTestAnthropic
            LLMClient.ProviderType.GOOGLE -> btnTestGoogle
            LLMClient.ProviderType.CUSTOM -> btnTestCustom
        }
        
        btn.text = "Testing..."
        btn.isEnabled = false
        
        // Update provider config from UI before testing
        updateProviderFromUI(type)
        
        lifecycleScope.launch {
            val result = llmClient?.testProvider(type)
            
            runOnUiThread {
                btn.text = originalText
                btn.isEnabled = true
                
                result?.fold(
                    onSuccess = { response ->
                        Toast.makeText(this@SettingsActivity, "${type.name} OK: $response", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@SettingsActivity, "${type.name} Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
    
    private fun updateProviderFromUI(type: LLMClient.ProviderType) {
        val config = when (type) {
            LLMClient.ProviderType.OPENAI -> LLMClient.ProviderConfig(
                type = type,
                name = "OpenAI",
                apiKey = etOpenAIKey.text.toString().trim(),
                defaultModel = etOpenAIModel.text.toString().trim(),
                enabled = etOpenAIKey.text.toString().isNotBlank()
            )
            LLMClient.ProviderType.ANTHROPIC -> LLMClient.ProviderConfig(
                type = type,
                name = "Anthropic",
                apiKey = etAnthropicKey.text.toString().trim(),
                defaultModel = etAnthropicModel.text.toString().trim(),
                enabled = etAnthropicKey.text.toString().isNotBlank()
            )
            LLMClient.ProviderType.GOOGLE -> LLMClient.ProviderConfig(
                type = type,
                name = "Google Gemini",
                apiKey = etGoogleKey.text.toString().trim(),
                defaultModel = etGoogleModel.text.toString().trim(),
                enabled = etGoogleKey.text.toString().isNotBlank()
            )
            LLMClient.ProviderType.CUSTOM -> LLMClient.ProviderConfig(
                type = type,
                name = etCustomName.text.toString().trim(),
                apiKey = etCustomKey.text.toString().trim(),
                baseUrl = etCustomUrl.text.toString().trim(),
                defaultModel = etCustomModel.text.toString().trim(),
                enabled = etCustomUrl.text.toString().isNotBlank() && etCustomKey.text.toString().isNotBlank()
            )
        }
        
        llmClient?.updateProvider(config)
    }
    
    private fun saveSettings() {
        val prefs = getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE).edit()
        
        // Save all providers
        updateProviderFromUI(LLMClient.ProviderType.OPENAI)
        updateProviderFromUI(LLMClient.ProviderType.ANTHROPIC)
        updateProviderFromUI(LLMClient.ProviderType.GOOGLE)
        updateProviderFromUI(LLMClient.ProviderType.CUSTOM)
        
        // Save active provider
        val activeProvider = when (rgActiveProvider.checkedRadioButtonId) {
            R.id.rbOpenAI -> "openai"
            R.id.rbAnthropic -> "anthropic"
            R.id.rbGoogle -> "google"
            R.id.rbCustom -> "custom"
            else -> "openai"
        }
        prefs.putString("active_provider", activeProvider)
        
        // Apply active provider to LLMClient
        val providerType = when (activeProvider) {
            "openai" -> LLMClient.ProviderType.OPENAI
            "anthropic" -> LLMClient.ProviderType.ANTHROPIC
            "google" -> LLMClient.ProviderType.GOOGLE
            "custom" -> LLMClient.ProviderType.CUSTOM
            else -> LLMClient.ProviderType.OPENAI
        }
        llmClient?.setActiveProvider(providerType)
        
        prefs.apply()
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
    }
}