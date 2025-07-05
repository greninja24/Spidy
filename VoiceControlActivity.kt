
package com.spiderbot.controller

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.spiderbot.controller.utils.BotCommandSender
import kotlinx.coroutines.*

class VoiceControlActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private lateinit var voiceIcon: ImageView
    private lateinit var voiceStatus: TextView
    private lateinit var voiceCommandDisplay: TextView
    private lateinit var voiceHint: TextView
    private lateinit var voiceRecordFab: FloatingActionButton
    private lateinit var voiceRecordingIndicator: View
    private lateinit var backButton: MaterialButton
    private lateinit var connectionStatus: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var botCommandSender: BotCommandSender? = null
    private var botIpAddress: String = ""
    private var isListening = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContentView(R.layout.activity_voice_control)

        // Get bot IP from intent
        botIpAddress = intent.getStringExtra("bot_ip") ?: ""

        initializeViews()
        setupClickListeners()
        setupSpeechRecognizer()

        botCommandSender = BotCommandSender()

        // Check microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    private fun initializeViews() {
        voiceIcon = findViewById(R.id.voice_icon)
        voiceStatus = findViewById(R.id.voice_status)
        voiceCommandDisplay = findViewById(R.id.voice_command_display)
        voiceHint = findViewById(R.id.voice_hint)
        voiceRecordFab = findViewById(R.id.voice_record_fab)
        voiceRecordingIndicator = findViewById(R.id.voice_recording_indicator)
        backButton = findViewById(R.id.back_button)
        connectionStatus = findViewById(R.id.connection_status)
    }

    private fun setupClickListeners() {
        voiceRecordFab.setOnClickListener {
            toggleVoiceRecording()
        }

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up)
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                voiceCommandDisplay.text = getString(R.string.listening)
            }

            override fun onBeginningOfSpeech() {
                voiceRecordingIndicator.visibility = View.VISIBLE
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Can be used to show audio level visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                voiceRecordingIndicator.visibility = View.GONE
            }

            override fun onError(error: Int) {
                isListening = false
                voiceRecordingIndicator.visibility = View.GONE
                voiceCommandDisplay.text = "Error occurred. Try again."

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> voiceCommandDisplay.text = "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> voiceCommandDisplay.text = "Speech timeout"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> voiceCommandDisplay.text = "Microphone permission required"
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                voiceRecordingIndicator.visibility = View.GONE

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    voiceCommandDisplay.text = command
                    processVoiceCommand(command)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    voiceCommandDisplay.text = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleVoiceRecording() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.permission_microphone), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(intent)
        isListening = true
        voiceStatus.text = "Listening..."
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        voiceStatus.text = getString(R.string.voice_control_active)
        voiceRecordingIndicator.visibility = View.GONE
    }

    private fun processVoiceCommand(command: String) {
        val lowerCommand = command.lowercase()
        val botCommand = when {
            lowerCommand.contains("forward") || lowerCommand.contains("up") || lowerCommand.contains("ahead") -> "forward"
            lowerCommand.contains("back") || lowerCommand.contains("backward") || lowerCommand.contains("reverse") -> "backward"
            lowerCommand.contains("left") || lowerCommand.contains("turn left") -> "left"
            lowerCommand.contains("right") || lowerCommand.contains("turn right") -> "right"
            lowerCommand.contains("stop") || lowerCommand.contains("halt") || lowerCommand.contains("brake") -> "stop"
            else -> null
        }

        if (botCommand != null) {
            sendVoiceCommand(botCommand)

            // Auto-stop after 2 seconds for movement commands
            if (botCommand != "stop") {
                activityScope.launch {
                    delay(2000)
                    sendVoiceCommand("stop")
                }
            }
        } else {
            Toast.makeText(this, "Command not recognized. Try: forward, back, left, right, stop", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendVoiceCommand(command: String) {
        activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    botCommandSender?.sendCommand(botIpAddress, command)
                }
                Toast.makeText(this@VoiceControlActivity, "Command sent: ${command.uppercase()}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@VoiceControlActivity, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_microphone), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        activityScope.cancel()
    }
}