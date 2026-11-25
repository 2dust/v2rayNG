package com.v2ray.ang.ui.server

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

internal class GroupEditorProfilesViewModel(application: Application) : BaseViewModel(application) {
    val remarks = flow { emit(SettingsManager.getSimpleProfileRemarks()) }
        .flowOn(Dispatchers.IO)
        .catch { error ->
            LogUtil.e(message = "Group editor: failed to load profile choices", throwable = error)
            toastError(R.string.toast_failure)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
internal fun GroupEditorLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
