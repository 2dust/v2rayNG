package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.v2ray.ang.R
import com.v2ray.ang.ui.ScSelectServerActivity

object ConfigShortcutManager {

    private const val SHORTCUT_ID_PREFIX = "select_server_"

    fun refresh(context: Context) {
        val shortcuts = MmkvManager.decodeAllServerList()
            .mapNotNull { guid ->
                val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                if (!profile.shortcutEnabled) return@mapNotNull null
                val label = profile.remarks.ifBlank { guid }
                val intent = Intent(context, ScSelectServerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(ScSelectServerActivity.EXTRA_GUID, guid)
                }
                ShortcutInfoCompat.Builder(context, "$SHORTCUT_ID_PREFIX$guid")
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_qu_switch_24dp))
                    .setIntent(intent)
                    .build()
            }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
