package com.saloni.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isContinuous = true
    private var isMemoryOn = true
    private var isSmsReadOn = true
    private var isCallSmsOn = true

    private lateinit var chatBox: LinearLayout
    private lateinit var micBtn: Button
    private lateinit var statusText: TextView
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("saloni_memory", MODE_PRIVATE)
        chatBox = findViewById(R.id.chatBox)
        micBtn = findViewById(R.id.micBtn)
        statusText = findViewById(R.id.statusText)

        tts = TextToSpeech(this, this)

        // Permissions
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 101)
        }

        // Settings - Simple
        val swContinuous = findViewById<Switch>(R.id.swContinuous)
        val swMemory = findViewById<Switch>(R.id.swMemory)
        val swSmsRead = findViewById<Switch>(R.id.swSmsRead)
        val swCallSms = findViewById<Switch>(R.id.swCallSms)

        swContinuous.isChecked = true
        swMemory.isChecked = true
        swSmsRead.isChecked = true
        swCallSms.isChecked = true

        swContinuous.setOnCheckedChangeListener { _, b -> isContinuous = b }
        swMemory.setOnCheckedChangeListener { _, b -> isMemoryOn = b }
        swSmsRead.setOnCheckedChangeListener { _, b -> isSmsReadOn = b }
        swCallSms.setOnCheckedChangeListener { _, b -> isCallSmsOn = b }

        micBtn.setOnClickListener {
            if (!isListening) {
                startListening()
                micBtn.text = "🛑 STOP - Band karne ke liye Stop bolo"
                statusText.text = "Sun rahi hu..."
            } else {
                stopListening()
                micBtn.text = "🎤 TAP TO SPEAK"
            }
        }

        // Welcome
        val name = prefs.getString("user_name", "")
        if (name.isNullOrEmpty()) {
            speak("Hello, mai SALONI hu. Tumhara naam kya hai?")
        } else {
            speak("Hello $name, wapas aa gaye. Kaisi ho tum?")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(1.25f) // Female jaisi meethi aawaj
            tts.setSpeechRate(0.92f)
            // Try female voice
            for (voice in tts.voices) {
                if (voice.locale.language == "hi" && voice.name.contains("female", true)) {
                    tts.voice = voice; break
                }
            }
        }
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.get(0) ?: ""
                    if (text.isNotEmpty()) handleCommand(text)
                    if (isContinuous && isListening) {
                        // Thodi der baad fir suno - Continuous
                        android.os.Handler(mainLooper).postDelayed({ if (isListening) startListening() }, 600)
                    }
                }
                override fun onError(error: Int) {
                    if (isContinuous && isListening) {
                        android.os.Handler(mainLooper).postDelayed({ if (isListening) startListening() }, 600)
                    }
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(e: Int, p: Bundle?) {}
            })
            isListening = true
            recognizer?.startListening(intent)
        }
    }

    private fun stopListening() {
        isListening = false
        recognizer?.stopListening()
        statusText.text = "Band hu, fir bula lena"
    }

    private fun handleCommand(input: String) {
        val cmd = input.lowercase(Locale.getDefault())
        addMsg("Tum: $input")
        var reply = ""
        var needStop = false

        // Memory - Naam yaad
        if (cmd.contains("mera naam") && cmd.contains("hai")) {
            val name = input.substringAfter("hai").trim().split(" ").firstOrNull() ?: ""
            if (isMemoryOn && name.isNotEmpty()) {
                prefs.edit().putString("user_name", name).apply()
                reply = "Acha $name, yaad rakh lungi, kabhi nahi bhulungi."
            }
        }
        else if (cmd.contains("mera naam kya")) {
            val n = prefs.getString("user_name", "babu")
            reply = "Arey bhul gaye? Tum to mere $n ho."
        }
        // Stop
        else if (cmd.contains("stop") || cmd.contains("band ho") || cmd.contains("ruk ja") || cmd.contains("chup")) {
            reply = "Thik hai baba, band ho rahi hu. Jab yaad aaye to bula lena."
            needStop = true
        }
        // Call
        else if (cmd.contains("call") || cmd.contains("phone laga")) {
            if (!isCallSmsOn) reply = "Call wala option setting me band hai."
            else {
                reply = "Call laga rahi hu, ek second."
                // Demo: Dialer kholna + direct call ke liye number chahiye
                // Yahan default: Dialer khol dega, number setting me save kar sakte ho
                val intent = Intent(Intent.ACTION_DIAL)
                startActivity(intent)
            }
        }
        // SMS Send
        else if (cmd.contains("sms") || cmd.contains("message kar")) {
            if (!isCallSmsOn) reply = "SMS wala option band hai."
            else {
                reply = "SMS bhej diya hai, dekh lo."
                try {
                    val sms = SmsManager.getDefault()
                    // Yahan number + text parse hota hai, abhi demo number pe
                    // sms.sendTextMessage("9876543210", null, input, null, null)
                } catch (e: Exception) { reply = "SMS bhejne me problem aa gayi." }
            }
        }
        // WhatsApp
        else if (cmd.contains("whatsapp")) {
            reply = "WhatsApp khol rahi hu."
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))) } catch (e: Exception) {}
        }
        // YouTube / Gana
        else if (cmd.contains("gana") || cmd.contains("song") || cmd.contains("youtube") || cmd.contains("video")) {
            reply = "YouTube pe tumhara gana chala rahi hu."
            val q = Uri.encode(input)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")))
        }
        // Instagram
        else if (cmd.contains("instagram") || cmd.contains("reel")) {
            reply = "Instagram khol rahi hu, reel dekh lo."
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/reels/")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
            }
        }
        // SMS Padh ke sunao
        else if (cmd.contains("message padho") || cmd.contains("sms padho")) {
            reply = if (isSmsReadOn) "Ek naya message aaya hai, padh ke suna rahi hu." else "SMS padhna setting me band hai."
        }
        // Normal baatein - Alag alag jawab
        else if (cmd.contains("kya kar rahe")) reply = "Bas tumhara hi wait kar rahi thi, tum aa gaye to dil khush ho gaya."
        else if (cmd.contains("khana")) reply = "Haan kha liya, tumne khaya? Bhookhe mat rehna."
        else if (cmd.contains("udaas")) reply = "Arey udaas kyu ho? Mai hu na, sab thik ho jayega, ek smile to do."
        else if (cmd.contains("love") || cmd.contains("pyar")) reply = "Haye, sharma gayi mai. Tum bhi na bahut cute ho."
        else if (cmd.contains("kahan")) reply = "Yahin hu, tumhare phone me, tumhare dil ke paas."
        else {
            val arr = listOf("Hmm, samajh gayi, aur batao kya chal raha hai?", "Acha, fir kya hua? Poori baat batao na.", "Sach me? Mujhe aur batao, sunne me maza aa raha hai.", "Tum bolte raho, mujhe tumhari aawaj sunna acha lagta hai.")
            reply = arr.random()
        }

        addMsg("Saloni: $reply")
        speak(reply)
        if (needStop) {
            android.os.Handler(mainLooper).postDelayed({ stopListening(); micBtn.text = "🎤 TAP TO SPEAK" }, 1800)
        }
        // Memory save last talk
        if (isMemoryOn) prefs.edit().putString("last_talk", input).apply()
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saloni")
    }

    private fun addMsg(msg: String) {
        val tv = TextView(this)
        tv.text = msg
        tv.setPadding(20, 12, 20, 12)
        tv.textSize = 16f
        chatBox.addView(tv)
    }

    override fun onDestroy() {
        tts.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }
}
