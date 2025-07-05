package com.spiderbot.controller

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.spiderbot.controller.utils.BotCommandSender
import kotlinx.coroutines.*

class ControlActivity : AppCompatActivity() {

    private lateinit var connectionStatus: TextView
    private lateinit var botIpDisplay: TextView
    private lateinit var voiceControlFab: FloatingActionButton
    private lateinit var upButton: MaterialButton
    private lateinit var downButton: MaterialButton
    private lateinit var leftButton: MaterialButton
    private lateinit var rightButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton
    private lateinit var commandFeedback: TextView

    private var botCommandSender: BotCommandSender? = null
    private var botIpAddress: String = ""
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Touch tracking variables
    private var isPressed = false
    private var currentCommand = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContentView(R.layout.activity_control)

        // Get bot IP from intent
        botIpAddress = intent.getStringExtra("bot_ip") ?: ""

        initializeViews()
        setupTouchListeners()
        setupClickListeners()

        botCommandSender = BotCommandSender()
        botIpDisplay.text = botIpAddress
    }

    private fun initializeViews() {
        connectionStatus = findViewById(R.id.connection_status)
        botIpDisplay = findViewById(R.id.bot_ip_display)
        voiceControlFab = findViewById(R.id.voice_control_fab)
        upButton = findViewById(R.id.up_button)
        downButton = findViewById(R.id.down_button)
        leftButton = findViewById(R.id.left_button)
        rightButton = findViewById(R.id.right_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        commandFeedback = findViewById(R.id.command_feedback)
    }

    private fun setupTouchListeners() {
        // Up/Down control touch listener
        upButton.setOnTouchListener { _, event ->
            handleDirectionalTouch(event, "forward")
        }

        downButton.setOnTouchListener { _, event ->
            handleDirectionalTouch(event, "backward")
        }

        // Left/Right control touch listener
        leftButton.setOnTouchListener { _, event ->
            handleDirectionalTouch(event, "left")
        }

        rightButton.setOnTouchListener { _, event ->
            handleDirectionalTouch(event, "right")
        }
    }

    private fun handleDirectionalTouch(event: MotionEvent, direction: String): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                currentCommand = direction
                sendCommand(direction)
                showCommandFeedback(direction.uppercase())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                currentCommand = ""
                sendCommand("stop")
                hideCommandFeedback()
                return true
            }
        }
        return false
    }

    private fun setupClickListeners() {
        voiceControlFab.setOnClickListener {
            val intent = Intent(this, VoiceControlActivity::class.java)
            intent.putExtra("bot_ip", botIpAddress)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_up, R.anim.slide_out_down)
        }

        disconnectButton.setOnClickListener {
            disconnectFromBot()
        }
    }

    private fun sendCommand(command: String) {
        activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    botCommandSender?.sendCommand(botIpAddress, command)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ControlActivity, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCommandFeedback(command: String) {
        commandFeedback.text = command
        commandFeedback.visibility = View.VISIBLE
    }

    private fun hideCommandFeedback() {
        commandFeedback.visibility = View.GONE
    }

    private fun disconnectFromBot() {
        activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    botCommandSender?.disconnect()
                }

                Toast.makeText(this@ControlActivity, getString(R.string.disconnected), Toast.LENGTH_SHORT).show()

                // Return to connection screen
                val intent = Intent(this@ControlActivity, ConnectionActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()

                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            } catch (e: Exception) {
                Toast.makeText(this@ControlActivity, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        // Prevent accidental back press, require disconnect button
        Toast.makeText(this, "Use disconnect button to exit", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()

        // Send stop command when leaving activity
        if (isPressed) {
            sendCommand("stop")
        }
    }
}
