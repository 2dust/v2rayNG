package com.v2ray.ang.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The test phases of subscription updates. Starting a configuration ends them (see CoreServiceManager): the running
 * phases stop, and the updates that run or wait at that moment start no more, while their downloads go on. Updates
 * that begin afterwards test again.
 */
internal class SubscriptionTestPhases<W>(private val cancelWorker: (W) -> Unit) {
    private var generation = 0
    private val running = HashMap<W, CompletableDeferred<Boolean>>()

    /** Returns the token of an update that begins now. */
    @Synchronized
    fun begin(): Int = generation

    /** Whether the update with [token] may still start a test phase. */
    @Synchronized
    fun mayStart(token: Int): Boolean = token == generation

    /**
     * Starts [worker] with [startWorker] as a test phase of the update with [token], unless its tests ended since.
     * The result is true once the worker finishes, and false when the phase ends first or never starts. The lock is
     * held while the worker starts, so that its finish or an end comes after it is registered.
     */
    @Synchronized
    fun start(token: Int, worker: W, startWorker: (W) -> Unit): Deferred<Boolean> {
        if (token != generation) {
            return CompletableDeferred(false)
        }
        startWorker(worker)
        return CompletableDeferred<Boolean>().also { running[worker] = it }
    }

    /** The worker of a test phase finished. */
    @Synchronized
    fun finish(worker: W) {
        running.remove(worker)?.complete(true)
    }

    /** Ends the running test phases, and those that the updates which run or wait now would start. */
    @Synchronized
    fun cancelAll() {
        generation++
        running.forEach { (worker, finished) ->
            cancelWorker(worker)
            finished.complete(false)
        }
        running.clear()
    }

    @Synchronized
    fun isIdle(): Boolean = running.isEmpty()
}

class SubscriptionUpdateService : Service() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val runningTasks = AtomicInteger(0)

    // manage active batch workers so each batch is independent and cancellable
    private val testPhases = SubscriptionTestPhases<RealPingWorkerService> { it.cancel() }

    // Starting a configuration ends the test phase of the updates (see CoreServiceManager)
    private val cancelTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("key", 0) == AppConfig.MSG_SUB_UPDATE_CANCEL_TEST) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: a configuration started, ending the test phase of the updates")
                testPhases.cancelAll()
            }
        }
    }

    private val updateSemaphore = Semaphore(2)

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
        ContextCompat.registerReceiver(
            this, cancelTestReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_SUBSCRIPTION), Utils.receiverFlags()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService is being destroyed")
        unregisterReceiver(cancelTestReceiver)
        // The updates end first: an update whose test phase ended would otherwise go on to its next subscription
        serviceJob.cancel()
        testPhases.cancelAll()
        NotificationHelper.stopForeground(this)
        NotificationHelper.cancel(NotificationChannelType.SUBSCRIPTION_UPDATE, this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.SUBSCRIPTION_UPDATE,
            getString(R.string.title_pref_auto_update_subscription),
            getString(R.string.app_name)
        )
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

        runningTasks.incrementAndGet()
        // A configuration that starts from now on ends the test phase of this update, also while it waits
        val testToken = testPhases.begin()
        serviceScope.launch {
            updateSemaphore.withPermit {
                try {
                    message.subIds.forEach { subId ->
                        updateSingle(subId, message.forcedUpdate, testToken)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "SubscriptionUpdateService update failed", e)
                } finally {
                    if (runningTasks.decrementAndGet() == 0 && testPhases.isIdle()) {
                        NotificationHelper.stopForeground(this@SubscriptionUpdateService)
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun updateSingle(subId: String, forcedUpdate: Boolean, testToken: Int) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        if (!subItem.enabled || subItem.url.isEmpty()) {
            return
        }

        val sub = SubscriptionCache(subId, subItem)
        // Whether the configs of the subscription or their test results changed, also by work that was then cancelled
        var changed = false
        try {
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: Updating ${subItem.remarks}")
            showNotification(
                context = this,
                titleResId = R.string.title_pref_auto_update_subscription,
                content = getString(R.string.subscription_update_updating, subItem.remarks)
            )

            if (forcedUpdate || MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)) {
                changed = AngConfigManager.updateConfigViaSub(sub).configCount > 0
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
                && testPhases.mayStart(testToken)
            ) {
                // The test saves each result as it comes
                changed = true
                // A configuration that starts meanwhile ends the test; removing and sorting need all its results
                val tested = testSubscriptionServers(sub, testToken)

                if (tested && MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)) {
                    LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: removing invalid servers for ${subItem.remarks}")
                    showNotification(
                        context = this,
                        titleResId = R.string.title_del_invalid_config,
                        content = subItem.remarks
                    )
                    AngConfigManager.removeInvalidServer(subId)
                }
                if (tested && MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)) {
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
        } finally {
            // The main screen reloads what it shows of the subscription
            if (changed) MessageHelper.sendMsg2UI(this, AppConfig.MSG_SERVERS_CHANGED, subId)
        }
    }

    /** Tests the servers of the subscription; returns false when a configuration that started ended the test first. */
    private suspend fun testSubscriptionServers(sub: SubscriptionCache, testToken: Int): Boolean {
        val subId = sub.guid
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: starting test phase for ${sub.subscription.remarks}")
        showNotification(
            context = this,
            titleResId = R.string.title_real_ping_all_server,
            content = sub.subscription.remarks
        )

        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isNotEmpty()) {
            lateinit var worker: RealPingWorkerService
            worker = RealPingWorkerService(
                context = this,
                guids = guids,
                onEvent = { event ->
                    handleWorkerEvent(event, sub.subscription.remarks) {
                        testPhases.finish(worker)
                    }
                }
            )
            if (!testPhases.start(testToken, worker) { it.start() }.await()) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: test phase ended for ${sub.subscription.remarks}, a configuration started")
                return false
            }
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: test phase finished for ${sub.subscription.remarks}")
        }
        return true
    }

    private fun handleWorkerEvent(event: RealPingEvent, remarks: String, onWorkerDone: () -> Unit) {
        when (event) {
            is RealPingEvent.Progress -> {
                val notificationText = getString(
                    R.string.subscription_update_progress,
                    event.text,
                    remarks
                )
                showNotification(
                    context = this,
                    titleResId = R.string.title_real_ping_all_server,
                    content = notificationText
                )
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: ${event.text} in $remarks")
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
