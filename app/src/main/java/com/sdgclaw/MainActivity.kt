package com.sdgclaw

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private var termuxBridge: TermuxBridge? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Get bridge from Application
        val app = application as SDGClawApplication
        termuxBridge = app.getTermuxBridge()
        
        setupUI()
        setupBridgeCallbacks()
    }
    
    private fun setupUI() {
        // Test connection button
        findViewById<View>(R.id.btnTestConnection).setOnClickListener {
            testConnection()
        }
        
        // Open settings button
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        // Open chat button
        findViewById<View>(R.id.btnChat).setOnClickListener {
            val intent = android.content.Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupBridgeCallbacks() {
        termuxBridge?.setOnConnected {
            runOnUiThread {
                updateConnectionStatus(true)
                Log.d(TAG, "Bridge connected!")
                Toast.makeText(this, "Connected to Termux Bridge!", Toast.LENGTH_SHORT).show()
            }
        }
        
        termuxBridge?.setOnDisconnected { reason ->
            runOnUiThread {
                updateConnectionStatus(false)
                Log.d(TAG, "Bridge disconnected: $reason")
                Toast.makeText(this, "Disconnected: $reason", Toast.LENGTH_SHORT).show()
            }
        }
        
        termuxBridge?.setOnMessage { message ->
            runOnUiThread {
                Log.d(TAG, "Received from bridge: $message")
                appendToLog("RX: $message")
            }
        }
        
        termuxBridge?.setOnError { error ->
            runOnUiThread {
                Log.e(TAG, "Bridge error: $error")
                appendToLog("ERROR: $error")
            }
        }
        
        // Check initial connection state
        updateConnectionStatus(termuxBridge?.isConnected() == true)
    }
    
    private fun testConnection() {
        Log.d(TAG, "Test connection button clicked")
        appendToLog("TX: Sending test message...")
        termuxBridge?.testConnection()
    }
    
    private fun updateConnectionStatus(connected: Boolean) {
        val statusText = findViewById<View>(R.id.tvConnectionStatus) as? android.widget.TextView
        val indicator = findViewById<View>(R.id.ivConnectionIndicator)
        
        statusText?.text = if (connected) "Connected" else "Disconnected"
        statusText?.setTextColor(if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        
        indicator?.setBackgroundColor(if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
    }
    
    private fun appendToLog(message: String) {
        val logView = findViewById<View>(R.id.tvLog) as? android.widget.TextView
        logView?.append("\n$message")
        // Auto-scroll to bottom
        val scrollView = findViewById<View>(R.id.scrollView) as? android.widget.ScrollView
        scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't disconnect bridge - let Application manage lifecycle
    }
}