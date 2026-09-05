package com.v2ray.ang.ui.backup

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

enum class BackupChannel { LOCAL, WEBDAV }

enum class WebDavField { URL, USERNAME, PASSWORD, PATH }

@Immutable
data class WebDavForm(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/"
) {
    val configured: Boolean get() = baseUrl.isNotBlank()
    val summary: String? get() = baseUrl.takeIf { it.isNotBlank() }

    fun updated(field: WebDavField, value: String): WebDavForm = when (field) {
        WebDavField.URL -> copy(baseUrl = value)
        WebDavField.USERNAME -> copy(username = value)
        WebDavField.PASSWORD -> copy(password = value)
        WebDavField.PATH -> copy(remotePath = value)
    }
}

fun WebDavConfig?.toForm(): WebDavForm = this?.let {
    WebDavForm(
        baseUrl = it.baseUrl,
        username = it.username.orEmpty(),
        password = it.password.orEmpty(),
        remotePath = it.remoteBasePath
    )
} ?: WebDavForm()

fun WebDavForm.toConfig(): WebDavConfig = WebDavConfig(
    baseUrl = baseUrl.trim(),
    username = username.trim().ifEmpty { null },
    password = password,
    remoteBasePath = remotePath.trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
)

/**
 * [webDav] is the persisted configuration, [draft] the edit buffer of the WebDAV dialog: the form
 * has exactly one source of truth, and the dialog's *visibility* stays out of the state.
 */
@Immutable
data class BackupUiState(
    val webDav: WebDavForm = WebDavForm(),
    val draft: WebDavForm = WebDavForm()
) : BaseUiState

sealed interface BackupAction : BaseAction {
    data object Back : BackupAction
    data object BackupClicked : BackupAction
    data object RestoreClicked : BackupAction
    data object ShareClicked : BackupAction
    data object WebDavClicked : BackupAction
    data object WebDavSaved : BackupAction
    data object CleanupClicked : BackupAction
    data object CleanupConfirmed : BackupAction
    data class ChannelSelected(val channel: BackupChannel) : BackupAction
    data class WebDavFieldChanged(val field: WebDavField, val value: String) : BackupAction
    data class ExportUriSelected(val uri: Uri?) : BackupAction
    data class ImportUriSelected(val uri: Uri?) : BackupAction
    data class ShareResult(val path: String, val ok: Boolean) : BackupAction
}

/**
 * The first three are screen-scoped (intercepted by `BackupScreen.onEvent`), the last three are
 * platform capabilities translated by `BackupActivity`.
 */
sealed interface BackupEvent : BaseEvent.Platform {
    data class ShowChannelPicker(val restoring: Boolean) : BackupEvent
    data object ShowWebDavEditor : BackupEvent
    data object ShowCleanupConfirmation : BackupEvent
    data class CreateDocument(val fileName: String) : BackupEvent
    data object PickFile : BackupEvent
    data class ShareFile(val path: String) : BackupEvent
}
