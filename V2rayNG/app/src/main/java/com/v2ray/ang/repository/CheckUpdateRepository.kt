package com.v2ray.ang.repository

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager

open class CheckUpdateRepository : BaseRepository() {

    open fun isCheckPreRelease(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

    /**
     * Persists the flag and reads it back, so the caller reflects what is actually stored rather
     * than what the UI event claimed.
     *
     * @return the persisted value
     */
    open suspend fun setCheckPreRelease(enabled: Boolean): Boolean = withIO {
        MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, enabled)
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)
    }

    open fun appVersionText(): String = "$VERSION_PREFIX${BuildConfig.VERSION_NAME}"

    open suspend fun fullVersionText(): String = withIO {
        "$VERSION_PREFIX${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})"
    }

    open suspend fun checkForUpdate(includePreRelease: Boolean): CheckUpdateResult =
        withIO { UpdateCheckerManager.checkForUpdate(includePreRelease) }

    private companion object {
        const val VERSION_PREFIX = "v"
    }
}
