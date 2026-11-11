package com.v2ray.ang.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlin.math.abs

/**
 * Decides whether the launch language prompt still has to be shown.
 *
 * The prompt belongs to a fresh install only: people who update an app they already use have
 * configured it and expect it to open straight into the home screen.
 */
internal object LanguageOnboarding {

    /** Set once the choice is stored, so the prompt never comes back. */
    const val PREF_LANGUAGE_CHOSEN = "pref_language_chosen"

    /**
     * A brand new install reports the first install and the last update at the same moment; any
     * later upgrade moves the update time forward. The window only absorbs slow package commits.
     */
    private const val FRESH_INSTALL_WINDOW_MS = 60_000L

    /** Pure decision, so the upgrade and repeat-launch paths stay testable without Android. */
    fun shouldAsk(chosen: Boolean, freshInstall: Boolean): Boolean = !chosen && freshInstall

    fun shouldAsk(context: Context): Boolean = shouldAsk(
        chosen = MmkvManager.decodeSettingsBool(PREF_LANGUAGE_CHOSEN, false),
        freshInstall = isFreshInstall(context),
    )

    fun markChosen() {
        MmkvManager.encodeSettings(PREF_LANGUAGE_CHOSEN, true)
    }

    private fun isFreshInstall(context: Context): Boolean = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        abs(info.lastUpdateTime - info.firstInstallTime) < FRESH_INSTALL_WINDOW_MS
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "Could not read the install times", e)
        false
    }
}
