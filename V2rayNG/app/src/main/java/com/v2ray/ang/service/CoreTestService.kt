package com.v2ray.ang.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

class CoreTestService : Service() {
    @Volatile
    private var activeWorker: RealPingWorkerService? = null
    @Volatile
    private var replacementRequested = false
    private var batchStarted = false
    private val batchFinished = AtomicBoolean(false)

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    private val cancelAction by lazy {
        val intent = Intent(this, CoreTestService::class.java).putExtra(
            "content",
            TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Action.Builder(
            R.drawable.ic_stop_24dp,
            getString(R.string.action_cancel),
            pendingIntent
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed")
        activeWorker?.cancel()
        activeWorker = null
        if (!replacementRequested && batchFinished.compareAndSet(false, true)) {
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, "-1")
        }
        NotificationHelper.stopForeground(this)
        super.onDestroy()
        // A new process for every batch prevents Xray's process-wide state from
        // leaking into a later probe or overlapping the long-running VPN core.
        Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.title_real_ping_all_server),
            cancelAction,
        )
        val message = intent?.serializable<TestServiceMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(message, startId)
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> handleMeasureCancel(startId)
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
            LogUtil.i(AppConfig.TAG, "CoreTestService handing the next batch to a fresh process")
            Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
            return START_REDELIVER_INTENT
        }
        batchStarted = true

        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }
        if (guids.isEmpty()) {
            batchFinished.set(true)
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, "0")
            NotificationHelper.stopForeground(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        LogUtil.i(AppConfig.TAG, "CoreTestService starting a ${guids.size}-profile batch")
        activeWorker = RealPingWorkerService(
            context = this,
            guids = guids,
            onlyTcp = message.onlyTcp,
            onEvent = { event -> handleWorkerEvent(event, message) },
        ).also { it.start() }
        return START_NOT_STICKY
    }

    private fun handleMeasureCancel(startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "CoreTestService cancelling the active batch")
        replacementRequested = false
        activeWorker?.cancel()
        if (batchFinished.compareAndSet(false, true)) {
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, "-1")
        }
        activeWorker = null
        NotificationHelper.stopForeground(this)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun handleWorkerEvent(event: RealPingEvent, message: TestServiceMessage) {
        if (replacementRequested) return
        when (event) {
            is RealPingEvent.Progress -> {
                NotificationHelper.updateNotification(
                    channelType = NotificationChannelType.CORE_TEST,
                    context = this,
                    title = getString(R.string.app_name),
                    content = getString(R.string.connection_running_task_left, event.text),
                )
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, event.text)
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, event.guid)
            }

            is RealPingEvent.Finish -> {
                if (message.subscriptionId.isNotEmpty()) {
                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)) {
                        AngConfigManager.removeInvalidServer(message.subscriptionId)
                    }

                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)) {
                        AngConfigManager.sortByTestResultsForSub(message.subscriptionId)
                    }
                }
                if (!batchFinished.compareAndSet(false, true)) return
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, event.status)
                activeWorker = null
                NotificationHelper.stopForeground(this)
                stopSelf()
            }
        }
    }
}
