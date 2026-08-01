package com.example.replaygain.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.replaygain.data.FFmpegAnalyzer
import com.example.replaygain.data.ReplayGainProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs

    private var currentDirectory: File? = null
    private var analyzer: FFmpegAnalyzer? = null

    data class UiState(
        val directoryPath: String = "",
        val isProcessing: Boolean = false,
        val status: String = "就绪",
        val completedCount: Int = 0,
        val canStart: Boolean = false
    )

    fun setDirectory(directory: File) {
        currentDirectory = directory
        _uiState.value = _uiState.value.copy(
            directoryPath = directory.absolutePath,
            canStart = true
        )
        log("已选择工作目录：${directory.absolutePath}")
    }

    fun initFfmpeg(ffmpegPath: String, nativeLibDir: String) {
        analyzer = FFmpegAnalyzer(ffmpegPath, nativeLibDir)
    }

    fun startProcessing(skipExisting: Boolean) {
        val dir = currentDirectory ?: return
        val analyzerInstance = analyzer ?: return
        val concurrency = computeConcurrency()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                status = "正在扫描…",
                completedCount = 0
            )
            clearLogs()
            log("开始处理…（并发数 $concurrency）")

            val processor = ReplayGainProcessor(analyzerInstance, concurrency) { message ->
                log(message)
                _uiState.value = _uiState.value.copy(status = message)
            }

            processor.process(dir, skipExisting)
                .onSuccess { count ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        status = "完成，共处理 $count 个文件",
                        completedCount = count
                    )
                    log("全部完成")
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        status = "错误：${throwable.localizedMessage}"
                    )
                    log("错误：${throwable.localizedMessage}")
                }
        }
    }

    // 按设备内存自适应并发数：内存越大并发越多（配合瘦身 ffmpeg，内存占用更低）
    private fun computeConcurrency(): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val totalRamMB = try {
            val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.totalMem / (1024 * 1024)).toInt()
        } catch (e: Exception) {
            cores * 256
        }
        return when {
            totalRamMB >= 12000 -> 8
            totalRamMB >= 6000 -> 6
            totalRamMB >= 4000 -> 4
            else -> 2
        }.coerceAtMost(cores)
    }

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        synchronized(this) {
            _logs.value += "[$timestamp] $message\n"
        }
    }

    private fun clearLogs() {
        _logs.value = ""
    }
}
