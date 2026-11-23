package com.rscrush.saloni.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

data class ChatMsg(val text: String, val isMe: Boolean)

class MainActivity : ComponentActivity() {
    var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { tts?.language = Locale("hi", "IN") }
        setContent { SaloniApp(tts) }
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

@Composable
fun SaloniApp(tts: TextToSpeech?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf(ChatMsg("Hello, mai SALONI hu ❤️ Bol kya kar raha hai?", false))) }
    var isListening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        messages = messages + ChatMsg(text, false)
        scope.launch { listState.animateScrollToItem(messages.size) }
    }

    fun findNumber(name: String): String? {
        val cursor = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)
        cursor?.use { while (it.moveToNext()) { if ((it.getString(1) ?: "").lowercase().contains(name.lowercase())) return it.getString(0) } }
        return null
    }

    fun openApp(pkg: String) {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) context.startActivity(i)
    }

    fun readLastSms() {
        try {
            val c = context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body"), null, null, "date DESC")
            c?.use { if (it.moveToFirst()) speak("Tera message aaya hai ${it.getString(0)} se, usne likha hai - ${it.getString(1)}") else speak("Koi naya message nahi aaya hai") }
        } catch (e: Exception) { speak("SMS padhne ke liye permission de de") }
    }

    fun handle(cmd: String) {
        messages = messages + ChatMsg(cmd, true)
        val c = cmd.lowercase(Locale.getDefault())
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            c.contains("saloni") || c.contains("crush") -> { speak("Haan bolo, mai yahin hu, tumhara wait kar rahi thi ❤️"); return }
            c.contains("kya kar rahe") || c.contains("kya kar rhe") -> { speak("Bas tumhara hi wait kar rahi thi, tum batao kya kar rahe ho?"); return }
            c.contains("khana") -> { if (hour in 12..15 || hour in 19..22) speak("Haan, khane ka time ho gaya hai, pehle khana kha lo, apna khayal rakho, fir baat karte hai") else speak("Khana kha liya tumne? Time pe kha liya karo"); return }
            c.contains("udaas") || c.contains("akela") || c.contains("sad") || c.contains("tension") -> { speak("Arey udaas mat ho, mai hu na yahan, jo dil me hai bol do, halka ho jayega"); return }
            c.contains("miss") -> { speak("Miss to mai bhi kar rahi thi tumhe, isliye to yahin hu"); return }
            c.contains("love") || c.contains("pyar") -> { speak("Haye, sharma gayi mai to, tum bahut cute ho"); return }
            c.contains("kaise ho") -> { speak("Mai badhiya hu, tum batao, tumne khana khaya?"); return }
            c.contains("good morning") -> { speak("Good Morning, aaj ka din tumhara bahut accha jayega"); return }
            c.contains("good night") || c.contains("sone ja") -> { speak("Good Night, aaram se so jana, kal fir baat karenge, khayal rakhna"); return }
            c.contains("thank") || c.contains("shukriya") -> { speak("Arey isme thank you kaisa, tumhare liye to hamesha hu"); return }
            c.contains("whatsapp") && c.contains("message") -> {
                val name = c.replace("whatsapp","").replace("pe message bhejo","").replace("ko","").trim().split(" ").firstOrNull() ?: ""
                val num = findNumber(name)
                if (num != null) { speak("$name ko WhatsApp pe message bhej rahi hu"); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num.replace("+","").replace(" ","")}?text=${Uri.encode("Hi")}"))) }
                else { speak("WhatsApp khol rahi hu"); openApp("com.whatsapp") }
                return
            }
            c.contains("whatsapp") -> { speak("Haan, WhatsApp khol rahi hu"); openApp("com.whatsapp"); return }
            c.contains("instagram") -> { speak("Instagram khol rahi hu"); openApp("com.instagram.android"); return }
            c.contains("youtube") -> { val q = cmd.replace("youtube","").replace("kholo","").replace("chalao","").trim(); speak("YouTube pe $q chala rahi hu"); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))); return }
            c.contains("music") || c.contains("gaana") || c.contains("gana") || c.contains("song") -> { val q = cmd.replace("music","").replace("chalao","").replace("gaana","").trim(); speak("$q gana chala rahi hu"); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q + " song")}"))); return }
            c.contains("call") -> { val name = c.replace("call","").replace("karo","").replace("ko","").trim(); val num = findNumber(name); if (num != null) { speak("$name ko call kar rahi hu"); if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num"))) } else { speak("Number nahi mila, dial pad khol rahi hu"); context.startActivity(Intent(Intent.ACTION_DIAL)) }; return }
            c.contains("message") && (c.contains("padho") || c.contains("padh")) -> { readLastSms(); return }
            c.contains("sms") && (c.contains("padho") || c.contains("dikhao")) -> { readLastSms(); return }
            c.contains("time") || c.contains("baje") -> { speak("Abhi ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())} baje hai"); return }
            else -> { speak("Hmm, samajh gayi, $cmd, aur batao kya kar rahe ho?"); return }
        }
    }

    fun listen() {
        permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_SMS))
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN") }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { isListening = true }
            override fun onResults(r: Bundle?) { isListening = false; val l = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (!l.isNullOrEmpty()) handle(l[0]); rec.destroy() }
            override fun onError(e: Int) { isListening = false; rec.destroy() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(a: Int, b: Bundle?) {}
        })
        rec.startListening(intent)
    }

    val inf = rememberInfiniteTransition(label = "ai")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val pulse by inf.animateFloat(1f, 1.1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")

    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020A2A), Color.Black))).padding(16.dp)) {
        Text("S.A.L.O.N.I", color = Color(0xFF00E5FF), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(10.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(220.dp).clickable { listen() }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.12f), radius = size.minDimension*0.45f)
                drawCircle(Color(0xFF00E5FF), radius = size.minDimension*0.38f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(Color(0xFF00E5FF), radius = 12f, center = Offset(size.width*0.40f, size.height*0.40f))
                drawCircle(Color(0xFF00E5FF), radius = 12f, center = Offset(size.width*0.60f, size.height*0.40f))
                for (i in 0 until 20) { val ang = ((i*18f + rot) * Math.PI / 180); drawLine(Color(0xFF00E5FF), Offset((size.width/2 + cos(ang)*size.width*0.44).toFloat(), (size.height/2 + sin(ang)*size.height*0.44).toFloat()), Offset((size.width/2 + cos(ang)*size.width*0.47).toFloat(), (size.height/2 + sin(ang)*size.height*0.47).toFloat()), strokeWidth = 5f) }
            }
            Box(modifier = Modifier.size(90.dp).scale(pulse).background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) { Text("🎤", fontSize = 30.sp) }
        }
        Text(if (isListening) "● SUN RAHI HU..." else "Bolo - Saloni / Crush", color = if (isListening) Color.Red else Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (m.isMe) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(m.text, color = if (m.isMe) Color.White else Color.Black, fontSize = 15.sp, modifier = Modifier.background(if (m.isMe) Color(0xFF00B4D8) else Color(0xFFB3E5FC), RoundedCornerShape(16.dp)).padding(12.dp).widthIn(max = 260.dp))
                }
            }
        }
        Button(onClick = { listen() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
            Text("🎤 TAP TO SPEAK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Text("Try: Kya kar rahe ho • Khana kha liya? • Udaas hu\nWhatsApp kholo • Rahul ko call karo • Mera message padho", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
    }
}
