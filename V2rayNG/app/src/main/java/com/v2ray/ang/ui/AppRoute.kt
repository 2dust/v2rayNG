package com.v2ray.ang.ui

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.about.AboutActivity
import com.v2ray.ang.ui.apppicker.AppPickerActivity
import com.v2ray.ang.ui.backup.BackupActivity
import com.v2ray.ang.ui.base.BaseRoute
import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.routing.RoutingEditActivity
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import com.v2ray.ang.ui.scanner.ScannerActivity
import com.v2ray.ang.ui.server.ServerEditActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.ui.subscription.SubEditActivity
import com.v2ray.ang.ui.subscription.SubSettingActivity
import com.v2ray.ang.ui.userasset.UserAssetActivity
import com.v2ray.ang.ui.userasset.UserAssetUrlActivity

/**
 * Every navigation target of the app, and the single owner of the Intent extra keys.
 *
 * A route builds its own Intent, so ViewModels emit `navigate(AppRoute.X)` without touching a
 * Context and Activities no longer keep a `when (destination)` table. Screens must read their
 * arguments through the keys in [AppRoute.Companion] rather than re-typing string literals.
 */
sealed interface AppRoute : BaseRoute {

    /** Home screen; used by entry points that hand control back to the app. */
    data object Main : AppRoute {
        override fun intent(context: Context) = Intent(context, MainActivity::class.java)
    }

    /** Subscription list. */
    data object SubSetting : AppRoute {
        override fun intent(context: Context) = Intent(context, SubSettingActivity::class.java)
    }

    /** Per-app proxy configuration. */
    data object PerAppProxy : AppRoute {
        override fun intent(context: Context) = Intent(context, PerAppProxyActivity::class.java)
    }

    /** Routing ruleset list. */
    data object RoutingSetting : AppRoute {
        override fun intent(context: Context) = Intent(context, RoutingSettingActivity::class.java)
    }

    /** Geo/user asset management. */
    data object UserAsset : AppRoute {
        override fun intent(context: Context) = Intent(context, UserAssetActivity::class.java)
    }

    /** App settings. */
    data object Settings : AppRoute {
        override fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }

    /** Log viewer. */
    data object Logcat : AppRoute {
        override fun intent(context: Context) = Intent(context, LogcatActivity::class.java)
    }

    /** Update checker. */
    data object CheckUpdate : AppRoute {
        override fun intent(context: Context) = Intent(context, CheckUpdateActivity::class.java)
    }

    /** Backup and restore. */
    data object Backup : AppRoute {
        override fun intent(context: Context) = Intent(context, BackupActivity::class.java)
    }

    /** About screen. */
    data object About : AppRoute {
        override fun intent(context: Context) = Intent(context, AboutActivity::class.java)
    }

    data object Scanner : AppRoute {
        override fun intent(context: Context) = Intent(context, ScannerActivity::class.java)
    }

    /**
     * Profile editor. One route for all protocols: [configType] picks the concrete editor.
     */
    data class ServerEdit(
        val configType: EConfigType,
        val guid: String = "",
        val subscriptionId: String = "",
        val isRunning: Boolean = false,
    ) : AppRoute {
        override fun intent(context: Context): Intent =
            Intent(context, ServerEditActivity::class.java)
                .putExtra(EXTRA_TYPE, configType.value)
                .putExtra(EXTRA_GUID, guid)
                .putExtra(EXTRA_SUB_ID, subscriptionId)
                .putExtra(EXTRA_RUNNING, isRunning)
    }

    /** Routing rule editor; [position] < 0 creates a new rule. */
    data class RoutingEdit(val position: Int = -1) : AppRoute {
        override fun intent(context: Context) =
            Intent(context, RoutingEditActivity::class.java)
                .putExtra(EXTRA_POSITION, position)
    }

    /** Subscription editor; empty [subId] creates a new subscription. */
    data class SubEdit(val subId: String = "") : AppRoute {
        override fun intent(context: Context) =
            Intent(context, SubEditActivity::class.java)
                .putExtra(EXTRA_SUB_GUID, subId)
    }

    /**
     * Asset URL editor; empty [assetId] creates a new asset and [qrCodeUrl] pre-fills the form
     * when the entry came from a scan.
     */
    data class UserAssetUrl(
        val assetId: String = "",
        val qrCodeUrl: String = "",
    ) : AppRoute {
        override fun intent(context: Context) =
            Intent(context, UserAssetUrlActivity::class.java)
                .putExtra(EXTRA_ASSET_ID, assetId)
                .putExtra(EXTRA_ASSET_QRCODE, qrCodeUrl)
    }

    /**
     * App picker; answers with [com.v2ray.ang.ui.base.BaseResult.Selected].
     *
     * @param selected package names checked on entry
     * @param titleRes title resource (0 = default), passed as an id so no layer resolves strings early
     */
    data class AppPicker(
        val selected: List<String> = emptyList(),
        @StringRes val titleRes: Int = 0,
    ) : AppRoute {
        override fun intent(context: Context) =
            Intent(context, AppPickerActivity::class.java)
                .putStringArrayListExtra(EXTRA_PICKER_SELECTED, ArrayList(selected))
                .putExtra(EXTRA_PICKER_TITLE_RES, titleRes)
    }

    /**
     * An external link. [intent] returns `null` on purpose: [com.v2ray.ang.ui.base.BaseScreen]
     * recognises this route and opens it in a browser instead of starting an Activity.
     */
    data class OpenUrl(val url: String) : AppRoute {
        override fun intent(context: Context): Intent? = null
    }

    companion object {
        /** Profile type ([EConfigType.value]). */
        const val EXTRA_TYPE = "configType"

        /** Profile guid being edited. */
        const val EXTRA_GUID = "guid"

        /** Owning subscription/group id. */
        const val EXTRA_SUB_ID = "subscriptionId"

        /** Whether the edited profile is currently active. */
        const val EXTRA_RUNNING = "isRunning"

        /** Routing ruleset index. */
        const val EXTRA_POSITION = "position"

        /** Subscription id being edited. */
        const val EXTRA_SUB_GUID = "subId"

        /** Asset id being edited. */
        const val EXTRA_ASSET_ID = "assetId"

        /** Asset URL captured from a QR code. */
        const val EXTRA_ASSET_QRCODE = "assetUrlQrcode"

        /** Package names pre-selected in [AppPicker]. */
        const val EXTRA_PICKER_SELECTED = "pickerSelected"

        /** Title string resource id for [AppPicker]. */
        const val EXTRA_PICKER_TITLE_RES = "pickerTitleRes"
    }
}
