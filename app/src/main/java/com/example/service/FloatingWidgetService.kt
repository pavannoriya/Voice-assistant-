package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat

class FloatingWidgetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingWidget()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }
    }

    private fun showFloatingWidget() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#EADDFF")) // Primary Container Color
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val statusText = android.widget.TextView(this).apply {
            text = "Teach Me"
            setTextColor(android.graphics.Color.parseColor("#21005D")) // On Primary Container
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, padding, 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        val actionButton = android.widget.Button(this).apply {
            text = "Start"
        }

        val closeButton = android.widget.Button(this).apply {
            text = "X"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.parseColor("#21005D"))
        }

        val backButton = android.widget.Button(this).apply {
            text = "Back"
            visibility = View.GONE
        }

        val homeButton = android.widget.Button(this).apply {
            text = "Home"
            visibility = View.GONE
        }

        var isRecording = MacroAccessibilityService.isRecording

        actionButton.setOnClickListener {
            if (isRecording) {
                MacroAccessibilityService.stopRecording()
                isRecording = false
                backButton.visibility = View.GONE
                homeButton.visibility = View.GONE
                val intent = Intent(this@FloatingWidgetService, com.example.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("ACTION_SAVE_MACRO", true)
                }
                startActivity(intent)
                stopSelf()
            } else {
                MacroAccessibilityService.startRecording()
                isRecording = true
                statusText.text = "Recording..."
                actionButton.text = "Stop"
                backButton.visibility = View.VISIBLE
                homeButton.visibility = View.VISIBLE
            }
        }

        backButton.setOnClickListener {
            if (isRecording) {
                MacroAccessibilityService.recordManualAction("BACK")
            }
        }

        homeButton.setOnClickListener {
            if (isRecording) {
                MacroAccessibilityService.recordManualAction("HOME")
            }
        }

        closeButton.setOnClickListener {
            stopSelf()
        }

        container.addView(statusText)
        container.addView(backButton)
        container.addView(homeButton)
        container.addView(actionButton)
        container.addView(closeButton)

        floatingView = container
        windowManager.addView(floatingView, params)
    }

    private fun createNotification(): Notification {
        val channelId = "floating_widget_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Macro Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Macro Assistant")
            .setContentText("Recording widget is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
