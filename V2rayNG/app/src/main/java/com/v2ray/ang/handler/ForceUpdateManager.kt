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
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class UpdateCheckResult {
    data class NewVersion(val remoteVersion: ForceUpdateManager.RemoteVersion) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object Error : UpdateCheckResult()
}

object ForceUpdateManager {

    private const val TAG = "SimpsonsVPN_ForceUpdate"
    private const val PREF_APP_BLOCKED = "pref_app_blocked"
    private val VERSION_JSON_URL: String by lazy { com.daggomostudios.simpsonsvpn.NativeCrypto.getVersionUrl() }

    private var currentDialog: AlertDialog? = null

    private const val PREF_LAST_DOWNLOAD_URL = "pref_last_download_url"

    data class RemoteVersion(
        val versionCode: Int = 0,
        val versionName: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = "",
        val blockingDate: String = ""
    )

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            var response = HttpUtil.getUrlContent(VERSION_JSON_URL, 10000)
            if (response.isNullOrEmpty()) {
                val httpPort = SettingsManager.getHttpPort()
                response = HttpUtil.getUrlContent(VERSION_JSON_URL, 10000, httpPort)
            }
            if (response.isNullOrEmpty()) {
                Log.w(TAG, "Failed to fetch version.json")
                return@withContext UpdateCheckResult.Error
            }

            val remoteVersion = JsonUtil.fromJson(response, RemoteVersion::class.java)
            if (remoteVersion == null) {
                Log.w(TAG, "Failed to parse version.json")
                return@withContext UpdateCheckResult.Error
            }

            Log.d(TAG, "Remote version: ${remoteVersion.versionName} (code: ${remoteVersion.versionCode}), current: ${BuildConfig.VERSION_NAME} (code: ${BuildConfig.VERSION_CODE})")

            if (compareVersions(remoteVersion.versionName, BuildConfig.VERSION_NAME) > 0) {
                return@withContext UpdateCheckResult.NewVersion(remoteVersion)
            }

            return@withContext UpdateCheckResult.UpToDate
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}")
            return@withContext UpdateCheckResult.Error
        }
    }

    fun isAppBlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_APP_BLOCKED, false)
    }

    private fun parseDateToMillis(dateStr: String): Long {
        if (dateStr.isEmpty()) return 0L
        return try {
            // Suportar tanto YYYY-MM-DD quanto YYYY/MM/DD
            val normalizedDate = dateStr.replace("/", "-")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val date = sdf.parse(normalizedDate) ?: return 0L

            // Definir o prazo para o FINAL do dia (23:59:59)
            val calendar = java.util.Calendar.getInstance()
            calendar.time = date
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            calendar.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing blockingDate: $dateStr")
            0L
        }
    }

    fun getDaysRemaining(remoteVersion: RemoteVersion): Int {
        val blockingMillis = parseDateToMillis(remoteVersion.blockingDate)
        if (blockingMillis == 0L) return 999

        val now = System.currentTimeMillis()
        if (now > blockingMillis) return -1

        val diff = blockingMillis - now
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun checkAndBlockIfExpired(context: Context, remoteVersion: RemoteVersion): Boolean {
        val blockingMillis = parseDateToMillis(remoteVersion.blockingDate)
        if (blockingMillis == 0L) return false

        if (System.currentTimeMillis() > blockingMillis) {
            val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_APP_BLOCKED, true).apply()
            clearAppData(context)
            Log.w(TAG, "App blocked as blockingDate (${remoteVersion.blockingDate}) has passed")
            return true
        }
        return false
    }

    fun clearUpdateState(context: Context) {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PREF_APP_BLOCKED)
            .apply()
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val v1 = version1.split(".")
        val v2 = version2.split(".")
        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i].toIntOrNull() ?: 0 else 0
            val num2 = if (i < v2.size) v2[i].toIntOrNull() ?: 0 else 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    fun saveLastDownloadUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LAST_DOWNLOAD_URL, url).apply()
    }

    fun getLastDownloadUrl(context: Context): String {
        val prefs = context.getSharedPreferences("simpsons_vpn_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREF_LAST_DOWNLOAD_URL, "https://mediafire.com") ?: "https://mediafire.com"
    }

    fun clearAppData(context: Context) {
        try {
            // Desconectar VPN imediatamente
            V2RayServiceManager.stopVService(context)
            // Apagar servidores
            MmkvManager.removeAllServer()
            Log.d(TAG, "App data cleared and VPN stopped due to expired update deadline")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing app data: ${e.message}")
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun dismissCurrentDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    fun showUpdateDialog(context: Context, remoteVersion: RemoteVersion, daysRemaining: Int) {
        dismissCurrentDialog()
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
            text = when {
                daysRemaining > 1 -> "⚠ Tens $daysRemaining dias para atualizar.\nApós esse prazo, o APP será bloqueado."
                daysRemaining == 1 -> "⚠ Amanhã é o último dia para atualizar!\nO APP será bloqueado em seguida."
                daysRemaining == 0 -> "⚠ HOJE é o último dia para atualizar!\nO APP será bloqueado à meia-noite."
                else -> "⚠ O prazo para atualizar expirou!"
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

        val isDeadlineReached = daysRemaining < 0
        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setCancelable(!isDeadlineReached)
            .create()

        currentDialog = dialog
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
        dismissCurrentDialog()
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
            text = "Esta versão do APP expirou.\nAs configurações foram apagadas.\n\nPor favor, baixa a nova versão para continuar a usar o Simpsons VPN."
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

        currentDialog = dialog
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
