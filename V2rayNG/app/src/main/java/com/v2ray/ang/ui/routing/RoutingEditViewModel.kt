package com.v2ray.ang.ui.routing

import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.repository.RoutingRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseEditViewModel
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.EditFormSaver
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Job

class RoutingEditViewModel(
    handle: SavedStateHandle,
    private val repo: RoutingRepository,
) : BaseEditViewModel<RoutingEditUiState, RoutingEditAction>(
    initialState = RoutingEditUiState(ruleId = handle.get<String>(AppRoute.EXTRA_RULE_ID) ?: "")
) {

    private val saver = EditFormSaver(handle, KEY_SAVED)

    private var initial: RulesetItem? = null
    private var loadFailed = false
    private var loadJob: Job? = null

    init {
        saver.restore()
            ?.getString(KEY_FORM)
            ?.let { JsonUtil.fromJsonSafe(it, RoutingForm::class.java) }
            ?.let { restored ->
                saver.markDirty()
                setState { copy(form = restored) }
            }
        saver.register { bundle -> bundle.putString(KEY_FORM, JsonUtil.toJson(state.form)) }
        loadJob = load()
    }

    private fun load(): Job = launch(onError = { loadFailed = true; toastError() }) {
        val data = repo.loadEditData(state.ruleId)
        initial = data.ruleset
        if (state.isEdit && data.ruleset == null) {
            loadFailed = true
            toastError()
        }
        setState {
            copy(
                form = if (saver.dirty) form else data.ruleset.toRoutingForm(),
                canUseProcess = data.canUseProcess,
                outboundOptions = data.outboundOptions,
            )
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
                saver.markDirty()
                setState { copy(form = form.copy(locked = action.value)) }
            }

            RoutingEditAction.SelectProcess -> {
                val current = state.form.process
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                navigate(
                    AppRoute.AppPicker(
                        selected = current,
                        titleRes = R.string.routing_settings_process_select,
                    )
                )
            }

            is RoutingEditAction.ResultReceived -> {
                if (action.result is BaseResult.Selected) {
                    saver.markDirty()
                    setState {
                        copy(form = form.copy(process = action.result.values.joinToString(",")))
                    }
                }
            }

            RoutingEditAction.Save -> save()
            RoutingEditAction.Back -> cancel()
            RoutingEditAction.Delete -> platform(RoutingEditEvent.ShowDeleteDialog)
            RoutingEditAction.ConfirmDelete -> delete()
        }
    }

    private fun updateField(field: RoutingField, value: String) {
        saver.markDirty()
        setState { copy(form = field.set(form, value)) }
    }

    override suspend fun doSave(): BaseResult? {
        if (!awaitLoad()) return null

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
        if (!awaitLoad()) return null
        if (!repo.removeRule(state.ruleId)) {
            toastError(R.string.toast_failure)
            return null
        }
        return BaseResult.Deleted(restartService = true, refreshList = false)
    }

    /** Never mutate storage from a half-initialised form; returns false when loading failed. */
    private suspend fun awaitLoad(): Boolean {
        loadJob?.join()
        if (loadFailed) toastError(R.string.toast_failure)
        return !loadFailed
    }

    private companion object {
        const val KEY_SAVED = "routing_edit_saved_state"
        const val KEY_FORM = "form"
    }
}
