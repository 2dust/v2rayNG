package com.v2ray.ang.shizuku

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.HotspotRoutingSync
import rikka.shizuku.Shizuku
import java.util.ArrayDeque
import java.util.concurrent.Executors

/** Relays app-core lifecycle updates to the shell-owned Shizuku UserService over Binder. */
class ShizukuRoutingSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppConfig.BROADCAST_ACTION_SHIZUKU ||
            intent.getIntExtra("key", 0) != AppConfig.MSG_HOTSPOT_SYNC
        ) {
            return
        }
        val update = readUpdate(intent) ?: run {
            Log.w(TAG, "Ignoring malformed hotspot synchronization broadcast")
            return
        }
        val pendingResult = goAsync()
        ShizukuRoutingSyncDispatcher.enqueue(context.applicationContext, update) {
            pendingResult.finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun readUpdate(intent: Intent): HotspotRoutingSync? {
        intent.setExtrasClassLoader(HotspotRoutingSync::class.java.classLoader)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("content", HotspotRoutingSync::class.java)
        } else {
            intent.getSerializableExtra("content") as? HotspotRoutingSync
        }
    }

    private companion object {
        const val TAG = "ShizukuSyncReceiver"
    }
}

/** Keeps updates ordered across the normal core's stop/start process boundary. */
private object ShizukuRoutingSyncDispatcher {
    private const val TAG = "ShizukuSyncReceiver"
    private const val BIND_TIMEOUT_MS = 10_000L

    private data class PendingUpdate(
        val context: Context,
        val update: HotspotRoutingSync,
        val finish: () -> Unit,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<PendingUpdate>()
    private var service: IShizukuTetheringService? = null
    private var binding = false
    private var inFlight = false
    private var bindGeneration = 0
    private var appContext: Context? = null

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

    fun enqueue(context: Context, update: HotspotRoutingSync, finish: () -> Unit) {
        mainHandler.post {
            appContext = context.applicationContext
            queue.addLast(PendingUpdate(context.applicationContext, update, finish))
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
            runCatching { forward(currentService, pending) }
                .onFailure { Log.e(TAG, "Unable to forward hotspot synchronization", it) }
            mainHandler.post {
                pending.finish()
                inFlight = false
                pump()
            }
        }
    }

    private fun bindIfNeeded() {
        if (binding) return
        val context = appContext ?: return
        if (!Shizuku.pingBinder() ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            failAll("Shizuku is unavailable or permission is missing")
            return
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuTetheringService::class.java.name)
        )
            .daemon(true)
            .processNameSuffix("shizuku_tethering")
            .debuggable(BuildConfig.DEBUG)
            .version(ShizukuTetheringService.USER_SERVICE_VERSION)
        binding = true
        val generation = ++bindGeneration
        runCatching { Shizuku.bindUserService(args, connection) }
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
                val config = HotspotRoutingConfig.fromSnapshot(pending.context, snapshot)
                service.synchronizeRouting(
                    update.token,
                    config.useHev,
                    config.profileName,
                    config.coreConfig,
                    config.hevConfig,
                )
            }
            HotspotRoutingSync.EVENT_CORE_START_FAILED -> {
                service.notifyCoreStartFailed(update.token, update.detail)
            }
            else -> error("Unknown hotspot synchronization event ${update.event}")
        }
        check(result == ShizukuTetheringService.RESULT_OK) {
            "Shizuku tethering service rejected synchronization with result $result"
        }
        Log.i(TAG, "Forwarded hotspot sync event ${update.event}")
    }

    private fun failAll(message: String) {
        if (queue.isNotEmpty()) Log.w(TAG, message)
        while (queue.isNotEmpty()) queue.removeFirst().finish()
        inFlight = false
    }
}
