package com.sdgclaw

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * LLMClient - Multi-provider LLM client
 * Supports: OpenAI, Anthropic, Google Gemini, Custom OpenAI-compatible endpoints
 */
class LLMClient(
    private val context: Context
) {

    companion object {
        private const val TAG = "LLMClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    enum class ProviderType {
        OPENAI,
        ANTHROPIC,
        GOOGLE,
        CUSTOM
    }

    data class ProviderConfig(
        val type: ProviderType,
        val name: String,
        val apiKey: String = "",
        val baseUrl: String = "",
        val defaultModel: String = "",
        val enabled: Boolean = true,
        val customHeaders: Map<String, String> = emptyMap()
    )

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String? = null,
        val toolCalls: List<ToolCall>? = null,
        val toolCallId: String? = null,
        val name: String? = null
    )

    @Serializable
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val arguments: String
    )

    @Serializable
    data class ToolDefinition(
        val type: String = "function",
        val function: FunctionDefinition
    )

    @Serializable
    data class FunctionDefinition(
        val name: String,
        val description: String,
        val parameters: Map<String, String> = emptyMap()
    )

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val tools: List<ToolDefinition>? = null,
        val temperature: Float = 0.7f,
        val max_tokens: Int? = null
    )

    @Serializable
    data class ChatResponse(
        val id: String = "",
        val choices: List<Choice> = emptyList(),
        val model: String? = null
    )

    @Serializable
    data class Choice(
        val index: Int = 0,
        val message: ChatMessage,
        val finish_reason: String? = null
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private var providers = mutableMapOf<ProviderType, ProviderConfig>()
    private var activeProvider: ProviderType = ProviderType.OPENAI

    init {
        loadProvidersFromSettings()
    }

    fun setActiveProvider(type: ProviderType) {
        activeProvider = type
        Log.d(TAG, "Active provider: $type")
    }

    fun getActiveProvider(): ProviderConfig? = providers[activeProvider]

    fun getProviders(): Map<ProviderType, ProviderConfig> = providers.toMap()

    fun updateProvider(config: ProviderConfig) {
        providers[config.type] = config
        saveProvidersToSettings()
    }

    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        model: String? = null,
        tools: List<ToolDefinition>? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Result<ChatResponse> {
        val provider = getActiveProvider()
            ?: return Result.failure(Exception("No active provider configured. Please add an API key in Settings."))

        val requestModel = model ?: provider.defaultModel
        if (requestModel.isBlank()) {
            return Result.failure(Exception("No model specified for ${provider.name}"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = sendRequest(provider, requestModel, messages, tools, temperature, maxTokens)
                Result.success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Chat completion failed", e)
                Result.failure(e)
            }
        }
    }

    private fun sendRequest(
        provider: ProviderConfig,
        model: String,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        temperature: Float,
        maxTokens: Int?
    ): ChatResponse {
        val url = buildRequestUrl(provider, model)

        val chatRequest = ChatRequest(
            model = model,
            messages = messages,
            tools = if (tools.isNullOrEmpty()) null else tools,
            temperature = temperature,
            max_tokens = maxTokens
        )

        val body = json.encodeToString(chatRequest).toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body)

        // Auth headers
        when (provider.type) {
            ProviderType.ANTHROPIC -> {
                requestBuilder.addHeader("x-api-key", provider.apiKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            ProviderType.GOOGLE -> {
                // Key is in URL for Google
            }
            else -> {
                requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey}")
            }
        }

        provider.customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $responseBody")
            }
            return json.decodeFromString<ChatResponse>(responseBody)
        }
    }

    private fun buildRequestUrl(provider: ProviderConfig, model: String): String {
        return when (provider.type) {
            ProviderType.OPENAI -> {
                val base = provider.baseUrl.ifBlank { "https://api.openai.com" }
                "$base/v1/chat/completions"
            }
            ProviderType.ANTHROPIC -> {
                val base = provider.baseUrl.ifBlank { "https://api.anthropic.com" }
                "$base/v1/messages"
            }
            ProviderType.GOOGLE -> {
                val base = provider.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }
                "$base/v1beta/models/$model:generateContent?key=${provider.apiKey}"
            }
            ProviderType.CUSTOM -> {
                provider.baseUrl.ifBlank { throw Exception("Custom provider requires a base URL") }
            }
        }
    }

    private fun loadProvidersFromSettings() {
        val prefs = context.getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE)

        val openaiKey = prefs.getString("openai_key", "") ?: ""
        if (openaiKey.isNotBlank()) {
            providers[ProviderType.OPENAI] = ProviderConfig(
                type = ProviderType.OPENAI,
                name = "OpenAI",
                apiKey = openaiKey,
                defaultModel = prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini",
                enabled = true
            )
        }

        val anthropicKey = prefs.getString("anthropic_key", "") ?: ""
        if (anthropicKey.isNotBlank()) {
            providers[ProviderType.ANTHROPIC] = ProviderConfig(
                type = ProviderType.ANTHROPIC,
                name = "Anthropic",
                apiKey = anthropicKey,
                defaultModel = prefs.getString("anthropic_model", "claude-sonnet-4-6") ?: "claude-sonnet-4-6",
                enabled = true
            )
        }

        val googleKey = prefs.getString("google_key", "") ?: ""
        if (googleKey.isNotBlank()) {
            providers[ProviderType.GOOGLE] = ProviderConfig(
                type = ProviderType.GOOGLE,
                name = "Google Gemini",
                apiKey = googleKey,
                defaultModel = prefs.getString("google_model", "gemini-1.5-flash") ?: "gemini-1.5-flash",
                enabled = true
            )
        }

        val customUrl = prefs.getString("custom_url", "") ?: ""
        val customKey = prefs.getString("custom_key", "") ?: ""
        if (customUrl.isNotBlank() && customKey.isNotBlank()) {
            providers[ProviderType.CUSTOM] = ProviderConfig(
                type = ProviderType.CUSTOM,
                name = prefs.getString("custom_name", "Custom") ?: "Custom",
                apiKey = customKey,
                baseUrl = customUrl,
                defaultModel = prefs.getString("custom_model", "") ?: "",
                enabled = true
            )
        }

        // Set default active provider
        activeProvider = when {
            providers[ProviderType.OPENAI] != null -> ProviderType.OPENAI
            providers[ProviderType.ANTHROPIC] != null -> ProviderType.ANTHROPIC
            providers[ProviderType.GOOGLE] != null -> ProviderType.GOOGLE
            providers[ProviderType.CUSTOM] != null -> ProviderType.CUSTOM
            else -> ProviderType.OPENAI
        }

        Log.d(TAG, "Loaded ${providers.size} providers, active: $activeProvider")
    }

    private fun saveProvidersToSettings() {
        val prefs = context.getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE).edit()

        providers.forEach { (type, config) ->
            val prefix = when (type) {
                ProviderType.OPENAI -> "openai"
                ProviderType.ANTHROPIC -> "anthropic"
                ProviderType.GOOGLE -> "google"
                ProviderType.CUSTOM -> "custom"
            }
            prefs.putString("${prefix}_key", config.apiKey)
            prefs.putString("${prefix}_model", config.defaultModel)
            prefs.putBoolean("${prefix}_enabled", config.enabled)
            if (type == ProviderType.CUSTOM) {
                prefs.putString("custom_url", config.baseUrl)
                prefs.putString("custom_name", config.name)
            }
        }

        prefs.apply()
    }

    /**
     * Returns a real embedding vector for the given text where the active provider
     * supports it (OpenAI, Google, and Custom OpenAI-compatible endpoints).
     * Anthropic has no embeddings endpoint, so this always fails for that provider -
     * callers should catch that and fall back to a cheap stand-in vector instead.
     */
    suspend fun getEmbedding(text: String): Result<DoubleArray> {
        val provider = getActiveProvider()
            ?: return Result.failure(Exception("No active provider configured."))

        return withContext(Dispatchers.IO) {
            try {
                when (provider.type) {
                    ProviderType.OPENAI, ProviderType.CUSTOM -> {
                        val base = provider.baseUrl.ifBlank { "https://api.openai.com" }
                        val body = """{"model":"text-embedding-3-small","input":${json.encodeToString(text)}}"""
                            .toRequestBody(JSON_MEDIA_TYPE)
                        val req = Request.Builder()
                            .url("$base/v1/embeddings")
                            .addHeader("Authorization", "Bearer ${provider.apiKey}")
                            .addHeader("Content-Type", "application/json")
                            .post(body)
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            val respBody = resp.body?.string() ?: throw Exception("Empty response")
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: $respBody")
                            val obj = org.json.JSONObject(respBody)
                            val arr = obj.getJSONArray("data").getJSONObject(0).getJSONArray("embedding")
                            Result.success(DoubleArray(arr.length()) { arr.getDouble(it) })
                        }
                    }
                    ProviderType.GOOGLE -> {
                        val base = provider.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }
                        val body = """{"content":{"parts":[{"text":${json.encodeToString(text)}}]}}"""
                            .toRequestBody(JSON_MEDIA_TYPE)
                        val req = Request.Builder()
                            .url("$base/v1beta/models/embedding-001:embedContent?key=${provider.apiKey}")
                            .addHeader("Content-Type", "application/json")
                            .post(body)
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            val respBody = resp.body?.string() ?: throw Exception("Empty response")
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: $respBody")
                            val obj = org.json.JSONObject(respBody)
                            val arr = obj.getJSONObject("embedding").getJSONArray("values")
                            Result.success(DoubleArray(arr.length()) { arr.getDouble(it) })
                        }
                    }
                    ProviderType.ANTHROPIC -> Result.failure(Exception("Anthropic has no embeddings endpoint"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun testProvider(type: ProviderType): Result<String> {
        val provider = providers[type]
            ?: return Result.failure(Exception("Provider not configured"))

        val testMessage = listOf(ChatMessage(role = "user", content = "Say OK"))

        return withContext(Dispatchers.IO) {
            try {
                val originalActive = activeProvider
                activeProvider = type
                val result = chatCompletion(testMessage, maxTokens = 10)
                activeProvider = originalActive

                result.fold(
                    onSuccess = { response ->
                        val text = response.choices.firstOrNull()?.message?.content ?: "No response"
                        Result.success(text)
                    },
                    onFailure = { Result.failure(it) }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
