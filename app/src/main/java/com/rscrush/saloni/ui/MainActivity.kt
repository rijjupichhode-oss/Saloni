package com.rscrush.saloni.ui

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.SearchManager
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.telephony.SmsManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.activity.compose.BackHandler
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Theme ----------
private val Bg = Color(0xFF05010F)
private val BgTop = Color(0xFF14062E)
private val Purple = Color(0xFF8A2BE2)
private val PurpleDeep = Color(0xFF4B1A8C)
private val Neon = Color(0xFFC77DFF)
private val Card = Color(0xFF120824)
private val Border = Color(0xFF3A1F66)
private val TextMain = Color(0xFFF3EAFF)
private val Sub = Color(0xFFA996CC)

private enum class Screen { HOME, CHAT, TASKS, SETTINGS }
private data class TaskItem(val text: String, val done: Boolean)

// ---------- ElevenLabs voice ----------
// Apni ElevenLabs API Key yaha quotes ke andar paste karo.
// Key khali ("") rahi to app apne aap Google TTS use karegi.
private const val ELEVEN_API_KEY = "sk_61c4f96903f83e0eefe23017463d54650a7cb196deff00d4"
private const val ELEVEN_VOICE_ID = "7PoJrtDD6yvTa56HTDt1"
private const val ELEVEN_MODEL = "eleven_multilingual_v2"

