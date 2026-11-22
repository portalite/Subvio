
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
        val modelFile = File(modelDirectory, "am/final.mdl")

        if (modelFile.exists()) {
            return@withContext modelDirectory
        }

        val zipFile = File(context.cacheDir, "turkish-model.zip")
        val staging = File(context.filesDir, "model-install-temp")

        staging.deleteRecursively()
        staging.mkdirs()

        val connection = URL(MODEL_URL).openConnection()
                as HttpURLConnection

        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Model indirilemedi.")
            }

            val totalSize = connection.contentLength
            var downloaded = 0L

            connection.inputStream.use { input ->
                BufferedInputStream(input).use { bufferedInput ->
                    BufferedOutputStream(zipFile.outputStream()).use { output ->
                        val buffer = ByteArray(8192)
                        var count: Int

                        while (bufferedInput.read(buffer).also {
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

        val rootPath = staging.canonicalPath +
                File.separator

        ZipInputStream(
            BufferedInputStream(FileInputStream(zipFile))
        ).use { zip ->

            var entry = zip.nextEntry

            while (entry != null) {
                val outputFile = File(staging, entry.name)

                if (!outputFile.canonicalPath.startsWith(rootPath)) {
                    throw Exception("Geçersiz model dosyası.")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val extractedModel = File(staging, MODEL_NAME)

        if (!File(extractedModel, "am/final.mdl").exists()) {
            staging.deleteRecursively()
            zipFile.delete()
            throw Exception("Model dosyaları eksik.")
        }

        modelDirectory.deleteRecursively()

        if (!extractedModel.renameTo(modelDirectory)) {
            staging.deleteRecursively()
            throw Exception("Model kurulamadı.")
        }

        staging.deleteRecursively()
        zipFile.delete()

        modelDirectory
    }
}
