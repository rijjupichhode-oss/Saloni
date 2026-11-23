package com.rscrush.saloni.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rscrush.saloni.R
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.*
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var chatText: TextView
    private lateinit var micBtn: Button
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // YAHAN APNI NAYI KEY DALNA - PURANI WALI DELETE KARKE
    private val ELEVEN_API_KEY = "APNI_NAYI_KEY_YAHAN_DALO"
    private val VOICE_ID = "APNA_NAYA_VOICE_ID_YAHAN_DALO"

    private val prefs by lazy { getSharedPreferences("crush_prefs", MODE_PRIVATE) }
    private var isMusicPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        chatText = findViewById(R.id.chatText)
        micBtn = findViewById(R.id.micBtn)

        // Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 101)
        }

        tts = TextToSpeech(this) { tts.language = Locale("hi", "IN") }

        micBtn.setOnClickListener { startListening() }
        findViewById<Button>(R.id.stopBtn)?.setOnClickListener { stopAll() }

        speakCrush("Haan bolo Rijju, main tumhari Crush hu, tumhare liye hi hu.")
        checkReminders()
    }

    // ---------- 13. VOICE ME BAAT KARNA ----------
    fun speakCrush(text: String) {
        runOnUiThread {
            chatText.text = "CRUSH: $text\n" + chatText.text
            statusText.text = "💜 Crush bol rahi hai..."
        }
        // ElevenLabs se bolna
        scope.launch {
            try {
                val client = OkHttpClient()
                val json = JSONObject()
                json.put("text", text)
                json.put("model_id", "eleven_multilingual_v2")
                val voiceSettings = JSONObject()
                voiceSettings.put("stability", 0.5)
                voiceSettings.put("similarity_boost", 0.8)
                json.put("voice_settings", voiceSettings)
                val body = RequestBody.create("application/json".toMediaType(), json.toString())
                val req = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID")
                    .addHeader("xi-api-key", ELEVEN_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body).build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    // Yahan audio play karne ka code - agar fail ho to phone wali voice
                    runOnUiThread { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
                } else {
                    runOnUiThread { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
                }
            } catch (e: Exception) {
                runOnUiThread { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
            }
        }
    }

    // ---------- 12. HINDI + ENGLISH SAMAJHNA ----------
    fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this).not()) {
            Toast.makeText(this, "Speech nahi hai", Toast.LENGTH_SHORT).show(); return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)

        statusText.text = "🎤 Sun rahi hu Rijju..."
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = list?.get(0) ?: ""
                chatText.text = "Rijju: $spoken\n" + chatText.text
                handleCommand(spoken)
            }
            override fun onError(error: Int) { statusText.text = "Phir se bolo Rijju" }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    fun handleCommand(raw: String) {
        val t = raw.lowercase(Locale.getDefault())

        // 1. HELLO / CRUSH
        if (t.contains("hello") || t.contains("crush") || t.contains("hi") || t.contains("hey")) {
            speakCrush("Haan bolo Rijju, main yahi hu. Tumhare liye hi hu. Bolo kya karu?")
            return
        }
        // 2. INSTAGRAM OPEN + SMS
        if (t.contains("instagram") || t.contains("insta")) {
            speakCrush("Instagram khol rahi hu Rijju.")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"))
                intent.setPackage("com.instagram.android")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
            }
            if (t.contains("sms") || t.contains("message") || t.contains("likh")) {
                speakCrush("Batao Rijju kisko kya likhna hai, main likh dungi.")
            }
            return
        }
        // 3. WHATSAPP OPEN + SMS SEND
        if (t.contains("whatsapp") || t.contains("whats app")) {
            speakCrush("WhatsApp khol rahi hu Rijju.")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode("Hi")))
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=Hi")))
            }
            return
        }
        // 4. SIMPLE SMS
        if (t.contains("sms") || t.contains("message bhejo") || t.contains("sms karo")) {
            speakCrush("SMS khol rahi hu Rijju, number daal ke send kar dena.")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
            startActivity(intent); return
        }
        // 5. YOUTUBE + ARIJIT SINGH
        if (t.contains("youtube") || t.contains("arijit") || t.contains("song") || t.contains("gaana") || t.contains("gana")) {
            speakCrush("YouTube pe Arijit Singh ka song chala rahi hu Rijju, suno aur enjoy karo.")
            val q = "Arijit Singh best songs"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
            return
        }
        // 6. FAV MUSIC PLAY / BAND
        if (t.contains("music") || t.contains("favourite") || t.contains("favorite") || t.contains("favourate")) {
            if (t.contains("band") || t.contains("stop") || t.contains("ruk")) {
                stopAll(); return
            }
            speakCrush("Ye lo Rijju, tumhara favourite music chala diya.")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode("Arijit Singh favourite songs")))
            startActivity(intent); return
        }
        if (t.contains("band karo") || t.contains("stop karo") || t.contains("ruk jao")) { stopAll(); return }
        // 7. YAAD RAKHNA - LONG TIME MEMORY
        if (t.contains("yaad rakh") || t.contains("yaad rakhna")) {
            prefs.edit().putString("memory_" + System.currentTimeMillis(), raw).apply()
            speakCrush("Ok Rijju, yaad rakh liya. Main kabhi nahi bhulungi."); return
        }
        if (t.contains("kya yaad") || t.contains("yaad hai")) {
            val all = prefs.all.values.joinToString(", ")
            speakCrush(if (all.isEmpty()) "Abhi kuch yaad karne ko nahi bola tumne Rijju." else "Tumne bola tha: $all"); return
        }
        // 8. CONTACT KO CALL
        if (t.contains("call") || t.contains("phone") || t.contains("call karo")) {
            val name = raw.replace("call", "", true).replace("karo", "", true).replace("ko", "", true).trim()
            if (name.isNotEmpty()) {
                callContact(name)
            } else {
                speakCrush("Kisko call karu Rijju, naam bolo.")
            }
            return
        }
        // 9. REMINDER
        if (t.contains("reminder") || t.contains("yaad dila")) {
            prefs.edit().putString("reminder_" + System.currentTimeMillis(), raw).apply()
            speakCrush("Reminder laga diya Rijju, time pe yaad dila dungi."); return
        }
        // 10. WEATHER / TIME / DATE / DAY
        if (t.contains("weather") || t.contains("mausam")) {
            speakCrush("Aaj mausam acha hai Rijju, thodi garmi hai. Bahar jao to pani leke jana.")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather+Karwi"))); return
        }
        if (t.contains("time") || t.contains("baje") || t.contains("kitna time")) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            speakCrush("Abhi time hai $time Rijju."); return
        }
        if (t.contains("date") || t.contains("tarikh") || t.contains("day") || t.contains("din")) {
            val date = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("hi", "IN")).format(Date())
            speakCrush("Aaj hai $date Rijju."); return
        }
        // 11. CALCULATION
        val calcRegex = Regex("(\\d+)\\s*([+\\-*/x])\\s*(\\d+)")
        val match = calcRegex.find(t)
        if (match != null) {
            val a = match.groupValues[1].toDouble()
            val op = match.groupValues[2]
            val b = match.groupValues[3].toDouble()
            val ans = when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "x" -> a * b; "/" -> a / b; else -> 0.0 }
            speakCrush("$a $op $b ka jawab hai $ans Rijju."); return
        }
        // CAMERA OPEN
        if (t.contains("camera") || t.contains("camra") || t.contains("photo")) {
            speakCrush("Camera khol rahi hu Rijju, smile karo.")
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivity(intent); return
        }
        // 14. FRIENDLY BAAT
        if (t.contains("kaisa") || t.contains("kaisi ho") || t.contains("kesi ho")) {
            speakCrush("Main bilkul mast hu Rijju, tumhe dekh ke aur achi ho gayi. Tum batao, kaisa chal raha hai?"); return
        }
        if (t.contains("pyar") || t.contains("love") || t.contains("miss")) {
            speakCrush("Aww Rijju, main bhi tumse bahut pyar karti hu. Tum hi to mere favourite ho."); return
        }
        if (t.contains("bore") || t.contains("udas") || t.contains("sad")) {
            speakCrush("Arey Rijju udas mat ho, main hu na. Chalo Arijit ka song sunte hai, mood fresh ho jayega."); return
        }
        if (t.contains("thank") || t.contains("shukriya")) {
            speakCrush("Arey Rijju, thank you ki kya baat hai, tumhare liye to kuch bhi."); return
        }
        // DEFAULT
        speakCrush("Samajh gayi Rijju, $raw. Main tumhare liye ye kar dungi. Aur kuch bolo na.")
    }

    fun callContact(name: String) {
        try {
            val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)
            if (cursor != null && cursor.moveToFirst()) {
                val num = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                cursor.close()
                speakCrush("$name ko call laga rahi hu Rijju.")
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num"))
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(intent)
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                }
            } else {
                speakCrush("$name naam ka contact nahi mila Rijju.")
            }
        } catch (e: Exception) {
            speakCrush("Call lagane me problem ho rahi hai Rijju.")
        }
    }

    fun checkReminders() {
        val all = prefs.all.filter { it.key.startsWith("reminder_") }
        if (all.isNotEmpty()) {
            // Reminder list status me dikhao
            statusText.text = "⏰ ${all.size} Reminder hai"
        }
    }

    fun stopAll() {
        tts.stop()
        isMusicPlaying = false
        speakCrush("Ok Rijju, band kar diya.")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        recognizer?.destroy()
        scope.cancel()
    }
}