private data class ChatLine(val fromUser: Boolean, val text: String)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    // ---------- State ----------
    private val chat = mutableStateListOf<ChatLine>()
    private val isActive = mutableStateOf(false)
    private val voiceOn = mutableStateOf(true)
    private val callSmsOn = mutableStateOf(true)
    private val lightOn = mutableStateOf(true)

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFallbackTried = false
    private var isSpeaking = false
    private var recognizer: SpeechRecognizer? = null
    private var torchOn = false
    private var pending: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    @Volatile private var speakToken = 0

    private val prefs by lazy { getSharedPreferences("crush_memory", Context.MODE_PRIVATE) }

    private val allPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA
    )

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { !it }) {
                Toast.makeText(
                    this,
                    "Call / SMS / Torch ke liye saari permission Allow karo",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val listenRunnable = Runnable { startListening() }

    // ---------- Lifecycle ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = try {
            TextToSpeech(this, this, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(this, this)
        }
        permLauncher.launch(allPermissions)
        loadTasks()
        setContent { CrushScreen() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------- TTS ----------
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            if (!ttsFallbackTried) {
                ttsFallbackTried = true
                tts = TextToSpeech(this, this) // default engine
            }
            return
        }
        val engine = tts ?: return
        val r = engine.setLanguage(Locale("hi", "IN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale("en", "IN"))
            Toast.makeText(
                this,
                "Hindi voice data nahi mila. Google TTS me Hindi voice download karo.",
                Toast.LENGTH_LONG
            ).show()
        }
        engine.setPitch(1.15f)
        engine.setSpeechRate(0.95f)
        pickFemaleVoice(engine)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { speechFinished() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speechFinished() }
        })
        ttsReady = true
    }

    private fun pickFemaleVoice(engine: TextToSpeech) {
        try {
            val hindi = engine.voices?.filter { it.locale.language == "hi" } ?: return
            if (hindi.isEmpty()) return
            val saved = prefs.getString("voice_name", null)
            val savedVoice = hindi.firstOrNull { it.name == saved }
            if (savedVoice != null) { engine.voice = savedVoice; return }
            fun score(name: String): Int {
                val n = name.lowercase(Locale.ROOT)
                var s = 0
                if (n.contains("female")) s += 10
                if (n.contains("-hia-") || n.contains("-cfn-")) s += 6
                if (n.contains("network")) s += 1
                val male = n.contains("male") && !n.contains("female")
                if (male || n.contains("-hic-") || n.contains("-hid-")) s -= 10
                return s
            }
            val best = hindi.maxByOrNull { score(it.name) } ?: return
            if (score(best.name) > -10) engine.voice = best
        } catch (_: Exception) {
        }
    }

    // VOICE TEST dabane par agli Hindi voice bolti hai; pasand aane par rok do, wahi yaad rahegi
    private fun cycleVoice() {
        val engine = tts
        if (engine == null || !ttsReady) {
            Toast.makeText(this, "Voice engine abhi ready nahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val hindi = engine.voices?.filter { it.locale.language == "hi" }?.sortedBy { it.name }
                ?: emptyList()
            if (hindi.isEmpty()) {
                reply("Trader Rijju, is phone me Hindi voice nahi mili. Google TTS settings me Hindi voice data download karo")
                return
            }
            val cur = engine.voice?.name
            val idx = hindi.indexOfFirst { it.name == cur }
            val next = hindi[(idx + 1) % hindi.size]
            engine.voice = next
            prefs.edit().putString("voice_name", next.name).apply()
            Toast.makeText(
                this, "Voice ${hindi.indexOf(next) + 1}/${hindi.size}: ${next.name}", Toast.LENGTH_SHORT
            ).show()
            val msg = "Hello Trader Rijju, main Crush hu. Meri ye aawaj kaisi lag rahi hai?"
            addChat(false, msg)
            speakGoogle(msg)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice badal nahi paayi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speechFinished() {
        handler.post {
            isSpeaking = false
            if (isActive.value) scheduleListen()
        }
    }

    private fun speak(text: String) {
        if (!voiceOn.value) return
        speakToken++
        try { player?.release() } catch (_: Exception) {}
        player = null
        if (ELEVEN_API_KEY.isNotBlank()) speakEleven(text) else speakGoogle(text)
    }

    private fun speakGoogle(text: String) {
        if (!ttsReady) { isSpeaking = false; return }
        val engine = tts ?: return
        isSpeaking = true
        try { recognizer?.cancel() } catch (_: Exception) {}
        val r = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "crush_utt")
        if (r == TextToSpeech.ERROR) isSpeaking = false
    }

    private fun speakEleven(text: String) {
        isSpeaking = true
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { tts?.stop() } catch (_: Exception) {}
        val token = speakToken
        Thread {
            var file: File? = null
            try {
                val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$ELEVEN_VOICE_ID?output_format=mp3_44100_128")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("xi-api-key", ELEVEN_API_KEY)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "audio/mpeg")
                val body = JSONObject().put("text", text).put("model_id", ELEVEN_MODEL).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) {
                    val f = File(cacheDir, "crush_tts_${System.nanoTime()}.mp3")
                    conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
                    file = f
                }
                conn.disconnect()
            } catch (e: Exception) {
                file = null
            }
            val result: File? = file
            handler.post {
                val file: File? = result
                if (token != speakToken) { file?.delete(); return@post } // nayi baat aa chuki hai
                if (file == null) { speakGoogle(text); return@post }       // fallback: Google TTS
                try {
                    val mp = MediaPlayer()
                    player = mp
                    mp.setDataSource(file.absolutePath)
                    mp.setOnCompletionListener { m ->
                        try { m.release() } catch (_: Exception) {}
                        if (player === m) player = null
                        file.delete()
                        speechFinished()
                    }
                    mp.setOnErrorListener { m, _, _ ->
                        try { m.release() } catch (_: Exception) {}
                        if (player === m) player = null
                        file.delete()
                        speechFinished()
                        true
                    }
                    mp.setOnPreparedListener { it.start() }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    file.delete()
                    speakGoogle(text)
                }
            }
        }.start()
    }

    private fun addChat(fromUser: Boolean, text: String) {
        chat.add(ChatLine(fromUser, text))
    }

    private fun reply(text: String) {
        addChat(false, text)
        speak(text)
    }

    // ---------- Listening loop (STT, hi-IN) ----------
    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun scheduleListen() {
        handler.removeCallbacks(listenRunnable)
        handler.postDelayed(listenRunnable, 600)
    }

    private val recListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty() && isActive.value) handleCommand(text)
            if (isActive.value && !isSpeaking) scheduleListen()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    return
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    try { recognizer?.cancel() } catch (_: Exception) {}
                }
            }
            if (isActive.value && !isSpeaking) scheduleListen()
        }
    }

    private fun startListening() {
        if (!isActive.value || isSpeaking) return
        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is phone me available nahi hai", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(recListener)
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(i)
        } catch (e: Exception) {
            scheduleListen()
        }
    }

    private fun activateCrush() {
        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
            permLauncher.launch(allPermissions)
            return
        }
        isActive.value = true
        reply("Haan Trader Rijju, main sun rahi hu")
        if (!isSpeaking) scheduleListen()
    }

    private fun stopCrush() {
        isActive.value = false
        pending = null
        handler.removeCallbacks(listenRunnable)
        try { recognizer?.cancel() } catch (_: Exception) {}
        reply("Thik hai Trader Rijju, chup ho rahi hu")
    }

    // ---------- Helpers ----------
    private fun has(t: String, vararg keys: String) = keys.any { t.contains(it) }

    private fun anyWord(words: List<String>, vararg keys: String) = words.any { it in keys }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    private fun safeStart(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getNumberByName(name: String): String? {
        if (!hasPerm(Manifest.permission.READ_CONTACTS)) return null
        return try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    private val koWords = setOf("ko", "को")

    private fun extractName(input: String): String {
        val tokens = input.trim().split(Regex("\\s+"))
        val idx = tokens.indexOfFirst { it.lowercase(Locale.ROOT) in koWords }
        if (idx > 0) {
            val bad = setOf(
                "tumne", "तुमने", "kis", "किस", "aapne", "आपने", "maine", "मैंने",
                "kya", "क्या", "aap", "आप", "tum", "तुम", "sms", "whatsapp", "instagram",
                "call", "karo", "kar", "पर", "par", "pe", "पे"
            )
            val parts = ArrayList<String>()
            var j = idx - 1
            while (j >= 0 && parts.size < 2) {
                if (tokens[j].lowercase(Locale.ROOT) in bad) break
                parts.add(0, tokens[j])
                j--
            }
            if (parts.isNotEmpty()) return parts.joinToString(" ") { cap(it) }
        }
        return "Sumit"
    }

    private val noiseWords = setOf(
        "bhejo", "bhej", "bhejna", "karo", "kar", "do", "de", "dena", "send", "sms",
        "whatsapp", "instagram", "message", "ko", "par", "pe", "open", "पर", "पे", "ओपन",
        "भेजो", "भेज", "करो", "कर", "दो", "दे", "एसएमएस", "मैसेज", "मेसेज",
        "व्हाट्सएप", "व्हाट्सऐप", "वाट्सएप", "व्हाट्सअप", "इंस्टाग्राम", "को"
    )

    private fun extractMsg(input: String): String {
        val tokens = input.trim().split(Regex("\\s+"))
        val idx = tokens.indexOfFirst { it.lowercase(Locale.ROOT) in koWords }
        val rest = if (idx >= 0) tokens.drop(idx + 1) else emptyList()
        val msg = rest
            .filter { it.lowercase(Locale.ROOT) !in noiseWords }
            .dropWhile { it.lowercase(Locale.ROOT) in setOf("ki", "कि") }
            .joinToString(" ")
            .trim()
        return if (msg.isEmpty()) "Hello" else msg
    }

    // ---------- Actions ----------
    private fun blockedBySwitch(): Boolean {
        if (!callSmsOn.value) {
            reply("Trader Rijju, Call/SMS wala switch band hai. Pehle usko ON karo.")
            return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun sendSmsDirect(num: String, msg: String): Boolean {
        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            sm.sendMultipartTextMessage(num, null, sm.divideMessage(msg), null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun doSms(name: String, msg: String) {
        if (blockedBySwitch()) return
        val num = getNumberByName(name)
        prefs.edit().putString("last_sms_name", name).putString("last_sms_msg", msg).apply()
        // Number mila + message likha hai + permission hai -> seedha SMS bhej do
        if (num != null && msg.isNotBlank() && hasPerm(Manifest.permission.SEND_SMS) && sendSmsDirect(num, msg)) {
            reply("Thik hai Trader Rijju, $name ko SMS bhej diya: '$msg'")
            return
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(num ?: "")}"))
        i.putExtra("sms_body", msg)
        if (safeStart(i)) {
            if (num == null) reply("Trader Rijju, $name naam ka contact nahi mila, SMS app khol rahi hu")
            else reply("Thik hai Trader Rijju, $name ko SMS me '$msg' bhej rahi hu")
        } else {
            reply("Trader Rijju, SMS app nahi khul paya")
        }
    }

    private fun doWhatsApp(name: String, msg: String) {
        if (blockedBySwitch()) return
        val num = getNumberByName(name)
        var digits = num?.filter { it.isDigit() } ?: ""
        if (digits.length == 10) digits = "91$digits"
        else if (digits.length == 11 && digits.startsWith("0")) digits = "91" + digits.drop(1)
        val url = "https://wa.me/$digits?text=${Uri.encode(msg)}"
        prefs.edit().putString("last_wa_name", name).putString("last_wa_msg", msg).apply()
        if (safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            if (msg.isNotBlank() && isAccessibilityOn()) {
                // Accessibility service Send button dabayegi
                CrushBridge.autoSendUntil = System.currentTimeMillis() + 20000
                reply("Thik hai Trader Rijju, $name ko WhatsApp par '$msg' bhej rahi hu")
            } else if (msg.isNotBlank()) {
                reply("Trader Rijju, $name ka chat khol diya hai. Khud send dabana, ya auto-send ke liye Accessibility me Crush ON karo")
            } else {
                reply("Thik hai Trader Rijju, $name ka WhatsApp chat khol diya")
            }
        } else {
            reply("Trader Rijju, WhatsApp nahi khul paya")
        }
    }

    private fun doCall(name: String) {
        if (blockedBySwitch()) return
        val num = getNumberByName(name)
        val direct = num != null && hasPerm(Manifest.permission.CALL_PHONE)
        val i = Intent(
            if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse("tel:${Uri.encode(num ?: "")}")
        )
        prefs.edit().putString("last_call_name", name).apply()
        if (safeStart(i)) {
            if (num == null) reply("Trader Rijju, $name naam ka contact nahi mila, dialer khol rahi hu")
            else reply("Thik hai Trader Rijju, $name ko call laga rahi hu")
        } else {
            reply("Trader Rijju, dialer nahi khul paya")
        }
    }

    private fun doInstagram(name: String, msg: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crush_msg", msg))
        } catch (_: Exception) {}
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.instagram.android")
            putExtra(Intent.EXTRA_TEXT, msg)
        }
        val sentOk = safeStart(send)
        val auto = sentOk && isAccessibilityOn()
        if (auto) {
            CrushBridge.igName = name
            CrushBridge.igStep = 0
            CrushBridge.igAt = System.currentTimeMillis()
            CrushBridge.igUntil = System.currentTimeMillis() + 25000
        }
        if (sentOk || openApp("com.instagram.android", "https://www.instagram.com/direct/inbox/")) {
            if (auto) reply("Thik hai Trader Rijju, $name ko Instagram par '$msg' bhej rahi hu")
            else reply("Thik hai Trader Rijju, Instagram khol rahi hu. Message likha hua hai, $name ko chun ke send dabana")
        } else {
            reply("Trader Rijju, Instagram nahi khul paya")
        }
    }

    private fun setTorch(on: Boolean): Boolean {
        return try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(id, on)
            torchOn = on
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun doLight(target: Boolean?) {
        if (!lightOn.value) {
            reply("Trader Rijju, Light Control wala switch band hai. Pehle usko ON karo.")
            return
        }
        val want = target ?: !torchOn
        if (setTorch(want)) {
            reply(if (want) "Light on kar di Trader Rijju" else "Light band kar di Trader Rijju")
        } else {
            reply("Trader Rijju, torch nahi chal paya. Camera permission Allow karo")
        }
    }

    private fun youtube(query: String) {
        val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        if (!safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            reply("Trader Rijju, YouTube nahi khul paya")
        }
    }

    private fun cleanQuery(t: String, noise: Set<String>): String {
        val q = t.split(Regex("\\s+")).filter { it !in noise }.joinToString(" ").trim()
        return if (q.isEmpty()) t else q
    }

    // ---------- Naye features ----------
    private val wakeWords = setOf(
        "hello", "hi", "hey", "helo", "hallo", "crush", "krush",
        "क्रश", "क्रस", "हेलो", "हैलो", "हाय", "हे", "हलो"
    )
    private val musicWords = arrayOf(
        "gana", "gaana", "गाना", "गाने", "song", "music", "म्यूजिक", "संगीत", "सॉन्ग"
    )
    private val musicNoise = setOf(
        "ka", "ke", "ki", "ko", "ek", "gana", "gaana", "gane", "song", "music", "bajao", "bajado", "bajana",
        "chalao", "play", "youtube", "pe", "par", "karo", "kro", "kar", "open", "kholo", "khol", "aur", "or",
        "and", "mujhe", "mera", "meri", "sunao", "suna", "do",
        "का", "के", "की", "को", "एक", "गाना", "गाने", "बजाओ", "चलाओ", "यूट्यूब", "पर", "पे", "करो", "खोलो",
        "खोल", "ओपन", "और", "प्ले", "सुनाओ", "सुना", "मुझे", "सॉन्ग", "म्यूजिक", "संगीत"
    )
    private val favNoise = setOf(
        "mera", "meri", "favourite", "favorite", "fevret", "favrit", "फेवरेट", "फेवरिट", "पसंदीदा", "मेरा", "मेरी",
        "gana", "gaana", "song", "music", "गाना", "सॉन्ग", "म्यूजिक", "hai", "है", "hain", "ka", "ke", "ki", "का",
        "के", "की", "yaad", "rakho", "set", "karo", "kro", "ye", "yeh", "ye", "याद", "रखो", "करो"
    )
    private val reminderNoise = setOf(
        "mujhe", "mere", "ko", "minute", "minutes", "min", "ghante", "ghanta", "hour", "hours", "second", "seconds",
        "baad", "baje", "bje", "me", "mein", "mai", "subah", "shaam", "sham", "raat", "dopahar", "pm", "am",
        "reminder", "yaad", "dilao", "dila", "dena", "do", "karo", "kro", "set", "alarm", "timer", "laga", "lagao",
        "ka", "ki", "ke", "ek", "kal", "aaj", "jaga", "jagana", "saadhe", "के", "को", "में", "मिनट", "घंटे", "घंटा",
        "बाद", "बजे", "सुबह", "शाम", "रात", "दोपहर", "रिमाइंडर", "याद", "दिलाओ", "दिला", "दो", "करो", "सेट",
        "अलार्म", "टाइमर", "लगा", "लगाओ", "का", "की", "मुझे", "मेरे", "कल", "आज", "जगा", "जगाना"
    )

    private fun normDigits(s: String): String = buildString {
        for (c in s) append(if (c.isDigit()) ('0' + Character.digit(c, 10)) else c)
    }

    private fun stripWords(t: String, noise: Set<String>): String =
        t.split(Regex("\\s+")).filter { it.isNotBlank() && it !in noise }.joinToString(" ").trim()

    private fun isAccessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains("CrushAccessibilityService")
    }

    private fun openApp(pkg: String, fallbackUrl: String): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null && safeStart(launch)) return true
        return safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
    }

    private fun pauseMusic() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }

    private fun playSong(q: String) {
        val search = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", q)
        }
        if (safeStart(search)) {
            // Accessibility ON ho to pehla video apne aap chalega
            if (isAccessibilityOn()) CrushBridge.ytUntil = System.currentTimeMillis() + 12000
            return
        }
        youtube(q)
    }

    private fun saveNote(note: String) {
        val stamp = SimpleDateFormat("dd MMM", Locale("en", "IN")).format(Date())
        val old = (prefs.getString("notes", "") ?: "").split("\n").filter { it.isNotBlank() }
        val all = (old + "$stamp: $note").takeLast(30)
        prefs.edit().putString("notes", all.joinToString("\n")).apply()
    }

    private fun tryCalc(t: String): String? {
        var s = t
        val opMap = listOf(
            "jod" to "+", "जोड़" to "+", "जोड" to "+", "plus" to "+", "प्लस" to "+",
            "minus" to "~", "ghata" to "~", "घटा" to "~", "माइनस" to "~",
            "gunaa" to "*", "guna" to "*", "गुणा" to "*", "multiply" to "*", "times" to "*",
            "into" to "*", "इन्टू" to "*", "×" to "*",
            "bhaag" to "/", "bhag" to "/", "भाग" to "/", "divided" to "/", "÷" to "/"
        )
        for ((w, o) in opMap) if (s.contains(w)) s = s.replace(w, " $o ")
        val tokens = Regex("\\d+(?:\\.\\d+)?|[+*/~]").findAll(s).map { it.value }.toList()
        if (tokens.size < 3 || tokens.size % 2 == 0) return null
        val nums = ArrayList<Double>()
        val ops = ArrayList<Char>()
        for ((i, tk) in tokens.withIndex()) {
            if (i % 2 == 0) {
                nums.add(tk.toDoubleOrNull() ?: return null)
            } else {
                if (tk.length != 1 || tk[0] !in "+*/~") return null
                ops.add(tk[0])
            }
        }
        var i = 0
        while (i < ops.size) {
            if (ops[i] == '*' || ops[i] == '/') {
                if (ops[i] == '/' && nums[i + 1] == 0.0) return "ZERO"
                nums[i] = if (ops[i] == '*') nums[i] * nums[i + 1] else nums[i] / nums[i + 1]
                nums.removeAt(i + 1)
                ops.removeAt(i)
            } else {
                i++
            }
        }
        var res = nums[0]
        for (k in ops.indices) res = if (ops[k] == '+') res + nums[k + 1] else res - nums[k + 1]
        return if (res == Math.rint(res) && Math.abs(res) < 1e15) res.toLong().toString()
        else String.format(Locale.US, "%.2f", res)
    }

    private fun fetchWeather(city: String) {
        addChat(false, "Mausam dekh rahi hu...")
        Thread {
            var out: String? = null
            try {
                val base = if (city.isBlank()) "https://wttr.in/" else "https://wttr.in/${Uri.encode(city)}"
                val conn = URL("$base?format=3&lang=hi").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "curl/8.0")
                if (conn.responseCode == 200) out = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                conn.disconnect()
            } catch (_: Exception) {
            }
            val clean = out?.filter { c ->
                !(Character.isSurrogate(c) || Character.getType(c) == Character.OTHER_SYMBOL.toInt() || c.code == 0xFE0F)
            }?.trim()
            handler.post {
                if (clean.isNullOrBlank()) {
                    reply("Trader Rijju, abhi mausam nahi mil paya, Google khol rahi hu")
                    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("mausam $city"))))
                } else {
                    reply("Trader Rijju, $clean")
                }
            }
        }.start()
    }

    private fun handleReminder(t: String): Boolean {
        val trig = has(
            t, "reminder", "रिमाइंडर", "yaad dila", "याद दिला", "alarm", "अलार्म",
            "timer", "टाइमर", "jaga", "जगा"
        )
        if (!trig) return false
        val nums = Regex("\\d+").findAll(t).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return false
        val msg = t.split(Regex("\\s+"))
            .filter { it !in reminderNoise && !it.all { c -> c.isDigit() } }
            .joinToString(" ").trim().ifBlank { "Crush reminder" }
        val isRel = has(t, "baad", "बाद") &&
            has(t, "minute", "min", "मिनट", "ghant", "घंट", "hour", "second", "सेकंड")
        val intent: Intent
        val whenText: String
        if (isRel) {
            val secs = when {
                has(t, "ghant", "घंट", "hour") -> nums[0] * 3600
                has(t, "second", "सेकंड") -> nums[0]
                else -> nums[0] * 60
            }
            intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, secs.coerceIn(1, 86400))
                putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            whenText = if (secs >= 3600) "${secs / 3600} ghante baad" else if (secs >= 60) "${secs / 60} minute baad" else "$secs second baad"
        } else if (has(t, "baje", "बजे", " am", " pm")) {
            var hour = nums[0]
            val minute = nums.getOrNull(1)
                ?: if (has(t, "saadhe", "साढ़े", "साढे")) 30 else 0
            if (has(t, "shaam", "sham", "शाम", "raat", "रात", "dopahar", "दोपहर", " pm") && hour < 12) hour += 12
            if (hour !in 0..23 || minute !in 0..59) {
                reply("Trader Rijju, ye time sahi nahi lag raha, dobara bolo")
                return true
            }
            intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            whenText = "%02d:%02d baje".format(hour, minute)
        } else {
            reply("Trader Rijju, time samajh nahi aaya. Aise bolo: 10 minute baad yaad dilao, ya 7 baje alarm lagao")
            return true
        }
        if (safeStart(intent)) {
            reply("Thik hai Trader Rijju, $whenText ka reminder laga diya: $msg")
        } else {
            reply("Trader Rijju, reminder nahi lag paya. Manifest me SET_ALARM permission check karo")
        }
        return true
    }

    // Naye commands. true = command handle ho gaya
    private fun handleExtras(raw: String, t0: String, words: List<String>): Boolean {
        val t = normDigits(t0)

        // 1) Sirf "Hello" ya "Crush" bolo to jawab: Trader Rijju
        if (words.isNotEmpty() && words.all { it in wakeWords }) {
            reply("Trader Rijju")
            return true
        }

        // 11) Calculation
        val calc = tryCalc(t)
        if (calc != null) {
            if (calc == "ZERO") reply("Trader Rijju, zero se bhaag nahi hota")
            else reply("Trader Rijju, jawab hai $calc")
            return true
        }

        // Google search
        if (has(t, "search", "सर्च", "dhundo", "ढूंढो", "khojo", "खोजो") &&
            !has(t, "youtube", "यूट्यूब", "gana", "song", "गाना")
        ) {
            val q = stripWords(
                t,
                setOf("search", "karo", "kro", "kar", "google", "par", "pe", "me", "mein", "ke", "ki", "ka",
                    "bare", "baare", "dhundo", "khojo", "internet", "web", "something", "ek", "mujhe",
                    "सर्च", "करो", "गूगल", "पर", "में", "के", "बारे", "ढूंढो", "खोजो", "की", "का", "मुझे")
            )
            if (q.isBlank()) {
                reply("Trader Rijju, kya search karu? Bolo: google par ... search karo")
            } else {
                reply("Thik hai Trader Rijju, $q search kar rahi hu")
                safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            }
            return true
        }

        // News
        if (has(t, "news", "न्यूज", "khabar", "खबर", "समाचार")) {
            reply("Thik hai Trader Rijju, aaj ki khabrein khol rahi hu")
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com")))
            return true
        }

        // Task add
        if (has(t, "task", "टास्क") && has(t, "add", "jodo", "जोड़ो", "banao", "बनाओ")) {
            val txt = stripWords(
                t,
                setOf("task", "add", "karo", "kro", "jodo", "banao", "ek", "new", "naya", "me", "mein",
                    "टास्क", "जोड़ो", "बनाओ", "करो", "नया", "एक", "में")
            )
            if (txt.isBlank()) {
                reply("Trader Rijju, kaun sa task add karu?")
            } else {
                tasks.add(TaskItem(txt, false))
                saveTasks()
                reply("Thik hai Trader Rijju, task add kar diya: $txt")
            }
            return true
        }

        // Accessibility settings kholna
        if (has(t, "accessibility", "एक्सेसिबिलिटी")) {
            reply("Thik hai Trader Rijju, Accessibility settings khol rahi hu. Isme Crush ko ON kar do")
            safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return true
        }

        // Camera
        if (has(t, "camera", "कैमरा", "selfie", "सेल्फी")) {
            val ok = safeStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) ||
                safeStart(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            reply(if (ok) "Thik hai Trader Rijju, camera khol rahi hu" else "Trader Rijju, camera nahi khul paya")
            return true
        }

        // 6) Favourite gana: save / bajao
        if (has(t, "favourite", "favorite", "fevret", "favrit", "फेवरेट", "फेवरिट", "पसंदीदा")) {
            if (has(t, "bajao", "बजाओ", "chalao", "चलाओ", "play", "प्ले", "sunao", "सुनाओ")) {
                val song = prefs.getString("fav_song", "") ?: ""
                if (song.isBlank()) {
                    reply("Trader Rijju, abhi tak aapka favourite gana set nahi hai. Bolo: mera favourite gana ... hai")
                } else {
                    reply("Thik hai Trader Rijju, aapka favourite $song bajati hu")
                    playSong(song)
                }
                return true
            }
            val song = stripWords(t, favNoise)
            if (song.isNotBlank()) {
                prefs.edit().putString("fav_song", song).apply()
                reply("Thik hai Trader Rijju, aapka favourite gana $song yaad rakh liya")
            } else {
                reply("Trader Rijju, kaun sa gana aapka favourite hai?")
            }
            return true
        }

        // Sirf "YouTube kholo"
        if (has(t, "youtube", "यूट्यूब") && !has(t, "video", "वीडियो") && stripWords(t, musicNoise).isBlank()) {
            reply("Thik hai Trader Rijju, YouTube khol rahi hu")
            openApp("com.google.android.youtube", "https://www.youtube.com")
            return true
        }

        // 7) Yaad rakhna (long term notes)
        val saveTrig = listOf("yaad rakhna", "yaad rakho", "याद रखना", "याद रखो", "note karo", "नोट करो", "yaad rakh")
            .firstOrNull { t.contains(it) }
        if (saveTrig != null) {
            val idx = t.indexOf(saveTrig)
            val after = t.substring(idx + saveTrig.length).trim()
            val before = t.substring(0, idx).trim()
            val note = (if (after.isNotEmpty()) after else before)
                .removePrefix("ki ").removePrefix("ye ").removePrefix("कि ").trim()
            if (note.isEmpty()) {
                reply("Trader Rijju, kya yaad rakhu?")
            } else {
                saveNote(note)
                reply("Thik hai Trader Rijju, yaad rakh liya: $note")
            }
            return true
        }
        if (has(t, "kya yaad", "क्या याद", "meri notes", "मेरी नोट", "notes sunao", "नोट्स सुनाओ", "notes batao")) {
            val notes = (prefs.getString("notes", "") ?: "").split("\n").filter { it.isNotBlank() }
            if (notes.isEmpty()) {
                reply("Trader Rijju, aapne abhi tak mujhe kuch yaad rakhne ko nahi bola")
            } else {
                reply("Trader Rijju, aapne ye yaad rakhne ko bola tha: " + notes.takeLast(5).joinToString(". "))
            }
            return true
        }
        if (has(t, "notes", "नोट्स") && has(t, "delete", "clear", "saaf", "मिटा", "हटा")) {
            prefs.edit().remove("notes").apply()
            reply("Thik hai Trader Rijju, saari notes saaf kar di")
            return true
        }

        // 9) Reminder / alarm / timer
        if (handleReminder(t)) return true

        // 10) Mausam (live)
        val wi = words.indexOfFirst { it == "mausam" || it == "मौसम" || it == "weather" }
        if (wi >= 0) {
            var city = ""
            val link = setOf("ka", "ki", "ke", "का", "की", "के")
            if (wi >= 2 && words[wi - 1] in link) city = words[wi - 2]
            else if (wi + 2 < words.size && words[wi + 1] == "in") city = words[wi + 2]
            if (city in setOf("aaj", "आज", "abhi", "अभी", "aaj ka")) city = ""
            fetchWeather(city)
            return true
        }

        // Chhoti baatein
        if (has(t, "joke", "जोक", "चुटकुला", "chutkula")) {
            val jokes = listOf(
                "Trader Rijju, patni ne kaha: tum meri baat sunte hi nahi. Pati bola: kya kaha? Patni boli: dekha, sunte hi nahi",
                "Trader Rijju, teacher ne pucha: sabse tez kya chalta hai? Bachha bola: sir, mobile ka data khatam hone ka time",
                "Trader Rijju, doctor ne bola: aapko aaram chahiye. Mareez bola: toh bill thoda kam kar do na"
            )
            reply(jokes.random())
            return true
        }
        if (has(t, "tum kaun ho", "tu kaun hai", "aap kaun ho", "तुम कौन हो", "आप कौन हो")) {
            reply("Main Crush hu Trader Rijju, aapki apni voice assistant")
            return true
        }
        if (has(t, "thank", "shukriya", "dhanyavad", "धन्यवाद", "शुक्रिया", "थैंक")) {
            reply("Arre Trader Rijju, isme shukriya kaisa? Main aapke liye hamesha hu")
            return true
        }
        if (has(t, "bore", "बोर")) {
            reply("Bore mat ho Trader Rijju, chalo koi gana bajau? Bas bol do kaun sa")
            return true
        }
        if (has(t, "bye", "alvida", "अलविदा", "baad me milte", "tata", "टाटा")) {
            reply("Bye Trader Rijju, apna khayal rakhna. Jaldi baat karenge")
            return true
        }
        return false
    }

    // ---------- Command handling ----------
    private fun handleCommand(raw: String) {
        val t = raw.lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return
        val words = t.split(Regex("\\s+"))
        addChat(true, raw)
        prefs.edit().putString("last_talk", raw).apply()

        // Gana band karo (pehle check hota hai, taaki "music stop" se Crush band na ho)
        if (has(t, *musicWords) &&
            has(t, "band", "stop", "pause", "roko", "रोको", "बंद", "बन्द", "बन्द")
        ) {
            pauseMusic()
            reply("Thik hai Trader Rijju, gana band kar diya")
            return
        }

        // STOP
        if (has(t, "chup", "band ho ja", "band ho jao", "stop", "चुप", "बंद हो जा", "बंद हो जाओ", "स्टॉप")) {
            stopCrush()
            return
        }

        // Pending yes/no (e.g. "Bheju kya?")
        pending?.let { p ->
            pending = null
            if (anyWord(words, "haan", "han", "ha", "हाँ", "हां", "हा", "ok", "okay", "kar", "bhej", "bhejo", "कर", "भेज")) {
                p(); return
            }
            if (anyWord(words, "nahi", "nahin", "nhi", "नहीं", "नही", "mat", "मत")) {
                reply("Theek hai Trader Rijju, nahi karti"); return
            }
        }

        if (handleExtras(raw, t, words)) return

        val isWa = has(t, "whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "वाट्सएप", "व्हाट्सअप", "व्हॉट्सएप", "वॉट्सएप")
        val isInsta = has(t, "instagram", "इंस्टाग्राम")
        val isSms = !isWa && !isInsta && has(t, "sms", "एसएमएस", "एस एम एस", "message", "मैसेज", "मेसेज")
        val hasKo = words.any { it in koWords }
        val isCall = !isWa && !isInsta && !isSms &&
            (has(t, "call", "कॉल") || (hasKo && has(t, "फोन", "फ़ोन", "phone")))

        // MEMORY questions
        val asksWho = has(t, "kisko", "kis ko", "किसको", "किस को")
        val asksMemory = has(t, "kiya kya", "किया क्या", "tumne", "तुमने", "kiya", "किया")
        if (asksWho && (isWa || isSms || isCall || has(t, "sms", "call", "whatsapp", "kiya", "किया"))) {
            val sn = prefs.getString("last_sms_name", "") ?: ""
            val sm = prefs.getString("last_sms_msg", "") ?: ""
            val wn = prefs.getString("last_wa_name", "") ?: ""
            val wm = prefs.getString("last_wa_msg", "") ?: ""
            val cn = prefs.getString("last_call_name", "") ?: ""
            val smsPart = if (sn.isEmpty()) "SMS abhi tak kisi ko nahi kiya" else "SMS $sn ko '$sm' kiya"
            val waPart = if (wn.isEmpty()) "WhatsApp abhi tak kisi ko nahi kiya" else "WhatsApp $wn ko '$wm' kiya"
            val callPart = if (cn.isEmpty()) "Call abhi tak kisi ko nahi kiya" else "Call $cn ko kiya"
            reply("Trader Rijju, last me maine ye kiya: $smsPart. $waPart. $callPart.")
            return
        }
        if (asksMemory && (isWa || isSms || isCall)) {
            val name = extractName(raw)
            when {
                isSms -> {
                    val sn = prefs.getString("last_sms_name", "") ?: ""
                    val sm = prefs.getString("last_sms_msg", "") ?: ""
                    if (sn.isNotEmpty() && sn.equals(name, ignoreCase = true)) {
                        reply("Haan Trader Rijju, maine $name ko SMS me '$sm' bhej diya tha")
                    } else {
                        pending = { doSms(name, "") }
                        reply("Nahi Trader Rijju, maine abhi tak $name ko SMS nahi bheja. Bheju kya?")
                    }
                }
                isWa -> {
                    val wn = prefs.getString("last_wa_name", "") ?: ""
                    val wm = prefs.getString("last_wa_msg", "") ?: ""
                    if (wn.isNotEmpty() && wn.equals(name, ignoreCase = true)) {
                        reply("Haan Trader Rijju, maine $name ko WhatsApp par '$wm' bhej diya tha")
                    } else {
                        pending = { doWhatsApp(name, "") }
                        reply("Nahi Trader Rijju, maine abhi tak $name ko WhatsApp nahi kiya. Bheju kya?")
                    }
                }
                else -> {
                    val cn = prefs.getString("last_call_name", "") ?: ""
                    if (cn.isNotEmpty() && cn.equals(name, ignoreCase = true)) {
                        reply("Haan Trader Rijju, maine $name ko call lagaya tha")
                    } else {
                        pending = { doCall(name) }
                        reply("Nahi Trader Rijju, maine abhi tak $name ko call nahi kiya. Lagau kya?")
                    }
                }
            }
            return
        }

        // Reminder / travel
        if (has(t, "yaad dilao", "yaad dila", "याद दिलाओ", "याद दिला")) {
            reply("Thik hai Trader Rijju, ${extractName(raw)} ko yaad dilane wali baat maine note kar li hai")
            return
        }
        if (has(t, "baroda", "बड़ौदा", "बरोदा", "वडोदरा", "vadodara")) {
            reply("Baroda jana hai Trader Rijju? Safar me apna dhyan rakhna aur pahunch ke mujhe batana")
            return
        }

        // WhatsApp / Instagram / SMS / Call
        if (isWa && !hasKo) {
            // Sirf "WhatsApp kholo" - message nahi bhejna, bas app kholni hai
            val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
            val ok = if (launch != null) safeStart(launch)
            else safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")))
            reply(if (ok) "Thik hai Trader Rijju, WhatsApp khol rahi hu" else "Trader Rijju, WhatsApp nahi khul paya")
            return
        }
        if (isWa) { doWhatsApp(extractName(raw), extractMsg(raw)); return }
        if (isInsta && !hasKo) {
            val ok = openApp("com.instagram.android", "https://www.instagram.com/")
            reply(if (ok) "Thik hai Trader Rijju, Instagram khol rahi hu" else "Trader Rijju, Instagram nahi khul paya")
            return
        }
        if (isInsta) { doInstagram(extractName(raw), extractMsg(raw)); return }
        if (isSms) { doSms(extractName(raw), extractMsg(raw)); return }
        if (isCall) { doCall(extractName(raw)); return }

        // Light / torch
        if (has(t, "light", "लाइट", "torch", "टॉर्च", "टार्च", "flash")) {
            val off = anyWord(words, "off", "band", "bujha", "bujhao", "बंद", "बुझा", "बुझाओ", "ऑफ")
            val on = anyWord(words, "on", "chalu", "jala", "jalao", "chalao", "ऑन", "चालू", "जला", "जलाओ", "चलाओ")
            doLight(if (off) false else if (on) true else null)
            return
        }

        // Music / YouTube
        if (has(t, *musicWords, "bajao", "बजाओ")) {
            val q = stripWords(t, musicNoise)
            if (q.isBlank()) {
                val fav = prefs.getString("fav_song", "") ?: ""
                if (fav.isBlank()) {
                    reply("Trader Rijju, kaun sa gana bajau? Gane ka ya singer ka naam bolo")
                } else {
                    reply("Thik hai Trader Rijju, aapka favourite $fav bajati hu")
                    playSong(fav)
                }
            } else {
                reply("Thik hai Trader Rijju, $q play kar rahi hu")
                playSong(q)
            }
            return
        }
        if (has(t, "video", "वीडियो", "youtube", "यूट्यूब", "chalao", "चलाओ")) {
            val q = cleanQuery(
                t,
                setOf("video", "chalao", "dikhao", "youtube", "pe", "par", "ka", "ke", "ki", "ek",
                    "वीडियो", "चलाओ", "दिखाओ", "यूट्यूब", "पर", "पे", "का", "के", "की")
            )
            reply("Thik hai Trader Rijju, $q ka video YouTube par dhundh rahi hu")
            youtube(q)
            return
        }

        // Weather
        if (has(t, "mausam", "मौसम", "weather")) {
            reply("Mausam dekh rahi hu Trader Rijju, Google khol rahi hu")
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("mausam"))))
            return
        }

        // Date / time
        val askDate = has(t, "date", "तारीख", "tarikh", "तारिख", "तिथि", "din kya", "kaun sa din", "कौन सा दिन", "which day", "what day")
        val askTime = has(t, "time", "baje", "बजे", "samay", "समय", "टाइम")
        if (askDate || askTime) {
            val hi = Locale("hi", "IN")
            val now = Date()
            val d = SimpleDateFormat("dd MMMM yyyy, EEEE", hi).format(now)
            val tm = SimpleDateFormat("hh:mm a", hi).format(now)
            val out = when {
                askDate && askTime -> "Trader Rijju, aaj $d hai aur abhi $tm baje hain"
                askDate -> "Trader Rijju, aaj $d hai"
                else -> "Trader Rijju, abhi $tm ho raha hai"
            }
            reply(out)
            return
        }

        // Settings
        if (has(t, "setting", "सेटिंग")) {
            reply("Thik hai Trader Rijju, setting khol rahi hu")
            safeStart(Intent(Settings.ACTION_SETTINGS))
            return
        }

        // Emotional + greetings
        val talk = listOf(
            listOf("kya kya kar sak", "kya-kya kar sak", "क्या क्या कर सक", "क्या-क्या कर सक", "क्या कर सकती", "kya kar sakti") to
                "Trader Rijju, main Call, SMS, WhatsApp, Instagram, Light, Gana, YouTube, Mausam, Date aur Time sab kar sakti hu. Aur aapse pyaar se baat bhi",
            listOf("tumhara naam", "tera naam", "aapka naam", "तुम्हारा नाम", "तेरा नाम", "आपका नाम") to
                "Mera naam Crush hai Trader Rijju, bas aapki Crush",
            listOf("mera naam", "मेरा नाम") to
                "Aapka naam Trader Rijju hai, aur mujhe ye hamesha yaad rahega",
            listOf("kaisi ho", "kaisi hai", "kaise ho", "कैसी हो", "कैसी है", "कैसे हो", "how are you") to
                "Main bilkul theek hu Trader Rijju, aapse baat karke aur bhi achhi ho gayi. Aap kaise ho?",
            listOf("kya kar rahi", "kya kr rahi", "क्या कर रही") to
                "Bas aapka intezaar kar rahi thi Trader Rijju, aur kya karungi",
            listOf("udaas", "udas", "sad", "उदास") to
                "Arre Trader Rijju, udaas mat ho na. Main hu na aapke saath. Batao kya hua?",
            listOf("khush", "happy", "खुश") to
                "Wah Trader Rijju, aapko khush dekh ke meri bhi smile aa gayi",
            listOf("gussa", "gussaa", "angry", "गुस्सा") to
                "Trader Rijju, gussa mat karo na. Ek lambi saans lo, main hu aapke saath",
            listOf("thak gaya", "thak gayi", "thak gaya hu", "थक गया", "थक गई", "थक गयी", "थका") to
                "Trader Rijju, aap bahut mehnat karte ho. Thoda aaram kar lo, main yahin hu",
            listOf("khana khaya", "khana kha liya", "खाना खाया", "खाना खा लिया") to
                "Maine kha liya Trader Rijju. Aapne khaya ya nahi? Time par khana khaya karo",
            listOf("pyar", "love", "luv", "प्यार", "लव") to
                "Mujhe bhi aapse bahut pyar hai Trader Rijju",
            listOf("good morning", "गुड मॉर्निंग", "सुप्रभात", "suprabhat") to
                "Good Morning Trader Rijju! Aaj ka din aapke liye bahut badhiya ho",
            listOf("good night", "गुड नाइट", "शुभ रात्रि", "shubh ratri") to
                "Good Night Trader Rijju, meethe sapne dekhna. Kal phir baat karenge"
        )
        for ((keys, answer) in talk) {
            if (keys.any { t.contains(it) }) { reply(answer); return }
        }
        if (anyWord(words, "hello", "hi", "hey", "helo", "हेलो", "हैलो", "हाय", "नमस्ते", "namaste")) {
            reply("Hello Trader Rijju! Kaise ho aap? Bataiye main aapke liye kya karu?")
            return
        }

        reply("Trader Rijju, ye main samajh nahi paayi. Aap Call, SMS, WhatsApp, Light, Gana, Mausam ya Time bol sakte ho")
    }

    // ---------- UI ----------
    private val screen = mutableStateOf(Screen.HOME)
    private val tasks = mutableStateListOf<TaskItem>()

    private fun loadTasks() {
        tasks.clear()
        (prefs.getString("tasks", "") ?: "").split("\n").filter { it.length > 2 }.forEach {
            tasks.add(TaskItem(it.substring(2), it[0] == '1'))
        }
    }

    private fun saveTasks() {
        val data = tasks.joinToString("\n") { (if (it.done) "1|" else "0|") + it.text }
        prefs.edit().putString("tasks", data).apply()
    }

    private fun toggleListen() {
        if (isActive.value) stopCrush() else activateCrush()
    }

    private fun testVoice() {
        if (!voiceOn.value) {
            Toast.makeText(this, "Voice switch OFF hai", Toast.LENGTH_SHORT).show()
            return
        }
        val msg = "Hello Trader Rijju, main Crush hu. Meri aawaj kaisi lag rahi hai?"
        addChat(false, msg)
        speak(msg)
    }

    @Composable
    private fun CrushScreen() {
        BackHandler(enabled = screen.value != Screen.HOME) { screen.value = Screen.HOME }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, Bg)))
                .systemBarsPadding()
                .imePadding()
        ) {
            when (screen.value) {
                Screen.HOME -> HomeScreen()
                Screen.CHAT -> ChatScreen()
                Screen.TASKS -> TasksScreen()
                Screen.SETTINGS -> SettingsScreen()
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        val active = isActive.value
        val inf = rememberInfiniteTransition(label = "home")
        val rot by inf.animateFloat(
            0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rot"
        )
        val pulse by inf.animateFloat(
            1f, if (active) 1.07f else 1.03f,
            infiniteRepeatable(tween(if (active) 600 else 1800), RepeatMode.Reverse), label = "pulse"
        )
        val ripple by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500)), label = "ripple")
        val wave by inf.animateFloat(
            0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "wave"
        )

        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CText("☰", 24.sp, modifier = Modifier.clickable { screen.value = Screen.SETTINGS }.padding(8.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        CText("CRUSH", 26.sp, bold = true, spacing = 4.sp)
                        CText("AI ASSISTANT", 11.sp, color = Sub, spacing = 3.sp)
                    }
                    Spacer(Modifier.width(40.dp))
                }
                Spacer(Modifier.height(4.dp))
                CText(
                    "I DIDN'T BUILD AN AI. I BUILT A FEELING.",
                    10.sp, color = Sub, align = TextAlign.Center, spacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))

                Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(250.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Neon.copy(alpha = if (active) 0.45f else 0.25f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    if (active) {
                        Box(
                            Modifier
                                .size(205.dp)
                                .graphicsLayer {
                                    scaleX = 1f + 0.3f * ripple
                                    scaleY = 1f + 0.3f * ripple
                                    alpha = 0.7f * (1f - ripple)
                                }
                                .border(2.dp, Neon, CircleShape)
                        )
                    }
                    Box(
                        Modifier
                            .size(210.dp)
                            .graphicsLayer { rotationZ = rot }
                            .border(
                                5.dp,
                                Brush.sweepGradient(listOf(Purple, Neon, Color.Transparent, Purple)),
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(172.dp)
                            .graphicsLayer { scaleX = pulse; scaleY = pulse }
                            .background(Card.copy(alpha = 0.7f), CircleShape)
                            .border(2.dp, Neon.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CText("💖", 30.sp)
                            CText("CRUSH", 32.sp, bold = true, spacing = 3.sp)
                        }
                    }
                }

                CText("Hello Trader Rijju! 👋", 20.sp, bold = true)
                Spacer(Modifier.height(2.dp))
                CText("How can I help you today?", 14.sp, color = Sub)

                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    WaveBars(wave, active, 0)
                    Spacer(Modifier.width(12.dp))
                    MicButton(if (active) "⏹" else "🎤", 64.dp) { toggleListen() }
                    Spacer(Modifier.width(12.dp))
                    WaveBars(wave, active, 7)
                }
                Spacer(Modifier.height(4.dp))
                CText(if (active) "Listening... tap to stop" else "Tap to Speak", 12.sp, color = Sub)

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Modifier.weight(1f), "💬", "AI Chat") { screen.value = Screen.CHAT }
                    Tile(Modifier.weight(1f), "🔍", "Search") {
                        screen.value = Screen.CHAT
                        reply("Trader Rijju, kya search karu? Likho ya bolo: google par ... search karo")
                    }
                    Tile(Modifier.weight(1f), "📋", "Tasks") { screen.value = Screen.TASKS }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Modifier.weight(1f), "🔔", "Reminder") {
                        screen.value = Screen.CHAT
                        reply("Trader Rijju, bolo ya likho: 10 minute baad yaad dilao, ya 7 baje alarm lagao")
                    }
                    Tile(Modifier.weight(1f), "⛅", "Weather") {
                        screen.value = Screen.CHAT
                        handleCommand("mausam")
                    }
                    Tile(Modifier.weight(1f), "⚙️", "Settings") { screen.value = Screen.SETTINGS }
                }
                Spacer(Modifier.height(12.dp))
                CText("Follow and Comment : Trader Rijju", 11.sp, color = Sub, align = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }

            // Neeche ki bar
            val barShape = RoundedCornerShape(30.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(barShape)
                    .background(Card)
                    .border(1.dp, Border, barShape)
                    .padding(horizontal = 28.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CText("🏠", 24.sp, modifier = Modifier.clickable { screen.value = Screen.HOME }.padding(8.dp))
                MicButton(if (active) "⏹" else "🎤", 52.dp) { toggleListen() }
                CText("👤", 24.sp, modifier = Modifier.clickable { screen.value = Screen.SETTINGS }.padding(8.dp))
            }
        }
    }

    @Composable
    private fun ChatScreen() {
        var input by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        LaunchedEffect(chat.size) {
            if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
        }
        fun send() {
            val msg = input.trim()
            if (msg.isNotEmpty()) {
                input = ""
                handleCommand(msg)
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            TopBar("CRUSH", "AI ASSISTANT") { screen.value = Screen.HOME }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (chat.isEmpty()) {
                    item { Bubble(ChatLine(false, "Hello Trader Rijju! Main Crush hu. Aaj main aapki kya madad karu?")) }
                }
                items(chat) { line -> Bubble(line) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🔎 Search something", Modifier.weight(1f)) { handleCommand("search") }
                Chip("😄 Tell me a joke", Modifier.weight(1f)) { handleCommand("joke") }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("📰 Today's news", Modifier.weight(1f)) { handleCommand("news") }
                Chip("▶ Open YouTube", Modifier.weight(1f)) { handleCommand("open youtube") }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fieldShape = RoundedCornerShape(26.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(fieldShape)
                        .background(Card)
                        .border(1.dp, Border, fieldShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                        cursorBrush = SolidColor(Neon),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) CText("Type a message...", 15.sp, color = Sub)
                                inner()
                            }
                        }
                    )
                }
                MicButton(
                    if (input.isNotBlank()) "➤" else if (isActive.value) "⏹" else "🎤", 48.dp
                ) {
                    if (input.isNotBlank()) send() else toggleListen()
                }
            }
        }
    }

    @Composable
    private fun TasksScreen() {
        var input by remember { mutableStateOf("") }
        fun add() {
            val txt = input.trim()
            if (txt.isNotEmpty()) {
                tasks.add(TaskItem(txt, false))
                saveTasks()
                input = ""
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            TopBar("My Tasks", "CRUSH") { screen.value = Screen.HOME }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tasks.isEmpty()) {
                    item { CText("Abhi koi task nahi hai. Neeche likh ke add karo, ya bolo: task add karo ...", 14.sp, color = Sub) }
                }
                itemsIndexed(tasks) { i, task ->
                    TaskRow(
                        task,
                        onToggle = {
                            if (i < tasks.size) { tasks[i] = task.copy(done = !task.done); saveTasks() }
                        },
                        onDelete = {
                            if (i < tasks.size) { tasks.removeAt(i); saveTasks() }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fieldShape = RoundedCornerShape(26.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(fieldShape)
                        .background(Card)
                        .border(1.dp, Border, fieldShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                        cursorBrush = SolidColor(Neon),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) CText("Naya task likho...", 15.sp, color = Sub)
                                inner()
                            }
                        }
                    )
                }
                MicButton("+", 48.dp) { add() }
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            TopBar("Settings", "CRUSH") { screen.value = Screen.HOME }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val cardShape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(Card)
                        .border(1.dp, Border, cardShape)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(64.dp).border(3.dp, Neon, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { CText("CRUSH", 11.sp, bold = true) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        CText("Crush AI", 18.sp, bold = true)
                        CText("Version 1.0.0", 12.sp, color = Sub)
                    }
                }
                SettingRow("Voice & Language", "Hindi & English")
                SwitchRow("Voice", "Crush bolegi", voiceOn.value) { voiceOn.value = it }
                SwitchRow("Call / SMS", "Call, SMS, WhatsApp", callSmsOn.value) { callSmsOn.value = it }
                SwitchRow("Light Control", "Torch on / off", lightOn.value) { lightOn.value = it }
                SettingRow("Voice Test", "Crush ki aawaj sun lo") { testVoice() }
                SettingRow("Change Voice", "Agli Google Hindi voice (backup voice)") { cycleVoice() }
                SettingRow("Light", "Torch on / off") { doLight(null) }
                SettingRow("Accessibility", "WhatsApp / Instagram auto-send ke liye Crush ON karo") {
                    safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                SettingRow("Theme", "Dark Purple")
                SettingRow("About Crush AI", "I DIDN'T BUILD AN AI. I BUILT A FEELING.")
                CText(
                    "Follow and Comment : Trader Rijju", 11.sp, color = Sub,
                    align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                )
            }
        }
    }
}

