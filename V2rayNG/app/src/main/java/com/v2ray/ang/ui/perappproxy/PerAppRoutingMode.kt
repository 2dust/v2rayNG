package com.v2ray.ang.ui.perappproxy

import com.v2ray.ang.AppConfig

/**
 * Presentation of the two existing, persisted VPN routing flags. Keeping the
 * storage representation unchanged allows upgrades and existing exports to work.
 */
enum class PerAppRoutingMode(val perAppEnabled: Boolean, val bypassSelected: Boolean) {
    ALL(false, false),
    ONLY_SELECTED(true, false),
    EXCEPT_SELECTED(true, true);

    companion object {
        fun fromSettings(perAppEnabled: Boolean, bypassSelected: Boolean): PerAppRoutingMode = when {
            !perAppEnabled -> ALL
            bypassSelected -> EXCEPT_SELECTED
            else -> ONLY_SELECTED
        }

        fun afterBulkSelection(current: PerAppRoutingMode): PerAppRoutingMode =
            if (current == ALL) ONLY_SELECTED else current

        /**
         * Commit both flags, restoring changed keys if the second write fails.
         * A failed rollback is an exception, never a successful settings change.
         */
        fun persist(
            previous: PerAppRoutingMode,
            target: PerAppRoutingMode,
            write: (String, Boolean) -> Boolean,
        ): Boolean {
            if (previous == target) return true
            var enabledWritten = false
            var bypassWritten = false
            var failed = false
            try {
                if (previous.perAppEnabled != target.perAppEnabled) {
                    enabledWritten = write(ENABLED_KEY, target.perAppEnabled)
                    if (!enabledWritten) failed = true
                }
                if (!failed && previous.bypassSelected != target.bypassSelected) {
                    bypassWritten = write(BYPASS_KEY, target.bypassSelected)
                    if (!bypassWritten) failed = true
                }
            } catch (_: Exception) {
                failed = true
            }
            // Rollback errors deliberately escape: never report a partial write as saved.
            return if (failed) rollback(previous, enabledWritten, bypassWritten, write) else true
        }

        private fun rollback(
            previous: PerAppRoutingMode,
            enabledWritten: Boolean,
            bypassWritten: Boolean,
            write: (String, Boolean) -> Boolean,
        ): Boolean {
            val enabledRestored = !enabledWritten || write(ENABLED_KEY, previous.perAppEnabled)
            val bypassRestored = !bypassWritten || write(BYPASS_KEY, previous.bypassSelected)
            check(enabledRestored && bypassRestored) { "Per-app routing settings rollback failed" }
            return false
        }

        private val ENABLED_KEY = AppConfig.PREF_PER_APP_PROXY
        private val BYPASS_KEY = AppConfig.PREF_BYPASS_APPS
    }
}
