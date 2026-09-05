package com.v2ray.ang.repository

import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.TaskerProfile
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager

/**
 * Data facade of the launcher-shortcut and Tasker entries.
 */
open class ShortcutRepository : BaseRepository() {

    /**
     * Probes the core. Only meaningful in `:RunSoLibV2RayDaemon`, which is where the three service
     * entries are declared to run; calling it from the UI process would load the native libs there.
     */
    open suspend fun isCoreRunning(): Boolean = withIO { CoreServiceManager.isRunning() }

    /**
     * Imports profiles and subscriptions from scanned text.
     *
     * @return the total number of imported profiles and subscriptions.
     */
    open suspend fun importBatchConfig(text: String): Int = withIO {
        // (server = text, subid = "", append = false)
        val (count, countSub) = AngConfigManager.importBatchConfig(text, "", false)
        count + countSub
    }

    /**
     * All stored profiles, in list order.
     */
    open suspend fun loadTaskerProfiles(): List<TaskerProfile> = withIO {
        MmkvManager.decodeAllServerList().mapNotNull { guid ->
            MmkvManager.decodeServerConfig(guid)?.let { config ->
                TaskerProfile(guid = guid, remarks = config.remarks)
            }
        }
    }
}
