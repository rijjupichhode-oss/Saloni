package com.rscrush.saloni.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Welcome Saloni ❤", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:9876543210")
                            }
                            startActivity(intent)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Call Saloni")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:9876543210")
                                putExtra("sms_body", "Hi Saloni ❤")
                            }
                            startActivity(intent)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("SMS Saloni")
                        }
                    }
                }
            }
        }
    }
}
