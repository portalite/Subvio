package com.subvio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SubvioApp()
        }
    }
}

@Composable
fun SubvioApp() {
    val background = Color(0xFF10111A)
    val accent = Color(0xFF8B75FF)

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(55.dp))

            Text(
                text = "SUBVIO",
                color = accent,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "Videolarına otomatik altyazı ekle.",
                color = Color.LightGray,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(65.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF202131)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Video seçilmedi",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Altyazı eklemek için bir video seç.",
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent
                        )
                    ) {
                        Text("Video Seç")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Tamamen çevrimdışı altyazı teknolojisi",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}