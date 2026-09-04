package com.v2ray.ang.ui.widget

import com.v2ray.ang.core.CoreConnectionState
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class WidgetProfile(val guid: String, val name: String)

internal data class LauncherWidgetState(
    val profile: WidgetProfile?,
    val connection: CoreConnectionState,
) {
    val isRunning: Boolean get() = connection.isRunning
    val isConnected: Boolean get() = isRunning && profile?.guid == connection.profileGuid
    val isTesting: Boolean get() = isConnected && connection.isTesting
    val result get() = connection.result.takeIf { isConnected }
    val canTest: Boolean get() = isConnected && !isTesting
}

/** Loads profile storage outside composition; connection state belongs to the live daemon. */
internal class LauncherWidgetStateRepository(
    private val readProfile: () -> WidgetProfile?,
    private val connection: StateFlow<CoreConnectionState>,
) {
    private val profile = MutableStateFlow<WidgetProfile?>(null)
    private val refreshMutex = Mutex()
    val states = combine(profile, connection, ::LauncherWidgetState)

    suspend fun refresh(): LauncherWidgetState = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            profile.value = readProfile()
            LauncherWidgetState(profile.value, connection.value)
        }
    }

    companion object {
        // Access only in the daemon process, where all Glance components are registered.
        val instance by lazy {
            LauncherWidgetStateRepository(
                readProfile = {
                    MmkvManager.getSelectServer()?.let { guid ->
                        MmkvManager.decodeServerConfig(guid)?.let { WidgetProfile(guid, it.remarks) }
                    }
                },
                connection = CoreServiceManager.connectionState,
            )
        }
    }
}
