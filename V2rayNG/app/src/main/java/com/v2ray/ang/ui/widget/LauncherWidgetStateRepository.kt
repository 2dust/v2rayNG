package com.v2ray.ang.ui.widget

import android.content.Context
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class LauncherWidgetLayout {
    COMPACT,
    MEDIUM,
    WIDE,
    EXTRA_WIDE,
}

internal enum class LauncherWidgetStatusTone {
    NORMAL,
    SUCCESS,
    ERROR,
}

internal data class LauncherWidgetUiState(
    val profileName: String,
    val isRunning: Boolean,
    val canTest: Boolean,
    val serviceStatus: String,
    val connectionStatus: String,
    val connectionStatusTone: LauncherWidgetStatusTone,
    val startActionLabel: String,
    val stopActionLabel: String,
    val testActionLabel: String,
    val restartActionLabel: String,
)

internal data class LauncherWidgetTextMetrics(
    val profileFontSizeSp: Float,
    val statusFontSizeSp: Float,
    val startPaddingDp: Float,
    val endPaddingDp: Float,
    val verticalPaddingDp: Float,
)

private data class StoredLauncherWidgetState(
    val isTesting: Boolean = false,
    val profileGuid: String? = null,
    val result: ConnectionTestResult? = null,
)

internal fun launcherWidgetLayoutForWidth(widthDp: Float): LauncherWidgetLayout = when {
    widthDp >= 320f -> LauncherWidgetLayout.EXTRA_WIDE
    widthDp >= 240f -> LauncherWidgetLayout.WIDE
    widthDp >= 110f -> LauncherWidgetLayout.MEDIUM
    else -> LauncherWidgetLayout.COMPACT
}

internal fun launcherWidgetRunningState(
    coreIsRunning: Boolean,
    serviceReportedStopped: Boolean,
): Boolean = coreIsRunning && !serviceReportedStopped

internal fun launcherWidgetServiceStatusResource(isRunning: Boolean): Int =
    if (isRunning) R.string.widget_status_connected else R.string.widget_status_disconnected

internal fun launcherWidgetTextMetrics(fontScale: Float): LauncherWidgetTextMetrics =
    if (fontScale >= 1.8f) {
        LauncherWidgetTextMetrics(
            profileFontSizeSp = 12f,
            statusFontSizeSp = 8f,
            startPaddingDp = 4f,
            endPaddingDp = 2f,
            verticalPaddingDp = 0f,
        )
    } else {
        LauncherWidgetTextMetrics(
            profileFontSizeSp = 14f,
            statusFontSizeSp = 12f,
            startPaddingDp = 8f,
            endPaddingDp = 4f,
            verticalPaddingDp = 5f,
        )
    }

internal fun buildLauncherWidgetConnectionDetails(
    delayLabel: String?,
    errorMessage: String,
    country: String?,
    fallbackError: String,
): String {
    if (delayLabel == null) {
        return errorMessage.ifBlank { fallbackError }
    }

    return listOfNotNull(
        delayLabel,
        country?.trim()?.takeIf(String::isNotEmpty),
    ).joinToString(" · ")
}

internal object LauncherWidgetStateRepository {
    private const val CACHE_KEY = "launcher_widget_state_v1"
    private const val SERVICE_STOPPED_KEY = "launcher_widget_service_stopped_v1"
    private val mutableStateRevision = MutableStateFlow(0L)

    // Every Glance component is configured in the daemon process. This process-local signal makes
    // an active Glance composition re-read the multi-process MMKV state immediately; update() alone
    // does not restart provideGlance while that composition is still alive.
    val stateRevision = mutableStateRevision.asStateFlow()

    @Synchronized
    fun recordServiceState(isRunning: Boolean) {
        // stopCoreLoop broadcasts before the native controller's running flag falls. Retain the
        // daemon event so a recomposition during that short shutdown window cannot show Connected.
        MmkvManager.encodeSettings(SERVICE_STOPPED_KEY, !isRunning)
        if (!isRunning) write(StoredLauncherWidgetState())
        notifyStateChanged()
    }

    @Synchronized
    fun startTesting(profileGuid: String) {
        write(
            StoredLauncherWidgetState(
                isTesting = true,
                profileGuid = profileGuid,
            )
        )
        notifyStateChanged()
    }

    @Synchronized
    fun storeResult(result: ConnectionTestResult, selectedProfileGuid: String?) {
        val current = read()
        write(
            StoredLauncherWidgetState(
                profileGuid = current.profileGuid ?: selectedProfileGuid,
                result = result,
            )
        )
        notifyStateChanged()
    }

    @Synchronized
    fun clearTestState() {
        write(StoredLauncherWidgetState())
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        mutableStateRevision.update { it + 1L }
    }

    fun loadUiState(context: Context, coreIsRunning: Boolean): LauncherWidgetUiState {
        val isRunning = launcherWidgetRunningState(
            coreIsRunning = coreIsRunning,
            serviceReportedStopped = MmkvManager.decodeSettingsBool(SERVICE_STOPPED_KEY, false),
        )
        val selectedProfileGuid = MmkvManager.getSelectServer()
        val profileName = selectedProfileGuid
            ?.let(MmkvManager::decodeServerConfig)
            ?.remarks
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.widget_no_profile)
        val stored = read().takeIf { it.profileGuid == selectedProfileGuid }

        val connectionDetails = when {
            !isRunning -> ""
            stored?.isTesting == true -> context.getString(R.string.connection_test_testing)
            stored?.result != null -> buildLauncherWidgetConnectionDetails(
                delayLabel = stored.result.delayMillis.takeIf { it >= 0L }?.let {
                    context.getString(R.string.server_test_delay_value, it)
                },
                errorMessage = stored.result.errorMessage,
                country = stored.result.country,
                fallbackError = context.getString(R.string.connection_test_empty_message),
            )
            else -> ""
        }
        val connectionStatus = buildList {
            add(
                context.getString(
                    if (isRunning) R.string.widget_status_connected
                    else R.string.widget_status_disconnected
                )
            )
            if (connectionDetails.isNotBlank()) add(connectionDetails)
        }.joinToString(" · ")
        val statusTone = when {
            !isRunning || stored?.isTesting == true || stored?.result == null ->
                LauncherWidgetStatusTone.NORMAL
            stored.result.delayMillis >= 0L -> LauncherWidgetStatusTone.SUCCESS
            else -> LauncherWidgetStatusTone.ERROR
        }

        return LauncherWidgetUiState(
            profileName = profileName,
            isRunning = isRunning,
            canTest = isRunning && !selectedProfileGuid.isNullOrBlank(),
            serviceStatus = context.getString(launcherWidgetServiceStatusResource(isRunning)),
            connectionStatus = connectionStatus,
            connectionStatusTone = statusTone,
            startActionLabel = context.getString(R.string.acc_start),
            stopActionLabel = context.getString(R.string.acc_stop),
            testActionLabel = context.getString(R.string.connection_test_pending),
            restartActionLabel = context.getString(R.string.title_service_restart),
        )
    }

    private fun read(): StoredLauncherWidgetState {
        val json = MmkvManager.decodeSettingsString(CACHE_KEY)
        if (json.isNullOrBlank()) return StoredLauncherWidgetState()
        return JsonUtil.fromJsonSafe(json, StoredLauncherWidgetState::class.java)
            ?: StoredLauncherWidgetState()
    }

    private fun write(state: StoredLauncherWidgetState) {
        if (!MmkvManager.encodeSettings(CACHE_KEY, JsonUtil.toJson(state))) {
            LogUtil.e("LauncherWidget", "Failed to persist launcher widget test state")
        }
    }
}
