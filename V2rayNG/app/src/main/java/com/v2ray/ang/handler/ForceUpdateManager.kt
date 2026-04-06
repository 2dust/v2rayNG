package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ForceUpdateManager {

    private const val TAG = "SimpsonsVPN_ForceUpdate"
    private const val PREF_UPDATE_FIRST_SEEN = "pref_force_update_first_seen"
    private const val PREF_APP_BLOCKED = "pref_app_blocked"
    private const val BLOCK_AFTER_DAYS = 15
    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/sarlindom39/Simpsons-VPN/master/version.json"

    data class RemoteVersion(
        val versionCode: Int = 0,
        val versionName: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = "",
        val forceUpdate: Boolean = false
    )

    suspend fun checkForUpdate(): RemoteVersion? = withContext(Dispatchers.IO) {
        try {
            var response = HttpUtil.getUrlContent(VERSION_JSON_URL, 10000)
            if (response.isNullOrEmpty()) {
                val httpPort = SettingsManager.getHttpPort()
                response = HttpUtil.getUrlContent(VERSION_JSON_URL, 10000, httpPort)
            }
            if (response.isNullOrEmpty()) {
                Log.w(TAG, "Failed to fetch version.json")
                return@withContext null
            }

            val remoteVersion = JsonUtil.fromJson(response, RemoteVersion::class.java)
            if (remoteVersion == null) {
                Log.w(TAG, "Failed to parse version.json")
                return@withContext null
            }

            Log.d(TAG, "Remote version: ${remoteVersion.versionName} (code: ${remoteVersion.versionCode}), current: ${BuildConfig.VERSION_NAME} (code: ${BuildConfig.VERSION_CODE})")

            if (remoteVersion.versionCode > BuildConfig.VERSION_CODE) {
                return@withContext remoteVersion
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}")
            return@withContext null
        }
    }

    fun isAppBlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_APP_BLOCKED, false)
    }

    fun getDaysRemaining(context: Context): Int {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        val firstSeen = prefs.getLong(PREF_UPDATE_FIRST_SEEN, 0)
        if (firstSeen == 0L) return BLOCK_AFTER_DAYS

        val daysPassed = ((System.currentTimeMillis() - firstSeen) / (1000 * 60 * 60 * 24)).toInt()
        return maxOf(0, BLOCK_AFTER_DAYS - daysPassed)
    }

    fun markUpdateFirstSeen(context: Context) {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        if (prefs.getLong(PREF_UPDATE_FIRST_SEEN, 0) == 0L) {
            prefs.edit().putLong(PREF_UPDATE_FIRST_SEEN, System.currentTimeMillis()).apply()
            Log.d(TAG, "First update notice seen, starting 15-day countdown")
        }
    }

    fun checkAndBlockIfExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        val firstSeen = prefs.getLong(PREF_UPDATE_FIRST_SEEN, 0)
        if (firstSeen == 0L) return false

        val daysPassed = ((System.currentTimeMillis() - firstSeen) / (1000 * 60 * 60 * 24)).toInt()
        if (daysPassed >= BLOCK_AFTER_DAYS) {
            prefs.edit().putBoolean(PREF_APP_BLOCKED, true).apply()
            clearAppData(context)
            Log.w(TAG, "App blocked after $BLOCK_AFTER_DAYS days without update")
            return true
        }
        return false
    }

    fun clearUpdateState(context: Context) {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PREF_UPDATE_FIRST_SEEN)
            .remove(PREF_APP_BLOCKED)
            .apply()
    }

    private fun clearAppData(context: Context) {
        try {
            MmkvManager.removeAllServer()
            Log.d(TAG, "App data cleared due to expired update deadline")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing app data: ${e.message}")
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun showUpdateDialog(context: Context, remoteVersion: RemoteVersion, daysRemaining: Int) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_neobrutalist_card)
            setPadding(dpToPx(context, 20), dpToPx(context, 20), dpToPx(context, 20), dpToPx(context, 20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16)) }
        }

        val titleView = TextView(context).apply {
            text = "NOVA VERSÃO DISPONÍVEL!"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(context, 12))
        }

        val versionView = TextView(context).apply {
            text = "Versão ${remoteVersion.versionName}"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(context, 8))
        }

        val notesView = TextView(context).apply {
            text = if (remoteVersion.releaseNotes.isNotEmpty()) {
                remoteVersion.releaseNotes
            } else {
                "Uma nova versão do Simpsons VPN está disponível."
            }
            textSize = 14f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(context, 12))
        }

        val warningView = TextView(context).apply {
            text = if (daysRemaining > 0) {
                "⚠ Tens $daysRemaining dias para atualizar.\nApós esse prazo, o APP será bloqueado."
            } else {
                "⚠ O prazo para atualizar expirou!"
            }
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F32013"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(context, 16))
        }

        val updateButton = TextView(context).apply {
            text = "ATUALIZAR AGORA"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_neobrutalist_card_selected)
            setPadding(dpToPx(context, 20), dpToPx(context, 12), dpToPx(context, 20), dpToPx(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        cardContainer.addView(titleView)
        cardContainer.addView(versionView)
        cardContainer.addView(notesView)
        cardContainer.addView(warningView)
        cardContainer.addView(updateButton)
        layout.addView(cardContainer)

        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setCancelable(daysRemaining > 0)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        updateButton.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteVersion.downloadUrl))
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening download URL: ${e.message}")
            }
            if (daysRemaining > 0) dialog.dismiss()
        }

        dialog.show()
    }

    fun showBlockedDialog(context: Context, downloadUrl: String) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_neobrutalist_card)
            setPadding(dpToPx(context, 20), dpToPx(context, 20), dpToPx(context, 20), dpToPx(context, 20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16)) }
        }

        val titleView = TextView(context).apply {
            text = "APP BLOQUEADO"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F32013"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(context, 12))
        }

        val messageView = TextView(context).apply {
            text = "O prazo de 15 dias para atualizar expirou.\nAs configurações foram apagadas.\n\nPor favor, baixa a nova versão para continuar a usar o Simpsons VPN."
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(context, 16))
        }

        val updateButton = TextView(context).apply {
            text = "BAIXAR NOVA VERSÃO"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_neobrutalist_card_selected)
            setPadding(dpToPx(context, 20), dpToPx(context, 12), dpToPx(context, 20), dpToPx(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        cardContainer.addView(titleView)
        cardContainer.addView(messageView)
        cardContainer.addView(updateButton)
        layout.addView(cardContainer)

        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        updateButton.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening download URL: ${e.message}")
            }
        }

        dialog.show()
    }
}
