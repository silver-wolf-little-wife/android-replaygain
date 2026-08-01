package com.example.replaygain.data

import android.util.Log
import java.io.File
import java.util.Locale

class FFmpegAnalyzer(
    private val ffmpegPath: String,
    private val nativeLibDir: String
) {

    companion object {
        private const val TAG = "FFmpegAnalyzer"
        private const val TARGET_LOUDNESS = -18.0
    }

    data class AnalysisResult(
        val inputI: Double,
        val trackGain: Double,
        val trackGainString: String
    )

    fun analyze(file: File): AnalysisResult {
        val command = listOf(
            ffmpegPath,
            "-i", file.absolutePath,
            "-af", "loudnorm=I=$TARGET_LOUDNESS:TP=-1.5:LRA=11:print_format=json",
            "-f", "null",
            "-"
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply {
                // 让链接器在 nativeLibraryDir 中找到 libc++_shared.so 等依赖
                environment()["LD_LIBRARY_PATH"] = nativeLibDir
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        Log.d(TAG, "ffmpeg exited $exitCode for ${file.name}")

        val inputI = parseInputI(output)
        if (inputI == null) {
            throw RuntimeException("无法解析响度（exit=$exitCode）：$output")
        }

        val trackGain = TARGET_LOUDNESS - inputI
        return AnalysisResult(
            inputI = inputI,
            trackGain = trackGain,
            trackGainString = formatGain(trackGain)
        )
    }

    private fun parseInputI(output: String): Double? {
        val regex = Regex("\"input_i\"\\s*:\\s*\"([^\"]+)\"")
        val match = regex.find(output)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun formatGain(gain: Double): String {
        return String.format(Locale.US, "%.2f dB", gain)
    }
}
