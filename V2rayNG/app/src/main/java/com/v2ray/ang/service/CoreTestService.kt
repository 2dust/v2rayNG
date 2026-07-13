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
    private val batchStarted = AtomicBoolean(false)
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
        if (batchFinished.compareAndSet(false, true)) {
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

        when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(message, startId)
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> handleMeasureCancel()
            else -> {
                NotificationHelper.stopForeground(this); stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int) {
        if (!batchStarted.compareAndSet(false, true)) {
            // This process is intentionally single-use. Even a request racing
            // with service teardown must wait for Android to create the next
            // :OutboundProbe process instead of starting a second native core.
            LogUtil.w(AppConfig.TAG, "CoreTestService ignored a second batch in its disposable process")
            return
        }
        LogUtil.i(AppConfig.TAG, "CoreTestService starting batch for subscription ${message.subscriptionId}")

        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.title_real_ping_all_server)
        )

        val guidsList = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }

        if (guidsList.isNotEmpty()) {
            batchFinished.set(false)
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
    }

    private fun handleWorkerEvent(event: RealPingEvent) {
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
                if (event.viableOutboundTag.isNotEmpty()) {
                    MessageUtil.sendMsg2Service(
                        this,
                        AppConfig.MSG_POLICY_ROUTE_OBSERVED,
                        PolicyRouteUpdate(event.guid, event.viableOutboundTag),
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

    private fun handleMeasureCancel() {
        LogUtil.i(AppConfig.TAG, "CoreTestService received cancel message")
        activeWorker?.cancel()
        activeWorker = null
        NotificationHelper.stopForeground(this)
        stopSelf()
    }
}
