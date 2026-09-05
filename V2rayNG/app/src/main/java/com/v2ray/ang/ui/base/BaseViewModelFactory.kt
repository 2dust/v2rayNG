package com.v2ray.ang.ui.base

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * One factory for all ViewModels, replacing the per-ViewModel inner `Factory` classes.
 *
 * The [Application] handed to [creator] is meant only for building data-layer dependencies
 * (repositories / data sources) inside the lambda; storing it in the ViewModel would break the
 * "no Context in the ViewModel" rule. The [SavedStateHandle] keeps editor input across process
 * death.
 */
class BaseViewModelFactory<VM : ViewModel>(
    private val creator: (Application, SavedStateHandle) -> VM,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
            "BaseViewModelFactory requires an Application in CreationExtras"
        }
        return creator(app, extras.createSavedStateHandle()) as T
    }
}

/** `private val vm by baseViewModels { app, handle -> XxxViewModel(XxxRepository(app), handle) }` */
inline fun <reified VM : ViewModel> ComponentActivity.baseViewModels(
    noinline creator: (Application, SavedStateHandle) -> VM,
) = viewModels<VM> { BaseViewModelFactory(creator) }
