package com.v2ray.ang.viewmodel

/**
 * Base interface for ViewModel UI events.
 */
sealed interface ViewModelEvent

/**
 * Common UI events for all ViewModels.
 */
sealed interface BaseViewModelEvent : ViewModelEvent {
    object FinishActivity : BaseViewModelEvent
}
