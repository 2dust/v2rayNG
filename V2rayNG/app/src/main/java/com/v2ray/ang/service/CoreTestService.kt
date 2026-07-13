package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.PolicyRouteUpdate
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.NotificationHelper
import java.util.concurrent.atomic.AtomicBoolean

class CoreTestService : Service() {

    @Volatile
    private var activeWorker: RealPingWorkerService? = null
    @Volatile
    private var replacementRequested = false
    private var batchStarted = false
    private val batchFinished = AtomicBoolean(false)

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Cleans up resources when the service is destroyed.
     */
    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed")
        activeWorker?.cancel()
        activeWorker = null
        if (!replacementRequested && batchFinished.compareAndSet(false, true)) {
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, "-1")
        }
        NotificationHelper.stopForeground(this)
        super.onDestroy()
        // This process exists solely to own one batch core. Discarding it after
        // the service stops makes the one-core-per-process boundary explicit
        // and prevents native singleton state from leaking into a later batch.
        Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<TestServiceMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(message, startId)
            else -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int): Int {
        if (batchStarted) {
            replacementRequested = true
            startProbeForeground()
            LogUtil.i(AppConfig.TAG, "CoreTestService handing the next batch to a fresh process")

            // The latest start intent must remain start-requested while this
            // single-use process exits. Returning REDELIVER before killing the
            // process makes Android replay that exact request in a new process;
            // stopService followed by startForegroundService can instead lose
            // the replacement to a lifecycle race in the dying service.
            Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
            return START_REDELIVER_INTENT
        }
        batchStarted = true
        LogUtil.i(AppConfig.TAG, "CoreTestService starting batch for subscription ${message.subscriptionId}")

        startProbeForeground()

        val guidsList = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }

        if (guidsList.isNotEmpty()) {
            activeWorker = RealPingWorkerService(
                context = this,
                guids = guidsList,
                onEvent = ::handleWorkerEvent,
            )
            activeWorker?.start()
        } else {
            NotificationHelper.stopForeground(this)
            batchFinished.set(true)
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, "0")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startProbeForeground() {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.title_real_ping_all_server)
        )
    }

    private fun handleWorkerEvent(event: RealPingEvent) {
        // Once another start has claimed the service, the old process must not
        // publish stale progress or results while waiting for its final kill.
        if (replacementRequested) return
        when (event) {
            is RealPingEvent.Progress -> {
                NotificationHelper.updateNotification(
                    channelType = NotificationChannelType.CORE_TEST,
                    context = this,
                    content = getString(R.string.connection_runing_task_left, event.text)
                )
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, event.text)
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                val networkKey = event.networkKey
                val networkHandle = event.networkHandle
                if (event.viableOutboundTag.isNotEmpty() && networkKey != null && networkHandle != null) {
                    MessageUtil.sendMsg2Service(
                        this,
                        AppConfig.MSG_POLICY_ROUTE_OBSERVED,
                        PolicyRouteUpdate(
                            event.guid,
                            event.viableOutboundTag,
                            networkKey,
                            networkHandle,
                        ),
                    )
                }
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, event.guid)
            }

            is RealPingEvent.Finish -> {
                if (!batchFinished.compareAndSet(false, true)) return
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, event.status)
                activeWorker = null
                NotificationHelper.stopForeground(this)
                stopSelf()
            }
        }
    }

}
