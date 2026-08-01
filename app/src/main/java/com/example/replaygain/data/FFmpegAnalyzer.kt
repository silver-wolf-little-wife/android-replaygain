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
        // -vn: 跳过视频流，只分析音频
        // aresample=44100: 高采样率(如 96k/192k)文件降到 44.1k 再测响度，减少 loudnorm 处理量
        //   LUFS 是 -18 LUFS 参考，44.1k 下 K 加权误差 <0.1 LU，不影响音量平衡
        val command = listOf(
            ffmpegPath,
            "-hide_banner",
            "-threads", "4",
            "-i", file.absolutePath,
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

        val inputI = parseInputI(output)
        if (inputI == null) {
            // 输出开头是元数据（含 LYRICS 歌词），又长又无助于排错；
            // 只保留末尾（loudnorm JSON / 真实错误行）用于日志
            throw RuntimeException("无法解析响度（exit=$exitCode）：${truncateOutput(output)}")
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

    private fun truncateOutput(output: String, maxLen: Int = 600): String {
        return if (output.length > maxLen) {
            "…[已省略前 ${output.length - maxLen} 字符]…\n" + output.takeLast(maxLen)
        } else {
            output
        }
    }
}
