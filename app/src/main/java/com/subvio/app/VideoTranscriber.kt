
package com.subvio.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VideoTranscriber {

    private const val TARGET_RATE = 16000
    private const val BUFFER_TIMEOUT = 10000L

    private data class Word(
        val start: Double,
        val end: Double,
        val text: String
    )

    suspend fun transcribe(
        context: Context,
        videoUri: Uri,
        modelDirectory: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        if (!modelDirectory.exists()) {
            throw Exception("Türkçe modeli bulunamadı.")
        }

        val pcmFile = File(context.cacheDir, "subvio_audio.pcm")
        val srtFile = File(context.cacheDir, "subvio_subtitles.srt")

        pcmFile.delete()
        srtFile.delete()

        try {
            decodeAudio(context, videoUri, pcmFile) { value ->
                onProgress(value)
            }

            val words = recognizeAudio(modelDirectory, pcmFile) { value ->
                onProgress(value)
            }

            if (words.isEmpty()) {
                throw Exception("Konuşma algılanamadı.")
            }

            srtFile.writeText(createSrt(words), Charsets.UTF_8)

            onProgress(100)
            srtFile
        } finally {
            pcmFile.delete()
        }
    }

    private suspend fun decodeAudio(
        context: Context,
        uri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)

            var audioTrack = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrack < 0 || trackFormat == null) {
                throw Exception("Videoda ses kanalı bulunamadı.")
            }

            extractor.selectTrack(audioTrack)

            val durationUs = if (
                trackFormat.containsKey(MediaFormat.KEY_DURATION)
            ) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw Exception("Ses biçimi okunamadı.")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            var decodedFormat = trackFormat

            FileOutputStream(outputFile).use { output ->

                val info = MediaCodec.BufferInfo()

                while (!outputDone) {

                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(
                            BUFFER_TIMEOUT
                        )

                        if (inputIndex >= 0) {
                            val inputBuffer =
                                codec.getInputBuffer(inputIndex)
                                    ?: throw Exception(
                                        "Ses verisi okunamadı."
                                    )

                            inputBuffer.clear()

                            val sampleSize = extractor.readSampleData(
                                inputBuffer,
                                0
                            )

                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (
                        val outputIndex = codec.dequeueOutputBuffer(
                            info,
                            BUFFER_TIMEOUT
                        )
                    ) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            decodedFormat = codec.outputFormat
                        }

                        else -> {
                            if (outputIndex >= 0) {
                                if (info.size > 0) {
                                    val buffer = codec.getOutputBuffer(
                                        outputIndex
                                    ) ?: throw Exception(
                                        "Çözülen ses okunamadı."
                                    )

                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)

                                    val pcm = convertToMono16k(
                                        buffer.slice(),
                                        decodedFormat
                                    )

                                    output.write(pcm)

                                    if (durationUs > 0) {
                                        val ratio = (
                                            info.presentationTimeUs.toDouble() /
                                                durationUs.toDouble()
                                            ).coerceIn(0.0, 1.0)

                                        val progress =
                                            5 + (ratio * 40).toInt()

                                        withContext(Dispatchers.Main) {
                                            onProgress(progress)
                                        }
                                    }
                                }

                                outputDone =
                                    info.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                                codec.releaseOutputBuffer(
                                    outputIndex,
                                    false
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }

            codec?.release()
            extractor.release()
        }
    }

    private fun convertToMono16k(
        input: ByteBuffer,
        format: MediaFormat
    ): ByteArray {

        val sampleRate = format.getInteger(
            MediaFormat.KEY_SAMPLE_RATE
        )

        val channels = format.getInteger(
            MediaFormat.KEY_CHANNEL_COUNT
        )

        val encoding = if (
            format.containsKey(MediaFormat.KEY_PCM_ENCODING)
        ) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> throw Exception("Desteklenmeyen ses biçimi.")
        }

        val frameSize = bytesPerSample * channels
        val frames = input.remaining() / frameSize

        if (frames <= 0) return ByteArray(0)

        val outputFrames = frames * TARGET_RATE / sampleRate
        val output = ByteBuffer.allocate(outputFrames * 2)
            .order(ByteOrder.LITTLE_ENDIAN)

        input.order(ByteOrder.LITTLE_ENDIAN)

        fun sample(frame: Int, channel: Int): Float {
            val index = frame * frameSize +
                channel * bytesPerSample

            return when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT ->
                    ((input.get(index).toInt() and 0xFF) - 128) / 128f

                AudioFormat.ENCODING_PCM_16BIT ->
                    input.getShort(index).toInt() / 32768f

                AudioFormat.ENCODING_PCM_FLOAT ->
                    input.getFloat(index).coerceIn(-1f, 1f)

                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val b0 = input.get(index).toInt() and 0xFF
                    val b1 = input.get(index + 1).toInt() and 0xFF
                    val b2 = input.get(index + 2).toInt()

                    var value = b0 or (b1 shl 8) or (b2 shl 16)

                    if (value and 0x800000 != 0) {
                        value = value or -0x1000000
                    }

                    value / 8388608f
                }

                AudioFormat.ENCODING_PCM_32BIT ->
                    input.getInt(index) / 2147483648f

                else -> 0f
            }
        }

        for (i in 0 until outputFrames) {
            val sourcePosition =
                i.toDouble() * sampleRate / TARGET_RATE

            val first = sourcePosition.toInt()
                .coerceIn(0, frames - 1)

            val second = (first + 1).coerceAtMost(frames - 1)
            val fraction = (sourcePosition - first).toFloat()

            var firstMono = 0f
            var secondMono = 0f

            for (channel in 0 until channels) {
                firstMono += sample(first, channel)
                secondMono += sample(second, channel)
            }

            firstMono /= channels
            secondMono /= channels

            val value = (
                firstMono + (secondMono - firstMono) * fraction
            ).coerceIn(-1f, 1f)

            output.putShort((value * 32767).toInt().toShort())
        }

        return output.array()
    }

    private suspend fun recognizeAudio(
        modelDirectory: File,
        pcmFile: File,
        onProgress: (Int) -> Unit
    ): List<Word> {

        val model = Model(modelDirectory.absolutePath)
        val recognizer = Recognizer(model, TARGET_RATE.toFloat())

        val words = mutableListOf<Word>()

        try {
            recognizer.setWords(true)

            BufferedInputStream(FileInputStream(pcmFile)).use { input ->
                val buffer = ByteArray(8000)
                val totalSize = pcmFile.length()
                var processed = 0L

                while (true) {
                    val count = input.read(buffer)

                    if (count < 0) break

                    if (recognizer.acceptWaveForm(buffer, count)) {
                        addWords(recognizer.result, words)
                    }

                    processed += count

                    val ratio = if (totalSize > 0) {
                        processed.toDouble() / totalSize
                    } else {
                        1.0
                    }

                    val progress = 45 + (ratio * 50).toInt()

                    withContext(Dispatchers.Main) {
                        onProgress(progress.coerceAtMost(95))
                    }
                }
            }

            addWords(recognizer.finalResult, words)

        } finally {
            recognizer.close()
            model.close()
        }

        return words
    }

    private fun addWords(
        json: String,
        destination: MutableList<Word>
    ) {
        val root = JSONObject(json)
        val result = root.optJSONArray("result") ?: return

        for (i in 0 until result.length()) {
            val item = result.getJSONObject(i)

            val text = item.optString("word")
            val start = item.optDouble("start", -1.0)
            val end = item.optDouble("end", -1.0)

            if (text.isNotBlank() && start >= 0 && end >= start) {
                destination.add(Word(start, end, text))
            }
        }
    }

    private fun createSrt(words: List<Word>): String {
        val output = StringBuilder()
        val groups = mutableListOf<List<Word>>()
        var current = mutableListOf<Word>()

        for (word in words) {
            val previous = current.lastOrNull()

            val shouldSplit = previous != null && (
                word.start - previous.end > 0.7 ||
                current.size >= 7 ||
                word.end - current.first().start > 4.5
            )

            if (shouldSplit) {
                groups.add(current.toList())
                current = mutableListOf()
            }

            current.add(word)
        }

        if (current.isNotEmpty()) {
            groups.add(current.toList())
        }

        groups.forEachIndexed { index, group ->
            val start = group.first().start
            val end = group.last().end
            val text = group.joinToString(" ") { it.text }

            output.append(index + 1)
                .append('\n')
                .append(formatTime(start))
                .append(" --> ")
                .append(formatTime(end))
                .append('\n')
                .append(text)
                .append("\n\n")
        }

        return output.toString()
    }

    private fun formatTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong().coerceAtLeast(0)

        val hours = totalMs / 3600000
        val minutes = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val millis = totalMs % 1000

        return "%02d:%02d:%02d,%03d".format(
            hours,
            minutes,
            secs,
            millis
        )
    }
}
