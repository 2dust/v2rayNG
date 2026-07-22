package com.dalulong.app.ui.base

/**
 * Base interface for ViewModel UI events.
 */
interface ViewModelEvent

/**
 * Common UI events for all ViewModels.
 */
interface BaseViewModelEvent : ViewModelEvent {
    object FinishActivity : BaseViewModelEvent
}
