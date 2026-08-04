package com.v2ray.ang.ui.logcat

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.handler.LogFileManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LogFileViewModel(application: Application) : BaseViewModel(application) {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun load(path: String) {
        launchLoading {
            _lines.value = withContext(Dispatchers.IO) { LogFileManager.readLogFile(path) }
        }
    }

    fun copyToClipboard() {
        Utils.setClipboard(app, lines.value.joinToString("\n"))
        toast(R.string.toast_success)
    }

    fun clear(path: String) {
        launchLoading {
            val cleared = withContext(Dispatchers.IO) { LogFileManager.clearLogFile(path) }
            if (cleared) {
                _lines.value = emptyList()
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
        }
    }
}
