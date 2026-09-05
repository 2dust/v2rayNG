package com.v2ray.ang.ui.routing

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.toRuleRows
import com.v2ray.ang.repository.RoutingRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update

class RoutingSettingViewModel(
    private val repo: RoutingRepository,
) : BaseViewModel<RoutingUiState, RoutingAction>(RoutingUiState()) {

    private var rulesets: List<RulesetItem> = emptyList()
    private var writeJob: Job? = null
    private var changed = false
    private var finishing = false
    private var loadJob: Job? = null
    private var loadEpoch = 0L

    init { load() }

    override fun onAction(action: RoutingAction) {
        when (action) {
            RoutingAction.Back -> finish()
            RoutingAction.AddRule -> navigate(AppRoute.RoutingEdit(ruleId = ""))
            is RoutingAction.EditRule -> navigate(AppRoute.RoutingEdit(ruleId = action.ruleId))
            is RoutingAction.ToggleRule -> toggle(action.ruleId, action.enabled)
            is RoutingAction.MoveRule -> move(action.fromId, action.toId)
            is RoutingAction.SelectDomainStrategy -> selectDomainStrategy(action.value)
            RoutingAction.PresetClicked -> platform(RoutingEvent.ShowDialog(RoutingDialog.Presets))
            is RoutingAction.PresetSelected -> handleImport(PendingImport.Preset(action.type))
            RoutingAction.ImportFromClipboard -> handleImport(PendingImport.Clipboard)
            RoutingAction.ImportFromQrCode -> platform(RoutingEvent.ScanQrCode)
            is RoutingAction.QrCodeScanned ->
                action.text?.takeIf { it.isNotBlank() }
                    ?.let { handleImport(PendingImport.Text(it)) }
            RoutingAction.ExportToClipboard -> export()
            is RoutingAction.ConfirmImport -> launch(loading = true) { runImport(action.pending) }
            is RoutingAction.ResultReceived -> if (action.result.isOk) {
                changed = true
                load()
            }
        }
    }

    private fun load() {
        val epoch = ++loadEpoch
        loadJob?.cancel()
        loadJob = launch {
            val strategy = repo.getDomainStrategy()
            if (loadEpoch != epoch) return@launch
            reload(epoch)
            setState { copy(domainStrategy = strategy) }
        }
    }

    private suspend fun reload(epoch: Long) {
        awaitWrites()
        if (loadEpoch != epoch) return
        rulesets = repo.loadRulesets()
        if (loadEpoch != epoch) return
        setState { copy(rules = rulesets.toRuleRows()) }
    }

    private fun enqueueWrite(block: suspend () -> Unit): Job {
        val previous = writeJob
        val job = launch(onError = { toastError() }) {
            previous?.join()
            block()
        }
        writeJob = job
        job.invokeOnCompletion { if (writeJob === job) writeJob = null }
        return job
    }

    private suspend fun awaitWrites() { writeJob?.join() }

    private fun toggle(ruleId: String, enabled: Boolean) {
        val index = rulesets.indexOfFirst { it.id == ruleId }
        if (index < 0) return
        val updated = rulesets[index].copy(enabled = enabled)
        rulesets = rulesets.toMutableList().also { it[index] = updated }
        setState { copy(rules = rulesets.toRuleRows()) }
        changed = true
        enqueueWrite { repo.updateRule(updated) }
    }

    private fun move(fromId: String, toId: String) {
        val list = rulesets.toMutableList()
        val from = list.indexOfFirst { it.id == fromId }
        val to = list.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        list.add(to, list.removeAt(from))
        rulesets = list
        setState { copy(rules = list.toRuleRows()) }
        changed = true
        enqueueWrite { repo.saveOrder(list) }
    }

    private fun selectDomainStrategy(value: String) {
        if (value == state.domainStrategy) return
        setState { copy(domainStrategy = value) }
        changed = true
        enqueueWrite { repo.setDomainStrategy(value) }
    }

    private fun handleImport(pending: PendingImport) {
        if (rulesets.isEmpty()) {
            launch(loading = true) { runImport(pending) }
        } else {
            platform(RoutingEvent.ShowDialog(RoutingDialog.ConfirmImport(pending)))
        }
    }

    private suspend fun runImport(pending: PendingImport) {
        awaitWrites()
        val imported = when (pending) {
            is PendingImport.Preset -> repo.importPresets(pending.type)
            PendingImport.Clipboard -> repo.importRulesets(repo.readClipboard())
            is PendingImport.Text -> repo.importRulesets(pending.value)
        }
        if (!imported) { toastError(); return }
        reload(++loadEpoch)
        changed = true
        toastSuccess()
    }

    private fun export() = launch(loading = true) {
        if (repo.exportToClipboard()) toastSuccess() else toastError()
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        launch {
            awaitWrites()
            finishWith(
                if (changed) BaseResult.Changed(restartService = true, refreshList = false)
                else BaseResult.Cancelled
            )
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        loadJob = null
        writeJob = null
        super.onCleared()
    }
}
