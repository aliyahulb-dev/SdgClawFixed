package com.sdgclaw

import android.util.Log
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * AgentLoop - Core agent execution loop
 * Handles the conversation flow between user, LLM, and tools
 */
class AgentLoop(
    private val coroutineScope: CoroutineScope,
    private val llmClient: LLMClient,
    private val toolRegistry: ToolRegistry,
    private val termuxBridge: TermuxBridge
) {

    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_ITERATIONS = 10
    }

    // Message channels
    private val userMessages = Channel<String>(Channel.UNLIMITED)
    private val agentResponses = Channel<String>(Channel.UNLIMITED)

    // State
    private var isRunning = false
    private var conversationHistory = mutableListOf<ChatMessage>()
    private var currentIteration = 0

    // Callbacks
    private var onAgentResponse: ((String) -> Unit)? = null
    private var onToolCall: ((String, JSONObject) -> Unit)? = null
    private var onToolResult: ((String, String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onStateChange: ((AgentState) -> Unit)? = null

    // Agent states
    enum class AgentState {
        IDLE,
        THINKING,
        CALLING_TOOL,
        WAITING_FOR_TOOL,
        RESPONDING,
        ERROR
    }

    data class ChatMessage(
        val role: String, // "user", "assistant", "tool", "system"
        val content: String,
        val toolCallId: String? = null,
        val toolName: String? = null
    )

    fun start() {
        if (isRunning) return
        isRunning = true
        currentIteration = 0
        setState(AgentState.IDLE)

        coroutineScope.launch {
            processUserMessages()
        }

        Log.d(TAG, "AgentLoop started")
    }

    fun stop() {
        isRunning = false
        userMessages.close()
        agentResponses.close()
        Log.d(TAG, "AgentLoop stopped")
    }

    fun sendUserMessage(message: String) {
        userMessages.trySend(message)
    }

    fun getAgentResponses(): ReceiveChannel<String> = agentResponses

    fun setOnAgentResponse(callback: (String) -> Unit) { onAgentResponse = callback }
    fun setOnToolCall(callback: (String, JSONObject) -> Unit) { onToolCall = callback }
    fun setOnToolResult(callback: (String, String) -> Unit) { onToolResult = callback }
    fun setOnError(callback: (String) -> Unit) { onError = callback }
    fun setOnStateChange(callback: (AgentState) -> Unit) { onStateChange = callback }

    private suspend fun processUserMessages() {
        for (message in userMessages) {
            if (!isRunning) break
            conversationHistory.add(ChatMessage("user", message))
            coroutineScope.launch { runAgentTurn() }
        }
    }

    private suspend fun runAgentTurn() {
        if (currentIteration >= MAX_ITERATIONS) {
            handleError("Max iterations reached")
            return
        }

        currentIteration++
        setState(AgentState.THINKING)

        try {
            val messages = buildLLMMessages()

            // Convert tool definitions to LLMClient format
            val toolDefs = toolRegistry.getToolDefinitions().map { jsonObj ->
                val fn = jsonObj.getJSONObject("function")
                LLMClient.ToolDefinition(
                    function = LLMClient.FunctionDefinition(
                        name = fn.getString("name"),
                        description = fn.getString("description"),
                        parameters = emptyMap() // simplified for now
                    )
                )
            }

            val chatMessages = messages.map { map ->
                LLMClient.ChatMessage(
                    role = map["role"] as String,
                    content = map["content"] as? String
                )
            }

            val result = llmClient.chatCompletion(chatMessages, tools = toolDefs)

            result.fold(
                onSuccess = { response ->
                    val choice = response.choices.firstOrNull()
                    val content = choice?.message?.content ?: ""
                    val toolCalls = choice?.message?.toolCalls

                    if (!toolCalls.isNullOrEmpty()) {
                        for (toolCall in toolCalls) {
                            setState(AgentState.CALLING_TOOL)
                            val args = try {
                                JSONObject(toolCall.function.arguments)
                            } catch (e: Exception) {
                                JSONObject()
                            }
                            onToolCall?.invoke(toolCall.function.name, args)

                            setState(AgentState.WAITING_FOR_TOOL)
                            val toolResult = toolRegistry.executeTool(toolCall.function.name, args)
                            onToolResult?.invoke(toolCall.function.name, toolResult)

                            conversationHistory.add(
                                ChatMessage(
                                    role = "tool",
                                    content = toolResult,
                                    toolCallId = toolCall.id,
                                    toolName = toolCall.function.name
                                )
                            )
                        }
                        coroutineScope.launch { runAgentTurn() }
                    } else {
                        setState(AgentState.RESPONDING)
                        conversationHistory.add(ChatMessage("assistant", content))
                        agentResponses.trySend(content)
                        onAgentResponse?.invoke(content)
                        setState(AgentState.IDLE)
                        currentIteration = 0
                    }
                },
                onFailure = { error ->
                    handleError(error.message ?: "LLM request failed")
                }
            )

        } catch (e: Exception) {
            handleError(e.message ?: "Unknown error")
        }
    }

    private fun buildLLMMessages(): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()

        messages.add(mapOf(
            "role" to "system",
            "content" to getSystemPrompt()
        ))

        val recentHistory = if (conversationHistory.size > 20) {
            conversationHistory.takeLast(20)
        } else {
            conversationHistory
        }

        for (msg in recentHistory) {
            val msgMap = mutableMapOf<String, Any>(
                "role" to msg.role,
                "content" to msg.content
            )
            msg.toolCallId?.let { msgMap["tool_call_id"] = it }
            msg.toolName?.let { msgMap["name"] = it }
            messages.add(msgMap)
        }

        return messages
    }

    private fun getSystemPrompt(): String {
        return """You are SDG Claw, an AI assistant running on Android with access to a Termux backend.
You can execute commands, manage files, and perform various tasks through the available tools.
Be helpful, concise, and explain what you're doing when using tools.""".trimIndent()
    }

    private fun handleError(error: String) {
        Log.e(TAG, "Agent error: $error")
        setState(AgentState.ERROR)
        onError?.invoke(error)
        agentResponses.trySend("Error: $error")
        currentIteration = 0
        setState(AgentState.IDLE)
    }

    private fun setState(state: AgentState) {
        onStateChange?.invoke(state)
    }

    fun clearHistory() {
        conversationHistory.clear()
        currentIteration = 0
    }

    fun getHistory(): List<ChatMessage> = conversationHistory.toList()
}
