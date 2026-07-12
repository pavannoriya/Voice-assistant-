package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.MacroAction
import com.example.service.VoiceService

class MacroAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("MacroService", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // In "Recording Mode", we could capture clicks. 
        // For this MVP, we capture bounds of clicked nodes to determine X, Y.
        if (isRecording && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source ?: return
            
            // Handle System Navigation Bar clicks
            val packageName = source.packageName?.toString()
            val contentDesc = source.contentDescription?.toString()
            val viewId = source.viewIdResourceName
            
            if (packageName == "com.android.systemui") {
                if (contentDesc?.equals("Back", ignoreCase = true) == true || viewId?.contains("back") == true) {
                    val action = MacroAction(
                        macroId = 0,
                        orderIndex = recordedActions.size,
                        actionType = "BACK",
                        xCoordinate = 0f,
                        yCoordinate = 0f,
                        delayMs = 1000L
                    )
                    recordedActions.add(action)
                    Log.d("MacroService", "Recorded System UI BACK")
                    return
                } else if (contentDesc?.equals("Home", ignoreCase = true) == true || viewId?.contains("home") == true) {
                    val action = MacroAction(
                        macroId = 0,
                        orderIndex = recordedActions.size,
                        actionType = "HOME",
                        xCoordinate = 0f,
                        yCoordinate = 0f,
                        delayMs = 1000L
                    )
                    recordedActions.add(action)
                    Log.d("MacroService", "Recorded System UI HOME")
                    return
                }
            }

            val rect = android.graphics.Rect()
            source.getBoundsInScreen(rect)
            
            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()
            
            val clickedText = source.text?.toString() ?: source.contentDescription?.toString()
            
            Log.d("MacroService", "Recorded Click: $centerX, $centerY, id: $viewId, text: $clickedText")
            
            val action = MacroAction(
                macroId = 0, // Assigned later
                orderIndex = recordedActions.size,
                actionType = "TAP",
                xCoordinate = centerX,
                yCoordinate = centerY,
                viewId = viewId,
                clickedText = clickedText,
                delayMs = 1000L
            )
            recordedActions.add(action)
        }
    }

    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isRecording && event.action == android.view.KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_BACK -> {
                    val action = MacroAction(
                        macroId = 0,
                        orderIndex = recordedActions.size,
                        actionType = "BACK",
                        xCoordinate = 0f,
                        yCoordinate = 0f,
                        delayMs = 1000L
                    )
                    recordedActions.add(action)
                    Log.d("MacroService", "Recorded BACK key")
                }
                android.view.KeyEvent.KEYCODE_HOME -> {
                    val action = MacroAction(
                        macroId = 0,
                        orderIndex = recordedActions.size,
                        actionType = "HOME",
                        xCoordinate = 0f,
                        yCoordinate = 0f,
                        delayMs = 1000L
                    )
                    recordedActions.add(action)
                    Log.d("MacroService", "Recorded HOME key")
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.d("MacroService", "Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun executeActions(actions: List<MacroAction>, parameterReplacements: Map<String, String> = emptyMap()) {
        CoroutineScope(Dispatchers.Main).launch {
            for (action in actions) {
                when (action.actionType) {
                    "TAP" -> {
                        val originalText = action.clickedText
                        val targetText = if (originalText != null) parameterReplacements[originalText] ?: originalText else null
                        
                        var clickedByText = false
                        if (targetText != null) {
                            Log.d("MacroService", "Looking for node with text: $targetText")
                            clickedByText = findNodeByTextAndClick(rootInActiveWindow, targetText)
                        }
                        
                        if (!clickedByText) {
                            Log.d("MacroService", "Executing TAP at ${action.xCoordinate}, ${action.yCoordinate}")
                            dispatchTap(action.xCoordinate, action.yCoordinate)
                        }
                    }
                    "BACK" -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        Log.d("MacroService", "Executing BACK")
                    }
                    "HOME" -> {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        Log.d("MacroService", "Executing HOME")
                    }
                }
                delay(action.delayMs)
            }
        }
    }

    fun findNodeByTextAndClick(root: android.view.accessibility.AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.text?.toString()?.contains(text, ignoreCase = true) == true || 
                node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                dispatchTap(rect.centerX().toFloat(), rect.centerY().toFloat())
                return true
            }
        }
        return false
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("MacroService", "Gesture completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("MacroService", "Gesture cancelled")
            }
        }, null)
    }

    fun getScreenHierarchy(): String {
        val rootNode = rootInActiveWindow ?: return "No active window"
        val stringBuilder = java.lang.StringBuilder()
        dumpNode(rootNode, 0, stringBuilder)
        return stringBuilder.toString()
    }

    private fun openAppByName(appName: String) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        
        // Exact match first
        for (app in apps) {
            val name = app.loadLabel(pm).toString()
            if (name.equals(appName, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    Log.d("MacroService", "Opened app: $name")
                    return
                }
            }
        }
        
        // Partial match
        for (app in apps) {
            val name = app.loadLabel(pm).toString()
            if (name.contains(appName, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    Log.d("MacroService", "Opened app: $name (partial match)")
                    return
                }
            }
        }
        Log.d("MacroService", "App not found: $appName")
    }

    private fun dumpNode(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int, builder: java.lang.StringBuilder) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrEmpty()) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            builder.append("${indent}[${node.className}] \"$text\" (Bounds: ${rect.left},${rect.top}-${rect.right},${rect.bottom})\n")
        }
        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), depth + 1, builder)
        }
    }

    fun executeAutonomousTask(goal: String) {
        CoroutineScope(Dispatchers.Main).launch {
            var step = 0
            while (step < 10) { // max 10 steps to prevent infinite loops
                val hierarchy = getScreenHierarchy()
                Log.d("Autonomous", "Hierarchy:\n$hierarchy")
                VoiceService.setProcessingAI(true)
                val action = com.example.ai.GeminiHelper.decideNextAutonomousAction(goal, hierarchy)
                VoiceService.setProcessingAI(false)
                
                if (action == null) {
                    Log.d("Autonomous", "Failed to decide next action")
                    break
                }
                
                Log.d("Autonomous", "Action: ${action.actionType}, Target: ${action.targetText}, Reason: ${action.reason}")
                
                when (action.actionType) {
                    "OPEN_APP" -> {
                        action.targetText?.let { openAppByName(it) }
                    }
                    "TAP" -> {
                        var tapped = false
                        if (action.targetText != null) {
                            tapped = findNodeByTextAndClick(rootInActiveWindow, action.targetText)
                        }
                        if (!tapped && action.targetX != null && action.targetY != null) {
                            dispatchTap(action.targetX.toFloat(), action.targetY.toFloat())
                        }
                    }
                    "TYPE" -> {
                        action.textToType?.let { ShizukuHelper.executeShellCommand("input text \"$it\"") }
                    }
                    "SCROLL_UP" -> ShizukuHelper.executeShellCommand("input swipe 500 1500 500 500")
                    "SCROLL_DOWN" -> ShizukuHelper.executeShellCommand("input swipe 500 500 500 1500")
                    "HOME" -> ShizukuHelper.executeShellCommand("input keyevent 3")
                    "BACK" -> ShizukuHelper.executeShellCommand("input keyevent 4")
                    "DONE" -> {
                        Log.d("Autonomous", "Goal Achieved")
                        break
                    }
                }
                
                delay(2000) // Wait for UI to settle
                step++
            }
        }
    }

    companion object {
        var instance: MacroAccessibilityService? = null
            private set
            
        var isRecording = false
        val recordedActions = mutableListOf<MacroAction>()
        
        fun startRecording() {
            recordedActions.clear()
            isRecording = true
        }
        
        fun stopRecording(): List<MacroAction> {
            isRecording = false
            return recordedActions.toList()
        }

        fun recordManualAction(type: String) {
            val action = MacroAction(
                macroId = 0,
                orderIndex = recordedActions.size,
                actionType = type,
                xCoordinate = 0f,
                yCoordinate = 0f,
                delayMs = 1000L
            )
            recordedActions.add(action)
            Log.d("MacroService", "Recorded manual action: $type")
            
            // Execute it immediately so the user can see the effect
            when (type) {
                "BACK" -> instance?.performGlobalAction(GLOBAL_ACTION_BACK)
                "HOME" -> instance?.performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }
}
