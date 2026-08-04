package com.v2ray.ang.ui.logcat

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.LogFileManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogFileViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        /** How often the file is checked for new content while the screen is open. */
        private const val POLL_INTERVAL_MS = 1000L
    }

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private var watchJob: Job? = null

    /**
     * Reads the file and keeps re-reading it while it grows, so the core writes show up live.
     *
     * @param path The absolute file path.
     */
    fun startWatching(path: String) {
        if (watchJob?.isActive == true) return

        watchJob = viewModelScope.launch {
            _isLoading.value = true
            var lastSize = -1L
            var lastModified = -1L
            try {
                while (isActive) {
                    val file = File(path)
                    val size = file.length()
                    val modified = file.lastModified()

                    if (size != lastSize || modified != lastModified) {
                        lastSize = size
                        lastModified = modified
                        // Хронологический порядок: свежие строки внизу, как в logcat
                        _lines.value = withContext(Dispatchers.IO) { LogFileManager.readLogFile(path) }
                    }
                    _isLoading.value = false
                    delay(POLL_INTERVAL_MS)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    fun copyToClipboard() {
        Utils.setClipboard(app, lines.value.joinToString("\n"))
        toast(R.string.toast_success)
    }

    fun clear(path: String) {
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) { LogFileManager.clearLogFile(path) }
            if (cleared) {
                _lines.value = emptyList()
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
        }
    }

    override fun onCleared() {
        stopWatching()
        super.onCleared()
    }
}
