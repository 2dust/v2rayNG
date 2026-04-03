package com.daggomostudios.simpsonsvpn

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class DebugInfo(
    var status: String = "Initializing",
    var httpStatus: String = "N/A",
    var decryptionStatus: String = "N/A",
    var jsonParseStatus: String = "N/A",
    var serversLoaded: String = "N/A",
    var errorMessage: String = "None"
)

class DebugViewModel : ViewModel() {
    val debugInfo = MutableLiveData(DebugInfo())

    fun updateStatus(status: String) {
        debugInfo.value = debugInfo.value?.copy(status = status)
    }

    fun updateHttpStatus(status: String) {
        debugInfo.value = debugInfo.value?.copy(httpStatus = status)
    }

    fun updateDecryptionStatus(status: String) {
        debugInfo.value = debugInfo.value?.copy(decryptionStatus = status)
    }

    fun updateJsonParseStatus(status: String) {
        debugInfo.value = debugInfo.value?.copy(jsonParseStatus = status)
    }

    fun updateServersLoaded(count: Int) {
        debugInfo.value = debugInfo.value?.copy(serversLoaded = count.toString())
    }

    fun updateError(message: String) {
        debugInfo.value = debugInfo.value?.copy(errorMessage = message)
    }

    fun reset() {
        debugInfo.value = DebugInfo()
    }
}
