package com.rscrush.saloni.ui

import android.Manifest
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private val Green = Color(0xFF00FF00)
private val DimGreen = Color(0xFF00AA00)
private val DarkGreen = Color(0xFF006600)

// ---------- ElevenLabs voice ----------
// Apni ElevenLabs API Key yaha quotes ke andar paste karo.
// Key khali ("") rahi to app apne aap Google TTS use karegi.
private const val ELEVEN_API_KEY = ""
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

    private fun doSms(name: String, msg: String) {
        if (blockedBySwitch()) return
        val num = getNumberByName(name)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(num ?: "")}"))
        i.putExtra("sms_body", msg)
        prefs.edit().putString("last_sms_name", name).putString("last_sms_msg", msg).apply()
        if (safeStart(i)) {
            reply("Thik hai Trader Rijju, $name ko SMS me '$msg' bhej rahi hu")
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
            reply("Thik hai Trader Rijju, $name ko WhatsApp par '$msg' bhej rahi hu")
        } else {
            reply("Trader Rijju, WhatsApp nahi khul paya")
        }
    }

    private fun doCall(name: String) {
        if (blockedBySwitch()) return
        val num = getNumberByName(name)
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(num ?: "")}"))
        prefs.edit().putString("last_call_name", name).apply()
        if (safeStart(i)) {
            reply("Thik hai Trader Rijju, $name ko call laga rahi hu")
        } else {
            reply("Trader Rijju, dialer nahi khul paya")
        }
    }

    private fun doInstagram(name: String, msg: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crush_msg", msg))
        } catch (_: Exception) {}
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox/"))
        if (safeStart(i)) {
            reply("Thik hai Trader Rijju, Instagram khol rahi hu. $name ko bhejne wala message copy kar diya hai, bas paste kar dena")
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

    // ---------- Command handling ----------
    private fun handleCommand(raw: String) {
        val t = raw.lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return
        val words = t.split(Regex("\\s+"))
        addChat(true, raw)
        prefs.edit().putString("last_talk", raw).apply()

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
        if (has(t, "gana", "gaana", "गाना", "गाने", "song", "music", "bajao", "बजाओ")) {
            val q = cleanQuery(
                t,
                setOf("ka", "ke", "ki", "ko", "gana", "gaana", "gane", "song", "music", "bajao", "bajado",
                    "chalao", "play", "youtube", "pe", "par", "ek", "ka", "का", "के", "की", "गाना", "गाने",
                    "बजाओ", "चलाओ", "यूट्यूब", "पर", "पे", "सुनाओ", "sunao")
            )
            reply("Thik hai Trader Rijju, $q ka gana YouTube par dhundh rahi hu")
            youtube("$q song")
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
        val askDate = has(t, "date", "तारीख", "tarikh", "तारिख", "तिथि", "din kya")
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
    @Composable
    private fun CrushScreen() {
        val listState = rememberLazyListState()
        LaunchedEffect(chat.size) {
            if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Mono(
                "I DIDN'T BUILD AN AI.\nI BUILT A FEELING.",
                size = 14.sp, bold = true, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // Animation: dil dhadakta hai (active me tez), aur active me ring bahar failti hai
            val inf = rememberInfiniteTransition(label = "crush")
            val active = isActive.value
            val beat by inf.animateFloat(
                initialValue = 1f,
                targetValue = if (active) 1.28f else 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (active) 450 else 1100),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "beat"
            )
            val ripple by inf.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple"
            )
            Box(modifier = Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.3f * ripple
                                scaleY = 1f + 0.3f * ripple
                                alpha = 0.7f * (1f - ripple)
                            }
                            .border(3.dp, Green, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .border(if (active) 4.dp else 2.dp, Green, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = beat
                                scaleY = beat
                            }
                        ) {
                            Mono("💖", size = 34.sp)
                        }
                        Mono("CRUSH", size = 20.sp, bold = true)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, DarkGreen)
                    .padding(8.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(chat) { line ->
                        Mono(
                            if (line.fromUser) "> Trader Rijju: ${line.text}" else "Crush: ${line.text}",
                            size = 13.sp,
                            color = if (line.fromUser) DimGreen else Green
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            HackerButton(
                label = if (isActive.value) "DEACTIVATE" else "ACTIVATE CRUSH",
                modifier = Modifier.fillMaxWidth(),
                big = true
            ) {
                if (isActive.value) stopCrush() else activateCrush()
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HackerButton("LIGHT", Modifier.weight(1f), filled = false) { doLight(null) }
                HackerButton("VOICE TEST", Modifier.weight(1f), filled = false) {
                    if (!voiceOn.value) {
                        Toast.makeText(this@MainActivity, "Voice switch OFF hai", Toast.LENGTH_SHORT).show()
                    } else if (ELEVEN_API_KEY.isBlank()) {
                        cycleVoice()
                    } else {
                        val msg = "Hello Trader Rijju, main Crush hu. Meri aawaj kaisi lag rahi hai?"
                        addChat(false, msg)
                        speak(msg)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HackerSwitch("Voice", voiceOn.value) { voiceOn.value = it }
                HackerSwitch("Call/SMS", callSmsOn.value) { callSmsOn.value = it }
                HackerSwitch("Light Control", lightOn.value) { lightOn.value = it }
            }
            Spacer(Modifier.height(10.dp))

            Mono(
                "Follow and Comment : Trader Rijju",
                size = 12.sp, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Mono(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = Green,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align
        )
    )
}

@Composable
private fun HackerButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    big: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) Green else Color.Black)
            .border(1.dp, Green, shape)
            .clickable(onClick = onClick)
            .padding(vertical = if (big) 18.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Mono(
            label,
            size = if (big) 18.sp else 14.sp,
            color = if (filled) Color.Black else Green,
            bold = true
        )
    }
}

@Composable
private fun HackerSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Mono(label, size = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(shape)
                .background(if (checked) Color(0xFF004400) else Color(0xFF111111))
                .border(1.dp, Green, shape)
                .clickable { onChange(!checked) }
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) Green else Color.Gray)
            )
        }
    }
}
