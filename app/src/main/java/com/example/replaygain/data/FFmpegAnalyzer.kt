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
        // -threads: 多线程解码（FLAC 帧级并行）
        // -map 0:a:0: 只取第一个音频流（主音轨），排除文件中混入的静音副流
        // -vn: 跳过封面图等视频流
        // aresample=44100: 高采样率(如 96k/192k)文件降到 44.1k 再测响度，减少 loudnorm 处理量
        //   LUFS 是 -18 LUFS 参考，44.1k 下 K 加权误差 <0.1 LU，不影响音量平衡
        val command = listOf(
            ffmpegPath,
            "-hide_banner",
            "-threads", "4",
            "-i", file.absolutePath,
            "-map", "0:a:0",
            "-vn",
            "-af", "aresample=44100,loudnorm=I=$TARGET_LOUDNESS:TP=-1.5:LRA=11:print_format=json",
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

        val streamInfo = extractStreamInfo(output)
        val inputI = parseInputI(output)

        if (inputI == null) {
            // 输出开头是元数据（含 LYRICS 歌词），又长又无助于排错；
            // 只保留末尾（loudnorm JSON / 真实错误行）用于日志
            throw RuntimeException("无法解析响度（exit=$exitCode）$streamInfo：${truncateOutput(output)}")
        }

        if (!inputI.isFinite()) {
            // input_i 为 -inf/inf/nan：静音或无法测量，明确提示并跳过
            throw RuntimeException("该文件为静音或无法测量响度，已跳过$streamInfo")
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
        val match = regex.find(output) ?: return null
        val raw = match.groupValues[1].trim()
        return when {
            raw.equals("-inf", ignoreCase = true) || raw.equals("-infinity", ignoreCase = true) ->
                Double.NEGATIVE_INFINITY
            raw.equals("inf", ignoreCase = true) || raw.equals("infinity", ignoreCase = true) ->
                Double.POSITIVE_INFINITY
            raw.equals("nan", ignoreCase = true) -> Double.NaN
            else -> raw.toDoubleOrNull()
        }
    }

    // 提取流信息，帮助诊断选了哪条流/为什么失败
    private fun extractStreamInfo(output: String): String {
        val sb = StringBuilder()
        output.lineSequence()
            .filter { it.contains("Input #") || it.contains("Duration:") || it.contains("Stream #") }
            .take(6)
            .forEach { sb.append('\n').append(it.trim()) }
        return sb.toString()
    }

    private fun formatGain(gain: Double): String {
        return String.format(Locale.US, "%.2f dB", gain)
    }

    private fun truncateOutput(output: String, maxLen: Int = 600): String {
        return if (output.length > maxLen) {
            "…[已省略前 ${output.length - maxLen} 字符]…\n" + output.takeLast(maxLen)
        } else {
            output
        }
    }
}
