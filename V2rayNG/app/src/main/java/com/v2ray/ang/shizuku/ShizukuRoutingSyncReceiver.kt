package com.v2ray.ang.shizuku

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSync
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import rikka.shizuku.Shizuku
import java.util.ArrayDeque
import java.util.concurrent.Executors

private const val TAG = "ShizukuSyncReceiver"

/** Relays app-core lifecycle updates to the shell-owned Shizuku UserService over Binder. */
class ShizukuRoutingSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.setExtrasClassLoader(HotspotRoutingSync::class.java.classLoader)
        val update = intent.serializable<HotspotRoutingSync>("content") ?: run {
            LogUtil.w(TAG, "Ignoring malformed hotspot synchronization broadcast")
            return
        }
        val pendingResult = goAsync()
        ShizukuRoutingSyncDispatcher.enqueue(context.applicationContext, update, intent.coreTetheringLease()) {
            pendingResult.finish()
        }
    }
}

/** Keeps updates ordered across the normal core's stop/start process boundary. */
private object ShizukuRoutingSyncDispatcher {
    private const val BIND_TIMEOUT_MS = 10_000L

    private data class PendingUpdate(
        val context: Context,
        val update: HotspotRoutingSync,
        val coreLease: ICoreTetheringLease?,
        val finish: () -> Unit,
        val retryAfterDisconnect: Boolean = true,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<PendingUpdate>()
    private var service: IShizukuTetheringService? = null
    private var binding = false
    private var inFlight = false
    private var bindGeneration = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IShizukuTetheringService.Stub.asInterface(binder)
            binding = false
            pump()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            binding = false
            failAll("Shizuku tethering service disconnected")
        }
    }

    fun enqueue(context: Context, update: HotspotRoutingSync, coreLease: ICoreTetheringLease?, finish: () -> Unit) {
        mainHandler.post {
            queue.addLast(PendingUpdate(context, update, coreLease, finish))
            pump()
        }
    }

    private fun pump() {
        if (inFlight || queue.isEmpty()) return
        val currentService = service
        if (currentService == null) {
            bindIfNeeded()
            return
        }

        val pending = queue.removeFirst()
        inFlight = true
        worker.execute {
            val result = runCatching { forward(currentService, pending) }
            mainHandler.post {
                if (result.exceptionOrNull() is DeadObjectException && pending.retryAfterDisconnect) {
                    // ServiceConnection may still hold the old proxy when the new Shizuku Binder arrives.
                    service = null
                    queue.addFirst(pending.copy(retryAfterDisconnect = false))
                } else {
                    result.onFailure { LogUtil.e(TAG, "Unable to forward hotspot synchronization", it) }
                    pending.finish()
                }
                inFlight = false
                pump()
            }
        }
    }

    private fun bindIfNeeded() {
        if (binding) return
        if (!Shizuku.pingBinder() ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            failAll("Shizuku is unavailable or permission is missing")
            return
        }

        binding = true
        val generation = ++bindGeneration
        runCatching {
            Shizuku.bindUserService(ShizukuTetheringService.createUserServiceArgs(), connection)
        }
            .onFailure {
                binding = false
                failAll("Unable to bind Shizuku tethering service: ${it.message.orEmpty()}")
            }
        mainHandler.postDelayed({
            if (binding && generation == bindGeneration) {
                binding = false
                failAll("Timed out binding Shizuku tethering service")
            }
        }, BIND_TIMEOUT_MS)
    }

    private fun forward(service: IShizukuTetheringService, pending: PendingUpdate) {
        val update = pending.update
        val result = when (update.event) {
            HotspotRoutingSync.EVENT_CORE_STOPPING -> service.notifyCoreStopping(update.token)
            HotspotRoutingSync.EVENT_CORE_STARTED -> {
                val snapshot = requireNotNull(update.snapshot) { "Core-start update has no snapshot" }
                val coreLease = requireNotNull(pending.coreLease) {
                    "Core-start update has no protected-network lease"
                }
                val parameters = HotspotRoutingConfig.parametersFromSnapshot(pending.context, snapshot)
                val syncResult = service.synchronizeRouting(
                    update.token,
                    parameters.useHev,
                    parameters.profileName,
                    parameters.dnsServers.toTypedArray(),
                    parameters.ipv6Enabled,
                    coreLease,
                )
                if (syncResult != ShizukuTetheringService.RESULT_INVALID_SESSION) {
                    syncResult
                } else {
                    LogUtil.i(TAG, "Recreating Shizuku tethering after its UserService was lost")
                    service.startRouting(
                        parameters.useHev,
                        parameters.profileName,
                        parameters.dnsServers.toTypedArray(),
                        parameters.ipv6Enabled,
                        parameters.assetPath,
                        parameters.xudpKey,
                        update.token,
                        coreLease,
                    )
                }
            }
            HotspotRoutingSync.EVENT_CORE_START_FAILED -> {
                service.notifyCoreStartFailed(update.token, update.detail)
            }
            else -> error("Unknown hotspot synchronization event ${update.event}")
        }
        if (result == ShizukuTetheringService.RESULT_INVALID_SESSION) {
            clearSyncTokenIfCurrent(update.token)
            LogUtil.w(TAG, "Dropped stale Shizuku tethering synchronization session")
            return
        }
        check(result == ShizukuTetheringService.RESULT_OK) {
            "Shizuku tethering service rejected synchronization with result $result"
        }
        LogUtil.i(TAG, "Forwarded hotspot sync event ${update.event}")
    }

    private fun clearSyncTokenIfCurrent(token: String) {
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN) == token) {
            MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
        }
    }

    private fun failAll(message: String) {
        if (queue.isNotEmpty()) LogUtil.w(TAG, message)
        while (queue.isNotEmpty()) queue.removeFirst().finish()
        inFlight = false
    }
}
