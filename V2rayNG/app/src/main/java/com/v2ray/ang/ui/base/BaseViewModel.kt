package com.v2ray.ang.ui.base

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AppLocaleManager
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

    protected val localizedContext: Context
        get() = AppLocaleManager.localizedContext(app)

    @Suppress("PropertyName")
    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @Suppress("PropertyName")
    protected val _viewModelEvent = Channel<ViewModelEvent>()
    val viewModelEvent = _viewModelEvent.receiveAsFlow()

    /**
     * Show informational feedback from a string resource.
     */
    fun toast(resId: Int) {
        localizedContext.toast(resId)
    }

    /**
     * Show informational feedback.
     */
    fun toast(message: String) {
        app.toast(message)
    }

    /**
     * Show success feedback from a string resource.
     */
    fun toastSuccess(resId: Int) {
        localizedContext.toastSuccess(resId)
    }

    /**
     * Show success feedback.
     */
    fun toastSuccess(message: String) {
        app.toastSuccess(message)
    }

    /**
     * Show error feedback from a string resource.
     */
    fun toastError(resId: Int) {
        localizedContext.toastError(resId)
    }

    /**
     * Show error feedback.
     */
    fun toastError(message: String) {
        app.toastError(message)
    }

    /**
     * Get string from resource ID.
     */
    fun getString(resId: Int): String {
        return localizedContext.getString(resId)
    }

    /**
     * Get formatted string from resource ID.
     */
    fun getString(resId: Int, vararg formatArgs: Any?): String {
        return localizedContext.getString(resId, *formatArgs)
    }

    /**
     * Get a localized quantity string from a plurals resource.
     */
    fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any): String {
        return localizedContext.resources.getQuantityString(resId, quantity, *formatArgs)
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
