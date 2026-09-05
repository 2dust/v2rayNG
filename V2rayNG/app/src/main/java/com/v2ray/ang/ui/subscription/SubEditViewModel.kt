package com.v2ray.ang.ui.subscription

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toLongEx
import com.v2ray.ang.repository.SubRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseEditViewModel
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Job

class SubEditViewModel(
    private val handle: SavedStateHandle,
    private val repo: SubRepository
) : BaseEditViewModel<SubEditUiState, SubEditAction>(
    SubEditUiState(subId = handle.get<String>(AppRoute.EXTRA_SUB_GUID).orEmpty())
) {

    private var loadJob: Job? = null
    private var formDirty = false
    private var confirmRemove = false

    init {
        handle.setSavedStateProvider(KEY_SAVED) {
            bundleOf(KEY_FORM to JsonUtil.toJson(state.form))
        }
        handle.get<Bundle>(KEY_SAVED)
            ?.getString(KEY_FORM)
            ?.let { JsonUtil.fromJsonSafe(it, SubEditForm::class.java) }
            ?.let { restored ->
                formDirty = true
                setState { copy(form = restored) }
            }
        load()
    }

    val isEditMode: Boolean
        get() = state.isEdit

    private fun load() {
        loadJob?.cancel()
        loadJob = launch {
            val item = if (state.isEdit) repo.loadSubscription(state.subId) else null
            confirmRemove = repo.confirmRemove()

            val form = if (formDirty) {
                state.form
            } else {
                item.toSubEditForm()
            }

            setState {
                copy(
                    form = form,
                    confirmRemove = confirmRemove,
                )
            }

            val profiles = repo.profileOptions()
            setState { copy(profileOptions = profiles) }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        loadJob = null
        super.onCleared()
    }

    override fun onAction(action: SubEditAction) {
        when (action) {
            is SubEditAction.TextChanged -> {
                formDirty = true
                setState { copy(form = action.field.set(form, action.value)) }
            }
            is SubEditAction.FlagChanged -> {
                formDirty = true
                setState { copy(form = action.flag.set(form, action.value)) }
            }
            SubEditAction.Save -> save()
            SubEditAction.Back -> cancel()
            SubEditAction.DeleteConfirmed -> delete()
        }
    }

    override suspend fun doSave(): BaseResult? {
        val form = state.form
        if (form.remarks.isBlank()) {
            toastError(R.string.sub_setting_remarks)
            return null
        }
        if (form.url.isNotEmpty()) {
            if (!Utils.isValidUrl(form.url)) {
                toastError(R.string.toast_invalid_url)
                return null
            }
            if (!Utils.isValidSubUrl(form.url)) {
                toast(R.string.toast_insecure_url_protocol)
                if (!form.allowInsecureUrl) return null
            }
        }
        if (form.autoUpdate &&
            form.updateInterval.toLongEx() < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES
        ) {
            toastError(R.string.toast_invalid_update_interval)
            return null
        }

        val item = (repo.loadSubscription(state.subId) ?: SubscriptionItem()).applySubEditForm(form)
        repo.save(state.subId, item)
        return BaseResult.Saved(id = state.subId, refreshList = true)
    }

    override suspend fun doDelete(): BaseResult? {
        if (!state.isEdit) return null
        repo.remove(state.subId)
        return BaseResult.Deleted(id = state.subId, refreshList = true)
    }

    private companion object {
        const val KEY_SAVED = "sub_edit_saved_state"
        const val KEY_FORM = "form"
    }
}
