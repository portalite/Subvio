
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    var status by remember {
        mutableStateOf("Türkçe modeli henüz indirilmedi.")
    }

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

            Spacer(modifier = Modifier.height(55.dp))

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
                        text = "Türkçe Konuşma Modeli",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = status,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = accent
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "%$progress",
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isDownloading = true
                                progress = 0
                                status = "Model indiriliyor..."

                                try {
                                    ModelDownloader.download(context) {
                                        progress = it
                                    }

                                    isReady = true
                                    status =
                                        "Model telefona kaydedildi. Sonraki adımda konuşma tanımaya bağlayacağız."

                                } catch (e: Exception) {
                                    status = e.message
                                        ?: "İndirme sırasında hata oluştu."
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        enabled = !isDownloading && !isReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent
                        )
                    ) {
                        Text(
                            when {
                                isDownloading -> "İndiriliyor..."
                                isReady -> "Model İndirildi"
                                else -> "Türkçe Modeli İndir"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Çevrimdışı altyazı teknolojisi",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
