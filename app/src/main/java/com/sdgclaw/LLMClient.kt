package com.sdgclaw

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLMClient — multi-provider HTTP client.
 *
 * Supported providers: OpenAI, Anthropic, Google Gemini, Custom (OpenAI-compatible).
 * Provider selection and API keys are read from SharedPreferences.
 */
class LLMClient(private val context: Context) {

    companion object {
        private const val TAG = "LLMClient"
        private const val PREFS = "sdgclaw_prefs"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    // ── Wire-format models ─────────────────────────────────────────────────────

    data class ChatMessage(
        val role: String,
        val content: String
    )

    data class ToolCallResult(
        val id: String,
        val name: String,
        val arguments: String
    )

    data class ChatResponse(
        val content: String?,
        val toolCalls: List<ToolCallResult> = emptyList()
    )

    // ── OkHttp client ──────────────────────────────────────────────────────────

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Send a chat completion request to the configured provider.
     *
     * @param messages  Conversation history (role + content pairs).
     * @param tools     Tool definitions expressed as [ToolDefinition] objects.
     * @return          [ChatResponse] with either a text reply or a list of tool calls.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList()
    ): ChatResponse {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val provider = prefs.getString("active_provider", "openai") ?: "openai"

        return when (provider.lowercase()) {
            "anthropic" -> chatAnthropic(messages, tools, prefs)
            "google"    -> chatGoogle(messages, tools, prefs)
            "custom"    -> chatOpenAiCompatible(messages, tools, prefs,
                baseUrl  = prefs.getString("custom_base_url", "https://api.openai.com/v1") ?: "",
                apiKey   = prefs.getString("custom_api_key", "") ?: "",
                model    = prefs.getString("custom_model", "gpt-4o") ?: "gpt-4o"
            )
            else        -> chatOpenAiCompatible(messages, tools, prefs,
                baseUrl  = "https://api.openai.com/v1",
                apiKey   = prefs.getString("openai_api_key", "") ?: "",
                model    = prefs.getString("openai_model", "gpt-4o") ?: "gpt-4o"
            )
        }
    }

    // ── OpenAI / OpenAI-compatible ─────────────────────────────────────────────

    private fun chatOpenAiCompatible(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        prefs: android.content.SharedPreferences,
        baseUrl: String,
        apiKey: String,
        model: String
    ): ChatResponse {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { t ->
                        put(JSONObject().apply {
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.parameters)
                            })
                        })
                    }
                })
                put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: throw RuntimeException("Empty response from LLM")
            if (!resp.isSuccessful) {
                throw RuntimeException("LLM HTTP ${resp.code}: $bodyStr")
            }
            return parseOpenAiResponse(bodyStr)
        }
    }

    private fun parseOpenAiResponse(json: String): ChatResponse {
        val root    = JSONObject(json)
        val choices = root.getJSONArray("choices")
        val message = choices.getJSONObject(0).getJSONObject("message")
        val content = message.optString("content", null.toString()).let {
            if (it == "null" || it.isEmpty()) null else it
        }

        val toolCalls = mutableListOf<ToolCallResult>()
        val tcArray = message.optJSONArray("tool_calls")
        if (tcArray != null) {
            for (i in 0 until tcArray.length()) {
                val tc   = tcArray.getJSONObject(i)
                val func = tc.getJSONObject("function")
                toolCalls.add(
                    ToolCallResult(
                        id        = tc.optString("id", "tc_$i"),
                        name      = func.getString("name"),
                        arguments = func.getString("arguments")
                    )
                )
            }
        }

        return ChatResponse(content = content, toolCalls = toolCalls)
    }

    // ── Anthropic Claude ───────────────────────────────────────────────────────

    private fun chatAnthropic(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        prefs: android.content.SharedPreferences
    ): ChatResponse {
        val apiKey = prefs.getString("anthropic_api_key", "") ?: ""
        val model  = prefs.getString("anthropic_model", "claude-3-5-sonnet-20241022") ?: ""

        // Separate system message(s) from the rest
        val systemText = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val userMessages = messages.filter { it.role != "system" }

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 8192)
            if (systemText.isNotBlank()) put("system", systemText)
            put("messages", JSONArray().apply {
                userMessages.forEach { m ->
                    put(JSONObject().apply {
                        put("role", if (m.role == "tool") "user" else m.role)
                        put("content", m.content)
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { t ->
                        put(JSONObject().apply {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.parameters)
                        })
                    }
                })
            }
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: throw RuntimeException("Empty response")
            if (!resp.isSuccessful) throw RuntimeException("Anthropic HTTP ${resp.code}: $bodyStr")
            return parseAnthropicResponse(bodyStr)
        }
    }

    private fun parseAnthropicResponse(json: String): ChatResponse {
        val root    = JSONObject(json)
        val content = root.getJSONArray("content")
        var text: String? = null
        val toolCalls = mutableListOf<ToolCallResult>()

        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            when (block.optString("type")) {
                "text"       -> text = block.optString("text")
                "tool_use"   -> toolCalls.add(
                    ToolCallResult(
                        id        = block.optString("id", "tc_$i"),
                        name      = block.getString("name"),
                        arguments = block.optJSONObject("input")?.toString() ?: "{}"
                    )
                )
            }
        }
        return ChatResponse(content = text, toolCalls = toolCalls)
    }

    // ── Google Gemini ──────────────────────────────────────────────────────────

    private fun chatGoogle(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        prefs: android.content.SharedPreferences
    ): ChatResponse {
        val apiKey = prefs.getString("google_api_key", "") ?: ""
        val model  = prefs.getString("google_model", "gemini-1.5-pro") ?: "gemini-1.5-pro"

        val contents = JSONArray().apply {
            messages.filter { it.role != "system" }.forEach { m ->
                put(JSONObject().apply {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", m.content) })
                    })
                })
            }
        }

        val body = JSONObject().apply {
            put("contents", contents)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: throw RuntimeException("Empty response")
            if (!resp.isSuccessful) throw RuntimeException("Google HTTP ${resp.code}: $bodyStr")
            return parseGoogleResponse(bodyStr)
        }
    }

    private fun parseGoogleResponse(json: String): ChatResponse {
        val root     = JSONObject(json)
        val cands    = root.getJSONArray("candidates")
        val content  = cands.getJSONObject(0).getJSONObject("content")
        val parts    = content.getJSONArray("parts")
        val text     = parts.getJSONObject(0).optString("text")
        return ChatResponse(content = text.ifBlank { null })
    }
}
