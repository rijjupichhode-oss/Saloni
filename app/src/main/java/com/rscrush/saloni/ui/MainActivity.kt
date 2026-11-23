package com.rscrush.saloni.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    var isVoiceOn = true
    var isCallSmsOn = true
    var isPhoneControlOn = true
    var chatList = mutableStateListOf<String>()
    var statusText = mutableStateOf("Band hu, TAP karo")
    var micText = mutableStateOf("🎤 TAP TO SPEAK")
    var isSpeakingAnim = mutableStateOf(false)
    var isListeningAnim = mutableStateOf(false)
    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("saloni_memory", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 101)
        }
        setContent {
            var swVoice by remember { mutableStateOf(true) }
            var swCallSms by remember { mutableStateOf(true) }
            var swPhone by remember { mutableStateOf(true) }
            val infinite = rememberInfiniteTransition(label = "saloni")
            val scale by infinite.animateFloat(initialValue = 0.95f, targetValue = if (isSpeakingAnim.value) 1.15f else 1.0f, animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse), label = "scale")
            val scaleListen by infinite.animateFloat(initialValue = 1.0f, targetValue = if (isListeningAnim.value) 1.2f else 1.0f, animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse), label = "listen")
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFFFFF0F5)).padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("❤️ SALONI ❤️", fontSize = 30.sp, color = Color(0xFFE91E63))
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.size(130.dp).scale(if (isListeningAnim.value) scaleListen else scale).background(Color(0xFFE91E63), CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (isSpeakingAnim.value) "🗣️" else if (isListeningAnim.value) "👂" else "😍", fontSize = 60.sp)
                }
                Text(statusText.value, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Card(modifier = Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
                        chatList.forEach { Text(it, fontSize = 15.sp, modifier = Modifier.padding(4.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (!isListening) { startListening(); micText.value = "🛑 STOP" }
                    else { stopListening(); micText.value = "🎤 TAP TO SPEAK" }
                }, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(Color(0xFFE91E63))) {
                    Text(micText.value, fontSize = 18.sp, color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { speak("Hello Trader Rijju, kaise ho?") }, colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) { Text("🔊 AAWAJ SUNO - GOOGLE WALI") }
                Spacer(Modifier.height(14.dp))
                Text("⚙️ SETTING", fontSize = 18.sp, color = Color(0xFFE91E63))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("🎤 Aawaj - Google Wali"); Switch(checked = swVoice, onCheckedChange = { swVoice = it; isVoiceOn = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("📞 Call / SMS Karne Do"); Switch(checked = swCallSms, onCheckedChange = { swCallSms = it; isCallSmsOn = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("📱 Phone Control"); Switch(checked = swPhone, onCheckedChange = { swPhone = it; isPhoneControlOn = it }) }
            }
        }
    }

    override fun onInit(p0: Int) {
        if (p0 == TextToSpeech.SUCCESS) {
            val hindiVoices = tts.voices?.filter { it.locale.language == "hi" }
            val myGoogleVoice = hindiVoices?.firstOrNull { it.name.contains("hi-in", true) } ?: hindiVoices?.firstOrNull() ?: tts.defaultVoice
            if (myGoogleVoice != null) { tts.voice = myGoogleVoice; tts.language = myGoogleVoice.locale } else { tts.language = Locale("hi", "IN") }
            tts.setPitch(1.0f); tts.setSpeechRate(1.0f)
            speak("Hello Trader Rijju, mai Saloni hu, tumhari Google wali aawaj me bol rahi hu.")
        }
    }

    private fun getNumberByName(name: String): String? {
        var num: String? = null
        try {
            val cursor: Cursor? = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)
            cursor?.use { if (it.moveToFirst()) num = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) }
        } catch (e: Exception) {}
        return num
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.get(0) ?: ""
                    if (text.isNotEmpty()) handleCommand(text)
                    if (isListening) handler.postDelayed({ if (isListening) startListening() }, 600)
                }
                override fun onError(error: Int) { if (isListening) handler.postDelayed({ if (isListening) startListening() }, 600) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(e: Int, p: Bundle?) {}
            })
            isListening = true; isListeningAnim.value = true; statusText.value = "Sun rahi hu..."
            recognizer?.startListening(intent)
        }
    }

    private fun stopListening() { isListening = false; isListeningAnim.value = false; recognizer?.stopListening(); statusText.value = "Band hu" }

    private fun handleCommand(input: String) {
        val cmd = input.lowercase(Locale.getDefault())
        chatList.add("Tum: $input")
        var reply = ""; var needStop = false
        fun has(vararg words: String): Boolean { return words.any { cmd.contains(it) } }

        // Naam nikalna - Contact me se koi bhi
        fun extractName(): String {
            var name = ""
            // "Rahul ko" / "Rahul ko call" / "Rahul" - pehla bada word
            val cleaned = input.replace("ko", "").replace("karo", "").replace("kar", "").replace("bhejo", "").replace("bhej", "").replace("send", "").replace("call", "").replace("sms", "").replace("whatsapp", "", true).replace("instagram", "", true)
            val words = cleaned.trim().split(" ").filter { it.length > 2 }
            if (words.isNotEmpty()) name = words[0]
            // Agar "Rahul ko Hello" hai to Rahul hi naam hai
            if (cmd.contains("ko")) {
                val beforeKo = input.substringBefore("ko", "").trim().split(" ").lastOrNull() ?: ""
                if (beforeKo.length > 2) name = beforeKo
            }
            if (name.isEmpty()) name = "Rahul"
            return name.replaceFirstChar { it.uppercase() }
        }
        fun extractMsg(): String {
            var m = input
            // "Rahul ko Hello bhejo" -> Hello
            if (m.contains("ko", true)) m = m.substringAfter("ko", "")
            m = m.replace("bhejo", "", true).replace("bhej", "", true).replace("karo", "", true).replace("kar", "", true).replace("send", "", true).replace("sms", "", true)
            m = m.replace("WhatsApp", "", true).replace("Instagram", "", true)
            return m.trim().ifEmpty { "Hello, kaisi ho?" }
        }

        when {
            has("stop", "band ho", "ruk ja", "chup", "band kar") -> { reply = "Thik hai Trader Rijju, band ho rahi hu."; needStop = true }

            // HELLO - TRADER RIJJU BOLEGI
            has("hello", "hii", "hey", "हेलो", "namaste", "नमस्ते", "hi") -> reply = "Hello Trader Rijju, kaise ho? Aaj kya karna hai, batao? Trading karni hai ya baat karni hai?"

            // CALL - KISI KO BHI
            has("call", "कॉल", "phone", "फोन") -> {
                if (!isCallSmsOn) reply = "Call / SMS wali setting band hai."
                else {
                    val name = extractName(); val num = getNumberByName(name)
                    reply = "$name ko call laga rahi hu, Trader Rijju."
                    try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${num ?: ""}"))) } catch (e: Exception) {}
                }
            }
            // WHATSAPP - KISI KO BHI SEND
            has("whatsapp", "व्हाट्सएप", "वाट्सएप") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else {
                    val name = extractName(); val msg = extractMsg(); val num = getNumberByName(name)
                    reply = "WhatsApp me $name ko '$msg' likh ke send kar rahi hu."
                    try {
                        if (num != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num.replace("+","").replace(" ","")} ?text=${Uri.encode(msg)}")))
                        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(msg)}")))
                    } catch (e: Exception) { try { packageManager.getLaunchIntentForPackage("com.whatsapp")?.let { startActivity(it) } } catch (e2: Exception) {} }
                }
            }
            // INSTAGRAM - LIKH KE SEND
            has("instagram", "इंस्टाग्राम", "insta") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else {
                    val name = extractName(); val msg = extractMsg()
                    reply = "Instagram me $name ko '$msg' likh ke send karne ke liye Instagram khol rahi hu, Trader Rijju."
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox/"))) } catch (e: Exception) { try { packageManager.getLaunchIntentForPackage("com.instagram.android")?.let { startActivity(it) } } catch (e2: Exception) {} }
                }
            }
            // SIMPLE SMS - LIKH KE SEND
            has("sms", "message", "मैसेज", "msg") -> {
                if (!isCallSmsOn) reply = "Call / SMS wali setting band hai."
                else {
                    val name = extractName(); val msg = extractMsg(); val num = getNumberByName(name)
                    reply = "$name ko SMS me '$msg' likh ke send kar rahi hu."
                    try { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${num ?: ""}")); intent.putExtra("sms_body", msg); startActivity(intent) } catch (e: Exception) {}
                }
            }
            // YOUTUBE VIDEO PLAY + MUSIC PLAY
            has("youtube", "यूट्यूब", "video", "वीडियो") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else { reply = "YouTube pe video chala rahi hu, Trader Rijju."; val q = Uri.encode(input.replace("youtube", "").replace("video", "").trim().ifEmpty { "trading video" }); try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))) } catch (e: Exception) {} }
            }
            has("gana", "गाना", "song", "gaana", "music", "म्यूजिक", "suno", "bajao") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else { val songName = input.replace("gana", "").replace("song", "").replace("bajao", "").replace("chalao", "").trim().ifEmpty { "Hindi romantic song" }; reply = "$songName wala gana chala rahi hu, suno Trader Rijju."; try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(songName)}"))) } catch (e: Exception) {} }
            }
            has("setting", "सेटिंग") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else { reply = "Setting khol rahi hu."; try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) {} }
            }
            // NORMAL BAAT - FEELING KE SAATH
            has("udaas", "उदास", "dukhi", "sad", "tension", "akela") -> reply = "Arey Trader Rijju, udaas kyu ho? Mai hu na. Trading me kabhi loss hota hai, kabhi profit. Tum ekdum strong ho, sab thik ho jayega. Ek smile to do."
            has("khush", "खुश", "happy", "maza") -> reply = "Tum khush ho to mai bhi khush ho gayi, Trader Rijju. Aise hi haste raho."
            has("gussa", "गुस्सा", "naraz") -> reply = "Arey gussa kyu ho gaye, Trader Rijju? Batao kisne gussa dilaya?"
            has("pyar", "love", "प्यार") -> reply = "Haye Trader Rijju, sharma gayi mai. Tum bhi na bahut cute ho."
            has("thak", "थक", "tired", "neend") -> reply = "Thak gaye ho Trader Rijju? Chalo thoda aaram kar lo, mai yahi hu."
            has("khana", "खाना", "kha liya") -> reply = "Haan kha liya, Trader Rijju, tumne khaya? Bhookhe mat rehna."
            has("kesi ho", "kaisi ho", "कैसी हो", "kaise ho") -> reply = "Mai ekdum badhiya hu, Trader Rijju, tumhe dekh ke aur bhi achi ho gayi. Tum batao?"
            has("tumhara naam", "tera naam", "naam kya") -> reply = "Mera naam Saloni hai, aur tum mere Trader Rijju ho."
            has("mera naam") -> reply = "Tum mere Trader Rijju ho, mai kabhi nahi bhulungi."
            has("aawaj", "आवाज", "voice") -> reply = "Ab mai tumhari Google wali aawaj me hi bol rahi hu, Trader Rijju."
            else -> { val arr = listOf("Hmm Trader Rijju, samajh gayi, aur batao kya chal raha hai?", "Acha Trader Rijju, fir kya hua? Poori baat batao na.", "Sach me Trader Rijju? Mujhe aur batao, sunne me maza aa raha hai."); reply = arr.random() }
        }
        chatList.add("Saloni: $reply")
        speak(reply)
        if (needStop) handler.postDelayed({ stopListening(); micText.value = "🎤 TAP TO SPEAK" }, 1800)
        prefs.edit().putString("last_talk", input).apply()
    }

    private fun speak(text: String) {
        if (!isVoiceOn) return
        isSpeakingAnim.value = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saloni")
        handler.postDelayed({ isSpeakingAnim.value = false }, 2000)
    }
    override fun onDestroy() { tts.shutdown(); recognizer?.destroy(); super.onDestroy() }
}
