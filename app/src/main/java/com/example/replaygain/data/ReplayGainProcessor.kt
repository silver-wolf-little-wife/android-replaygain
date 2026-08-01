package com.example.replaygain.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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

            // 跳过已有标签的文件（读标签较快，无需并行）
            val targets = if (skipExisting) {
                files.filter { !ReplayGainTagger.hasReplayGainTags(it) }
            } else {
                files
            }
            onProgress("待分析 ${targets.size} 个文件")

            if (targets.isEmpty()) {
                onProgress("所有文件均已跳过（已有 ReplayGain 标签）")
                return@runCatching 0
            }

            // 并发分析：多核设备并行跑多个 ffmpeg，大幅提速
            // 并发数取 2~4，避免同时跑太多 ffmpeg 占用过多内存
            val maxConcurrency = (Runtime.getRuntime().availableProcessors().coerceAtLeast(2) - 1)
                .coerceIn(2, 4)
            val semaphore = Semaphore(maxConcurrency)
            val results = ConcurrentLinkedQueue<FileResult>()
            val completed = AtomicInteger(0)

            coroutineScope {
                targets.map { file ->
                    async {
                        semaphore.withPermit {
                            onProgress("正在分析：${file.name}")
                            try {
                                val r = analyzer.analyze(file)
                                results.add(FileResult(file, r.inputI, r.trackGain))
                            } catch (e: Exception) {
                                Log.w(TAG, "分析失败：${file.name}", e)
                                onProgress("  分析失败：${file.name} - ${e.localizedMessage}")
                            }
                            onProgress("  进度 ${completed.incrementAndGet()}/${targets.size}")
                        }
                    }
                }.forEach { it.await() }
            }

            if (results.isEmpty()) {
                throw IllegalStateException("没有成功分析的音频文件")
            }

            val albumGains = results.groupBy { it.file.parentFile }
                .mapValues { (_, list) ->
                    val avgLoudness = list.map { it.inputI }.average()
                    -18.0 - avgLoudness
                }

            // 写入标签保持串行，避免并发写文件
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
