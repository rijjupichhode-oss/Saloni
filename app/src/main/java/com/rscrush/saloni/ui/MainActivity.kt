package com.rscrush.saloni.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaloniScreen(
                onCallClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                    startActivity(intent)
                },
                onSmsClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun SaloniScreen(onCallClick: () -> Unit, onSmsClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome Saloni", fontSize = 28.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCallClick) {
            Text(text = "Call")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSmsClick) {
            Text(text = "SMS")
        }
    }
}
