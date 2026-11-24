package com.rscrush.saloni.ui

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

// ───────────────────────── Activity ─────────────────────────

class MainActivity : ComponentActivity() {
    private lateinit var eagle: EagleBrain

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eagle = EagleBrain(this)
        setContent { EagleScreen(eagle) }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 1 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) eagle.listen()
    }

    override fun onDestroy() {
        eagle.shutdown()
        super.onDestroy()
    }
}

// ───────────────────────── Brain ─────────────────────────

class EagleBrain(private val act: ComponentActivity) {

    // UI state: Waiting / Listening / Thinking / Speaking
    var state by mutableStateOf("Waiting")
    var mood by mutableStateOf("Chill 😎")
    var userName by mutableStateOf("boss")
    val messages = mutableStateListOf<Pair<Boolean, String>>() // true = user

    private val prefs = act.getSharedPreferences("eagle", Context.MODE_PRIVATE)
    private var sr: SpeechRecognizer? = null
    private var ttsReady = false
    private var torchOn = false

    private val tts: TextToSpeech = TextToSpeech(act) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale("hi", "IN"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault())
            }
            tts.setPitch(0.9f)
            tts.setSpeechRate(1.0f)
            ttsReady = true
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { state = "Speaking" }
                override fun onDone(id: String?) { state = "Waiting" }
                @Deprecated("deprecated")
                override fun onError(id: String?) { state = "Waiting" }
            })
        }
    }

    init {
        userName = prefs.getString("name", "boss") ?: "boss"
        messages.add(false to "${greeting()}, $userName! Main Eagle hoon. Mic dabao aur bolo.")
    }

    fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "Good night"
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            h < 21 -> "Good evening"
            else -> "Good night"
        }
    }

    // ── Listening ──
    fun listen() {
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(act)) {
            say("Is phone mein speech recognition available nahi hai.")
            return
        }
        tts.stop()
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(act)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { state = "Listening" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { state = "Thinking" }
            override fun onError(error: Int) {
                state = "Waiting"
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    say("Mujhe kuch sunai nahi diya, dobara bolo.")
                }
            }
            override fun onResults(r: Bundle?) {
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) state = "Waiting" else handle(text)
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        }
        state = "Listening"
        sr?.startListening(i)
    }

    // ── Speaking ──
    fun say(text: String) {
        messages.add(false to text)
        if (ttsReady) {
            state = "Speaking"
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eagle")
        } else state = "Waiting"
    }

    fun shutdown() {
        sr?.destroy()
        tts.stop()
        tts.shutdown()
        if (torchOn) setTorch(false)
    }

    // ── Brain ──
    fun handle(raw: String) {
        messages.add(true to raw)
        state = "Thinking"
        val t = raw.lowercase(Locale.ROOT).replace("eagle", "").replace("ईगल", "").trim()
        say(think(t))
    }

    private fun strip(t: String, vararg words: String): String {
        var s = " $t "
        for (w in words) s = s.replace(" $w ", " ")
        return s.trim().replace(Regex("\\s+"), " ")
    }

    private fun has(t: String, vararg keys: String) = keys.any { t.contains(it) }

    private fun launch(i: Intent): Boolean = try { act.startActivity(i); true } catch (e: Exception) { false }

    private fun think(t: String): String {
        // naam yaad rakho
        Regex("(mera naam|my name is|main)\\s+(\\w+)\\s*(hai|hoon|h)?$").find(t)?.let { m ->
            if (t.startsWith("mera naam") || t.startsWith("my name")) {
                val n = m.groupValues[2].replaceFirstChar { it.uppercase() }
                prefs.edit().putString("name", n).apply()
                userName = n
                mood = "Happy 😊"
                return "Theek hai $n, ab se main tumhe $n hi bulaunga."
            }
        }

        when {
            // YouTube / gaana
            has(t, "youtube", "yt", "bajao", "gana", "gaana", "play ", "chalao") -> {
                val q = strip(t, "youtube", "yt", "pe", "par", "on", "play", "chalao", "bajao",
                    "karo", "kar", "do", "search", "mere", "liye", "ek", "ka", "ki", "ko", "gana", "gaana", "song")
                return if (q.isBlank()) {
                    if (openApp("youtube")) "YouTube khol diya." else "YouTube nahi mila."
                } else {
                    val ok = launch(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                    mood = "Happy 😊"
                    if (ok) "YouTube pe $q chala raha hoon." else "YouTube nahi khul paaya."
                }
            }
            // Maps
            has(t, "navigate", "rasta", "route", "directions", "map", "kaise jaun", "kaise jaaun") -> {
                val q = strip(t, "navigate", "to", "ka", "ki", "ko", "rasta", "route", "directions", "map", "maps",
                    "dikhao", "batao", "kaise", "jaun", "jaaun", "karo", "me", "mein", "pe", "par")
                val ok = launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
                return if (ok) "Maps mein $q dikha raha hoon." else "Maps nahi khul paaya."
            }
            // Call
            has(t, "call", "phone laga", "dial", "फोन") -> {
                val digits = t.filter { it.isDigit() }
                val uri = if (digits.length >= 3) "tel:$digits" else "tel:"
                return if (launch(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))) "Dialer khol diya." else "Dialer nahi khula."
            }
            // App kholo
            has(t, "open", "kholo", "khol", "chalu karo", "start karo", "launch") -> {
                val n = strip(t, "open", "kholo", "khol", "do", "karo", "kar", "app", "ko", "chalu", "start", "launch", "please", "plz")
                return if (n.isNotBlank() && openApp(n)) "$n khol diya." else "$n naam ka app mujhe nahi mila."
            }
            // Torch
            has(t, "torch", "flashlight", "flash light", "light") -> {
                val off = has(t, "off", "band", "bujha")
                return if (setTorch(!off)) (if (off) "Torch band kar di." else "Torch chalu kar di.") else "Torch available nahi hai."
            }
            // Volume
            has(t, "volume", "awaaz", "awaz") -> {
                val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val up = has(t, "badha", "increase", "up", "zyada", "tez")
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return if (up) "Volume badha diya." else "Volume kam kar diya."
            }
            // Time / date / battery
            has(t, "time", "samay", "kitne baje", "baj gaye") -> {
                val c = Calendar.getInstance()
                val h = if (c.get(Calendar.HOUR) == 0) 12 else c.get(Calendar.HOUR)
                return String.format(Locale.US, "Abhi %d:%02d ho rahe hain.", h, c.get(Calendar.MINUTE))
            }
            has(t, "date", "tarikh", "tareekh", "aaj kya din", "aaj ki date") -> {
                val c = Calendar.getInstance()
                return "Aaj ${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.YEAR)} hai."
            }
            has(t, "battery") -> {
                val bm = act.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                return "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent hai."
            }
            // Google search
            has(t, "search", "google", "dhundo", "dhoondo", "kya hai", "kaun hai", "kya hota") -> {
                val q = strip(t, "search", "google", "karo", "kar", "pe", "par", "dhundo", "dhoondo", "batao")
                return if (googleSearch(q)) "$q search kar raha hoon." else "Search nahi ho paaya."
            }
            // Chit-chat
            t.split(" ").any { it in listOf("hi", "hello", "hey", "namaste", "salam") } -> { mood = "Happy 😊"; return "Hello $userName! Bolo, kya karna hai?" }
            has(t, "kaise ho", "kaisa hai", "how are you", "kya haal") -> { mood = "Happy 😊"; return "Main bilkul mast hoon $userName, tum batao?" }
            has(t, "tera naam", "tumhara naam", "your name", "kaun ho", "who are you") -> { mood = "Chill 😎"; return "Main Eagle hoon, tumhara apna AI assistant. Aasman se bhi upar ki nazar!" }
            has(t, "thank", "shukriya", "dhanyavaad", "thanks") -> { mood = "Warm 🥰"; return "Arre koi baat nahi $userName, main hoon na." }
            has(t, "love you", "i love", "pyaar", "pyar") -> { mood = "Warm 🥰"; return "Aww, main bhi tumhe pasand karta hoon $userName." }
            has(t, "joke", "chutkula", "hasao") -> { mood = "Happy 😊"; return "Teacher ne pucha: sabse tez kya hai? Student bola: result, kyunki fail hone ki khabar pehle pahunchti hai." }
            has(t, "bakwas", "chup", "stupid", "pagal", "gussa") -> { mood = "Annoyed 😒"; return "Theek hai, theek hai. Itna gussa mat karo." }
            has(t, "good night", "shubh ratri", "sone ja") -> { mood = "Warm 🥰"; return "Good night $userName, meethe sapne." }
            has(t, "good morning", "suprabhat") -> { mood = "Happy 😊"; return "Good morning $userName! Aaj ka din shaandar hoga." }
        }
        // Fallback: Google pe dhundo
        mood = "Chill 😎"
        return if (googleSearch(t)) "Ye mujhe pata nahi, Google pe dhundh raha hoon." else "Samajh nahi aaya, dobara bolo."
    }

    // ── Actions ──
    private fun googleSearch(q: String): Boolean {
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
        return launch(i) || launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
    }

    private fun openApp(name: String): Boolean {
        val pm = act.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(main, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.ROOT).contains(name.lowercase(Locale.ROOT))
        } ?: return false
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return false
        return launch(i)
    }

    private fun setTorch(on: Boolean): Boolean = try {
        val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        cm.setTorchMode(id, on)
        torchOn = on
        true
    } catch (e: Exception) { false }
}

