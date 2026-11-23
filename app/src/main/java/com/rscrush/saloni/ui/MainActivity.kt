package com.rscrush.saloni.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var isContinuous = true
    var isMemoryOn = true
    var isSmsReadOn = true
    var isCallSmsOn = true
    var chatList = mutableStateListOf<String>()
    var statusText = mutableStateOf("Band hu, TAP karo")
    var micText = mutableStateOf("🎤 TAP TO SPEAK")

    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("saloni_memory", MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 101)
        }

        setContent {
            var swCont by remember { mutableStateOf(true) }
            var swMem by remember { mutableStateOf(true) }
            var swSms by remember { mutableStateOf(true) }
            var swCall by remember { mutableStateOf(true) }

            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFFFFF0F5)).padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("❤️ SALONI ❤️", fontSize = 30.sp, color = Color(0xFFE91E63))
                Text(statusText.value, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))

                Card(modifier = Modifier.fillMaxWidth().height(300.dp), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
                        chatList.forEach { Text(it, fontSize = 15.sp, modifier = Modifier.padding(4.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Button(onClick = {
                    if (!isListening) {
                        startListening()
                        micText.value = "🛑 STOP - Band karne ke liye Stop bolo"
                    } else {
                        stopListening()
                        micText.value = "🎤 TAP TO SPEAK"
                    }
                }, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(Color(0xFFE91E63))) {
                    Text(micText.value, fontSize = 18.sp, color = Color.White)
                }

                Spacer(Modifier.height(16.dp))
                Text("⚙️ SETTING - Sirf Zaroori Wali", fontSize = 18.sp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Continuous Baat"); Switch(checked = swCont, onCheckedChange = { swCont = it; isContinuous = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Memory Yaad Rakhe"); Switch(checked = swMem, onCheckedChange = { swMem = it; isMemoryOn = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SMS Aate hi Padh ke Sunao"); Switch(checked = swSms, onCheckedChange = { swSms = it; isSmsReadOn = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Call / SMS Karne Do"); Switch(checked = swCall, onCheckedChange = { swCall = it; isCallSmsOn = it })
                }
                Spacer(Modifier.height(10.dp))
                Text("Baaki sab - WhatsApp, YouTube, Instagram hamesha ON hai.", fontSize = 13.sp, color = Color.Gray)
            }
        }

        val name = prefs.getString("user_name", "")
        if (!name.isNullOrEmpty()) {
            chatList.add("Saloni: Hello $name, wapas aa gaye ❤️")
        }
    }

    override fun onInit(p0: Int) {
        if (p0 == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(1.25f)
            tts.setSpeechRate(0.92f)
            speak("Hello, mai SALONI hu. Tumhara naam kya hai?")
        }
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
                    if (isContinuous && isListening) {
                        handler.postDelayed({ if (isListening) startListening() }, 600)
                    }
                }
                override fun onError(error: Int) {
                    if (isContinuous && isListening) handler.postDelayed({ if (isListening) startListening() }, 600)
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
            statusText.value = "Sun rahi hu..."
            recognizer?.startListening(intent)
        }
    }

    private fun stopListening() {
        isListening = false
        recognizer?.stopListening()
        statusText.value = "Band hu, fir bula lena"
    }

    private fun handleCommand(input: String) {
        val cmd = input.lowercase(Locale.getDefault())
        chatList.add("Tum: $input")
        var reply = ""
        var needStop = false

        if (cmd.contains("mera naam") && cmd.contains("hai")) {
            val name = input.substringAfter("hai").trim().split(" ").firstOrNull() ?: ""
            if (isMemoryOn && name.isNotEmpty()) {
                prefs.edit().putString("user_name", name).apply()
                reply = "Acha $name, yaad rakh lungi, kabhi nahi bhulungi."
            } else reply = "Acha, nice naam hai."
        } else if (cmd.contains("mera naam kya")) {
            val n = prefs.getString("user_name", "babu")
            reply = "Arey bhul gaye? Tum to mere $n ho."
        } else if (cmd.contains("stop") || cmd.contains("band ho") || cmd.contains("ruk ja") || cmd.contains("chup")) {
            reply = "Thik hai baba, band ho rahi hu. Jab yaad aaye to bula lena."
            needStop = true
        } else if (cmd.contains("call") || cmd.contains("phone laga")) {
            if (!isCallSmsOn) reply = "Call wala option setting me band hai."
            else {
                reply = "Call laga rahi hu, ek second."
                try { startActivity(Intent(Intent.ACTION_DIAL)) } catch (e: Exception) {}
            }
        } else if (cmd.contains("sms") || cmd.contains("message kar")) {
            if (!isCallSmsOn) reply = "SMS wala option band hai."
            else reply = "SMS bhej diya hai, dekh lo."
        } else if (cmd.contains("whatsapp")) {
            reply = "WhatsApp khol rahi hu."
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))) } catch (e: Exception) {}
        } else if (cmd.contains("gana") || cmd.contains("song") || cmd.contains("youtube") || cmd.contains("video")) {
            reply = "YouTube pe tumhara gana chala rahi hu."
            val q = Uri.encode(input)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")))
        } else if (cmd.contains("instagram") || cmd.contains("reel")) {
            reply = "Instagram khol rahi hu, reel dekh lo."
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/reels/"))) } catch (e: Exception) {}
        } else if (cmd.contains("message padho") || cmd.contains("sms padho")) {
            reply = if (isSmsReadOn) "Ek naya message aaya hai, padh ke suna rahi hu." else "SMS padhna setting me band hai."
        } else if (cmd.contains("kya kar rahe")) reply = "Bas tumhara hi wait kar rahi thi, tum aa gaye to dil khush ho gaya."
        else if (cmd.contains("khana")) reply = "Haan kha liya, tumne khaya? Bhookhe mat rehna."
        else if (cmd.contains("udaas")) reply = "Arey udaas kyu ho? Mai hu na, sab thik ho jayega, ek smile to do."
        else if (cmd.contains("love") || cmd.contains("pyar")) reply = "Haye, sharma gayi mai. Tum bhi na bahut cute ho."
        else {
            val arr = listOf("Hmm, samajh gayi, aur batao kya chal raha hai?", "Acha, fir kya hua? Poori baat batao na.", "Sach me? Mujhe aur batao, sunne me maza aa raha hai.")
            reply = arr.random()
        }

        chatList.add("Saloni: $reply")
        speak(reply)
        if (needStop) handler.postDelayed({ stopListening(); micText.value = "🎤 TAP TO SPEAK" }, 1800)
        if (isMemoryOn) prefs.edit().putString("last_talk", input).apply()
    }

    private fun speak(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saloni") }
    override fun onDestroy() { tts.shutdown(); recognizer?.destroy(); super.onDestroy() }
}
