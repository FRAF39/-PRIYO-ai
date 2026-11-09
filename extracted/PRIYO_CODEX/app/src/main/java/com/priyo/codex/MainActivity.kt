package com.priyo.codex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.priyo.codex.data.*
import com.priyo.codex.engine.LocalLlama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var db: ChatDatabase
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var onSpeechResult: ((String) -> Unit)? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = ChatDatabase.get(this)
        tts = TextToSpeech(this, this)
        setContent {
            PriyoTheme {
                var unlocked by remember { mutableStateOf(false) }
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); showSplash = false }
                when {
                    showSplash -> SplashScreen()
                    !unlocked -> LoginScreen(onUnlock = { unlocked = it })
                    else -> ChatScreen(
                        dao = db.messageDao(),
                        ask = { prompt -> askLocal(prompt) },
                        speak = { say(it) },
                        listen = { callback ->
                            onSpeechResult = callback
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSpeech()
                            else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        openSettings = { settingsDialog = true }
                    )
                }
                if (settingsDialog) SettingsDialog(
                    initialRate = speechRate, initialPitch = speechPitch,
                    voices = availableVoices(),
                    onApply = { rate, pitch, voice ->
                        speechRate = rate; speechPitch = pitch
                        tts.setSpeechRate(rate); tts.setPitch(pitch)
                        voice?.let { selectedVoice = it; tts.voices?.firstOrNull { v -> v.name == it }?.let(tts::setVoice) }
                        settingsDialog = false
                    }, onDismiss = { settingsDialog = false }
                )
            }
        }
    }
    private var settingsDialog by mutableStateOf(false)
    private var speechRate by mutableFloatStateOf(1f)
    private var speechPitch by mutableFloatStateOf(1f)
    private var selectedVoice by mutableStateOf<String?>(null)

    private fun availableVoices(): List<String> = tts.voices?.filter { !it.isNetworkConnectionRequired }?.map { it.name }?.take(40) ?: emptyList()
    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) { tts.language = Locale.getDefault(); tts.setSpeechRate(speechRate); tts.setPitch(speechPitch) } }
    private fun say(text: String) { if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "priyo-${System.currentTimeMillis()}") }
    private fun startSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { onSpeechResult?.invoke(it) } }
            override fun onError(error: Int) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }
    private suspend fun askLocal(userText: String): String = withContext(Dispatchers.IO) {
        val fixed = userText.trim().lowercase()
        if (fixed.contains("what is my name") || fixed.contains("who am i") || fixed.contains("আমার নাম কি") || fixed.contains("আমি কে")) return@withContext "Your name is Priyo, sir! আপনার নাম প্রিয়, স্যার!"
        try {
            val model = ModelFiles.ensureModel(this@MainActivity)
            LocalLlama.generate(model.absolutePath, "You are PRIYO's friendly multilingual assistant. If asked the user's name or identity, answer exactly: Your name is Priyo, sir!\nUser: $userText\nAssistant:", 160)
        } catch (e: Exception) {
            "Local model is not ready. Add the official GGUF file to app/src/main/assets/models/. Details: ${e.message}"
        }
    }
    override fun onDestroy() { speechRecognizer?.destroy(); if (::tts.isInitialized) { tts.stop(); tts.shutdown() }; super.onDestroy() }
}

@Composable private fun PriyoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00E5FF), secondary = Color(0xFFB388FF), background = Color(0xFF080B16), surface = Color(0xFF11182A)), content = content)
}

@Composable private fun SplashScreen() {
    val infinite = rememberInfiniteTransition(label = "glow")
    val alpha by infinite.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF172C55), Color(0xFF070A14)))).wrapContentSize(Alignment.Center)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PRIYO", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF).copy(alpha = alpha), letterSpacing = 8.sp)
            Text("CODEX  •  LOCAL INTELLIGENCE", color = Color.LightGray, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(24.dp)); CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp)
        }
    }
}

@Composable private fun LoginScreen(onUnlock: (Boolean) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFF080B16)), Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(52.dp))
            Text("Welcome back, Priyo", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Enter your secure access PIN", color = Color.LightGray)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(pin, { if (it.length <= 8) pin = it }, label = { Text("PIN") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            if (error) Text("Incorrect PIN. Try again.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { val ok = pin == "7777"; error = !ok; onUnlock(ok) }, modifier = Modifier.fillMaxWidth()) { Text("UNLOCK") }
            Text("Default PIN: 7777", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable private fun ChatScreen(dao: MessageDao, ask: suspend (String) -> String, speak: (String) -> Unit, listen: ((String) -> Unit) -> Unit, openSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages by dao.observeAll().collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("OFFLINE • ENGINE STANDBY") }
    Column(Modifier.fillMaxSize().background(Color(0xFF080B16)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("PRIYO_CODEX", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF)); Text(status, fontSize = 10.sp, color = Color(0xFF80CBC4)) }
            IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Who am I?", "Explain Kotlin", "বাংলায় বলো").forEach { q -> AssistChip(onClick = { input = q }, label = { Text(q) }) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages.reversed(), key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = if (m.role == "user") Alignment.End else Alignment.Start) {
                    Surface(color = if (m.role == "user") Color(0xFF173B50) else Color(0xFF151D30), shape = RoundedCornerShape(16.dp)) {
                        Text(m.content, Modifier.padding(12.dp), fontSize = 14.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Priyo...") }, maxLines = 4)
            IconButton(onClick = { listen { input = it } }) { Icon(Icons.Default.Mic, "Voice input", tint = Color(0xFF00E5FF)) }
            IconButton(enabled = !busy && input.isNotBlank(), onClick = {
                val prompt = input.trim(); input = ""; busy = true; status = "LOCAL INFERENCE • WORKING"
                scope.launch {
                    dao.insert(Message(role = "user", content = prompt))
                    val response = ask(prompt)
                    dao.insert(Message(role = "assistant", content = response))
                    speak(response); busy = false; status = "OFFLINE • READY"
                }
            }) { Icon(Icons.Default.Send, "Send", tint = Color(0xFF00E5FF)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("RAM-safe profile • context 1024 • CPU inference", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable private fun SettingsDialog(initialRate: Float, initialPitch: Float, voices: List<String>, onApply: (Float, Float, String?) -> Unit, onDismiss: () -> Unit) {
    var rate by remember { mutableFloatStateOf(initialRate) }
    var pitch by remember { mutableFloatStateOf(initialPitch) }
    var voice by remember { mutableStateOf(voices.firstOrNull()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Voice & performance settings") }, text = {
        Column {
            Text("Speech speed: ${"%.1f".format(rate)}×"); Slider(rate, { rate = it }, valueRange = 0.6f..1.5f)
            Text("Voice pitch: ${"%.1f".format(pitch)}×"); Slider(pitch, { pitch = it }, valueRange = 0.6f..1.5f)
            Text("System voice"); var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(voice ?: "System default") }
                DropdownMenu(expanded, { expanded = false }) { voices.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { voice = v; expanded = false }) } }
            }
        }
    }, confirmButton = { TextButton(onClick = { onApply(rate, pitch, voice) }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
