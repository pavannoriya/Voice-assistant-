package com.example

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.MacroRepository
import com.example.data.SettingsManager
import com.example.service.FloatingWidgetService
import com.example.service.MacroAccessibilityService
import com.example.service.SpeechHelper
import com.example.service.VoiceService
import com.example.ui.MacroViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MacroViewModel
    private lateinit var speechHelper: SpeechHelper
    
    private var isListening by mutableStateOf(false)
    private var lastSpokenText by mutableStateOf("")
    private var partialSpokenText by mutableStateOf("")

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted
            } else {
                Toast.makeText(this, "Microphone permission required for voice commands", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsManager.init(applicationContext)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        val repository = MacroRepository.getInstance(applicationContext as android.app.Application)
        viewModel = androidx.lifecycle.ViewModelProvider(
            this, 
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MacroViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return MacroViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        )[MacroViewModel::class.java]

        try {
            // Check if Shizuku is available, initialization is typically not required for simply calling shell commands if the service is running
            if (!com.example.service.ShizukuHelper.isShizukuAvailable()) {
                android.util.Log.w("Shizuku", "Shizuku is not running")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        speechHelper = SpeechHelper(this, { result ->
            isListening = false
            if (result.isNotEmpty()) {
                lastSpokenText = result
                partialSpokenText = ""
                processVoiceCommand(result)
            } else {
                Toast.makeText(this, "I don't know or didn't hear.", Toast.LENGTH_SHORT).show()
                partialSpokenText = ""
            }
        }, { partialResult ->
            partialSpokenText = partialResult
        })

        if (intent.getBooleanExtra("ACTION_SAVE_MACRO", false) == true) {
            val recorded = MacroAccessibilityService.recordedActions
            if (recorded.isNotEmpty()) {
                showSaveMacroDialog()
            }
        }

        setContent {
            val vsIsListening by VoiceService.isListening.collectAsStateWithLifecycle()
            val vsLastSpoken by VoiceService.lastSpokenText.collectAsStateWithLifecycle()
            val vsPartialSpoken by VoiceService.partialSpokenText.collectAsStateWithLifecycle()
            val vsCommand by VoiceService.commandFlow.collectAsStateWithLifecycle()
            
            LaunchedEffect(vsCommand) {
                vsCommand?.let { cmd ->
                    if (cmd == "hey_jarvis_awake") {
                        Toast.makeText(this@MainActivity, "Yes?", Toast.LENGTH_SHORT).show()
                    } else {
                        processVoiceCommand(cmd)
                    }
                    VoiceService.clearCommand()
                }
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    onDismiss = { showSettingsDialog = false }
                )
            }

            MyApplicationTheme {
                val isProcessingAI by VoiceService.isProcessingAI.collectAsStateWithLifecycle()
                MainScreen(
                    viewModel = viewModel,
                    onStartTeachMode = {
                        if (checkOverlayPermission() && checkAccessibilityPermission()) {
                            startService(Intent(this, FloatingWidgetService::class.java))
                        }
                    },
                    onListen = {
                        isListening = true
                        partialSpokenText = ""
                        speechHelper.startListening(continuous = false)
                    },
                    isListening = isListening || vsIsListening,
                    lastSpokenText = if (vsIsListening) vsLastSpoken else lastSpokenText,
                    partialSpokenText = if (vsIsListening) vsPartialSpoken else partialSpokenText,
                    isProcessingAI = isProcessingAI
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("ACTION_SAVE_MACRO", false) == true) {
            // Re-render UI to show Save Macro dialog
            // We can handle this via state.
            val recorded = MacroAccessibilityService.recordedActions
            if (recorded.isNotEmpty()) {
                // Show dialog for naming
                showSaveMacroDialog()
            }
        }
    }

    private var showSaveDialog by mutableStateOf(false)

    private var showSettingsDialog by mutableStateOf(false)

    private fun showSaveMacroDialog() {
        showSaveDialog = true
    }

    private fun processVoiceCommand(phrase: String) {
        val lowerPhrase = phrase.lowercase(java.util.Locale.ROOT)
        
        if (lowerPhrase.contains("learn") || lowerPhrase.contains("sikhna") || lowerPhrase.contains("seekh")) {
            if (checkOverlayPermission() && checkAccessibilityPermission()) {
                startService(Intent(this, com.example.service.FloatingWidgetService::class.java))
                Toast.makeText(this, "Teach mode started", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        if (lowerPhrase.contains("save")) {
            val recorded = com.example.service.MacroAccessibilityService.recordedActions
            if (recorded.isNotEmpty()) {
                com.example.service.MacroAccessibilityService.stopRecording()
                stopService(Intent(this, com.example.service.FloatingWidgetService::class.java))
                showSaveMacroDialog()
            } else {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (lowerPhrase.contains("back") || lowerPhrase.contains("piche") || lowerPhrase.contains("peeche")) {
            MacroAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Toast.makeText(this, "Going Back", Toast.LENGTH_SHORT).show()
            return
        }
        if (lowerPhrase.contains("home") || lowerPhrase.contains("hom screen") || lowerPhrase.contains("hom")) {
            MacroAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            Toast.makeText(this, "Going Home", Toast.LENGTH_SHORT).show()
            return
        }
        if (lowerPhrase.contains("recent") || lowerPhrase.contains("app switcher") || lowerPhrase.contains("recent apps") || lowerPhrase.contains("minimise") || lowerPhrase.contains("minimize")) {
            MacroAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            Toast.makeText(this, "Recent Apps", Toast.LENGTH_SHORT).show()
            return
        }
        if (lowerPhrase.contains("swipe up") || lowerPhrase.contains("upar") || lowerPhrase.contains("scroll up")) {
            com.example.service.ShizukuHelper.executeShellCommand("input swipe 500 1500 500 500")
            Toast.makeText(this, "Swiping Up", Toast.LENGTH_SHORT).show()
            return
        }
        if (lowerPhrase.contains("swipe down") || lowerPhrase.contains("niche") || lowerPhrase.contains("neeche") || lowerPhrase.contains("scroll down")) {
            com.example.service.ShizukuHelper.executeShellCommand("input swipe 500 500 500 1500")
            Toast.makeText(this, "Swiping Down", Toast.LENGTH_SHORT).show()
            return
        }
        if (lowerPhrase.contains("swipe left") || lowerPhrase.contains("left") || lowerPhrase.contains("baayein") || lowerPhrase.contains("bayein")) {
            com.example.service.ShizukuHelper.executeShellCommand("input swipe 900 1000 100 1000")
            Toast.makeText(this, "Swiping Left", Toast.LENGTH_SHORT).show()
            return
        }
        if (lowerPhrase.contains("swipe right") || lowerPhrase.contains("right") || lowerPhrase.contains("daayein") || lowerPhrase.contains("dayein")) {
            com.example.service.ShizukuHelper.executeShellCommand("input swipe 100 1000 900 1000")
            Toast.makeText(this, "Swiping Right", Toast.LENGTH_SHORT).show()
            return
        }
        
        val clickRegex1 = Regex("(?:click on|click|tap on|tap|open) (.*)", RegexOption.IGNORE_CASE).find(lowerPhrase) 
        val clickRegex2 = Regex("(.*) (?:par click|par tap|click|tap|open)", RegexOption.IGNORE_CASE).find(lowerPhrase)
        val clickMatch = clickRegex1 ?: clickRegex2
            
        if (clickMatch != null) {
            val target = clickMatch.groupValues[1].trim().removeSuffix(" karo").trim()
            if (target.isNotEmpty() && target != "karo" && target != "kare") {
                lifecycleScope.launch {
                    val clicked = MacroAccessibilityService.instance?.findNodeByTextAndClick(MacroAccessibilityService.instance?.rootInActiveWindow, target) ?: false
                    if (clicked) {
                        Toast.makeText(this@MainActivity, "Clicked on $target", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Could not find $target. Trying Autonomous Mode...", Toast.LENGTH_SHORT).show()
                        MacroAccessibilityService.instance?.executeAutonomousTask(phrase)
                    }
                }
                return
            }
        }

        lifecycleScope.launch {
            val repository = MacroRepository.getInstance(applicationContext)
            var macro = repository.getMacroByVoicePhrase(phrase)
            var parameterReplacements = emptyMap<String, String>()
            
            if (macro == null) {
                // Try to use Gemini to find intent
                val allMacros = repository.allMacros.first()
                if (allMacros.isNotEmpty()) {
                    VoiceService.setProcessingAI(true)
                    val intent = com.example.ai.GeminiHelper.analyzeCommand(phrase, allMacros)
                    VoiceService.setProcessingAI(false)
                    if (intent != null && intent.macroId != null) {
                        macro = allMacros.find { it.id == intent.macroId }
                        intent.parameterReplacements?.let { replacements ->
                            parameterReplacements = replacements.associate { it.originalText to it.newText }
                        }
                    }
                }
            }
            
            if (macro != null) {
                val actions = repository.getActionsForMacro(macro.id)
                MacroAccessibilityService.instance?.executeActions(actions, parameterReplacements)
                    ?: Toast.makeText(this@MainActivity, "Accessibility Service not running", Toast.LENGTH_SHORT).show()
                Toast.makeText(this@MainActivity, "Executing: ${macro.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "No exact macro. Trying Autonomous Mode...", Toast.LENGTH_SHORT).show()
                MacroAccessibilityService.instance?.executeAutonomousTask(phrase)
                    ?: Toast.makeText(this@MainActivity, "Accessibility Service not running", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return false
        }
        return true
    }

    private fun checkAccessibilityPermission(): Boolean {
        var isAccessibilityEnabled = false
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                isAccessibilityEnabled = true
                break
            }
        }
        
        if (!isAccessibilityEnabled) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Please enable Macro Assistant Service", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(
        viewModel: MacroViewModel,
        onStartTeachMode: () -> Unit,
        onListen: () -> Unit,
        isListening: Boolean,
        lastSpokenText: String,
        partialSpokenText: String,
        isProcessingAI: Boolean = false
    ) {
        val macros by viewModel.allMacros.collectAsStateWithLifecycle()

        if (showSaveDialog) {
            var macroName by remember { mutableStateOf("") }
            var voicePhrase by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Macro") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = macroName,
                            onValueChange = { macroName = it },
                            label = { Text("Macro Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = voicePhrase,
                            onValueChange = { voicePhrase = it },
                            label = { Text("Voice Phrase (e.g. send audit)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Recorded ${MacroAccessibilityService.recordedActions.size} steps.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.saveMacro(macroName, voicePhrase, MacroAccessibilityService.recordedActions.toList())
                        MacroAccessibilityService.recordedActions.clear()
                        showSaveDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Macro Assistant") },
                    actions = {
                        val vsIsListening by VoiceService.isListening.collectAsStateWithLifecycle()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Always Listen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = vsIsListening,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        context.startService(Intent(context, VoiceService::class.java))
                                    } else {
                                        context.stopService(Intent(context, VoiceService::class.java))
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onListen) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Listen for Command")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                if (isProcessingAI) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Jarvis is thinking...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                } else if (isListening) {
                    Text("Listening...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (partialSpokenText.isNotEmpty()) {
                        Text(partialSpokenText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (lastSpokenText.isNotEmpty()) {
                    Text("Heard: \"$lastSpokenText\"", color = MaterialTheme.colorScheme.secondary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartTeachMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Teach Me")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Teach Me a Macro")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Saved Macros",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(macros) { macro ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    lifecycleScope.launch {
                                        val actions = viewModel.getActionsForMacro(macro.id)
                                        MacroAccessibilityService.instance?.executeActions(actions)
                                            ?: Toast.makeText(this@MainActivity, "Accessibility Service not running", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = macro.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = "Trigger: \"${macro.voicePhrase}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    lifecycleScope.launch {
                                        val actions = viewModel.getActionsForMacro(macro.id)
                                        MacroAccessibilityService.instance?.executeActions(actions)
                                            ?: Toast.makeText(this@MainActivity, "Accessibility Service not running", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Macro", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteMacro(macro) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Macro", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsDialog(onDismiss: () -> Unit) {
        var apiKey by remember { mutableStateOf(SettingsManager.getApiKey().ifEmpty { com.example.BuildConfig.GEMINI_API_KEY }) }
        var selectedModel by remember { mutableStateOf(SettingsManager.getSelectedModel()) }
        val models = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-1.0-pro"
        )
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("AI Settings") },
            text = {
                Column {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth() // Removed menuAnchor to fix build error
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        selectedModel = model
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    SettingsManager.setApiKey(apiKey)
                    SettingsManager.setSelectedModel(selectedModel)
                    onDismiss()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
