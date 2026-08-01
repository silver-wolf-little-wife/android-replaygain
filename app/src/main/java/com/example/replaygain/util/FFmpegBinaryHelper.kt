package com.example.replaygain.util

import android.content.Context
import android.util.Log
import java.io.File

object FFmpegBinaryHelper {

    private const val BINARY_NAME = "libffmpeg.so"

    fun getOrExtractFfmpeg(context: Context): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
            ?: throw IllegalStateException("nativeLibraryDir is null")

        val ffmpegFile = File(nativeDir, BINARY_NAME)
        Log.d("FFmpegBinaryHelper", "Looking for ffmpeg at ${ffmpegFile.absolutePath}")
        Log.d("FFmpegBinaryHelper", "exists=${ffmpegFile.exists()}, canRead=${ffmpegFile.canRead()}, canExecute=${ffmpegFile.canExecute()}")

        if (!ffmpegFile.exists()) {
            throw IllegalStateException("FFmpeg binary not found in nativeLibraryDir: $nativeDir")
        }

        if (!ffmpegFile.canExecute()) {
            ffmpegFile.setExecutable(true, false)
        }

        if (!ffmpegFile.canExecute()) {
            throw IllegalStateException("FFmpeg binary is not executable: ${ffmpegFile.absolutePath}")
        }

        return ffmpegFile.absolutePath
    }
}