// ───────────────────────── UI ─────────────────────────

@Composable
fun EagleScreen(e: EagleBrain) {
    val inf = rememberInfiniteTransition(label = "orb")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "rot")
    val pulse by inf.animateFloat(0.95f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val active = e.state == "Listening" || e.state == "Speaking"
    val ring = when (e.state) {
        "Listening" -> Color(0xFF00E5FF)
        "Speaking" -> Color(0xFFFFB300)
        "Thinking" -> Color(0xFFB388FF)
        else -> Color(0xFF5C6BC0)
    }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(e.messages.size) { if (e.messages.isNotEmpty()) list.animateScrollToItem(e.messages.size - 1) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF1A1033))))
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)
    ) {
        BasicText(e.greeting() + ",", style = TextStyle(Color(0xFFB0B8D0), 14.sp))
        BasicText(e.userName, style = TextStyle(Color.White, 26.sp, FontWeight.Bold))
        BasicText(e.state + "...   •   Mood: " + e.mood, style = TextStyle(ring, 12.sp))

        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp).scale(if (active) pulse else 1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(ring.copy(alpha = 0.30f), Color.Transparent)))
                    val st = Stroke(width = 9f, cap = StrokeCap.Round)
                    rotate(rot) {
                        drawArc(ring, 0f, 110f, false, Offset(10f, 10f), Size(size.width - 20f, size.height - 20f), style = st)
                        drawArc(ring.copy(alpha = 0.5f), 180f, 60f, false, Offset(10f, 10f), Size(size.width - 20f, size.height - 20f), style = st)
                    }
                    rotate(-rot * 1.5f) {
                        drawArc(Color.White.copy(alpha = 0.8f), 40f, 70f, false, Offset(34f, 34f), Size(size.width - 68f, size.height - 68f), style = st)
                    }
                }
                BasicText("E.A.G.L.E", style = TextStyle(Color.White, 22.sp, FontWeight.ExtraBold))
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(e.messages) { (user, text) ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(
                        Modifier.background(
                            if (user) Color(0xFF3949AB) else Color(0xFF1E2542), RoundedCornerShape(16.dp)
                        ).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { BasicText(text, style = TextStyle(Color.White, 15.sp)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).background(Color(0xFF1E2542), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (input.isEmpty()) BasicText("Eagle se kuch bhi pucho...", style = TextStyle(Color(0xFF7C86A8), 15.sp))
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = TextStyle(Color.White, 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { e.handle(input.trim()); input = "" }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                Modifier.padding(start = 10.dp).size(56.dp).background(ring, CircleShape).clickable { e.listen() },
                contentAlignment = Alignment.Center
            ) { BasicText("🎤", style = TextStyle(fontSize = 24.sp)) }
        }
    }
}
           
