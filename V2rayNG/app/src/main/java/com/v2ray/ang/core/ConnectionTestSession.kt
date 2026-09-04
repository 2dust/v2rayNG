package com.v2ray.ang.core

import com.v2ray.ang.dto.ConnectionTestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CoreConnectionState(
    val profileGuid: String? = null,
    val isTesting: Boolean = false,
    val result: ConnectionTestResult? = null,
) {
    val isRunning: Boolean get() = profileGuid != null
}

/** Daemon-session state: survives widget compositions, but never a stopped or replaced core. */
internal class ConnectionTestSession {
    internal data class Request(val profileGuid: String, val generation: Long)

    private val mutableState = MutableStateFlow(CoreConnectionState())
    val state = mutableState.asStateFlow()
    private var generation = 0L

    @Synchronized
    fun started(profileGuid: String) {
        generation++
        mutableState.value = CoreConnectionState(profileGuid)
    }

    @Synchronized
    fun stopped() {
        generation++
        mutableState.value = CoreConnectionState()
    }

    @Synchronized
    fun beginTest(): Request? {
        val current = mutableState.value
        val guid = current.profileGuid ?: return null
        val request = Request(guid, ++generation)
        mutableState.value = current.copy(isTesting = true, result = null)
        return request
    }

    @Synchronized
    fun complete(request: Request, result: ConnectionTestResult): Boolean {
        if (request.generation != generation || request.profileGuid != mutableState.value.profileGuid ||
            !mutableState.value.isTesting
        ) {
            return false
        }
        mutableState.value = CoreConnectionState(request.profileGuid, result = result)
        return true
    }
}
