package com.example.replaygain.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReplayGainProcessor(
    private val analyzer: FFmpegAnalyzer,
    private val onProgress: suspend (message: String) -> Unit
) {

    data class FileResult(
        val file: File,
        val inputI: Double,
        val trackGain: Double
    )

    suspend fun process(
        rootDir: File,
        skipExisting: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("扫描目录：${rootDir.absolutePath}")
            val files = AudioFileScanner.scan(rootDir)
            onProgress("找到 ${files.size} 个音频文件")

            if (files.isEmpty()) {
                throw IllegalStateException("未找到 FLAC/MP3 文件")
            }

            val results = mutableListOf<FileResult>()
            files.forEachIndexed { index, file ->
                onProgress("正在分析 (${index + 1}/${files.size})：${file.name}")

                if (skipExisting && ReplayGainTagger.hasReplayGainTags(file)) {
                    onProgress("  跳过（已有标签）：${file.name}")
                    return@forEachIndexed
                }

                try {
                    val result = analyzer.analyze(file)
                    results.add(
                        FileResult(
                            file = file,
                            inputI = result.inputI,
                            trackGain = result.trackGain
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "分析失败：${file.name}", e)
                    onProgress("  分析失败：${file.name} - ${e.localizedMessage}")
                }
            }

            if (results.isEmpty()) {
                throw IllegalStateException("没有成功分析的音频文件")
            }

            val albumGains = results.groupBy { it.file.parentFile }
                .mapValues { (_, list) ->
                    val avgLoudness = list.map { it.inputI }.average()
                    -18.0 - avgLoudness
                }

            results.forEachIndexed { index, result ->
                onProgress("正在写入 (${index + 1}/${results.size})：${result.file.name}")
                val albumGain = albumGains[result.file.parentFile] ?: result.trackGain
                try {
                    ReplayGainTagger.write(result.file, result.trackGain, albumGain)
                } catch (e: Exception) {
                    Log.w(TAG, "写入失败：${result.file.name}", e)
                    onProgress("  写入失败：${result.file.name} - ${e.localizedMessage}")
                }
            }

            onProgress("完成，共处理 ${results.size} 个文件")
            results.size
        }
    }

    companion object {
        private const val TAG = "ReplayGainProcessor"
    }
}
