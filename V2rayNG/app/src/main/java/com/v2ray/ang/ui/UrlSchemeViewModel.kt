package com.v2ray.ang.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.RemoteControlManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface UrlSchemeState {
    data object Loading : UrlSchemeState
    data class Pending(val content: String) : UrlSchemeState
    data object Importing : UrlSchemeState
    data object Imported : UrlSchemeState
    data object Failed : UrlSchemeState
    data object Cancelled : UrlSchemeState
}

internal enum class UrlSchemeAction { Confirm, Cancel }

internal class UrlSchemeViewModel internal constructor(
    application: Application,
    private val configs: AngConfigManager,
    private val remoteControl: RemoteControlManager,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, AngConfigManager, RemoteControlManager)

    private val _state = MutableStateFlow<UrlSchemeState>(UrlSchemeState.Loading)
    val state = _state.asStateFlow()
    private var initialized = false

    fun initialize(
        action: String?,
        type: String?,
        data: String?,
        sharedText: String?,
        senderUid: Int,
        claimedPackage: String?,
        token: String?,
    ) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.Default) {
                    UrlSchemeRequest.parse(action, type, data, sharedText)
                }
                if (_state.value == UrlSchemeState.Cancelled) return@launch
                if (content == null) {
                    _state.value = UrlSchemeState.Failed
                    return@launch
                }
                val authorized = withContext(Dispatchers.IO) {
                    remoteControl.isAuthorized(getApplication(), senderUid, claimedPackage, token)
                }
                if (_state.value == UrlSchemeState.Cancelled) return@launch
                if (authorized) {
                    // Automation grants also permit replacing profiles for unattended updates.
                    importContent(content, append = false)
                } else {
                    _state.value = UrlSchemeState.Pending(content)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun onAction(action: UrlSchemeAction) {
        when (action) {
            UrlSchemeAction.Cancel -> {
                if (_state.value == UrlSchemeState.Loading || _state.value is UrlSchemeState.Pending) {
                    _state.value = UrlSchemeState.Cancelled
                }
            }
            UrlSchemeAction.Confirm -> {
                val pending = _state.value as? UrlSchemeState.Pending ?: return
                _state.value = UrlSchemeState.Importing
                viewModelScope.launch { importContent(pending.content, append = true) }
            }
        }
    }

    private suspend fun importContent(content: String, append: Boolean) {
        _state.value = UrlSchemeState.Importing
        try {
            val (profiles, subscriptions) = withContext(Dispatchers.IO) {
                configs.importBatchConfig(content, "", append)
            }
            _state.value = if (profiles + subscriptions > 0) {
                UrlSchemeState.Imported
            } else {
                UrlSchemeState.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun fail(error: Exception) {
        // Parser exception messages can contain the supplied credentials or URL.
        LogUtil.e(AppConfig.TAG, "UrlSchemeViewModel: external import failed (${error.javaClass.simpleName})")
        _state.value = UrlSchemeState.Failed
    }
}
