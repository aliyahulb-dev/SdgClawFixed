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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        initViews()
        setupToolbar()
        setupRecyclerView()
        setupInput()
        initAgent()
    }
    
    private fun initViews() {
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        typingIndicator = findViewById(R.id.typingIndicator)
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
        
        llmClient = LLMClient(this)
        toolRegistry = ToolRegistry(termuxBridge!!)
        
        agentLoop = AgentLoop(
            coroutineScope = lifecycleScope,
            llmClient = llmClient!!,
            toolRegistry = toolRegistry!!,
            termuxBridge = termuxBridge!!
        )
        
        setupAgentCallbacks()
        agentLoop!!.start()
    }
    
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
                    AgentLoop.AgentState.THINKING -> showTyping()
                    AgentLoop.AgentState.RESPONDING -> hideTyping()
                    AgentLoop.AgentState.ERROR -> hideTyping()
                    else -> {}
                }
            }
        }
    }
    
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
    
    // Message Types
    enum class Type {
        USER, ASSISTANT, TOOL, SYSTEM
    }
    
    data class Message(
        val type: Type,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Adapter
    class MessageAdapter(private val messages: List<Message>) :
        RecyclerView.Adapter<MessageAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val llUserMessage: LinearLayout = view.findViewById(R.id.llUserMessage)
            val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
            val tvUserTime: TextView = view.findViewById(R.id.tvUserTime)
            val llAssistantMessage: LinearLayout = view.findViewById(R.id.llAssistantMessage)
            val tvAssistantMessage: TextView = view.findViewById(R.id.tvAssistantMessage)
            val tvAssistantTime: TextView = view.findViewById(R.id.tvAssistantTime)
            val llToolMessage: LinearLayout = view.findViewById(R.id.llToolMessage)
            val tvToolMessage: TextView = view.findViewById(R.id.tvToolMessage)
            val tvToolTime: TextView = view.findViewById(R.id.tvToolTime)
            val tvSystemMessage: TextView = view.findViewById(R.id.tvSystemMessage)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]
            val time = DateFormat.format("HH:mm", Date(message.timestamp)).toString()
            
            // Hide all
            holder.llUserMessage.visibility = View.GONE
            holder.llAssistantMessage.visibility = View.GONE
            holder.llToolMessage.visibility = View.GONE
            holder.tvSystemMessage.visibility = View.GONE
            
            when (message.type) {
                Type.USER -> {
                    holder.llUserMessage.visibility = View.VISIBLE
                    holder.tvUserMessage.text = message.content
                    holder.tvUserTime.text = time
                }
                Type.ASSISTANT -> {
                    holder.llAssistantMessage.visibility = View.VISIBLE
                    holder.tvAssistantMessage.text = message.content
                    holder.tvAssistantTime.text = time
                }
                Type.TOOL -> {
                    holder.llToolMessage.visibility = View.VISIBLE
                    holder.tvToolMessage.text = message.content
                    holder.tvToolTime.text = time
                }
                Type.SYSTEM -> {
                    holder.tvSystemMessage.visibility = View.VISIBLE
                    holder.tvSystemMessage.text = message.content
                }
            }
        }
        
        override fun getItemCount() = messages.size
    }
}