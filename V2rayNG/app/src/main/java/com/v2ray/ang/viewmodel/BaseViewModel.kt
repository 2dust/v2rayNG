package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.extension.myApp
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel that encapsulates common UI event handling logic.
 */
abstract class BaseViewModel : ViewModel() {

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
        myApp.toast(resId)
    }

    /**
     * Send neutral toast event (String).
     */
    fun toast(message: String) {
        myApp.toast(message)
    }

    /**
     * Send success toast event (Resource ID).
     */
    fun toastSuccess(resId: Int) {
        myApp.toastSuccess(resId)
    }

    /**
     * Send success toast event (String).
     */
    fun toastSuccess(message: String) {
        myApp.toastSuccess(message)
    }

    /**
     * Send error toast event (Resource ID).
     */
    fun toastError(resId: Int) {
        myApp.toastError(resId)
    }

    /**
     * Send error toast event (String).
     */
    fun toastError(message: String) {
        myApp.toastError(message)
    }

    /**
     * Get string from resource ID.
     */
    fun getString(resId: Int): String {
        return myApp.getString(resId)
    }

    /**
     * Get formatted string from resource ID.
     */
    fun getString(resId: Int, vararg formatArgs: Any?): String {
        return myApp.getString(resId, *formatArgs)
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
