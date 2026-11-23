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
            Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(14.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text("> VOICE: HEENA STYLE - FEMALE FIXED", fontSize = 12.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        Text("> WHATSAPP | SMS | CALL | INSTA : CONNECTED", fontSize = 11.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                        chatList.forEach { Text(it, fontSize = 13.sp, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(3.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (!isListening) { startListening(); micText.value = "🛑 DEACTIVATE" } else { stopListening(); micText.value = "🎤 ACTIVATE CRUSH" } }, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.buttonColors(Color(0xFF00FF00)), shape = RoundedCornerShape(8.dp)) { Text(micText.value, fontSize = 16.sp, color = Color.Black, fontFamily = FontFamily.Monospace) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { toggleTorch(!isTorchOn); speak(if (isTorchOn) "Light on kar diya Trader Rijju." else "Light band kar diya Trader Rijju.") }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))) { Text("💡 LIGHT", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, maxLines = 1) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { speak("Hello Trader Rijju, kaise ho?") }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FF00), RoundedCornerShape(8.dp))) { Text("VOICE TEST", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, maxLines = 1, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(14.dp))
                Text("⚙️ SYSTEM SETTINGS", fontSize = 14.sp, color = Color(0xFF00FF00), fontFamily =
