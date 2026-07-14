package com.sdgclaw

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sdgclaw.bridge.TermuxBridge

/**
 * MainActivity — home screen of SDG Claw.
 *
 * Shows the live Termux-bridge connection status and provides entry points
 * for testing the connection, opening Settings, starting a Chat session,
 * and — when the bridge is not connected — navigating to [BridgeSetupActivity].
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // ── Views ──────────────────────────────────────────────────────────────

    private lateinit var statusDot: ImageView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvBridgeHint: TextView
    private lateinit var btnSetupBridge: Button
    private lateinit var btnTestConnection: Button
    private lateinit var btnSettings: Button
    private lateinit var btnChat: Button

    // ── Bridge reference ───────────────────────────────────────────────────

    private val bridge: TermuxBridge?
        get() = (application as? SDGClawApplication)?.termuxBridge

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        wireConnectionCallbacks()
    }

    override fun onResume() {
        super.onResume()
        // Refresh status whenever the user returns (e.g. from SettingsActivity
        // or BridgeSetupActivity).
        updateConnectionStatus()
    }

    // ── View binding ───────────────────────────────────────────────────────

    private fun initViews() {
        statusDot = findViewById(R.id.statusDot)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvBridgeHint = findViewById(R.id.tvBridgeHint)
        btnSetupBridge = findViewById(R.id.btnSetupBridge)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSettings = findViewById(R.id.btnSettings)
        btnChat = findViewById(R.id.btnChat)
    }

    // ── Listeners ──────────────────────────────────────────────────────────

    private fun setupListeners() {
        btnTestConnection.setOnClickListener {
            bridge?.testConnection()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        btnSetupBridge.setOnClickListener {
            startActivity(Intent(this, BridgeSetupActivity::class.java))
        }

        // The hint text row is also tappable — same destination as the button.
        tvBridgeHint.setOnClickListener {
            startActivity(Intent(this, BridgeSetupActivity::class.java))
        }
    }

    // ── Connection callbacks ───────────────────────────────────────────────

    private fun wireConnectionCallbacks() {
        bridge?.setOnConnected {
            runOnUiThread { updateConnectionStatus() }
        }
        bridge?.setOnDisconnected {
            runOnUiThread { updateConnectionStatus() }
        }
    }

    // ── Status display ─────────────────────────────────────────────────────

    private fun updateConnectionStatus() {
        val connected = bridge?.isConnected() ?: false

        if (connected) {
            statusDot.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.circle_green)
            )
            tvConnectionStatus.text = getString(R.string.status_connected)
            tvConnectionStatus.setTextColor(
                ContextCompat.getColor(this, R.color.status_connected)
            )

            // Hide the bridge-setup nudge when already connected.
            tvBridgeHint.visibility = View.GONE
            btnSetupBridge.visibility = View.GONE
        } else {
            statusDot.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.circle_red)
            )
            tvConnectionStatus.text = getString(R.string.status_disconnected)
            tvConnectionStatus.setTextColor(
                ContextCompat.getColor(this, R.color.status_disconnected)
            )

            // Show the bridge-setup nudge when disconnected.
            tvBridgeHint.visibility = View.VISIBLE
            btnSetupBridge.visibility = View.VISIBLE
        }
    }
}
