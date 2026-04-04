package com.daggomostudios.simpsonsvpn

import android.content.Context
import android.util.Log
import com.v2ray.ang.handler.MmkvManager
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Simpsons VPN Crash Handler
 * Captura erros fatais e guarda-os para que possamos mostrar ao utilizador porque o APP fechou.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        private const val KEY_LAST_CRASH = "last_crash_log"

        fun init(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
        }

        fun getLastCrash(): String? {
            return MmkvManager.decodeSettingsString(KEY_LAST_CRASH)
        }

        fun clearLastCrash() {
            MmkvManager.encodeSettings(KEY_LAST_CRASH, "")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val crashLog = sw.toString()
            
            Log.e("SimpsonsVPN_Crash", "FALHA CRÍTICA DETECTADA: $crashLog")
            
            // Guardar o erro no MMKV para mostrar no próximo arranque
            MmkvManager.encodeSettings(KEY_LAST_CRASH, crashLog)
            
        } catch (e: Exception) {
            Log.e("SimpsonsVPN_Crash", "Erro ao guardar log de crash: ${e.message}")
        } finally {
            // Deixar o Android lidar com o encerramento original
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }
}
