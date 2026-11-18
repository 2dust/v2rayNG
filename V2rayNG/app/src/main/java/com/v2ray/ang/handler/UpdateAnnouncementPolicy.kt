package com.v2ray.ang.handler

/**
 * Pure rules for the automatic "new version available" announcement, kept free of Android types so
 * they are covered by plain JVM tests.
 */
object UpdateAnnouncementPolicy {
    /** How often the automatic check may hit the network. */
    const val CHECK_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L

    /**
     * Compares dotted numeric versions ("2.3.14.33"); missing or non-numeric parts count as 0.
     *
     * @return a negative number, zero or a positive number as [version1] is older than, equal to or newer than [version2].
     */
    fun compareVersions(version1: String, version2: String): Int {
        val v1 = version1.split(".")
        val v2 = version2.split(".")
        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = v1.getOrNull(i)?.toIntOrNull() ?: 0
            val num2 = v2.getOrNull(i)?.toIntOrNull() ?: 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    /**
     * True when [latestVersion] should be announced: the user has not postponed anything yet, or the
     * latest version is newer than the one they postponed with "Later".
     */
    fun shouldAnnounce(latestVersion: String, dismissedVersion: String?): Boolean =
        dismissedVersion.isNullOrBlank() || compareVersions(latestVersion, dismissedVersion) > 0

    /** True when enough time has passed since the last check (a clock set back also makes a check due). */
    fun isCheckDue(nowMillis: Long, lastCheckMillis: Long, intervalMillis: Long = CHECK_INTERVAL_MILLIS): Boolean =
        lastCheckMillis <= 0L || nowMillis < lastCheckMillis || nowMillis - lastCheckMillis >= intervalMillis
}
