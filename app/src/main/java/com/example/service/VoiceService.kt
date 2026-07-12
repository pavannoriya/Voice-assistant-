package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceService : Service() {

    private lateinit var speechHelper: SpeechHelper
    
    companion object {
        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening
        
        private val _lastSpokenText = MutableStateFlow("")
        val lastSpokenText: StateFlow<String> = _lastSpokenText
        
        private val _partialSpokenText = MutableStateFlow("")
        val partialSpokenText: StateFlow<String> = _partialSpokenText
        
        private val _commandFlow = MutableStateFlow<String?>(null)
        val commandFlow: StateFlow<String?> = _commandFlow
        
        private val _isProcessingAI = MutableStateFlow(false)
        val isProcessingAI: StateFlow<Boolean> = _isProcessingAI
        
        fun clearCommand() {
            _commandFlow.value = null
        }
        
        fun setProcessingAI(isProcessing: Boolean) {
            _isProcessingAI.value = isProcessing
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(2, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(2, createNotification())
        }
        
        var isAwake = false

        speechHelper = SpeechHelper(this, { result ->
            val lower = result.lowercase()
            _lastSpokenText.value = result
            _partialSpokenText.value = ""
            
            if (lower.contains("stop")) {
                stopSelf()
                return@SpeechHelper
            }
            
            val wakeWords = listOf("hey jarvis", "ok jarvis", "jarvis")
            var matchedWakeWord = ""
            for (wakeWord in wakeWords) {
                if (lower.contains(wakeWord)) {
                    matchedWakeWord = wakeWord
                    break
                }
            }
            
            if (isAwake) {
                if (result.isNotEmpty()) {
                    _commandFlow.value = result
                }
                isAwake = false
            } else if (matchedWakeWord.isNotEmpty()) {
                val cmd = lower.substringAfter(matchedWakeWord).trim()
                if (cmd.isNotEmpty()) {
                    _commandFlow.value = cmd
                } else {
                    isAwake = true
                    _lastSpokenText.value = "Yes?"
                    _commandFlow.value = "hey_jarvis_awake"
                }
            }
        }, { partialResult ->
            _partialSpokenText.value = partialResult
        })
        
        _isListening.value = true
        speechHelper.startListening(continuous = true)
    }

    private fun createNotification(): Notification {
        val channelId = "voice_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Voice Assistant")
            .setContentText("Listening for 'Hey Jarvis'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper.stopListening()
        _isListening.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
