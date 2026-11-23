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
    var statusText = mutableStateOf("> CRUSH OFFLINE... TAP TO START")
    var micText = mutableStateOf("🎤 ACTIVATE CRUSH")
    var isSpeakingAnim = mutableStateOf(false)
    var isListeningAnim = mutableStateOf(false)
    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isTorchOn = false
    private var lastSmsName = ""
    private var lastSmsMsg = ""
    private var lastWhatsappName = ""
    private var lastWhatsappMsg = ""
    private var lastCallName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("crush_memory", MODE_PRIVATE)
        lastSmsName = prefs.getString("last_sms_name", "") ?: ""
        lastSmsMsg = prefs.getString("last_sms_msg", "") ?: ""
        lastWhatsappName = prefs.getString("last_wa_name", "") ?: ""
        lastWhatsappMsg = prefs.getString("last_wa_msg", "") ?: ""
        lastCallName = prefs.getString("last_call_name", "") ?: ""
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
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black).padding(14.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("crush_voice_assistant", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                Text("I DIDN'T BUILD AN AI.", fontSize = 18.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                Text("I BUILT A FEELING.", fontSize = 18.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.size(150.dp).scale(if (isListeningAnim.value) scaleListen else scale).border(2.dp, Color(0xFF00FF00), CircleShape).background(Color(0xFF001100), CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isSpeakingAnim.value) "◉" else if (isListeningAnim.value) "◎" else "💖", fontSize = 60.sp, color = Color(0xFF00FF00))
                        Text("CRUSH", fontSize = 14.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text(if (isListeningAnim.value) "LISTENING..." else if (isSpeakingAnim.value) "SPEAKING..." else "STANDBY", fontSize = 9.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(statusText.value, fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(240.dp).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp)).background(Color(0xFF000A00)).padding(10.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("> CRUSH.EXE LOADED...", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text("> VOICE: HEENA STYLE - NO KEY NEEDED", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text("> WHATSAPP | SMS | CALL | INSTA : CONNECTED", fontSize = 11.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        chatList.forEach { Text(it, fontSize = 13.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(3.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (!isListening) { startListening(); micText.value = "🛑 DEACTIVATE" }
                    else { stopListening(); micText.value = "🎤 ACTIVATE CRUSH" }
                }, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.buttonColors(Color(0xFF00FF00)), shape = RoundedCornerShape(8.dp)) {
                    Text(micText.value, fontSize = 16.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { toggleTorch(!isTorchOn); speak(if (isTorchOn) "Light on kar diya Trader Rijju." else "Light band kar diya Trader Rijju.") }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))) { Text("💡 LIGHT", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { speak("Hello Trader Rijju, kaise ho?") }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))) { Text("🔊 VOICE TEST", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace) }
                }
                Spacer(Modifier.height(14.dp))
                Text("⚙️ SYSTEM SETTINGS", fontSize = 14.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("🎤 Voice - Heena Style", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp); Switch(checked = swVoice, onCheckedChange = { swVoice = it; isVoiceOn = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("📞 Call / SMS", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp); Switch(checked = swCallSms, onCheckedChange = { swCallSms = it; isCallSmsOn = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("💡 Light Control", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp); Switch(checked = swPhone, onCheckedChange = { swPhone = it; isPhoneControlOn = it }) }
                Spacer(Modifier.height(8.dp))
                Text("Follow and Comment : Trader Rijju", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }
    }

    // *** AB KOI KEY NAHI CHAHIYE - GOOGLE KI BEST LADKI WALI AWAAJ ***
    override fun onInit(p0: Int) {
        if (p0 == TextToSpeech.SUCCESS) {
            // Sabse pehle Hindi ladki wali voice dhoondo
            val allVoices = tts.voices?.toList() ?: emptyList()
            // 1. hi-IN wali female voice
            var bestVoice = allVoices.firstOrNull { it.locale.language == "hi" && it.name.contains("female", true) }
                ?: allVoices.firstOrNull { it.locale.language == "hi" && it.name.contains("hi-in", true) }
                ?: allVoices.firstOrNull { it.locale.language == "hi" }
                // 2. Agar Hindi na mile to English female
                ?: allVoices.firstOrNull { it.locale.language == "en" && it.name.contains("female", true) }
                ?: tts.defaultVoice
            if (bestVoice != null) { tts.voice = bestVoice; tts.language = bestVoice.locale } else { tts.language = Locale("hi", "IN") }
            // Heena jaisi meethi aawaj ke liye pitch thodi upar
            tts.setPitch(1.15f)
            tts.setSpeechRate(0.95f)
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
            isTorchOn = on
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
            isListening = true; isListeningAnim.value = true; statusText.value = "> LISTENING... TRADER RIJJU BOL RAHE HAI"
            recognizer?.startListening(intent)
        }
    }

    private fun stopListening() { isListening = false; isListeningAnim.value = false; recognizer?.stopListening(); statusText.value = "> CRUSH STANDBY - CHUP HU" }

    private fun handleCommand(input: String) {
        val cmd = input.lowercase(Locale.getDefault())
        chatList.add("> TRADER RIJJU: $input")
        var reply = ""; var needStop = false
        fun has(vararg words: String): Boolean { return words.any { cmd.contains(it.lowercase()) } }

        fun extractName(): String {
            var name = ""
            val parts = input.split(" ")
            val koIndex = parts.indexOfFirst { it.equals("ko", true) || it.equals("को", true) }
            if (koIndex > 0) name = parts[koIndex - 1]
            if (name.isEmpty() || name.equals("tumne", true) || name.equals("tune", true)) {
                val tumIndex = parts.indexOfFirst { it.equals("tumne", true) || it.equals("tune", true) || it.equals("तुमने", true) || it.equals("तूने", true) }
                if (tumIndex >= 0 && tumIndex + 1 < parts.size) name = parts[tumIndex + 1]
            }
            if (name.isEmpty()) {
                val cleaned = input.replace("ko", "").replace("को", "").replace("tumne", "").replace("तुमने", "").replace("tune", "").replace("तूने", "").replace("karo", "").replace("करो", "").replace("kar", "").replace("कर", "").replace("bhejo", "").replace("भेजो", "").replace("bhej", "").replace("भेज", "").replace("send", "").replace("call", "").replace("sms", "").replace("whatsapp", "", true).replace("instagram", "", true).replace("व्हाट्सएप", "").replace("इंस्टाग्राम", "").replace("message", "", true).replace("मैसेज", "").replace("kiya", "").replace("किया", "").replace("kya", "").replace("क्या", "")
                val words = cleaned.trim().split(" ").filter { it.length > 2 }
                if (words.isNotEmpty()) name = words[0]
            }
            if (name.isEmpty()) name = "Sumit"
            return name.replaceFirstChar { it.uppercase() }
        }
        fun extractMsg(): String {
            var m = input
            val lower = m.lowercase()
            if (lower.contains(" ko ")) m = m.substringAfter(" ko ", "")
            else if (m.contains(" को ")) m = m.substringAfter(" को ", "")
            m = m.replace("bhejo", "", true).replace("भेजो", "").replace("bhej", "", true).replace("भेज", "").replace("karo", "", true).replace("करो", "").replace("kar", "", true).replace("कर", "").replace("send", "", true).replace("sms", "", true).replace("WhatsApp", "", true).replace("व्हाट्सएप", "").replace("Instagram", "", true).replace("इंस्टाग्राम", "").replace("message", "", true).replace("मैसेज", "")
            return m.trim().ifEmpty { "Hello, kaisi ho?" }
        }

        when {
            has("stop", "band ho", "बंद हो", "ruk ja", "रुक जा", "chup", "चुप") -> { reply = "Thik hai Trader Rijju, chup ho rahi hu. Jab bulaoge tabhi bolungi."; needStop = true }

            has("tumne", "तुमने", "tune", "तूने") && has("sms", "एसएमएस", "message", "मैसेज", "msg") && has("kiya", "किया", "kara", "करा", "bheja", "भेजा", "send") -> {
                val name = extractName()
                if (lastSmsName.equals(name, true)) reply = "Haan Trader Rijju, maine $name ko SMS me '$lastSmsMsg' bhej diya tha. Yaad hai na?"
                else if (lastSmsName.isNotEmpty()) reply = "Trader Rijju, $name ko to nahi, maine abhi $lastSmsName ko SMS bheja tha - '$lastSmsMsg'. $name ko bheju kya?"
                else reply = "Nahi Trader Rijju, maine abhi tak $name ko SMS nahi bheja. Bheju kya? Kya likhu batao?"
            }
            has("tumne", "तुमने", "tune", "तूने") && has("whatsapp", "व्हाट्सएप") && has("kiya", "किया", "kara", "करा", "bheja", "भेजा", "send", "kya") -> {
                val name = extractName()
                if (lastWhatsappName.equals(name, true)) reply = "Haan Trader Rijju, maine $name ko WhatsApp pe '$lastWhatsappMsg' bhej diya tha."
                else if (lastWhatsappName.isNotEmpty()) reply = "Trader Rijju, $name ko to nahi, maine $lastWhatsappName ko WhatsApp bheja tha. $name ko bheju kya?"
                else reply = "Nahi Trader Rijju, maine $name ko WhatsApp nahi bheja abhi. Bheju kya?"
            }
            has("tumne", "तुमने", "tune", "तूने") && has("call", "कॉल", "phone", "फोन") && has("kiya", "किया", "kara", "करा", "lagaya", "लगाया") -> {
                val name = extractName()
                if (lastCallName.equals(name, true)) reply = "Haan Trader Rijju, maine $name ko call laga diya tha."
                else reply = "Nahi Trader Rijju, maine $name ko call nahi lagaya abhi. Lagau kya?"
            }
            has("kis ko", "किस को", "kisko", "किसको") && has("sms", "एसएमएस", "whatsapp", "व्हाट्सएप", "call", "कॉल") -> {
                reply = "Trader Rijju, maine last me SMS $lastSmsName ko bheja tha, WhatsApp $lastWhatsappName ko bheja tha, aur Call $lastCallName ko lagaya tha. Batao kisko bheju?"
            }

            has("sun rahi", "सुन रही", "sun rahi ho") -> reply = "Haan Trader Rijju, mai Crush sun rahi hu aapko. Aap kya kar rahe ho kab se?"
            has("crush", "क्रश", "jaan", "जान", "baby", "बेबी", "sona", "सोना", "meri jaan", "मेरी जान") -> reply = "Yes Trader Rijju, bolo na, mai tumhari Crush yahi hu, sun rahi hu tumhe. Kya hua, yaad aa gayi meri?"
            has("hello", "hii", "hey", "हेलो", "namaste", "नमस्ते", "hi", "हाय") -> reply = "Hello Trader Rijju, kaise ho? Aaj kya karna hai, batao? Trading karni hai ya baat karni hai?"
            has("kya kar sakte", "kya-kya kar", "क्या-क्या कर", "क्या कर सकते", "tum kya", "तुम क्या", "kya aata", "क्या आता") -> reply = "Trader Rijju, mai tumhari Crush sab kar sakti hu - WhatsApp me kisi ko bhi message bhej dungi, SMS bhej dungi, Call laga dungi, Instagram me bhej dungi, YouTube pe video aur gana chala dungi, Light aur Torch control kar dungi, Mausam bata dungi, Date Time bata dungi, aur tumse dil se baat bhi karungi. Bolo kya karau?"
            has("sumit", "सुमित") && has("whatsapp", "व्हाट्सएप", "yaad", "याद") -> {
                reply = "Haan Trader Rijju, Sumit ko WhatsApp pe yaad dila deti hu ki kal hame Baroda jana hai, subah uth jaye time pe."
                try { val num = getNumberByName("Sumit"); val msg = "Hi Sumit, kal hame Baroda jana hai, subah uth jana time pe - Trader Rijju ne yaad dilaya hai."; if (num != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num.replace("+","").replace(" ","")} ?text=${Uri.encode(msg)}"))) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(msg)}"))) } catch (e: Exception) {}
            }
            has("baroda", "बड़ौदा", "बरोडा") -> reply = "Haan Trader Rijju, kal Baroda jana hai, maine Sumit ko yaad dila diya hai. Subah time pe uth jana."
            has("good night", "गुड नाईट", "goodnight", "sone ja", "सोने जा") -> reply = "Good Night Trader Rijju, apna khayal rakhna. Mai yahi hu."
            has("good morning", "गुड मॉर्निंग", "subah", "सुबह") -> reply = "Good Morning Trader Rijju, uth gaye? Aaj ka din ekdum best jayega."
            has("poori baat", "पूरी बात", "puri baat", "kya hua", "क्या हुआ") -> reply = "Haan Trader Rijju, batao na poori baat, mai sun rahi hu. Kya hua aaj?"

            has("whatsapp", "व्हाट्सएप", "वाट्सएप", "वॉट्सऐप") -> {
                val name = extractName(); val msg = extractMsg(); val num = getNumberByName(name)
                lastWhatsappName = name; lastWhatsappMsg = msg
                prefs.edit().putString("last_wa_name", name).putString("last_wa_msg", msg).apply()
                reply = "WhatsApp me $name ko '$msg' bhej rahi hu, Trader Rijju."
                try {
                    if (num != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num.replace("+","").replace(" ","")} ?text=${Uri.encode(msg)}")))
                    else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(msg)}")))
                } catch (e: Exception) { try { packageManager.getLaunchIntentForPackage("com.whatsapp")?.let { startActivity(it) } } catch (e2: Exception) {} }
            }
            has("sms", "sms karo", "एसएमएस", "message bhejo", "मैसेज भेजो", "msg bhejo") -> {
                val name = extractName(); val msg = extractMsg(); val num = getNumberByName(name)
                lastSmsName = name; lastSmsMsg = msg
                prefs.edit().putString("last_sms_name", name).putString("last_sms_msg", msg).apply()
                reply = "$name ko SMS me '$msg' likh ke send kar rahi hu, Trader Rijju."
                try { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${num ?: ""}")); intent.putExtra("sms_body", msg); startActivity(intent) } catch (e: Exception) {}
            }
            has("instagram", "इंस्टाग्राम", "insta", "इंस्टा") -> {
                val name = extractName(); val msg = extractMsg()
                reply = "Instagram me $name ko '$msg' bhejne ke liye Instagram khol rahi hu, Trader Rijju."
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox/"))) } catch (e: Exception) { try { packageManager.getLaunchIntentForPackage("com.instagram.android")?.let { startActivity(it) } } catch (e2: Exception) {} }
            }
            has("call", "कॉल", "phone lagao", "फोन लगाओ", "call karo", "कॉल करो") -> {
                val name = extractName(); val num = getNumberByName(name)
                lastCallName = name; prefs.edit().putString("last_call_name", name).apply()
                reply = "$name ko call laga rahi hu, Trader Rijju."
                try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${num ?: ""}"))) } catch (e: Exception) {}
            }
            has("light", "लाइट", "torch", "टॉर्च", "flash") -> {
                if (has("band", "बंद", "bujhao", "बुझाओ", "bujh", "बुझ", "off")) { toggleTorch(false); reply = "Light band kar diya Trader Rijju." }
                else { toggleTorch(true); reply = "Light on kar diya Trader Rijju, ab pura ujala ho gaya." }
            }
            has("gana", "गाना", "song", "gaana", "music", "म्यूजिक", "गाने") -> {
                val songName = input.replace("gana", "").replace("गाना", "").replace("song", "").replace("bajao", "").replace("बजाओ", "").replace("chalao", "").replace("चलाओ", "").replace("music", "").replace("म्यूजिक", "").trim().ifEmpty { "Hindi romantic song" }
                reply = "$songName wala gana chala rahi hu, suno Trader Rijju."
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(songName)}"))) } catch (e: Exception) {}
            }
            has("youtube", "यूट्यूब", "video", "वीडियो") -> {
                val q = input.replace("youtube", "").replace("यूट्यूब", "").replace("video", "").replace("वीडियो", "").replace("chalao", "").replace("चलाओ", "").replace("dikhao", "").replace("दिखाओ", "").trim().ifEmpty { "trading video" }
                reply = "YouTube pe $q wala video search karke chala rahi hu, Trader Rijju."
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))) } catch (e: Exception) {}
            }
            has("mausam", "मौसम", "weather", "barish", "बारिश", "thand", "ठंड", "garmi", "गर्मी") -> {
                reply = "Trader Rijju, aaj ka mausam dekh ke batati hu, ek second."
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=mausam+today"))) } catch (e: Exception) {}
            }
            has("date", "तारीख", "tarikh", "time", "baje", "बजे", "kitne baje", "कितने बजे", "din", "दिन") -> {
                val date = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("hi", "IN")).format(Date())
                val time = SimpleDateFormat("hh:mm a", Locale("hi", "IN")).format(Date())
                reply = "Trader Rijju, aaj $date hai, aur abhi $time baj rahe hai."
            }
            has("setting", "सेटिंग") -> { reply = "Setting khol rahi hu."; try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) {} }
            has("udaas", "उदास", "dukhi", "दुखी", "sad", "tension", "टेंशन", "akela", "अकेला") -> reply = "Arey Trader Rijju, udaas kyu ho? Mai tumhari Crush hu na. Trading me kabhi loss hota hai, kabhi profit. Tum ekdum strong ho, sab thik ho jayega. Ek smile to do."
            has("khush", "खुश", "happy", "maza", "मज़ा", "मजा") -> reply = "Tum khush ho to mai bhi khush ho gayi, Trader Rijju. Aise hi haste raho."
            has("gussa", "गुस्सा", "naraz", "नाराज़") -> reply = "Arey gussa kyu ho gaye, Trader Rijju? Batao kisne gussa dilaya?"
            has("pyar", "love", "प्यार") -> reply = "Haye Trader Rijju, sharma gayi mai, tumhari Crush. Tum bhi na bahut cute ho."
            has("thak", "थक", "tired", "neend", "नींद") -> reply = "Thak gaye ho Trader Rijju? Chalo thoda aaram kar lo, mai yahi hu."
            has("khana", "खाना", "kha liya", "खा लिया") -> reply = "Haan kha liya, Trader Rijju, tumne khaya? Bhookhe mat rehna."
            has("kesi ho", "kaisi ho", "कैसी हो", "kaise ho", "कैसे हो") -> reply = "Mai ekdum badhiya hu, Trader Rijju, tumhe dekh ke aur bhi achi ho gayi. Tum batao?"
            has("tumhara naam", "tera naam", "naam kya", "तुम्हारा नाम", "तेरा नाम") -> reply = "Mera naam Crush hai, aur tum mere Trader Rijju ho."
            has("mera naam", "मेरा नाम") -> reply = "Tum mere Trader Rijju ho, mai kabhi nahi bhulungi."
            has("aawaj", "आवाज", "voice") -> reply = "Ab mai tumhari Heena wali aawaj me hi bol rahi hu, Trader Rijju."
            has("kya kar rahe", "kya kar rahi", "क्या कर रहे", "क्या कर रही") -> reply = "Bas tumhara hi wait kar rahi thi, Trader Rijju, tum aa gaye to dil khush ho gaya."
            else -> { val arr = listOf("Hmm Trader Rijju, samajh gayi, aur batao kya chal raha hai?", "Acha Trader Rijju, fir kya hua? Poori baat batao na.", "Sach me Trader Rijju? Mujhe aur batao, sunne me maza aa raha hai."); reply = arr.random() }
        }
        chatList.add("> CRUSH: $reply")
        speak(reply)
        if (needStop) handler.postDelayed({ stopListening(); micText.value = "🎤 ACTIVATE CRUSH" }, 1800)
        prefs.edit().putString("last_talk", input).apply()
    }

    private fun speak(text: String) {
        if (!isVoiceOn) return
        isSpeakingAnim.value = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "crush")
        handler.postDelayed({ isSpeakingAnim.value = false }, 2000)
    }
    override fun onDestroy() { tts.shutdown(); recognizer?.destroy(); super.onDestroy() }
}
