package com.spiderbot.controller

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.spiderbot.controller.utils.NetworkUtils
import com.spiderbot.controller.utils.BotCommandSender
import kotlinx.coroutines.*

class ConnectionActivity : AppCompatActivity() {

    private lateinit var wifiSsidEdit: TextInputEditText
    private lateinit var wifiPasswordEdit: TextInputEditText
    private lateinit var botIpEdit: TextInputEditText
    private lateinit var sendCredentialsButton: MaterialButton
    private lateinit var connectButton: MaterialButton

    private var networkUtils: NetworkUtils? = null
    private var botCommandSender: BotCommandSender? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force portrait orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContentView(R.layout.activity_connection)

        initializeViews()
        setupClickListeners()

        networkUtils = NetworkUtils(this)
        botCommandSender = BotCommandSender()
    }

    private fun initializeViews() {
        wifiSsidEdit = findViewById(R.id.wifi_ssid_edit)
        wifiPasswordEdit = findViewById(R.id.wifi_password_edit)
        botIpEdit = findViewById(R.id.bot_ip_edit)
        sendCredentialsButton = findViewById(R.id.send_credentials_button)
        connectButton = findViewById(R.id.connect_button)
    }

    private fun setupClickListeners() {
        sendCredentialsButton.setOnClickListener {
            sendWifiCredentials()
        }

        connectButton.setOnClickListener {
            connectToBot()
        }
    }

    private fun sendWifiCredentials() {
        val ssid = wifiSsidEdit.text.toString().trim()
        val password = wifiPasswordEdit.text.toString().trim()

        if (ssid.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        sendCredentialsButton.isEnabled = false
        sendCredentialsButton.text = getString(R.string.sending_credentials)

        activityScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    botCommandSender?.sendWifiCredentials(ssid, password) ?: false
                }

                if (success) {
                    Toast.makeText(this@ConnectionActivity, getString(R.string.credentials_sent), Toast.LENGTH_SHORT).show()
                    // Enable IP input after successful credential sending
                    botIpEdit.isEnabled = true
                    connectButton.isEnabled = true
                } else {
                    Toast.makeText(this@ConnectionActivity, getString(R.string.credentials_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ConnectionActivity, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            } finally {
                sendCredentialsButton.isEnabled = true
                sendCredentialsButton.text = getString(R.string.send_credentials)
            }
        }
    }

    private fun connectToBot() {
        val ipAddress = botIpEdit.text.toString().trim()

        if (ipAddress.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_invalid_ip), Toast.LENGTH_SHORT).show()
            return
        }

        if (!networkUtils?.isValidIpAddress(ipAddress)!!) {
            Toast.makeText(this, getString(R.string.error_invalid_ip), Toast.LENGTH_SHORT).show()
            return
        }

        connectButton.isEnabled = false
        connectButton.text = getString(R.string.connecting)

        activityScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    botCommandSender?.connectToBot(ipAddress) ?: false
                }

                if (success) {
                    Toast.makeText(this@ConnectionActivity, getString(R.string.connected), Toast.LENGTH_SHORT).show()

                    // Navigate to Control screen
                    val intent = Intent(this@ConnectionActivity, ControlActivity::class.java)
                    intent.putExtra("bot_ip", ipAddress)
                    startActivity(intent)
                    finish()

                    // Add smooth transition animation
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                } else {
                    Toast.makeText(this@ConnectionActivity, getString(R.string.connection_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ConnectionActivity, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            } finally {
                connectButton.isEnabled = true
                connectButton.text = getString(R.string.connect_to_bot)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
