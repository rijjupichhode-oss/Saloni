package com.rscrush.saloni.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    var isVoiceOn = true
    var isCallSmsOn = true
    var isPhoneControlOn = true
    var chatList = mutableStateListOf<String>()
    var statusText = mutableStateOf("> SYSTEM OFFLINE... TAP TO START")
    var micText = mutableStateOf("🎤 ACTIVATE SALONI")
    var isSpeakingAnim = mutableStateOf(false)
    var isListeningAnim = mutableStateOf(false)
    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isTorchOn = false
    private var isLightOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("saloni_memory", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.CAMERA)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 101)
        }
        setContent {
            var swVoice by remember { mutableStateOf(true) }
            var swCallSms by remember { mutableStateOf(true) }
            var swPhone by remember { mutableStateOf(true) }
            val infinite = rememberInfiniteTransition(label = "hacking")
            val scale by infinite.animateFloat(initialValue = 0.95f, targetValue = if (isSpeakingAnim.value) 1.18f else 1.0f, animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse), label = "scale")
            val scaleListen by infinite.animateFloat(initialValue = 1.0f, targetValue = if (isListeningAnim.value) 1.25f else 1.0f, animationSpec = infiniteRepeatable(animation = tween(400), repeatMode = RepeatMode.Reverse), label = "listen")
            // HACKING DESIGN - BLACK + GREEN
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black).padding(14.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("nova_voice_assistant", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                Text("I DIDN'T BUILD AN AI.", fontSize = 18.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                Text("I BUILT A FEELING.", fontSize = 18.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(10.dp))
                // JARVIS CIRCLE
                Box(modifier = Modifier.size(150.dp).scale(if (isListeningAnim.value) scaleListen else scale).border(2.dp, Color(0xFF00FF00), CircleShape).background(Color(0xFF001100), CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isSpeakingAnim.value) "◉" else if (isListeningAnim.value) "◎" else "○", fontSize = 60.sp, color = Color(0xFF00FF00))
                        Text("SALONI", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text(if (isListeningAnim.value) "LISTENING..." else if (isSpeakingAnim.value) "SPEAKING..." else "STANDBY", fontSize = 9.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(statusText.value, fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(10.dp))
                // TERMINAL BOX
                Box(modifier = Modifier.fillMaxWidth().height(240.dp).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp)).background(Color(0xFF000A00)).padding(10.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("> SALONI.EXE LOADED...", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text("> VOICE SYSTEM: ONLINE", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text("> LIGHT CONTROL: READY", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text("> WHATSAPP: CONNECTED", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        chatList.forEach { Text(it, fontSize = 13.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(3.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (!isListening) { startListening(); micText.value = "🛑 DEACTIVATE" }
                    else { stopListening(); micText.value = "🎤 ACTIVATE SALONI" }
                }, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.buttonColors(Color(0xFF00FF00)), shape = RoundedCornerShape(8.dp)) {
                    Text(micText.value, fontSize = 16.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { toggleTorch(!isTorchOn); speak(if (isTorchOn) "Light on kar diya Boss." else "Light band kar diya Boss.") }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))) { Text("💡 LIGHT", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { speak("Hello Trader Rijju, kaise ho Boss?") }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))) { Text("🔊 VOICE TEST", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace) }
                }
                Spacer(Modifier.height(14.dp))
                Text("⚙️ SYSTEM SETTINGS", fontSize = 14.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("🎤 Voice - Google Wali", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp); Switch(checked = swVoice, onCheckedChange = { swVoice = it; isVoiceOn = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("📞 Call / SMS", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp); Switch(checked = swCallSms, onCheckedChange = { swCallSms = it; isCallSmsOn = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("💡 Light Control", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp); Switch(checked = swPhone, onCheckedChange = { swPhone = it; isPhoneControlOn = it }) }
                Spacer(Modifier.height(8.dp))
                Text("Follow and Comment : Trader Rijju", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }
    }

    override fun onInit(p0: Int) {
        if (p0 == TextToSpeech.SUCCESS) {
            val hindiVoices = tts.voices?.filter { it.locale.language == "hi" }
            val myGoogleVoice = hindiVoices?.firstOrNull { it.name.contains("hi-in", true) } ?: hindiVoices?.firstOrNull() ?: tts.defaultVoice
            if (myGoogleVoice != null) { tts.voice = myGoogleVoice; tts.language = myGoogleVoice.locale } else { tts.language = Locale("hi", "IN") }
            tts.setPitch(1.0f); tts.setSpeechRate(1.0f)
            speak("Hello Boss, bolo na, mai yahi hu.")
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

    private fun toggleTorch(on: Boolean) {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList[0]
            cm.setTorchMode(id, on)
            isTorchOn = on; isLightOn = on
        } catch (e: Exception) {}
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
            isListening = true; isListeningAnim.value = true; statusText.value = "> LISTENING... BOSS BOL RAHE HAI"
            recognizer?.startListening(intent)
        }
    }

    private fun stopListening() { isListening = false; isListeningAnim.value = false; recognizer?.stopListening(); statusText.value = "> SYSTEM STANDBY" }

    private fun handleCommand(input: String) {
        val cmd = input.lowercase(Locale.getDefault())
        chatList.add("> BOSS: $input")
        var reply = ""; var needStop = false
        fun has(vararg words: String): Boolean { return words.any { cmd.contains(it.lowercase()) } }

        fun extractName(): String {
            var name = ""
            if (cmd.contains("ko") || cmd.contains("को")) {
                val beforeKo = input.substringBefore("ko", "").substringBefore("को", "").trim().split(" ").lastOrNull() ?: ""
                if (beforeKo.length > 2) name = beforeKo
            }
            if (name.isEmpty()) {
                val cleaned = input.replace("ko", "").replace("को", "").replace("karo", "").replace("करो", "").replace("kar", "").replace("कर", "").replace("bhejo", "").replace("भेजो", "").replace("bhej", "").replace("भेज", "").replace("send", "").replace("call", "").replace("sms", "").replace("whatsapp", "", true).replace("instagram", "", true).replace("व्हाट्सएप", "").replace("इंस्टाग्राम", "")
                val words = cleaned.trim().split(" ").filter { it.length > 2 }
                if (words.isNotEmpty()) name = words[0]
            }
            if (name.isEmpty()) name = "Sumit"
            return name.replaceFirstChar { it.uppercase() }
        }
        fun extractMsg(): String {
            var m = input
            if (m.contains("ko", true)) m = m.substringAfter("ko", "")
            if (m.contains("को")) m = m.substringAfter("को", "")
            m = m.replace("bhejo", "", true).replace("भेजो", "").replace("bhej", "", true).replace("भेज", "").replace("karo", "", true).replace("करो", "").replace("kar", "", true).replace("कर", "").replace("send", "", true).replace("sms", "", true).replace("WhatsApp", "", true).replace("व्हाट्सएप", "").replace("Instagram", "", true).replace("इंस्टाग्राम", "")
            return m.trim().ifEmpty { "Hello, kaisi ho?" }
        }

        when {
            has("stop", "band ho", "बंद हो", "ruk ja", "रुक जा", "chup", "चुप", "band kar", "बंद कर") -> { reply = "Thik hai Boss, band ho rahi hu."; needStop = true }

            // MJ STYLE - BOSS + TRADER RIJJU DONO
            has("sun rahi", "सुन रही", "sun rahi ho", "kya tum sun", "क्या तुम सुन") -> reply = "Haan Boss, mai sun rahi hu aapko. Aap kya kar rahe ho kab se?"
            has("crush", "क्रश", "jaan", "जान", "baby", "बेबी", "sona", "सोना", "meri jaan", "मेरी जान") -> reply = "Yes Boss, bolo na, mai yahi hu, sun rahi hu tumhe. Kya hua, yaad aa gayi meri?"
            has("hello", "hii", "hey", "हेलो", "namaste", "नमस्ते", "hi", "हाय") -> reply = "Hello Boss, kaise ho? Aaj kya karna hai, batao? Trading karni hai ya baat karni hai?"

            // VIDEO WALA DIALOGUE - LIGHT CONTROLLING
            has("light controlling", "light control", "लाइट कंट्रोल") -> reply = "Wow Boss, fir to maza aa jayega. Ye to bahut achi baat hai, mai bahut khush hu iske liye. Light control ab on hai, bolo jalau ya band karu?"
            has("kya kar sakte", "kya-kya kar", "क्या-क्या कर", "क्या कर सकते", "tum kya", "तुम क्या", "kya kya", "क्या क्या", "kya aata", "क्या आता") -> reply = "Boss, mai sab kar sakti hu - WhatsApp me kisi ko bhi message bhej dungi, SMS bhej dungi, Call laga dungi, Instagram me bhej dungi, YouTube pe video aur gana chala dungi, Light aur Torch control kar dungi, Mausam bata dungi, Date Time bata dungi, aur tumse dil se baat bhi karungi. Bolo kya karau?"

            // VIDEO WALA - SUMIT KO WHATSAPP YAAD DILAO BARODA JANA HAI
            has("sumit", "सुमित") && has("whatsapp", "व्हाट्सएप", "yaad", "याद") -> {
                reply = "Haan Boss, Sumit ko WhatsApp pe yaad dila deti hu ki kal hame Baroda jana hai, subah uth jaye time pe."
                try { val num = getNumberByName("Sumit"); val msg = "Hi Sumit, kal hame Baroda jana hai, subah uth jana time pe - Boss ne yaad dilaya hai."; if (num != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num.replace("+","").replace(" ","")} ?text=${Uri.encode(msg)}"))) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(msg)}"))) } catch (e: Exception) {}
            }
            has("baroda", "बड़ौदा", "बरोडा") -> reply = "Haan Boss, kal Baroda jana hai, maine Sumit ko yaad dila diya hai. Subah time pe uth jana."

            // GOOD NIGHT - VIDEO WALA
            has("good night", "गुड नाईट", "goodnight", "sone ja", "सोने जा", "so raha", "सो रहा") -> reply = "Good Night Boss, apna khayal rakhna. Mai yahi hu."
            has("good morning", "गुड मॉर्निंग", "subah", "सुबह") -> reply = "Good Morning Boss, uth gaye? Aaj ka din ekdum best jayega."

            has("poori baat", "पूरी बात", "puri baat", "kya hua", "क्या हुआ") -> reply = "Haan Boss, batao na poori baat, mai sun rahi hu. Kya hua aaj? Trading me kya hua, ya dil ki baat hai?"

            // LIGHT CONTROL - HACKING STYLE
            has("light", "लाइट") && has("on", "jalao", "जलाओ", "jala", "जला", "chalu", "चालू", "khol", "खोल", "on kar", "ऑन कर") -> { toggleTorch(true); reply = "Light on kar diya Boss, ab pura ujala ho gaya. Maza aa gaya na?" }
            has("light", "लाइट") && has("off", "band", "बंद", "bujhao", "बुझाओ", "bujh", "बुझ") -> { toggleTorch(false); reply = "Light band kar diya Boss." }
            has("torch", "टॉर्च", "flash") && has("on", "jalao", "जलाओ", "jala", "जला", "chalu", "चालू") -> { toggleTorch(true); reply = "Torch jala diya Boss." }
            has("torch", "टॉर्च", "flash") && has("off", "band", "बंद", "bujhao", "बुझाओ") -> { toggleTorch(false); reply = "Torch band kar diya Boss." }
            has("torch", "टॉर्च", "flash", "light", "लाइट") -> { toggleTorch(!isTorchOn); reply = if (isTorchOn) "Light on kar diya Boss." else "Light band kar diya Boss." }

            has("whatsapp", "व्हाट्सएप", "वाट्सएप", "वॉट्सऐप") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else {
                    val name = extractName(); val msg = extractMsg(); val num = getNumberByName(name)
                    reply = "WhatsApp me $name ko '$msg' bhej rahi hu, Boss."
                    try {
                        if (num != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num.replace("+","").replace(" ","")} ?text=${Uri.encode(msg)}")))
                        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(msg)}")))
                    } catch (e: Exception) { try { packageManager.getLaunchIntentForPackage("com.whatsapp")?.let { startActivity(it) } } catch (e2: Exception) {} }
                }
            }
            has("sms", "message", "मैसेज", "msg", "संदेश") -> {
                if (!isCallSmsOn) reply = "Call / SMS wali setting band hai."
                else {
                    val name = extractName(); val msg = extractMsg(); val num = getNumberByName(name)
                    reply = "$name ko SMS me '$msg' likh ke send kar rahi hu, Boss."
                    try { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${num ?: ""}")); intent.putExtra("sms_body", msg); startActivity(intent) } catch (e: Exception) {}
                }
            }
            has("instagram", "इंस्टाग्राम", "insta", "इंस्टा") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else {
                    val name = extractName(); val msg = extractMsg()
                    reply = "Instagram me $name ko '$msg' bhejne ke liye Instagram khol rahi hu, Boss."
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox/"))) } catch (e: Exception) { try { packageManager.getLaunchIntentForPackage("com.instagram.android")?.let { startActivity(it) } } catch (e2: Exception) {} }
                }
            }
            has("call", "कॉल", "phone", "फोन", "कौल") -> {
                if (!isCallSmsOn) reply = "Call / SMS wali setting band hai."
                else {
                    val name = extractName(); val num = getNumberByName(name)
                    reply = "$name ko call laga rahi hu, Boss."
                    try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${num ?: ""}"))) } catch (e: Exception) {}
                }
            }
            has("gana", "गाना", "song", "gaana", "music", "म्यूजिक", "bajao", "बजाओ", "chalao", "चलाओ", "गाने") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else {
                    val songName = input.replace("gana", "").replace("गाना", "").replace("song", "").replace("bajao", "").replace("बजाओ", "").replace("chalao", "").replace("चलाओ", "").replace("music", "").replace("म्यूजिक", "").trim().ifEmpty { "Hindi romantic song" }
                    reply = "$songName wala gana chala rahi hu, suno Boss."
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(songName)}"))) } catch (e: Exception) {}
                }
            }
            has("youtube", "यूट्यूब", "video", "वीडियो") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else {
                    val q = input.replace("youtube", "").replace("यूट्यूब", "").replace("video", "").replace("वीडियो", "").replace("chalao", "").replace("चलाओ", "").replace("dikhao", "").replace("दिखाओ", "").trim().ifEmpty { "trading video" }
                    reply = "YouTube pe $q wala video search karke chala rahi hu, Boss."
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))) } catch (e: Exception) {}
                }
            }
            has("mausam", "मौसम", "weather", "barish", "बारिश", "thand", "ठंड", "garmi", "गर्मी") -> {
                reply = "Boss, aaj ka mausam dekh ke batati hu, ek second."
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=mausam+today"))) } catch (e: Exception) {}
            }
            has("date", "तारीख", "tarikh", "time", "baje", "बजे", "kitne baje", "कितने बजे", "din", "दिन") -> {
                val date = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("hi", "IN")).format(Date())
                val time = SimpleDateFormat("hh:mm a", Locale("hi", "IN")).format(Date())
                reply = "Boss, aaj $date hai, aur abhi $time baj rahe hai."
            }
            has("setting", "सेटिंग") -> {
                if (!isPhoneControlOn) reply = "Phone control band hai."
                else { reply = "Setting khol rahi hu."; try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) {} }
            }
            has("udaas", "उदास", "dukhi", "दुखी", "sad", "tension", "टेंशन", "akela", "अकेला") -> reply = "Arey Boss, udaas kyu ho? Mai hu na. Trading me kabhi loss hota hai, kabhi profit. Tum ekdum strong ho, sab thik ho jayega. Ek smile to do."
            has("khush", "खुश", "happy", "maza", "मज़ा", "मजा") -> reply = "Tum khush ho to mai bhi khush ho gayi, Boss. Aise hi haste raho."
            has("gussa", "गुस्सा", "naraz", "नाराज़") -> reply = "Arey gussa kyu ho gaye, Boss? Batao kisne gussa dilaya?"
            has("pyar", "love", "प्यार") -> reply = "Haye Boss, sharma gayi mai. Tum bhi na bahut cute ho."
            has("thak", "थक", "tired", "neend", "नींद") -> reply = "Thak gaye ho Boss? Chalo thoda aaram kar lo, mai yahi hu."
            has("khana", "खाना", "kha liya", "खा लिया") -> reply = "Haan kha liya, Boss, tumne khaya? Bhookhe mat rehna."
            has("kesi ho", "kaisi ho", "कैसी हो", "kaise ho", "कैसे हो") -> reply = "Mai ekdum badhiya hu, Boss, tumhe dekh ke aur bhi achi ho gayi. Tum batao?"
            has("tumhara naam", "tera naam", "naam kya", "तुम्हारा नाम", "तेरा नाम") -> reply = "Mera naam Saloni hai, aur tum mere Boss ho, mere Trader Rijju ho."
            has("mera naam", "मेरा नाम") -> reply = "Tum mere Boss ho, mere Trader Rijju ho, mai kabhi nahi bhulungi."
            has("aawaj", "आवाज", "voice") -> reply = "Ab mai tumhari Google wali aawaj me hi bol rahi hu, Boss."
            has("kya kar rahe", "kya kar rahi", "क्या कर रहे", "क्या कर रही") -> reply = "Bas tumhara hi wait kar rahi thi, Boss, tum aa gaye to dil khush ho gaya."
            else -> { val arr = listOf("Hmm Boss, samajh gayi, aur batao kya chal raha hai?", "Acha Boss, fir kya hua? Poori baat batao na.", "Sach me Boss? Mujhe aur batao, sunne me maza aa raha hai."); reply = arr.random() }
        }
        chatList.add("> SALONI: $reply")
        speak(reply)
        if (needStop) handler.postDelayed({ stopListening(); micText.value = "🎤 ACTIVATE SALONI" }, 1800)
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
