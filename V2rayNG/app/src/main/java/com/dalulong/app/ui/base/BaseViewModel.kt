package com.dalulong.app.ui.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalulong.app.AngApplication
import com.dalulong.app.extension.toast
import com.dalulong.app.extension.toastError
import com.dalulong.app.extension.toastSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel that encapsulates common UI event handling logic.
 */
abstract class BaseViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Safe access to custom application instance.
     */
    protected val app: AngApplication by lazy {
        application as AngApplication
    }

    @Suppress("PropertyName")
    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @Suppress("PropertyName")
    protected val _viewModelEvent = Channel<ViewModelEvent>()
    val viewModelEvent = _viewModelEvent.receiveAsFlow()

    /**
     * Send neutral toast event (Resource ID).
     */
    fun toast(resId: Int) {
        app.toast(resId)
    }

    /**
     * Send neutral toast event (String).
     */
    fun toast(message: String) {
        app.toast(message)
    }

    /**
     * Send success toast event (Resource ID).
     */
    fun toastSuccess(resId: Int) {
        app.toastSuccess(resId)
    }

    /**
     * Send success toast event (String).
     */
    fun toastSuccess(message: String) {
        app.toastSuccess(message)
    }

    /**
     * Send error toast event (Resource ID).
     */
    fun toastError(resId: Int) {
        app.toastError(resId)
    }

    /**
     * Send error toast event (String).
     */
    fun toastError(message: String) {
        app.toastError(message)
    }

    /**
     * Get string from resource ID.
     */
    fun getString(resId: Int): String {
        return app.getString(resId)
    }

    /**
     * Get formatted string from resource ID.
     */
    fun getString(resId: Int, vararg formatArgs: Any?): String {
        return app.getString(resId, *formatArgs)
    }

    /**
     * Send finish activity event.
     */
    fun finishActivity() {
        viewModelScope.launch {
            _viewModelEvent.send(BaseViewModelEvent.FinishActivity)
        }
    }

    /**
     * Helper method: execute task in coroutine and automatically manage isLoading state.
     */
    protected fun launchLoading(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
            } finally {
                _isLoading.value = false
            }
        }
    }
}