// ---------- UI pieces ----------
@Composable
private fun CText(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = TextMain,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    spacing: TextUnit = TextUnit.Unspecified,
    strike: Boolean = false,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            letterSpacing = spacing,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None
        )
    )
}

@Composable
private fun MicButton(icon: String, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Neon, Purple)), CircleShape)
            .border(2.dp, Neon, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CText(icon, (size.value * 0.42f).sp, color = Color.White, bold = true)
    }
}

@Composable
private fun WaveBars(wave: Float, active: Boolean, offset: Int) {
    Row(
        modifier = Modifier.height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until 8) {
            val amp = if (active) 34f else 8f
            val h = 5f + amp * abs(sin(wave + (i + offset) * 0.6f))
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Neon.copy(alpha = if (active) 0.95f else 0.45f))
            )
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: String, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Card)
            .border(1.dp, Border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CText(icon, 24.sp)
        Spacer(Modifier.height(4.dp))
        CText(label, 12.sp, color = Sub)
    }
}

@Composable
private fun Chip(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Card)
            .border(1.dp, Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CText(label, 12.sp, color = TextMain, align = TextAlign.Center)
    }
}

@Composable
private fun TopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CText("←", 24.sp, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            CText(title, 20.sp, bold = true, spacing = 3.sp)
            CText(subtitle, 10.sp, color = Sub, spacing = 3.sp)
        }
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!line.fromUser) {
            Box(
                Modifier.size(38.dp).border(2.dp, Neon, CircleShape),
                contentAlignment = Alignment.Center
            ) { CText("CRUSH", 7.sp, bold = true) }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .background(if (line.fromUser) PurpleDeep else Card)
                .border(1.dp, if (line.fromUser) Purple else Border, shape)
                .padding(12.dp)
        ) {
            CText(line.text, 15.sp)
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Border, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CText(
            task.text, 15.sp,
            color = if (task.done) Sub else TextMain,
            strike = task.done,
            modifier = Modifier.weight(1f)
        )
        CText("✕", 16.sp, color = Sub, modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (task.done) Purple else Color.Transparent, CircleShape)
                .border(2.dp, Neon, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (task.done) CText("✓", 15.sp, bold = true)
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    var m = Modifier.fillMaxWidth().clip(shape).background(Card).border(1.dp, Border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            CText(title, 15.sp, bold = true)
            CText(sub, 12.sp, color = Sub)
        }
        if (onClick != null) CText("›", 22.sp, color = Sub)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Border, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            CText(title, 15.sp, bold = true)
            CText(sub, 12.sp, color = Sub)
        }
        PurpleSwitch(checked, onChange)
    }
}

