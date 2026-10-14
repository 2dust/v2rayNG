package com.v2ray.ang.ui.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AetherSettings
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AetherEditorState(
    val remarks: String = "",
    val settings: AetherSettings = AetherSettings(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val errorRes: Int? = null,
    val savedGuid: String? = null,
)

class AetherEditorViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val editorDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val loadProfile: (String, String) -> ProfileItem? = { guid, subscriptionId ->
        if (guid.isBlank()) ProfileItem.create(EConfigType.AETHER).copy(subscriptionId = subscriptionId)
        else MmkvManager.decodeServerConfig(guid)?.takeIf { it.configType == EConfigType.AETHER }
    },
    private val saveProfile: (String, ProfileItem) -> String = MmkvManager::encodeServerConfigTransactional,
    private val reportFailure: (String, String, Exception) -> Unit = { operation, profileGuid, failure ->
        LogUtil.e(AppConfig.TAG, "Aether editor: $operation profile failed, guid=$profileGuid", failure)
    },
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(AetherEditorState())
    val state = mutableState.asStateFlow()
    private var initialProfile: ProfileItem? = null
    private var guid = ""
    private var initialized = false

    fun initialize(profileGuid: String, subscriptionId: String) {
        if (initialized) return
        initialized = true
        guid = profileGuid
        viewModelScope.launch(editorDispatcher) {
            val profile = try {
                withContext(Dispatchers.IO) { loadProfile(guid, subscriptionId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure("load", guid, e)
                null
            }
            if (profile == null) {
                mutableState.value = AetherEditorState(loading = false, errorRes = R.string.aether_load_error)
                return@launch
            }
            initialProfile = profile
            val draft = savedState.get<String>("aether_draft")?.let {
                JsonUtil.fromJsonSafe(it, AetherSettings::class.java)
            } ?: profile.aetherSettings ?: AetherSettings()
            mutableState.value = AetherEditorState(
                remarks = savedState.get<String>("aether_remarks") ?: profile.remarks,
                settings = draft,
                loading = false,
            )
        }
    }

    fun changeRemarks(value: String) {
        if (mutableState.value.loading || mutableState.value.saving) return
        savedState["aether_remarks"] = value
        mutableState.value = mutableState.value.copy(remarks = value, errorRes = null)
    }

    fun changeSettings(update: (AetherSettings) -> AetherSettings) {
        if (mutableState.value.loading || mutableState.value.saving) return
        val value = update(mutableState.value.settings)
        savedState["aether_draft"] = JsonUtil.toJson(value)
        mutableState.value = mutableState.value.copy(settings = value, errorRes = null)
    }

    fun save() {
        val snapshot = mutableState.value
        val initial = initialProfile ?: return
        if (snapshot.loading || snapshot.saving) return
        if (snapshot.remarks.isBlank() || !snapshot.settings.isValid()) {
            mutableState.value = snapshot.copy(errorRes = R.string.aether_invalid_settings)
            return
        }
        mutableState.value = snapshot.copy(saving = true, errorRes = null)
        viewModelScope.launch(editorDispatcher) {
            try {
                val savedGuid = withContext(Dispatchers.IO) {
                    val profile = initial.copy(
                        remarks = snapshot.remarks.trim(), server = "127.0.0.1",
                        serverPort = snapshot.settings.socksPort,
                        aetherSettings = snapshot.settings,
                    )
                    saveProfile(guid, profile)
                }
                check(savedGuid.isNotBlank()) { "Saved profile GUID missing" }
                mutableState.value = snapshot.copy(saving = false, savedGuid = savedGuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure("save", guid, e)
                mutableState.value = snapshot.copy(saving = false, errorRes = R.string.aether_save_error)
            }
        }
    }
}
