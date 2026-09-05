package com.v2ray.ang.ui.subscription

import com.v2ray.ang.R
import com.v2ray.ang.dto.SubUpdateOptions
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.repository.SubRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SubSettingViewModel(
    private val repo: SubRepository
) : BaseViewModel<SubUiState, SubAction>(
    SubUiState(confirmRemove = repo.confirmRemove())
) {

    private var subscriptions: List<SubscriptionCache> = emptyList()
    private var persistedOptions = SubUpdateOptions()
    private var changed = false
    private var finishing = false
    private var writeJob: Job? = null

    private val _updateProgress = MutableStateFlow<SubUpdateProgress?>(null)
    val updateProgress: StateFlow<SubUpdateProgress?> = _updateProgress.asStateFlow()

    init {
        load()
    }

    override fun onAction(action: SubAction) {
        when (action) {
            SubAction.Back -> finish()
            SubAction.Add -> navigate(AppRoute.SubEdit())
            is SubAction.Edit -> edit(action.subId)
            is SubAction.RemoveConfirmed -> remove(action.subId)
            is SubAction.ToggleEnabled -> toggleEnabled(action.subId, action.enabled)
            is SubAction.Move -> move(action.fromId, action.toId)
            SubAction.OpenUpdateOptions -> platform(SubEvent.ShowUpdateOptions)
            is SubAction.UpdateOptionChanged ->
                setState { copy(updateOptions = action.field.set(updateOptions, action.value)) }
            SubAction.ConfirmUpdateOptions -> confirmUpdateOptions()
            SubAction.DismissUpdateOptions -> setState { copy(updateOptions = persistedOptions) }
            is SubAction.ShareClicked -> platform(SubEvent.ShowShare(action.url))
            is SubAction.ShareMethodSelected -> share(action.method, action.url)
            is SubAction.ResultReceived -> if (action.result.isOk) {
                changed = true
                launch(loading = true) { reload() }
            }
        }
    }

    override fun onCleared() {
        writeJob?.cancel()
        writeJob = null
        super.onCleared()
    }

    // ===== Loading =====

    private fun load() = launch(loading = true) {
        persistedOptions = repo.loadUpdateOptions()
        setState { copy(updateOptions = persistedOptions) }
        reload()
    }

    private suspend fun reload() {
        awaitWrites()
        subscriptions = repo.loadSubscriptions()
        val rows = subscriptions.toSubRows()
        setState { copy(subscriptions = rows) }
    }

    // ===== Serialised writes =====

    private fun enqueueWrite(block: suspend () -> Unit): Job {
        val previous = writeJob
        val job = launch(onError = { toastError() }) {
            previous?.join()
            withContext(NonCancellable) { block() }
        }
        writeJob = job
        return job
    }

    private suspend fun awaitWrites() {
        writeJob?.join()
    }

    // ===== Mutations =====

    private fun remove(subId: String) = launch(loading = true) {
        awaitWrites()
        repo.remove(subId)
        changed = true
        reload()
        toastSuccess()
    }

    private fun toggleEnabled(subId: String, enabled: Boolean) {
        val index = subscriptions.indexOfFirst { it.guid == subId }
        if (index < 0) return
        val current = subscriptions[index].subscription
        if (current.enabled == enabled) return

        val item = current.copy().also { it.enabled = enabled }
        subscriptions = subscriptions.toMutableList()
            .also { it[index] = SubscriptionCache(subId, item) }
        val rows = subscriptions.toSubRows()
        setState { copy(subscriptions = rows) }
        changed = true
        enqueueWrite { repo.updateItem(subId, item) }
    }

    private fun move(fromId: String, toId: String) {
        val list = subscriptions.toMutableList()
        val from = list.indexOfFirst { it.guid == fromId }
        val to = list.indexOfFirst { it.guid == toId }
        if (from < 0 || to < 0 || from == to) return

        list.add(to, list.removeAt(from))
        subscriptions = list
        val rows = list.toSubRows()
        setState { copy(subscriptions = rows) }
        changed = true

        val guids = list.map { it.guid }
        enqueueWrite { repo.saveOrder(guids) }
    }

    private fun edit(subId: String) = launch {
        awaitWrites()
        navigate(AppRoute.SubEdit(subId))
    }

    // ===== Update options =====

    private fun confirmUpdateOptions() = launch(loading = true) {
        val options = state.updateOptions
        repo.saveUpdateOptions(options)
        persistedOptions = options
        platform(SubEvent.CloseUpdateOptions)
        performUpdate(options)
    }

    private suspend fun performUpdate(options: SubUpdateOptions) {
        if (!options.updateSubscription) {
            toastInfo(R.string.title_sub_update)
            return
        }

        if (options.autoTestAfterUpdate) {
            repo.updateInBackground()
            changed = true
            toast(R.string.subscription_updater_job_tips)
            return
        }

        awaitWrites()
        val result = try {
            repo.updateAll { done, total -> _updateProgress.value = SubUpdateProgress(done, total) }
        } finally {
            _updateProgress.value = null
        }

        val total = result.successCount + result.failureCount + result.skipCount
        when {
            total == 0 -> toast(R.string.title_update_subscription_no_subscription)
            result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                toast(BaseText.of(R.string.title_update_config_count, result.configCount))
            else -> toast(
                BaseText.of(
                    R.string.title_update_subscription_result,
                    result.configCount,
                    result.successCount,
                    result.failureCount,
                    result.skipCount
                )
            )
        }
        changed = true
        reload()
    }

    // ===== Share =====

    private fun share(method: ShareMethod, url: String) {
        when (method) {
            ShareMethod.QR_CODE -> launch(loading = true) {
                val bitmap = repo.createQrCode(url)
                if (bitmap == null) toastError()
                else platform(SubEvent.ShowQrCode(bitmap))
            }
            ShareMethod.CLIPBOARD -> launch {
                if (repo.copyToClipboard(url)) toastSuccess() else toastError()
            }
        }
    }

    // ===== Finishing =====

    private fun finish() {
        if (finishing) return
        finishing = true
        launch {
            awaitWrites()
            finishWith(
                if (changed) BaseResult.Changed(refreshList = true) else BaseResult.Cancelled
            )
        }
    }
}
