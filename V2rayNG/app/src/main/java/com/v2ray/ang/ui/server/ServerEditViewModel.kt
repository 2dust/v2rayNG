package com.v2ray.ang.ui.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.repository.ServerRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseEditViewModel
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.EditFormSaver
import com.v2ray.ang.ui.compose.ToastType
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ServerEditViewModel(
    handle: SavedStateHandle,
    private val repository: ServerRepository,
) : BaseEditViewModel<ServerUiState, ServerAction>(initialState(handle)) {

    private val subscriptionId: String = handle.get<String>(AppRoute.EXTRA_SUB_ID).orEmpty()

    private val saver = EditFormSaver(handle, KEY_SAVED)

    val header: StateFlow<ServerHeader> = uiState
        .map { it.header }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.header)

    private var initialProfile: ProfileItem = ProfileItem.create(state.configType)
    private var loadFailed = false
    private var loadJob: Job? = null

    init {
        saver.restore()?.let { restored ->
            val form = restored.getString(KEY_FORM)
                ?.let { JsonUtil.fromJsonSafe(it, ServerForm::class.java) }
            val raw = restored.getString(KEY_RAW)
            if (form != null || raw != null) {
                saver.markDirty()
                setState { copy(form = form ?: this.form, rawContent = raw ?: rawContent) }
            }
        }
        saver.register { bundle ->
            bundle.putString(KEY_FORM, JsonUtil.toJson(state.form))
            bundle.putString(KEY_RAW, state.rawContent)
        }
        loadJob = load()
    }

    private fun load(): Job = launch(onError = { loadFailed = true; toastError() }) {
        val guid = state.guid
        val data = repository.loadEdit(guid, state.configType)

        if (guid.isNotEmpty() && data.profile == null) {
            loadFailed = true
            toastError(R.string.toast_failure)
            return@launch
        }

        val configType = data.profile?.configType ?: state.configType
        initialProfile = data.profile ?: ProfileItem.create(configType)

        val options = when (configType) {
            EConfigType.POLICYGROUP -> ServerOptions(
                subscriptions = data.subscriptions,
                fallbackTags = data.fallbackTags,
            )

            EConfigType.PROXYCHAIN -> ServerOptions(profileRemarks = data.profileRemarks)
            else -> ServerOptions()
        }
        val form = when {
            saver.dirty -> state.form
            data.profile == null -> ServerForm()
            else -> ServerForm.from(data.profile)
        }
        val rawContent = when {
            configType != EConfigType.CUSTOM -> ""
            saver.dirty -> state.rawContent
            else -> data.rawContent
        }

        setState {
            copy(
                configType = configType,
                isRunning = isRunning && data.isSelected,
                form = form,
                options = options,
                rawContent = rawContent,
            )
        }
    }

    override fun onAction(action: ServerAction) {
        when (action) {
            is ServerAction.TextChanged -> {
                saver.markDirty()
                setState { copy(form = action.field.set(form, action.value)) }
            }

            is ServerAction.FlagChanged -> {
                saver.markDirty()
                setState { copy(form = action.flag.set(form, action.value)) }
            }

            is ServerAction.RawContentChanged -> {
                saver.markDirty()
                setState { copy(rawContent = action.value) }
            }

            ServerAction.Save -> save()
            ServerAction.Back -> cancel()

            ServerAction.DeleteClicked -> platform(ServerEvent.ConfirmDeleteProfile)
            ServerAction.ConfirmDeleteProfile -> delete()
            is ServerAction.ConfirmRemoveChainMember -> removeChainMember(action.id)

            ServerAction.FetchCertificate -> fetchCertificate()

            ServerAction.AddChainMember -> {
                saver.markDirty()
                setState {
                    copy(form = form.copy(chainMembers = form.chainMembers + ChainMember()))
                }
            }

            is ServerAction.ChainMemberChanged -> {
                saver.markDirty()
                setState {
                    copy(
                        form = form.copy(
                            chainMembers = form.chainMembers.map {
                                if (it.id == action.id) it.copy(remarks = action.value) else it
                            }
                        )
                    )
                }
            }

            is ServerAction.ChainMemberRemoveClicked -> {
                val member = state.form.chainMembers.firstOrNull { it.id == action.id } ?: return
                if (member.remarks.isBlank()) removeChainMember(action.id)
                else platform(ServerEvent.ConfirmRemoveChainMember(action.id))
            }

            is ServerAction.ChainMemberMoved -> moveChainMember(action.fromId, action.toId)
        }
    }

    private fun removeChainMember(id: String) {
        saver.markDirty()
        setState {
            copy(form = form.copy(chainMembers = form.chainMembers.filterNot { it.id == id }))
        }
    }

    private fun moveChainMember(fromId: String, toId: String) {
        val list = state.form.chainMembers.toMutableList()
        val from = list.indexOfFirst { it.id == fromId }
        val to = list.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        list.add(to, list.removeAt(from))
        saver.markDirty()
        setState { copy(form = form.copy(chainMembers = list)) }
    }

    private fun fetchCertificate() {
        if (state.isFetchingCert) return
        if (!state.canFetchCert) {
            toastError(R.string.toast_fetch_cert_sha256_failed)
            return
        }
        val snapshot = state.form
        if (snapshot.address.isBlank()) {
            toastError(R.string.server_lab_address)
            return
        }
        if (state.configType != EConfigType.HYSTERIA2 &&
            (snapshot.port.toIntOrNull() ?: 0) <= 0
        ) {
            toastError(R.string.server_lab_port)
            return
        }

        launch(onError = { toastError(R.string.toast_fetch_cert_sha256_failed) }) {
            if (!awaitLoad()) return@launch
            setState { copy(isFetchingCert = true) }
            try {
                val sha256 = repository.fetchCertSha256(
                    snapshot.toProfileItem(initialProfile, state.configType)
                )
                if (sha256.isNullOrBlank()) {
                    toastError(R.string.toast_fetch_cert_sha256_failed)
                } else {
                    saver.markDirty()
                    setState { copy(form = form.copy(pinnedCA256 = sha256)) }
                    toastSuccess(R.string.toast_fetch_cert_sha256_success)
                }
            } finally {
                setState { copy(isFetchingCert = false) }
            }
        }
    }

    override suspend fun doSave(): BaseResult? {
        if (!awaitLoad()) return null
        return when (state.configType) {
            EConfigType.CUSTOM -> saveCustom()
            EConfigType.POLICYGROUP -> savePolicyGroup()
            EConfigType.PROXYCHAIN -> saveProxyChain()
            else -> saveStandard()
        }
    }

    override suspend fun doDelete(): BaseResult? {
        if (!awaitLoad()) return null
        val guid = state.guid
        if (guid.isEmpty() || repository.isSelectedServer(guid)) {
            toastError(R.string.toast_action_not_allowed)
            return null
        }
        repository.removeProfile(guid)
        return BaseResult.Deleted(id = guid)
    }

    private suspend fun saveStandard(): BaseResult? {
        val configType = state.configType
        val form = state.form
        ServerValidator.validateForm(configType, form)?.let { return fail(it) }

        val profile = form.toProfileItem(initialProfile, configType)
        if (configType == EConfigType.HYSTERIA2 && profile.security.isNullOrBlank()) {
            profile.security = AppConfig.TLS
        }
        ServerValidator.validateProfile(configType, profile)?.let { return fail(it) }

        profile.description = repository.generateDescription(profile)
        applySubscriptionId(profile)
        return BaseResult.Saved(
            id = repository.saveProfile(state.guid, profile),
            restartService = state.isRunning,
        )
    }

    private suspend fun saveCustom(): BaseResult? {
        val form = state.form
        if (form.remarks.isBlank()) return fail(BaseText.of(R.string.server_lab_remarks))

        val content = state.rawContent
        val parsed = runCatching { repository.parseCustomConfig(content) }
            .getOrElse { return fail(BaseText.of(R.string.toast_malformed_json)) }

        val profile = initialProfile.takeIf { state.isEdit }
            ?: ProfileItem.create(EConfigType.CUSTOM)
        profile.remarks = form.remarks.ifEmpty { parsed?.remarks.orEmpty() }
        profile.server = parsed?.server
        profile.serverPort = parsed?.serverPort
        profile.description = repository.generateDescription(profile)
        applySubscriptionId(profile)

        val savedGuid = repository.saveProfile(state.guid, profile)
        repository.saveRawConfig(savedGuid, content)
        return BaseResult.Saved(id = savedGuid, restartService = state.isRunning)
    }

    private suspend fun savePolicyGroup(): BaseResult? {
        val form = state.form
        if (form.remarks.isBlank()) return fail(BaseText.of(R.string.server_lab_remarks))

        val typeIndex = form.groupType.toIntOrNull() ?: 0
        val profile = initialProfile.takeIf { state.isEdit }
            ?: ProfileItem.create(EConfigType.POLICYGROUP)
        profile.remarks = form.remarks.trim()
        profile.policyGroupFilter = form.groupFilter.trim()
        profile.policyGroupType = typeIndex.toString()
        profile.policyGroupSubscriptionId = form.groupSubId
        profile.policyGroupTestOutbounds = form.groupTestOutbounds
        profile.policyGroupFallbackTag = form.groupFallbackTag.trim().takeIf { it.isNotEmpty() }
        profile.description = repository.buildPolicyGroupDescription(
            typeIndex = typeIndex,
            subId = form.groupSubId,
            filter = profile.policyGroupFilter.orEmpty(),
        )
        applySubscriptionId(profile)

        return BaseResult.Saved(
            id = repository.saveProfile(state.guid, profile),
            restartService = state.isRunning,
        )
    }

    private suspend fun saveProxyChain(): BaseResult? {
        val form = state.form
        if (form.remarks.isBlank()) return fail(BaseText.of(R.string.server_lab_remarks))

        val members = form.chainMembers.map { it.remarks.trim() }
        if (members.any { it.isEmpty() }) {
            return fail(BaseText.of(R.string.server_proxy_chain_members_unselected))
        }
        if (members.size < 2) {
            return fail(BaseText.of(R.string.server_proxy_chain_members_insufficient))
        }
        val invalid = members.filter { remarks ->
            val profile = repository.findProfileByRemarks(remarks)
            profile == null || profile.configType.isComplexType()
        }
        if (invalid.isNotEmpty()) {
            return fail(
                BaseText.of(
                    R.string.server_proxy_chain_members_invalid,
                    invalid.joinToString(", "),
                )
            )
        }

        val profile = initialProfile.takeIf { state.isEdit }
            ?: ProfileItem.create(EConfigType.PROXYCHAIN)
        profile.remarks = form.remarks.trim()
        profile.proxyChainProfiles = members.joinToString(",")
        profile.description = members.joinToString(" -> ")
        applySubscriptionId(profile)

        return BaseResult.Saved(
            id = repository.saveProfile(state.guid, profile),
            restartService = state.isRunning,
        )
    }

    private fun applySubscriptionId(profile: ProfileItem) {
        if (profile.subscriptionId.isEmpty() && subscriptionId.isNotEmpty()) {
            profile.subscriptionId = subscriptionId
        }
    }

    private suspend fun awaitLoad(): Boolean {
        loadJob?.join()
        if (loadFailed) toastError(R.string.toast_failure)
        return !loadFailed
    }

    private fun fail(text: BaseText): BaseResult? {
        toast(text, ToastType.ERROR)
        return null
    }

    companion object {
        private const val KEY_SAVED = "server_edit_saved_state"
        private const val KEY_FORM = "form"
        private const val KEY_RAW = "raw"

        private fun initialState(handle: SavedStateHandle): ServerUiState {
            val typeValue = handle.get<Int>(AppRoute.EXTRA_TYPE) ?: EConfigType.VMESS.value
            val guid = handle.get<String>(AppRoute.EXTRA_GUID).orEmpty()
            return ServerUiState(
                configType = EConfigType.fromInt(typeValue) ?: EConfigType.VMESS,
                guid = guid,
                isRunning = (handle.get<Boolean>(AppRoute.EXTRA_RUNNING) ?: false) &&
                        guid.isNotEmpty(),
            )
        }
    }
}
