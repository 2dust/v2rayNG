package com.v2ray.ang.ui.routing

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.repository.RoutingRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseEditViewModel
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Job
import java.util.UUID

class RoutingEditViewModel(
    private val handle: SavedStateHandle,
    private val repo: RoutingRepository,
) : BaseEditViewModel<RoutingEditUiState, RoutingEditAction>(
    initialState = RoutingEditUiState(ruleId = handle.get<String>(AppRoute.EXTRA_RULE_ID) ?: "")
) {

    private var initial: RulesetItem? = null
    private var formDirty = false
    private var loadJob: Job? = null
    private var loadEpoch = 0L

    init {
        handle.setSavedStateProvider(KEY_SAVED) {
            bundleOf(KEY_FORM to JsonUtil.toJson(state.form))
        }
        handle.get<Bundle>(KEY_SAVED)
            ?.getString(KEY_FORM)
            ?.let { JsonUtil.fromJsonSafe(it, RoutingForm::class.java) }
            ?.let { restored ->
                formDirty = true
                setState { copy(form = restored) }
            }
        load()
    }

    private fun load() {
        val epoch = ++loadEpoch
        loadJob?.cancel()
        loadJob = launch {
            val data = repo.loadEditData(state.ruleId)
            if (loadEpoch != epoch) return@launch
            initial = data.ruleset
            setState {
                copy(
                    form = if (formDirty) form else data.ruleset.toRoutingForm(),
                    canUseProcess = data.canUseProcess,
                )
            }
            val outbounds = repo.outboundOptions()
            if (loadEpoch != epoch) return@launch
            setState { copy(outboundOptions = outbounds) }
        }
    }

    override fun onAction(action: RoutingEditAction) {
        when (action) {
            is RoutingEditAction.UpdateRemarks -> updateField(RoutingField.REMARKS, action.value)
            is RoutingEditAction.UpdateDomain -> updateField(RoutingField.DOMAIN, action.value)
            is RoutingEditAction.UpdateIp -> updateField(RoutingField.IP, action.value)
            is RoutingEditAction.UpdateProcess -> updateField(RoutingField.PROCESS, action.value)
            is RoutingEditAction.UpdateProtocol -> updateField(RoutingField.PROTOCOL, action.value)
            is RoutingEditAction.UpdateNetwork -> updateField(RoutingField.NETWORK, action.value)
            is RoutingEditAction.UpdatePort -> updateField(RoutingField.PORT, action.value)
            is RoutingEditAction.UpdateOutbound -> updateField(RoutingField.OUTBOUND, action.value)
            is RoutingEditAction.ToggleLocked -> {
                formDirty = true
                setState { copy(form = form.copy(locked = action.value)) }
            }
            RoutingEditAction.SelectProcess -> {
                val current = state.form.process
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                navigate(AppRoute.AppPicker(selected = current, titleRes = R.string.routing_settings_process_select))
            }
            is RoutingEditAction.ResultReceived -> {
                if (action.result is BaseResult.Selected) {
                    formDirty = true
                    setState { copy(form = form.copy(process = action.result.values.joinToString(","))) }
                }
            }
            RoutingEditAction.Save -> save()
            RoutingEditAction.Back -> cancel()
            RoutingEditAction.Delete -> platform(RoutingEditEvent.ShowDeleteDialog)
            RoutingEditAction.ConfirmDelete -> delete()
        }
    }

    private fun updateField(field: RoutingField, value: String) {
        formDirty = true
        setState { copy(form = field.set(form, value)) }
    }

    override suspend fun doSave(): BaseResult? {
        val form = state.form
        if (form.remarks.isBlank()) {
            toastError(R.string.sub_setting_remarks)
            return null
        }
        val base = initial
        if (state.isEdit && base == null) {
            toastError(R.string.toast_failure)
            return null
        }
        val item = (base ?: RulesetItem()).applyRoutingForm(form)
        val id = if (state.isEdit) {
            if (!repo.updateRule(item)) {
                toastError(R.string.toast_failure)
                return null
            }
            item.id
        } else {
            repo.insertRule(item).takeIf { it.isNotEmpty() } ?: run {
                toastError(R.string.toast_failure)
                return null
            }
        }
        return BaseResult.Saved(id = id, restartService = true, refreshList = false)
    }

    override suspend fun doDelete(): BaseResult? {
        if (!state.isEdit) return null
        if (!repo.removeRule(state.ruleId)) {
            toastError(R.string.toast_failure)
            return null
        }
        return BaseResult.Deleted(restartService = true, refreshList = false)
    }

    override fun onCleared() {
        loadJob?.cancel()
        loadJob = null
        super.onCleared()
    }

    private companion object {
        const val KEY_SAVED = "routing_edit_saved_state"
        const val KEY_FORM = "form"
    }
}
