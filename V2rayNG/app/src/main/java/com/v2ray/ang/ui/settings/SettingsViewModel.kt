package com.v2ray.ang.ui.settings

import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.repository.BoolPref
import com.v2ray.ang.repository.SettingsRepository
import com.v2ray.ang.repository.StringPref
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repo: SettingsRepository,
) : BaseViewModel<SettingsUiState, SettingsAction>(SettingsUiState()) {

    private var writeJob: Job? = null
    private var exiting = false

    init {
        load()
    }

    override fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Back -> exit()
            is SettingsAction.BoolChanged -> onBoolChanged(action.pref, action.value)
            is SettingsAction.TextChanged -> onTextChanged(action.pref, action.value)
            SettingsAction.ModeHelpClicked -> navigate(AppRoute.OpenUrl(AppConfig.APP_WIKI_MODE))
        }
    }

    private fun load() = launch(loading = true) {
        val prefs = repo.load()
        setState {
            copy(
                bools = prefs.bools,
                strings = prefs.strings,
                dynamicColorSupported = prefs.dynamicColorSupported,
            )
        }
    }

    /** Waits for the write chain, so leaving right after a tap cannot drop that tap. */
    private fun exit() {
        if (exiting) return
        exiting = true
        launch(onError = {}) {
            try {
                writeJob?.join()
            } finally {
                finishWith(
                    if (state.changed) {
                        BaseResult.Changed(restartService = state.restartService, refreshList = true)
                    } else {
                        BaseResult.Cancelled
                    }
                )
            }
        }
    }

    // ===== boolean rules =====

    private fun onBoolChanged(pref: BoolPref, value: Boolean) {
        when (pref) {
            BoolPref.ROOT_MODE_ENABLE, BoolPref.ROOT_LAN_SHARING -> requireRoot(pref, value)

            // hev-tunnel needs the local proxy; turn both on instead of failing silently later.
            BoolPref.USE_HEV_TUNNEL -> {
                applyBool(pref, value)
                if (value) applyBool(BoolPref.ENABLE_LOCAL_PROXY, true)
            }

            BoolPref.ENABLE_LOCAL_PROXY -> {
                if (state.localProxyForced) return
                applyBool(pref, value)
                if (!value) applyBool(BoolPref.APPEND_HTTP_PROXY, false)
            }

            BoolPref.DYNAMIC_COLOR -> {
                if (!state.dynamicColorSupported) return
                applyBool(pref, value)
            }

            else -> applyBool(pref, value)
        }
    }

    private fun requireRoot(pref: BoolPref, value: Boolean) {
        if (!value) {
            applyBool(pref, false)
            return
        }
        launch(loading = true) {
            if (repo.ensureRoot()) applyBool(pref, true) else toastError(R.string.toast_root_required)
        }
    }

    // ===== string rules =====

    private fun onTextChanged(pref: StringPref, value: String) {
        val text = value.trim()
        when (pref) {
            StringPref.OBS_LEAST_PING_INTERVAL,
            StringPref.OBS_LEAST_LOAD_INTERVAL,
            StringPref.OBS_LEAST_LOAD_TIMEOUT -> applyString(pref, validDuration(text) ?: return)

            StringPref.OBS_LEAST_LOAD_SAMPLING -> applyString(pref, validSampling(text) ?: return)

            else -> applyString(pref, text)
        }
    }

    private fun validDuration(value: String): String? {
        if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(value)) return value
        toastError(R.string.toast_invalid_observatory_duration)
        return null
    }

    private fun validSampling(value: String): String? {
        val sampling = value.toIntOrNull()?.takeIf { it > 0 }
        if (sampling != null) return sampling.toString()
        toastError(R.string.toast_invalid_observatory_sampling)
        return null
    }

    // ===== state + persistence =====

    private fun applyBool(pref: BoolPref, value: Boolean) {
        if (state[pref] == value) return
        setState {
            copy(
                bools = bools + (pref to value),
                changed = true,
                restartService = restartService || !pref.uiOnly,
            )
        }
        persist { repo.setBool(pref, value) }
    }

    private fun applyString(pref: StringPref, value: String) {
        if (state[pref] == value) return
        setState {
            copy(
                strings = strings + (pref to value),
                changed = true,
                restartService = restartService || !pref.uiOnly,
            )
        }
        persist { repo.setString(pref, value) }
        if (pref == StringPref.LANGUAGE) {
            platform(SettingsEvent.ApplyLanguage(value))
        }
    }

    /**
     * Writes are chained so a later tap can never overwrite an earlier one, and made
     * NonCancellable so a started write survives the ViewModel being cleared.
     */
    private fun persist(write: suspend () -> Unit) {
        val previous = writeJob
        writeJob = launch {
            previous?.join()
            withContext(NonCancellable) { write() }
        }
    }
}
