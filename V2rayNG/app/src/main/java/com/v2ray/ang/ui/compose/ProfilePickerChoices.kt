package com.v2ray.ang.ui.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

private class ProfileChoicesViewModel(app: Application, load: () -> List<String>) : BaseViewModel(app) {
    val choices = flow { emit(load()) }.flowOn(Dispatchers.IO).catch {
        LogUtil.e(message = "Profile picker: failed to load choices", throwable = it)
        toastError(R.string.toast_failure)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** Enter only while a picker is open. Each editor uses one choice list, retained across its dialogs. */
@Composable
internal fun ProfilePickerChoices(load: () -> List<String>, onDismiss: () -> Unit, content: @Composable (List<String>) -> Unit) {
    val owner = LocalActivity.current as ComponentActivity
    val model = remember(owner) {
        val factory = viewModelFactory { initializer { ProfileChoicesViewModel(checkNotNull(this[APPLICATION_KEY]), load) } }
        ViewModelProvider(owner, factory)[ProfileChoicesViewModel::class.java]
    }
    val choices = model.choices.collectAsStateWithLifecycle().value
    if (choices == null) Dialog(onDismissRequest = onDismiss) { CircularProgressIndicator() } else content(choices)
}