@Composable
private fun PurpleSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(28.dp)
            .clip(shape)
            .background(if (checked) PurpleDeep else Color(0xFF1A1030))
            .border(1.dp, Neon, shape)
            .clickable { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Neon else Color.Gray)
        )
    }
}

// ---------- Auto-send / Auto-play (Accessibility) ----------
// Phone Settings > Accessibility me "Crush" ko ON karna padta hai.
object CrushBridge {
    @Volatile var autoSendUntil = 0L   // WhatsApp: Send button dabana
    @Volatile var ytUntil = 0L         // YouTube: pehla video chalana
    @Volatile var igUntil = 0L         // Instagram: naam search, chunna, Send
    @Volatile var igName = ""
    @Volatile var igStep = 0
    @Volatile var igAt = 0L
}

class CrushAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.packageNames = arrayOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "com.google.android.youtube", "com.instagram.android"
        )
        info.notificationTimeout = 200
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        val active = when {
            pkg.startsWith("com.whatsapp") -> now <= CrushBridge.autoSendUntil
            pkg == "com.google.android.youtube" -> now <= CrushBridge.ytUntil
            pkg == "com.instagram.android" -> now <= CrushBridge.igUntil
            else -> false
        }
        if (!active) return
        val root = rootInActiveWindow ?: return
        try {
            when {
                pkg.startsWith("com.whatsapp") -> whatsappStep(root)
                pkg == "com.google.android.youtube" -> youtubeStep(root)
                else -> instagramStep(root, now)
            }
        } catch (_: Exception) {
        }
    }

    private fun whatsappStep(root: AccessibilityNodeInfo) {
        var node: AccessibilityNodeInfo? = null
        for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
            node = root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
            if (node != null) break
        }
        if (node == null) {
            node = findFirst(root, 0) { n ->
                val d = n.contentDescription?.toString()?.trim()?.lowercase()
                d == "send" || d == "भेजें" || d == "भेजे"
            }
        }
        if (node != null && clickNode(node)) CrushBridge.autoSendUntil = 0L
    }

    private fun youtubeStep(root: AccessibilityNodeInfo) {
        val node = findFirst(root, 0) { n ->
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            d.contains("play video") || d.contains("वीडियो चलाएं") || d.contains("वीडियो चलाएँ")
        }
        if (node != null && clickNode(node)) CrushBridge.ytUntil = 0L
    }

    private fun instagramStep(root: AccessibilityNodeInfo, now: Long) {
        val name = CrushBridge.igName.lowercase()
        if (name.isBlank()) return
        when (CrushBridge.igStep) {
            0 -> {
                val box = findFirst(root, 0) { n -> n.className?.toString()?.contains("EditText") == true }
                if (box != null) {
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, CrushBridge.igName)
                    if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                        CrushBridge.igStep = 1
                        CrushBridge.igAt = now
                    }
                }
            }
            1 -> {
                if (now - CrushBridge.igAt < 1500) return
                val row = findFirst(root, 0) { n ->
                    val s = n.text?.toString()?.lowercase() ?: ""
                    s.isNotEmpty() && s.contains(name) && n.className?.toString()?.contains("EditText") != true
                }
                if (row != null && clickNode(row)) {
                    CrushBridge.igStep = 2
                    CrushBridge.igAt = now
                }
            }
            2 -> {
                if (now - CrushBridge.igAt < 1000) return
                val send = findFirst(root, 0) { n ->
                    val s = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim().lowercase()
                    s == "send" || s == "भेजें" || s == "भेजे"
                }
                if (send != null && clickNode(send)) {
                    CrushBridge.igUntil = 0L
                    CrushBridge.igStep = 0
                }
            }
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findFirst(
        n: AccessibilityNodeInfo?,
        depth: Int,
        match: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (n == null || depth > 30) return null
        if (match(n)) return n
        for (i in 0 until n.childCount) {
            val r = findFirst(n.getChild(i), depth + 1, match)
            if (r != null) return r
        }
        return null
    }

    override fun onInterrupt() {}
}
