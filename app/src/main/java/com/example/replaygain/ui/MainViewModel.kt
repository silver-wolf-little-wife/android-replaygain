package com.example.replaygain.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.replaygain.data.FFmpegAnalyzer
import com.example.replaygain.data.ReplayGainProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                status = "正在扫描…",
                completedCount = 0
            )
            clearLogs()
            log("开始处理…")

            val processor = ReplayGainProcessor(analyzerInstance) { message ->
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

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _logs.value += "[$timestamp] $message\n"
    }

    private fun clearLogs() {
        _logs.value = ""
    }
}
