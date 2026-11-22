
package com.subvio.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object ModelDownloader {

    private const val MODEL_NAME = "vosk-model-small-tr-0.3"

    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"

    suspend fun download(
        context: Context,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val modelDirectory = File(context.filesDir, MODEL_NAME)

        // Türkçe modelinde final.mdl doğrudan kök klasördedir.
        if (File(modelDirectory, "final.mdl").exists()) {
            return@withContext modelDirectory
        }

        val zipFile = File(context.cacheDir, "turkish-model.zip")
        val staging = File(context.filesDir, "model-install-temp")

        staging.deleteRecursively()
        zipFile.delete()
        staging.mkdirs()

        try {
            val connection =
                URL(MODEL_URL).openConnection() as HttpURLConnection

            try {
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Model indirilemedi.")
                }

                val totalSize = connection.contentLength
                var downloaded = 0L

                connection.inputStream.use { input ->
                    BufferedInputStream(input).use { source ->
                        BufferedOutputStream(zipFile.outputStream()).use { output ->
                            val buffer = ByteArray(8192)
                            var count: Int

                            while (source.read(buffer).also {
                                    count = it
                                } != -1) {

                                output.write(buffer, 0, count)
                                downloaded += count

                                if (totalSize > 0) {
                                    val percent =
                                        (downloaded * 100 / totalSize).toInt()

                                    withContext(Dispatchers.Main) {
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            val rootPath = staging.canonicalPath + File.separator

            var extractedEntries = 0

            ZipInputStream(
                BufferedInputStream(FileInputStream(zipFile))
            ).use { zip ->

                var entry = zip.nextEntry

                while (entry != null) {
                    val outputFile = File(staging, entry.name)

                    if (!outputFile.canonicalPath.startsWith(rootPath)) {
                        throw Exception("Geçersiz model arşivi.")
                    }

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()

                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var count: Int

                            while (zip.read(buffer).also {
                                    count = it
                                } != -1) {
                                output.write(buffer, 0, count)
                            }
                        }
                    }

                    extractedEntries++
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (extractedEntries == 0) {
                throw Exception("İndirilen dosya geçerli bir ZIP arşivi değil.")
            }

            // Türkçe modelinin düz klasör yapısını bul.
            val modelFile = staging.walkTopDown().firstOrNull {
                it.isFile &&
                    it.name == "final.mdl" &&
                    File(it.parentFile, "mfcc.conf").exists() &&
                    File(it.parentFile, "Gr.fst").exists() &&
                    File(it.parentFile, "HCLr.fst").exists()
            } ?: throw Exception(
                "Türkçe model dosyaları bulunamadı. Arşiv yapısı beklenenden farklı."
            )

            val extractedModel = modelFile.parentFile
                ?: throw Exception("Model klasörü bulunamadı.")

            modelDirectory.deleteRecursively()

            if (!extractedModel.renameTo(modelDirectory)) {
                throw Exception("Model klasörü taşınamadı.")
            }

            if (!File(modelDirectory, "final.mdl").exists()) {
                throw Exception("Model kurulumu doğrulanamadı.")
            }

            staging.deleteRecursively()
            zipFile.delete()

            modelDirectory

        } catch (e: Exception) {
            staging.deleteRecursively()
            zipFile.delete()
            throw e
        }
    }
}
