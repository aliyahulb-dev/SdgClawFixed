package com.sdgclaw

import android.content.Context
import android.util.Log
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Core agent loop that orchestrates the conversation between the user, the
 * LLM, and the tool layer.
 *
 * System-prompt injection
 * ───────────────────────
 * When [sendUserMessage] is called for the very first turn (i.e. the history
 * is empty before the user message is added), [AgentLoop] reads the
 * `system_prompt` key from the `sdgclaw_llm` SharedPreferences.  If the
 * value is non-empty it is prepended to [conversationHistory] as a
 * `ChatMessage(role = "system", …)` before the user message.  This means
 * every subsequent LLM call in the same session sees the system prompt at
 * position 0 without any further work.
 */
class AgentLoop(
    private val context: Context,
    private val llmClient: LLMClient,
    private val toolRegistry: ToolRegistry,
    private val termuxBridge: TermuxBridge?
) {

    // ─────────────────────────────────────────────────────────────────────
    // Types
    // ─────────────────────────────────────────────────────────────────────

    data class ChatMessage(
        val role: String,             // "user" | "assistant" | "tool" | "system"
        val content: String,
        val toolCallId: String? = null,
        val toolName: String?   = null
    )

    enum class AgentState {
        IDLE, THINKING, CALLING_TOOL, WAITING_FOR_TOOL, RESPONDING, ERROR
    }

    // ─────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG           = "AgentLoop"
        private const val MAX_ITERATIONS = 10
    }

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private val conversationHistory = mutableListOf<ChatMessage>()
    private var currentState = AgentState.IDLE

    // ─────────────────────────────────────────────────────────────────────
    // Callbacks (set by the host Activity)
    // ─────────────────────────────────────────────────────────────────────

    private var onAgentResponse: ((String) -> Unit)?        = null
    private var onToolCall:      ((String, String) -> Unit)? = null   // (toolName, args)
    private var onToolResult:    ((String, String) -> Unit)? = null   // (toolName, result)
    private var onError:         ((String) -> Unit)?        = null
    private var onStateChange:   ((AgentState) -> Unit)?   = null

    fun setOnAgentResponse(cb: (String) -> Unit)         { onAgentResponse = cb }
    fun setOnToolCall(cb: (String, String) -> Unit)      { onToolCall      = cb }
    fun setOnToolResult(cb: (String, String) -> Unit)    { onToolResult    = cb }
    fun setOnError(cb: (String) -> Unit)                 { onError         = cb }
    fun setOnStateChange(cb: (AgentState) -> Unit)       { onStateChange   = cb }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Entry point called by [ChatActivity] when the user submits a message.
     *
     * On the very first call (empty history) the system prompt stored in
     * SharedPreferences is injected at position 0 of [conversationHistory]
     * before the user message is appended.
     */
    fun sendUserMessage(userMessage: String, scope: CoroutineScope) {
        if (currentState != AgentState.IDLE) {
            Log.w(TAG, "sendUserMessage ignored: agent is not IDLE (state=$currentState)")
            return
        }

        scope.launch {
            // ── Inject system prompt on the first turn ─────────────────
            if (conversationHistory.isEmpty()) {
                val systemPrompt = readSystemPrompt()
                if (systemPrompt.isNotEmpty()) {
                    Log.d(TAG, "Injecting system prompt (${systemPrompt.length} chars)")
                    conversationHistory.add(
                        ChatMessage(role = "system", content = systemPrompt)
                    )
                }
            }

            conversationHistory.add(ChatMessage(role = "user", content = userMessage))
            runAgentTurn()
        }
    }

    /** Clears the conversation history so the next message starts a fresh session. */
    fun resetConversation() {
        conversationHistory.clear()
        setState(AgentState.IDLE)
        Log.d(TAG, "Conversation history cleared")
    }

    /** Returns an unmodifiable snapshot of the current conversation history. */
    fun getHistory(): List<ChatMessage> = conversationHistory.toList()

    // ─────────────────────────────────────────────────────────────────────
    // Internal: agent turn
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun runAgentTurn() {
        var iterations = 0

        while (iterations < MAX_ITERATIONS) {
            iterations++
            Log.d(TAG, "Agent iteration $iterations / $MAX_ITERATIONS")

            setState(AgentState.THINKING)

            val response = withContext(Dispatchers.IO) {
                try {
                    llmClient.chat(conversationHistory, toolRegistry.getToolDefinitions())
                } catch (e: Exception) {
                    Log.e(TAG, "LLM call failed", e)
                    null
                }
            }

            if (response == null) {
                setState(AgentState.ERROR)
                onError?.invoke("LLM request failed. Check your API key and connection.")
                setState(AgentState.IDLE)
                return
            }

            // ── Text response ──────────────────────────────────────────
            if (response.content.isNotBlank()) {
                conversationHistory.add(
                    ChatMessage(role = "assistant", content = response.content)
                )
                setState(AgentState.RESPONDING)
                onAgentResponse?.invoke(response.content)
            }

            // ── Tool calls ─────────────────────────────────────────────
            val toolCalls = response.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // No tool calls: the agent is done for this turn.
                setState(AgentState.IDLE)
                return
            }

            setState(AgentState.CALLING_TOOL)

            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                val toolArgs = toolCall.function.arguments

                Log.d(TAG, "Executing tool: $toolName  args=$toolArgs")
                onToolCall?.invoke(toolName, toolArgs)

                setState(AgentState.WAITING_FOR_TOOL)

                val toolResult = withContext(Dispatchers.IO) {
                    try {
                        toolRegistry.executeTool(toolName, toolArgs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Tool execution failed: $toolName", e)
                        ToolRegistry.ToolResult(
                            success = false,
                            output  = "",
                            error   = "Tool error: ${e.message}"
                        )
                    }
                }

                val resultContent = if (toolResult.success) {
                    toolResult.output
                } else {
                    "ERROR: ${toolResult.error ?: "unknown error"}"
                }

                onToolResult?.invoke(toolName, resultContent)

                conversationHistory.add(
                    ChatMessage(
                        role       = "tool",
                        content    = resultContent,
                        toolCallId = toolCall.id,
                        toolName   = toolName
                    )
                )
            }

            // Loop back to send the tool results to the LLM.
        }

        // Iteration cap reached
        Log.w(TAG, "MAX_ITERATIONS ($MAX_ITERATIONS) reached — stopping agent turn")
        setState(AgentState.ERROR)
        onError?.invoke("Agent reached the maximum number of iterations ($MAX_ITERATIONS).")
        setState(AgentState.IDLE)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reads the system prompt from SharedPreferences.
     * Returns an empty string if none has been set.
     */
    private fun readSystemPrompt(): String {
        return try {
            val prefs = context.getSharedPreferences("sdgclaw_llm", Context.MODE_PRIVATE)
            prefs.getString(SettingsActivity.KEY_SYSTEM_PROMPT, "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read system prompt", e)
            ""
        }
    }

    private fun setState(newState: AgentState) {
        currentState = newState
        onStateChange?.invoke(newState)
    }
}
