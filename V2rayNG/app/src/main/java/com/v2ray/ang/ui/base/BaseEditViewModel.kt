package com.v2ray.ang.ui.base

/**
 * Parent of every editor screen (Server*, SubEdit, RoutingEdit, UserAssetUrl…).
 *
 * Subclasses implement [doSave] / [doDelete]:
 * `null` means validation failed and the screen stays open (the subclass reports which field via
 * [toastError]); a [BaseResult] means success and the framework closes the screen with it.
 *
 * The success toast is shown by the caller from [BaseResult.notify], because the child's snackbar
 * host is already gone once it finishes.
 */
abstract class BaseEditViewModel<S : BaseUiState, A : BaseAction>(
    initialState: S,
) : BaseViewModel<S, A>(initialState) {

    /** Validate and persist. Return `null` to keep the screen open. */
    protected abstract suspend fun doSave(): BaseResult?

    /** Delete the edited entity. Returns `null` by default (screen has no delete action). */
    protected open suspend fun doDelete(): BaseResult? = null

    protected fun save() = launch(loading = true) { doSave()?.let(::finishWith) }

    protected fun delete() = launch(loading = true) { doDelete()?.let(::finishWith) }

    protected fun cancel() = finishWith(BaseResult.Cancelled)
}
