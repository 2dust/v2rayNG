package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.NotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionUpdateService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val runningTasks = AtomicInteger(0)

    // manage active batch workers so each batch is independent and cancellable
    private val activeWorkers = Collections.synchronizedList(mutableListOf<RealPingWorkerService>())

    private val updateSemaphore = Semaphore(2)

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService is being destroyed")
        val snapshot = ArrayList(activeWorkers)
        snapshot.forEach { it.cancel() }
        activeWorkers.clear()
        serviceJob.cancel()
        NotificationHelper.stopForeground(this)
        NotificationHelper.cancel(NotificationChannelType.SUBSCRIPTION_UPDATE, this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<SubscriptionUpdateMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_SUB_UPDATE_START -> handleUpdateStart(message)
            AppConfig.MSG_SUB_UPDATE_CANCEL -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
            }

            else -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleUpdateStart(message: SubscriptionUpdateMessage) {
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService starting update task for ${message.subIds.size} subscriptions")

        NotificationHelper.startForeground(
            this,
            NotificationChannelType.SUBSCRIPTION_UPDATE,
            getString(R.string.title_pref_auto_update_subscription),
            getString(R.string.app_name)
        )

        runningTasks.incrementAndGet()
        serviceScope.launch {
            updateSemaphore.withPermit {
                try {
                    message.subIds.forEach { subId ->
                        updateSingle(subId, message.forcedUpdate)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "SubscriptionUpdateService update failed", e)
                } finally {
                    if (runningTasks.decrementAndGet() == 0 && activeWorkers.isEmpty()) {
                        NotificationHelper.stopForeground(this@SubscriptionUpdateService)
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun updateSingle(subId: String, forcedUpdate: Boolean) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        if(!subItem.enabled || subItem.url.isEmpty()){
            return
        }

        val sub = SubscriptionCache(subId, subItem)

        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: Updating ${subItem.remarks}")
        showNotification(
            context = this,
            titleResId = R.string.title_pref_auto_update_subscription,
            content = "Updating ${subItem.remarks}"
        )

        if (forcedUpdate || MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)) {
            AngConfigManager.updateConfigViaSub(sub)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)) {
            testSubscriptionServers(sub)

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: removing invalid servers for ${subItem.remarks}")
                showNotification(
                    context = this,
                    titleResId = R.string.title_del_invalid_config,
                    content = subItem.remarks
                )
                AngConfigManager.removeInvalidServer(subId)
            }
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: sorting servers for ${subItem.remarks}")
                showNotification(
                    context = this,
                    titleResId = R.string.title_sort_by_test_results,
                    content = subItem.remarks
                )
                AngConfigManager.sortByTestResultsForSub(subId)
            }
        }

        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: Finished ${subItem.remarks}")
    }

    private suspend fun testSubscriptionServers(sub: SubscriptionCache) {
        val subId = sub.guid
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: starting test phase for ${sub.subscription.remarks}")
        showNotification(
            context = this,
            titleResId = R.string.title_real_ping_all_server,
            content = sub.subscription.remarks
        )

        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isNotEmpty()) {
            val deferred = CompletableDeferred<Unit>()
            lateinit var worker: RealPingWorkerService
            worker = RealPingWorkerService(
                context = this,
                guids = guids,
                onEvent = { event ->
                    handleWorkerEvent(event, sub.subscription.remarks) {
                        activeWorkers.remove(worker)
                        deferred.complete(Unit)
                    }
                }
            )
            activeWorkers.add(worker)
            worker.start()
            deferred.await()
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: test phase finished for ${sub.subscription.remarks}")
        }
    }

    private fun handleWorkerEvent(event: RealPingEvent, remarks: String, onWorkerDone: () -> Unit) {
        when (event) {
            is RealPingEvent.Progress -> {
                val text = "${event.text} in $remarks"
                showNotification(
                    context = this,
                    titleResId = R.string.title_real_ping_all_server,
                    content = text
                )
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: $text")
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
            }

            is RealPingEvent.Finish -> {
                onWorkerDone()
            }
        }
    }

    private fun showNotification(context: Context, titleResId: Int, content: String) {
        NotificationHelper.notify(
            NotificationChannelType.SUBSCRIPTION_UPDATE,
            context,
            context.getString(titleResId),
            content
        )
    }
}