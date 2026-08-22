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
import com.v2ray.ang.dto.RealPingProgress
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.RealPingSummary
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil

class CoreTestService : Service() {
    @Volatile
    private var activeWorker: RealPingWorkerService? = null

    @Volatile
    private var activeMessage: TestServiceMessage? = null

    @Volatile
    private var suppressWorkerEvents = false

    private val terminalLock = Any()
    private var batchStarted = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    private val cancelAction by lazy {
        val intent = Intent(this, CoreTestService::class.java).putExtra(
            "content",
            TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL),
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Action.Builder(
            R.drawable.ic_stop_24dp,
            getString(R.string.action_cancel),
            pendingIntent,
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed")
        suppressWorkerEvents = true
        activeWorker?.cancel()
        activeWorker = null
        activeMessage = null
        NotificationHelper.stopForeground(this)
        super.onDestroy()
        // Xray owns process-wide dialer state. Do not reuse this process after a batch.
        disposeProcess()
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
            // Never let two per-profile worker pools multiply the configured limit.
            // Redeliver only the newest request after this disposable process exits.
            synchronized(terminalLock) {
                suppressWorkerEvents = true
                activeWorker?.cancel()
            }
            LogUtil.i(AppConfig.TAG, "CoreTestService handing replacement batch to a fresh process")
            disposeProcess()
            return START_REDELIVER_INTENT
        }
        batchStarted = true
        activeMessage = message

        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }.distinct()

        if (guids.isEmpty()) {
            sendSummary(
                RealPingSummary(
                    testId = message.testId,
                    live = 0,
                    total = 0,
                    cancelled = false,
                ),
            )
            NotificationHelper.stopForeground(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        LogUtil.i(AppConfig.TAG, "CoreTestService starting ${guids.size} probes")
        activeWorker = RealPingWorkerService(
            context = this,
            guids = guids,
            onlyTcp = message.onlyTcp,
            onEvent = ::handleWorkerEvent,
        ).also { it.start() }
        return START_NOT_STICKY
    }

    private fun handleMeasureCancel(startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "CoreTestService cancelling the active batch")
        synchronized(terminalLock) {
            suppressWorkerEvents = true
            val message = activeMessage
            val summary = activeWorker?.cancel()
            activeWorker = null
            activeMessage = null
            if (message != null && summary != null) {
                sendSummary(
                    RealPingSummary(
                        testId = message.testId,
                        live = summary.live,
                        total = summary.total,
                        cancelled = true,
                    ),
                )
            }
        }
        NotificationHelper.stopForeground(this)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun handleWorkerEvent(event: RealPingEvent) {
        if (suppressWorkerEvents) return
        val message = activeMessage ?: return
        when (event) {
            is RealPingEvent.Progress -> {
                val progressText = "${event.completed} / ${event.total}"
                NotificationHelper.updateNotification(
                    channelType = NotificationChannelType.CORE_TEST,
                    context = this,
                    title = getString(R.string.app_name),
                    content = getString(R.string.connection_running_task_left, progressText),
                )
                MessageHelper.sendMsg2UI(
                    this,
                    AppConfig.MSG_MEASURE_CONFIG_NOTIFY,
                    RealPingProgress(message.testId, event.completed, event.total),
                )
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                MessageHelper.sendMsg2UI(
                    this,
                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS,
                    RealPingResult(message.testId, event.guid, event.delayMillis),
                )
            }

            is RealPingEvent.Finish -> synchronized(terminalLock) {
                if (!suppressWorkerEvents && activeMessage == message) {
                    finishBatch(message, event)
                }
            }
        }
    }

    private fun finishBatch(message: TestServiceMessage, event: RealPingEvent.Finish) {
        val autoRemove = message.subscriptionId.isNotEmpty() &&
                MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        val autoSort = message.subscriptionId.isNotEmpty() &&
                MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)
        val listBefore = if (autoRemove || autoSort) {
            MmkvManager.decodeServerList(message.subscriptionId)
        } else {
            emptyList()
        }
        if (autoRemove) {
            AngConfigManager.removeInvalidServer(message.subscriptionId)
        }
        if (autoSort) {
            AngConfigManager.sortByTestResultsForSub(message.subscriptionId)
        }
        val listChanged = (autoRemove || autoSort) &&
                listBefore != MmkvManager.decodeServerList(message.subscriptionId)

        sendSummary(
            RealPingSummary(
                testId = message.testId,
                live = event.live,
                total = event.total,
                cancelled = false,
                listChanged = listChanged,
            ),
        )
        suppressWorkerEvents = true
        activeWorker = null
        activeMessage = null
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    private fun sendSummary(summary: RealPingSummary) {
        MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, summary)
    }

    private fun disposeProcess() {
        Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
    }
}
