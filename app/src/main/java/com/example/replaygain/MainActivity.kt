package com.example.replaygain

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.replaygain.ui.MainViewModel
import com.example.replaygain.util.FFmpegBinaryHelper
import com.example.replaygain.util.PermissionHelper
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var tvDirectory: TextView
    private lateinit var btnSelectDirectory: Button
    private lateinit var cbSkipExisting: CheckBox
    private lateinit var btnStart: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initFfmpeg()
        observeUiState()
        checkPermission()
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasManageExternalStorage(this)) {
            btnSelectDirectory.isEnabled = true
            btnStart.isEnabled = viewModel.uiState.value.canStart
        } else {
            btnSelectDirectory.isEnabled = false
            btnStart.isEnabled = false
        }
    }

    private fun initViews() {
        tvDirectory = findViewById(R.id.tvDirectory)
        btnSelectDirectory = findViewById(R.id.btnSelectDirectory)
        cbSkipExisting = findViewById(R.id.cbSkipExisting)
        btnStart = findViewById(R.id.btnStart)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        scrollView = findViewById(R.id.scrollView)

        btnSelectDirectory.setOnClickListener { openDirectoryPicker() }
        btnStart.setOnClickListener {
            if (!PermissionHelper.hasManageExternalStorage(this)) {
                showPermissionDialog()
                return@setOnClickListener
            }
            viewModel.startProcessing(cbSkipExisting.isChecked)
        }
    }

    private fun initFfmpeg() {
        lifecycleScope.launch {
            try {
                val ffmpegPath = FFmpegBinaryHelper.getOrExtractFfmpeg(applicationContext)
                val nativeLibDir = applicationContext.applicationInfo.nativeLibraryDir
                viewModel.initFfmpeg(ffmpegPath, nativeLibDir)
            } catch (e: Exception) {
                val message = "FFmpeg 初始化失败：${e.message}"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                tvLog.append("[ERROR] $message\n")
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        tvDirectory.text = state.directoryPath.ifBlank {
                            getString(R.string.no_directory_selected)
                        }
                        btnStart.isEnabled = state.canStart && !state.isProcessing &&
                                PermissionHelper.hasManageExternalStorage(this@MainActivity)
                        progressBar.visibility = if (state.isProcessing) {
                            ProgressBar.VISIBLE
                        } else {
                            ProgressBar.GONE
                        }
                        tvStatus.text = state.status
                    }
                }

                launch {
                    viewModel.logs.collect { logs ->
                        tvLog.text = logs
                        scrollView.post {
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                }
            }
        }
    }

    private fun openDirectoryPicker() {
        if (!PermissionHelper.hasManageExternalStorage(this)) {
            showPermissionDialog()
            return
        }

        val initialDir = viewModel.uiState.value.directoryPath
            .takeIf { it.isNotBlank() && File(it).isDirectory }
            ?: Environment.getExternalStorageDirectory().absolutePath

        showDirectoryBrowser(File(initialDir))
    }

    private fun showDirectoryBrowser(currentDir: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_directory_picker, null)
        val tvPath = dialogView.findViewById<TextView>(R.id.tvCurrentPath)
        val listView = dialogView.findViewById<ListView>(R.id.listDirectories)
        val btnSelectCurrent = dialogView.findViewById<Button>(R.id.btnSelectCurrent)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        fun refresh(current: File) {
            tvPath.text = current.absolutePath

            val entries = mutableListOf<String>()
            current.parentFile?.let {
                entries.add(getString(R.string.parent_directory))
            }

            val subDirs = current.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()

            entries.addAll(subDirs.map { it.name })

            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
            listView.setOnItemClickListener { _, _, position, _ ->
                when {
                    position == 0 && current.parentFile != null -> refresh(current.parentFile!!)
                    current.parentFile != null -> {
                        val selected = subDirs[position - 1]
                        refresh(selected)
                    }
                    else -> {
                        val selected = subDirs[position]
                        refresh(selected)
                    }
                }
            }
        }

        btnSelectCurrent.setOnClickListener {
            viewModel.setDirectory(currentDir)
            dialog.dismiss()
        }

        refresh(currentDir)
        dialog.show()
    }

    private fun checkPermission() {
        if (!PermissionHelper.hasManageExternalStorage(this)) {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage("本应用需要「所有文件访问权限」才能扫描并修改您选择的工作目录中的音乐标签。")
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                PermissionHelper.requestManageExternalStorage(this)
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }
}
