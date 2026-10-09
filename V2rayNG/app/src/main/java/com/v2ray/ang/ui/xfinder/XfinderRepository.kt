package com.v2ray.ang.ui.xfinder

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remote sources for the Xfinder and scanner tabs. */
object XfinderSources {
    const val TOP20_URL = "https://imatixofficel.github.io/Xfinder/output/top20.txt"
    const val ALL_URL = "https://imatixofficel.github.io/Xfinder/output/all.txt"
    const val SCANNER_URL = "https://imatixofficel.github.io/Scanner-matix/data/clean_ips.json"
}

/**
 * Network + import plumbing for the Xfinder screens. Fetching uses the app's existing OkHttp stack
 * through [HttpUtil]; importing goes through the same [AngConfigManager] path the main screen uses.
 */
class XfinderRepository {
    /** raw share link -> GUID it was imported as, so re-tapping a row never duplicates the profile. */
    private val importedGuids = HashMap<String, String>()

    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = FETCH_TIMEOUT_MS))
    }

    /**
     * Imports [raw], marks it as the selected server and flags the main screen to refresh its
     * lists and restart a running service. Returns the GUID, or null when nothing was imported.
     */
    suspend fun importAndSelect(raw: String): String? = withContext(Dispatchers.IO) {
        try {
            val known = importedGuids[raw]
            val existing = MmkvManager.decodeAllServerList().toSet()
            val guid = if (known != null && known in existing) {
                known
            } else {
                val (count, _) = AngConfigManager.importBatchConfig(raw, "", true)
                if (count <= 0) return@withContext null
                val created = MmkvManager.decodeAllServerList().firstOrNull { it !in existing }
                    ?: return@withContext null
                importedGuids[raw] = created
                created
            }
            MmkvManager.setSelectServer(guid)
            SettingsChangeManager.makeSetupGroupTab()
            SettingsChangeManager.makeRestartService()
            guid
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Xfinder: failed to import and select config", e)
            null
        }
    }

    /** Imports every share link of [raws] in one batch as connection links; returns how many profiles were added. */
    suspend fun importAll(raws: List<String>): Int = withContext(Dispatchers.IO) {
        try {
            val (count, _) = AngConfigManager.importBatchConfig(raws.joinToString("\n"), "", true)
            if (count > 0) {
                SettingsChangeManager.makeSetupGroupTab()
            }
            count
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Xfinder: failed to import all configs", e)
            0
        }
    }

    private companion object {
        const val FETCH_TIMEOUT_MS = 15_000
    }
}
