
package com.subvio.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SubvioApp() }
    }
}

@Composable
fun SubvioApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val background = Color(0xFF10111A)
    val accent = Color(0xFF8B75FF)

    val modelDirectory = remember {
        File(context.filesDir, "vosk-model-small-tr-0.3")
    }

    var isReady by remember {
        mutableStateOf(modelDirectory.exists())
    }

    var isDownloading by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    var status by remember {
        mutableStateOf(
            if (modelDirectory.exists())
                "Türkçe modeli hazır."
            else
                "Türkçe modeli henüz indirilmedi."
        )
    }

    var selectedVideo by remember {
        mutableStateOf<Uri?>(null)
    }

    var videoName by remember {
        mutableStateOf("Henüz video seçilmedi.")
    }

    var generatedSrt by remember {
        mutableStateOf<File?>(null)
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedVideo = uri
            generatedSrt = null

            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            videoName = uri.lastPathSegment ?: "Video seçildi"
            status = "Video seçildi."
        }
    }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        val file = generatedSrt

        if (uri != null && file != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    file.inputStream().use { input ->
                        input.copyTo(it)
                    }
                } ?: throw Exception("Dosya kaydedilemedi.")

                status = "Altyazı başarıyla kaydedildi."
            } catch (e: Exception) {
                status = e.message ?: "Kaydetme hatası."
            }
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(35.dp))

            Text(
                "SUBVIO",
                color = accent,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                "Videolarına otomatik altyazı ekle.",
                color = Color.LightGray
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF202131)
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Türkçe Konuşma Modeli",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(status, color = Color.LightGray)

                    if (isDownloading || isProcessing) {
                        Spacer(Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent
                        )

                        Text(
                            "%$progress",
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(14.dp))

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
                                    status = "Türkçe modeli hazır."
                                } catch (e: Exception) {
                                    status = e.message
                                        ?: "Model indirme hatası."
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        enabled = !isDownloading &&
                            !isProcessing && !isReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent
                        )
                    ) {
                        Text(
                            if (isReady) "Model Hazır"
                            else "Türkçe Modeli İndir"
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF202131)
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Video Altyazısı",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        videoName,
                        color = Color.LightGray
                    )

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            videoPicker.launch(arrayOf("video/*"))
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent
                        )
                    ) {
                        Text("Video Seç")
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val video = selectedVideo

                            if (video == null) {
                                status = "Önce bir video seç."
                                return@Button
                            }

                            if (!modelDirectory.exists()) {
                                isReady = false
                                status = "Önce Türkçe modeli indir."
                                return@Button
                            }

                            scope.launch {
                                isProcessing = true
                                progress = 0
                                generatedSrt = null
                                status = "Ses analiz ediliyor..."

                                try {
                                    val result = VideoTranscriber.transcribe(
                                        context,
                                        video,
                                        modelDirectory
                                    ) {
                                        progress = it
                                    }

                                    generatedSrt = result
                                    status = "Altyazı hazır! Kaydedebilirsin."

                                    savePicker.launch("subvio_altyazi.srt")

                                } catch (e: Exception) {
                                    status = e.message
                                        ?: "Altyazı oluşturulamadı."
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing && !isDownloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent
                        )
                    ) {
                        Text(
                            if (isProcessing)
                                "Altyazı Oluşturuluyor..."
                            else
                                "Altyazı Oluştur"
                        )
                    }

                    if (generatedSrt != null) {
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                savePicker.launch("subvio_altyazi.srt")
                            },
                            enabled = !isProcessing
                        ) {
                            Text("Altyazıyı Tekrar Kaydet")
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Çevrimdışı altyazı teknolojisi",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}
