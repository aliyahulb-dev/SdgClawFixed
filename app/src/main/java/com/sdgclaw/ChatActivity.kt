package com.sdgclaw

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.launch
import java.util.Date

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var typingIndicator: LinearLayout
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    // Agent components
    private var agentLoop: AgentLoop? = null
    private var llmClient: LLMClient? = null
    private var toolRegistry: ToolRegistry? = null
    private var termuxBridge: TermuxBridge? = null

    // ── Stability dashboard views ──────────────────────────────────────────────
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var tvDashboardHandle: TextView
    private lateinit var chipRegime: Chip
    private lateinit var chipSScore: Chip
    private lateinit var chipDrift: Chip
    private lateinit var chipEntropy: Chip
    private lateinit var tvSparkline: TextView
    private lateinit var tvRegimeDetail: TextView
    private lateinit var btnForceStop: com.google.android.material.button.MaterialButton

    /** Most-recently received diagnostic report — kept for re-render on sheet expand. */
    private var lastReport: StabilityDiagnostic.Report? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupInput()
        initAgent()
        setupDashboard()
    }

    private fun initViews() {
        rvMessages        = findViewById(R.id.rvMessages)
        etMessage         = findViewById(R.id.etMessage)
        btnSend           = findViewById(R.id.btnSend)
        typingIndicator   = findViewById(R.id.typingIndicator)

        // Stability dashboard
        tvDashboardHandle = findViewById(R.id.tvDashboardHandle)
        chipRegime        = findViewById(R.id.chipRegime)
        chipSScore        = findViewById(R.id.chipSScore)
        chipDrift         = findViewById(R.id.chipDrift)
        chipEntropy       = findViewById(R.id.chipEntropy)
        tvSparkline       = findViewById(R.id.tvSparkline)
        tvRegimeDetail    = findViewById(R.id.tvRegimeDetail)
        btnForceStop      = findViewById(R.id.btnForceStop)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(messages)
        rvMessages.adapter = adapter
    }

    private fun setupInput() {
        btnSend.setOnClickListener { sendMessage() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    private fun initAgent() {
        val app = application as SDGClawApplication
        termuxBridge = app.getTermuxBridge()

        // Read system prompt from SharedPreferences
        val prefs = getSharedPreferences("sdgclaw_prefs", MODE_PRIVATE)
        val systemPrompt = prefs.getString("system_prompt", "") ?: ""

        llmClient    = LLMClient(this)
        toolRegistry = ToolRegistry(termuxBridge!!)

        agentLoop = AgentLoop(
            coroutineScope = lifecycleScope,
            llmClient      = llmClient!!,
            toolRegistry   = toolRegistry!!,
            termuxBridge   = termuxBridge!!,
            systemPrompt   = systemPrompt
        )

        setupAgentCallbacks()
        agentLoop!!.start()
    }

    // ── Bottom-sheet dashboard ─────────────────────────────────────────────────
    private fun setupDashboard() {
        val bottomSheet: View = findViewById(R.id.stabilityDashboard)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // Start hidden — shown automatically on first diagnostic report
        bottomSheetBehavior.state     = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight =
            resources.getDimensionPixelSize(R.dimen.dashboard_peek_height)
        bottomSheetBehavior.isHideable = true

        // Toggle collapsed ↔ expanded by tapping the handle row
        tvDashboardHandle.setOnClickListener {
            bottomSheetBehavior.state = when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.STATE_EXPANDED  -> BottomSheetBehavior.STATE_COLLAPSED
                else                                -> BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // Force Stop — cancel agent, hide sheet
        btnForceStop.setOnClickListener {
            agentLoop?.forceStop()
            Toast.makeText(this, "Agent force-stopped", Toast.LENGTH_SHORT).show()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    // ── Agent callbacks ────────────────────────────────────────────────────────
    private fun setupAgentCallbacks() {
        agentLoop!!.setOnAgentResponse { response ->
            runOnUiThread {
                hideTyping()
                addMessage(Message(Type.ASSISTANT, response))
            }
        }

        agentLoop!!.setOnToolCall { name, args ->
            runOnUiThread {
                showTyping()
                addMessage(Message(Type.TOOL, "Calling: $name\nArgs: $args"))
            }
        }

        agentLoop!!.setOnToolResult { name, result ->
            runOnUiThread {
                addMessage(Message(Type.TOOL, "Result ($name):\n$result"))
            }
        }

        agentLoop!!.setOnError { error ->
            runOnUiThread {
                hideTyping()
                addMessage(Message(Type.SYSTEM, "Error: $error"))
            }
        }

        agentLoop!!.setOnStateChange { state ->
            runOnUiThread {
                when (state) {
                    AgentLoop.AgentState.THINKING       -> showTyping()
                    AgentLoop.AgentState.RESPONDING     -> hideTyping()
                    AgentLoop.AgentState.ERROR          -> hideTyping()
                    AgentLoop.AgentState.IDLE           -> {
                        hideTyping()
                        // Collapse (but keep visible) so the user can review the last report
                        if (lastReport != null &&
                            bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                    }
                    else -> { /* CALLING_TOOL / WAITING_FOR_TOOL — keep as-is */ }
                }

                // Reveal collapsed sheet whenever the agent is actively working
                val isWorking = state != AgentLoop.AgentState.IDLE
                if (isWorking && lastReport != null &&
                    bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }

                // Recalculate Force Stop visibility after state change
                updateForceStopVisibility()
            }
        }

        // ── Diagnostic report ──────────────────────────────────────────────────
        agentLoop!!.setOnDiagnosticReport { report ->
            runOnUiThread {
                lastReport = report
                renderDashboard(report)

                // Show collapsed sheet on first report
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }
    }

    // ── Dashboard rendering ────────────────────────────────────────────────────

    /**
     * Populate chips, sparkline and detail text from a [StabilityDiagnostic.Report].
     */
    private fun renderDashboard(report: StabilityDiagnostic.Report) {
        // ── Regime chip ────────────────────────────────────────────────────────
        val (regimeLabel, regimeColorRes) = when (report.regime) {
            "converging"           -> "✅ Converging"  to R.color.chip_converging
            "diverging"            -> "🔴 Diverging"   to R.color.chip_diverging
            "limit-cycle"          -> "🔁 Limit-Cycle" to R.color.chip_limit_cycle
            "chaotic-or-unsettled" -> "⚡ Chaotic"     to R.color.chip_chaotic
            else                   -> "❓ ${report.regime}" to R.color.chip_unknown
        }
        chipRegime.text = regimeLabel
        chipRegime.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(getColor(regimeColorRes))

        // ── Metric chips ───────────────────────────────────────────────────────
        chipSScore.text  = "S: ${"%.3f".format(report.sScore)}"
        chipDrift.text   = "Drift: ${"%.3f".format(report.finalDrift)}"
        chipEntropy.text = "H̄: ${"%.3f".format(report.meanEntropy)}"

        // ── Drift sparkline ────────────────────────────────────────────────────
        tvSparkline.text = buildDriftSparkline(report.drift)

        // ── Regime detail ──────────────────────────────────────────────────────
        tvRegimeDetail.text = report.regimeDetail

        // ── Force Stop visibility ──────────────────────────────────────────────
        updateForceStopVisibility()
    }

    /**
     * Build a monospace ASCII sparkline of the drift series.
     *
     * Uses Unicode block elements (▁▂▃▄▅▆▇█, 8 levels) scaled to the local
     * min/max of the visible window.  Shows the last [MAX_SPARK_CHARS] values
     * so the string fits on a single line on a typical phone.
     */
    private fun buildDriftSparkline(drifts: List<Double>): String {
        val MAX_SPARK_CHARS = 32
        val BLOCKS = "▁▂▃▄▅▆▇█"

        if (drifts.isEmpty()) return "─"

        val values = drifts.takeLast(MAX_SPARK_CHARS)
        val minV = values.min()
        val maxV = values.max()
        val range = maxV - minV

        val bar = if (range < 1e-9) {
            // Flat trajectory — render at mid level
            "▄".repeat(values.size)
        } else {
            values.joinToString("") { v ->
                val idx = ((v - minV) / range * (BLOCKS.length - 1))
                    .toInt()
                    .coerceIn(0, BLOCKS.length - 1)
                BLOCKS[idx].toString()
            }
        }

        return "$bar  (${drifts.size} steps)"
    }

    /**
     * Show the Force Stop button only for regimes that indicate runaway behaviour.
     */
    private fun updateForceStopVisibility() {
        val report = lastReport
        val dangerous = report != null &&
            (report.regime == "diverging" || report.regime == "chaotic-or-unsettled")
        btnForceStop.visibility = if (dangerous) View.VISIBLE else View.GONE
    }

    // ── Message helpers ────────────────────────────────────────────────────────
    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isBlank()) return

        etMessage.text.clear()
        addMessage(Message(Type.USER, text))
        agentLoop!!.sendUserMessage(text)
        showTyping()
    }

    private fun addMessage(message: Message) {
        messages.add(message)
        adapter.notifyItemInserted(messages.lastIndex)
        rvMessages.scrollToPosition(messages.lastIndex)
    }

    private fun showTyping() {
        typingIndicator.visibility = View.VISIBLE
    }

    private fun hideTyping() {
        typingIndicator.visibility = View.GONE
    }

    override fun onDestroy() {
        agentLoop?.stop()
        super.onDestroy()
    }

    // ── Message Types ──────────────────────────────────────────────────────────
    enum class Type { USER, ASSISTANT, TOOL, SYSTEM }

    data class Message(
        val type: Type,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ── RecyclerView adapter ───────────────────────────────────────────────────
    class MessageAdapter(private val messages: List<Message>) :
        RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val llUserMessage: LinearLayout      = view.findViewById(R.id.llUserMessage)
            val tvUserMessage: TextView          = view.findViewById(R.id.tvUserMessage)
            val tvUserTime: TextView             = view.findViewById(R.id.tvUserTime)
            val llAssistantMessage: LinearLayout = view.findViewById(R.id.llAssistantMessage)
            val tvAssistantMessage: TextView     = view.findViewById(R.id.tvAssistantMessage)
            val tvAssistantTime: TextView        = view.findViewById(R.id.tvAssistantTime)
            val llToolMessage: LinearLayout      = view.findViewById(R.id.llToolMessage)
            val tvToolMessage: TextView          = view.findViewById(R.id.tvToolMessage)
            val tvToolTime: TextView             = view.findViewById(R.id.tvToolTime)
            val tvSystemMessage: TextView        = view.findViewById(R.id.tvSystemMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]
            val time = DateFormat.format("HH:mm", Date(message.timestamp)).toString()

            holder.llUserMessage.visibility      = View.GONE
            holder.llAssistantMessage.visibility = View.GONE
            holder.llToolMessage.visibility      = View.GONE
            holder.tvSystemMessage.visibility    = View.GONE

            when (message.type) {
                Type.USER -> {
                    holder.llUserMessage.visibility = View.VISIBLE
                    holder.tvUserMessage.text       = message.content
                    holder.tvUserTime.text          = time
                }
                Type.ASSISTANT -> {
                    holder.llAssistantMessage.visibility = View.VISIBLE
                    holder.tvAssistantMessage.text       = message.content
                    holder.tvAssistantTime.text          = time
                }
                Type.TOOL -> {
                    holder.llToolMessage.visibility = View.VISIBLE
                    holder.tvToolMessage.text       = message.content
                    holder.tvToolTime.text          = time
                }
                Type.SYSTEM -> {
                    holder.tvSystemMessage.visibility = View.VISIBLE
                    holder.tvSystemMessage.text       = message.content
                }
            }
        }

        override fun getItemCount() = messages.size
    }
}
