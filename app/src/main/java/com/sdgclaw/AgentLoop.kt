package com.sdgclaw

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.sdgclaw.bridge.TermuxBridge

class AgentLoop(
    private val coroutineScope: CoroutineScope,
    private val llmClient: LLMClient,
    private val toolRegistry: ToolRegistry,
    private val termuxBridge: TermuxBridge,
    private val systemPrompt: String = ""
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_ITERATIONS = 10

        /**
         * Number of text features extracted per ChatMessage for StabilityDiagnostic.
         * Features: [charCount, wordCount, sentenceCount, avgWordLen, punctRatio,
         *            digitRatio, upperRatio, uniqueWordRatio, roleBit0, roleBit1]
         */
        private const val FEATURE_DIM = 10
    }

    enum class AgentState { IDLE, THINKING, CALLING_TOOL, WAITING_FOR_TOOL, RESPONDING, ERROR }

    data class ChatMessage(
        val role: String,          // "user" | "assistant" | "tool" | "system"
        val content: String,
        val toolCallId: String? = null,
        val toolName: String? = null
    )

    // Mutable history accessible for state-vector extraction
    private val _conversationHistory = mutableListOf<ChatMessage>()
    val conversationHistory: List<ChatMessage> get() = _conversationHistory.toList()

    private val userMessageChannel = Channel<String>(Channel.UNLIMITED)
    private var agentJob: Job? = null

    // ── Callbacks ──────────────────────────────────────────────────────────────
    private var onAgentResponse: ((String) -> Unit)? = null
    private var onToolCall: ((String, String) -> Unit)? = null
    private var onToolResult: ((String, String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onStateChange: ((AgentState) -> Unit)? = null
    private var onDiagnosticReport: ((StabilityDiagnostic.Report) -> Unit)? = null

    fun setOnAgentResponse(cb: (String) -> Unit) { onAgentResponse = cb }
    fun setOnToolCall(cb: (String, String) -> Unit) { onToolCall = cb }
    fun setOnToolResult(cb: (String, String) -> Unit) { onToolResult = cb }
    fun setOnError(cb: (String) -> Unit) { onError = cb }
    fun setOnStateChange(cb: (AgentState) -> Unit) { onStateChange = cb }
    fun setOnDiagnosticReport(cb: (StabilityDiagnostic.Report) -> Unit) { onDiagnosticReport = cb }

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    fun start() {
        if (systemPrompt.isNotBlank()) {
            _conversationHistory.add(ChatMessage("system", systemPrompt))
        }
        agentJob = coroutineScope.launch { agentLoop() }
    }

    fun stop() {
        agentJob?.cancel()
        agentJob = null
    }

    fun sendUserMessage(text: String) {
        userMessageChannel.trySend(text)
    }

    /**
     * Cancel the running agent job immediately (used by Force Stop).
     */
    fun forceStop() {
        Log.w(TAG, "forceStop() called — cancelling agent coroutine")
        agentJob?.cancel()
        agentJob = null
        onStateChange?.invoke(AgentState.IDLE)
    }

    // ── Main loop ─────────────────────────────────────────────────────────────
    private suspend fun agentLoop() {
        while (coroutineScope.isActive) {
            try {
                val userText = userMessageChannel.receive()
                _conversationHistory.add(ChatMessage("user", userText))
                runAgentTurn()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Agent loop error", e)
                onError?.invoke(e.message ?: "Unknown error")
                onStateChange?.invoke(AgentState.ERROR)
            }
        }
    }

    private suspend fun runAgentTurn() {
        var iterations = 0
        onStateChange?.invoke(AgentState.THINKING)

        while (iterations < MAX_ITERATIONS && coroutineScope.isActive) {
            iterations++
            Log.d(TAG, "Agent iteration $iterations")

            try {
                // ── LLM call ──────────────────────────────────────────────────
                val response = withContext(Dispatchers.IO) {
                    llmClient.chat(
                        messages = _conversationHistory.map {
                            LLMClient.ChatMessage(role = it.role, content = it.content)
                        },
                        tools = toolRegistry.getToolDefinitions()
                    )
                }

                // ── Tool calls? ───────────────────────────────────────────────
                if (response.toolCalls.isNotEmpty()) {
                    _conversationHistory.add(
                        ChatMessage("assistant", response.content ?: "", toolCallId = null)
                    )

                    for (toolCall in response.toolCalls) {
                        onStateChange?.invoke(AgentState.CALLING_TOOL)
                        onToolCall?.invoke(toolCall.name, toolCall.arguments)

                        onStateChange?.invoke(AgentState.WAITING_FOR_TOOL)
                        val result = withContext(Dispatchers.IO) {
                            toolRegistry.executeTool(toolCall.name, toolCall.arguments)
                        }

                        val resultText = if (result.success) result.output else "Error: ${result.error}"
                        onToolResult?.invoke(toolCall.name, resultText)

                        _conversationHistory.add(
                            ChatMessage(
                                role = "tool",
                                content = resultText,
                                toolCallId = toolCall.id,
                                toolName = toolCall.name
                            )
                        )

                        // ── Emit diagnostic after each tool result ─────────────
                        emitDiagnosticIfPossible()
                    }

                    onStateChange?.invoke(AgentState.THINKING)
                } else {
                    // ── Final text response ────────────────────────────────────
                    val text = response.content ?: ""
                    _conversationHistory.add(ChatMessage("assistant", text))
                    onStateChange?.invoke(AgentState.RESPONDING)
                    onAgentResponse?.invoke(text)
                    onStateChange?.invoke(AgentState.IDLE)
                    return
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Error during agent iteration", e)
                onError?.invoke(e.message ?: "Unknown error")
                onStateChange?.invoke(AgentState.ERROR)
                return
            }
        }

        // Max iterations reached
        onStateChange?.invoke(AgentState.IDLE)
    }

    // ── Diagnostic helpers ─────────────────────────────────────────────────────

    /**
     * Run StabilityDiagnostic if we have enough history and fire the callback.
     * We use only non-system messages so the system prompt doesn't skew the vectors.
     */
    private fun emitDiagnosticIfPossible() {
        val relevant = _conversationHistory.filter { it.role != "system" }
        if (relevant.size < 2) return
        try {
            val vectors = Array(relevant.size) { i -> extractFeatureVector(relevant[i]) }
            val report = StabilityDiagnostic.analyze(vectors)
            onDiagnosticReport?.invoke(report)
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostic skipped: ${e.message}")
        }
    }

    /**
     * Convert a ChatMessage into a fixed-length numeric feature vector for
     * StabilityDiagnostic. All features are normalised to a [0, 1] range so
     * the Euclidean drift metric is meaningful across heterogeneous dimensions.
     *
     * Dimensions:
     *  0 – charCount          (log-scaled, capped at 4000 chars → 1.0)
     *  1 – wordCount          (log-scaled, capped at  500 words → 1.0)
     *  2 – sentenceCount      (log-scaled, capped at   50 sents → 1.0)
     *  3 – avgWordLen         (capped at 20 chars → 1.0)
     *  4 – punctRatio         fraction of chars that are punctuation
     *  5 – digitRatio         fraction of chars that are digits
     *  6 – upperRatio         fraction of alpha chars that are uppercase
     *  7 – uniqueWordRatio    unique words / total words
     *  8 – roleBit0           LSB of role ordinal (user=0, assistant=1, tool=0, system=1)
     *  9 – roleBit1           MSB of role ordinal (user=0, assistant=0, tool=1, system=1)
     */
    private fun extractFeatureVector(msg: ChatMessage): DoubleArray {
        val text = msg.content
        val len = text.length.toDouble().coerceAtLeast(1.0)

        val chars = text.toCharArray()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wordCount = words.size.toDouble().coerceAtLeast(1.0)
        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }

        val punctCount = chars.count { it in ".,;:!?\"'()-" }.toDouble()
        val digitCount = chars.count { it.isDigit() }.toDouble()
        val alphaChars = chars.filter { it.isLetter() }
        val upperCount = alphaChars.count { it.isUpperCase() }.toDouble()
        val alphaTotal = alphaChars.size.toDouble().coerceAtLeast(1.0)

        val avgWordLen = words.map { it.length }.average().coerceAtLeast(0.0)
        val uniqueWordRatio = words.map { it.lowercase() }.toSet().size.toDouble() / wordCount

        val roleOrdinal = when (msg.role) {
            "user"      -> 0
            "assistant" -> 1
            "tool"      -> 2
            "system"    -> 3
            else        -> 0
        }

        return doubleArrayOf(
            /* 0 charCount      */ logNorm(text.length.toDouble(), 4000.0),
            /* 1 wordCount      */ logNorm(wordCount, 500.0),
            /* 2 sentenceCount  */ logNorm(sentences.size.toDouble().coerceAtLeast(1.0), 50.0),
            /* 3 avgWordLen     */ (avgWordLen / 20.0).coerceIn(0.0, 1.0),
            /* 4 punctRatio     */ (punctCount / len).coerceIn(0.0, 1.0),
            /* 5 digitRatio     */ (digitCount / len).coerceIn(0.0, 1.0),
            /* 6 upperRatio     */ (upperCount / alphaTotal).coerceIn(0.0, 1.0),
            /* 7 uniqueWordRatio*/ uniqueWordRatio.coerceIn(0.0, 1.0),
            /* 8 roleBit0       */ (roleOrdinal and 1).toDouble(),
            /* 9 roleBit1       */ ((roleOrdinal shr 1) and 1).toDouble()
        )
    }

    /** log(1 + x) / log(1 + cap) clamped to [0, 1] */
    private fun logNorm(x: Double, cap: Double): Double {
        val result = Math.log1p(x) / Math.log1p(cap)
        return result.coerceIn(0.0, 1.0)
    }
}
