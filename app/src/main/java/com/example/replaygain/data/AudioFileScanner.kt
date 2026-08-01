package com.example.replaygain.data

import java.io.File

object AudioFileScanner {

    private val SUPPORTED_EXTENSIONS = setOf("flac", "mp3")

    fun scan(directory: File): List<File> {
        require(directory.isDirectory) { "Not a directory: ${directory.absolutePath}" }
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            .sortedBy { it.absolutePath }
            .toList()
    }
}
