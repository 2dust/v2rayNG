package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Handles periodic server testing and automatic server switching.
 * Uses WorkManager for reliable background execution.
 */
object BackgroundServerTester {

    private const val NOTIFICATION_ID = 4
    private const val SWITCH_THRESHOLD = 0.8 // Switch if new server is 20% faster

    data class TestResult(
        val guid: String,
        val remarks: String,
        val delay: Long
    )

    /**
     * WorkManager task for background server testing and auto-switching.
     */
    class TestAndSwitchTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        private val notificationManager = NotificationManagerCompat.from(applicationContext)

        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            Log.i(AppConfig.TAG, "Background server testing starting")

            createNotificationChannel()

            val subscriptionId = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "") ?: ""
            val servers = MmkvManager.getServersBySubscriptionId(subscriptionId)
            if (servers.isEmpty()) {
                Log.i(AppConfig.TAG, "No servers to test")
                return Result.success()
            }

            showNotification(applicationContext.getString(R.string.smart_connect_testing))

            val bestResult = testServers(applicationContext, servers, "background")
                .filter { it.delay > 0 }
                .minByOrNull { it.delay }

            if (bestResult != null) {
                Log.i(AppConfig.TAG, "Best server: ${bestResult.remarks} (${bestResult.delay}ms)")
                handleAutoSwitch(bestResult)
            } else {
                Log.w(AppConfig.TAG, "No working servers found")
            }

            cancelNotification()
            return Result.success()
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    AppConfig.AUTO_SWITCH_CHANNEL,
                    AppConfig.AUTO_SWITCH_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun buildNotification(): NotificationCompat.Builder {
            return NotificationCompat.Builder(applicationContext, AppConfig.AUTO_SWITCH_CHANNEL)
                .setWhen(0)
                .setTicker("Testing")
                .setContentTitle(applicationContext.getString(R.string.title_auto_server_test))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        }

        @SuppressLint("MissingPermission")
        private fun showNotification(text: String) {
            try {
                notificationManager.notify(NOTIFICATION_ID, buildNotification().setContentText(text).build())
            } catch (e: Exception) {
                Log.w(AppConfig.TAG, "Failed to show notification", e)
            }
        }

        private fun cancelNotification() {
            try {
                notificationManager.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.w(AppConfig.TAG, "Failed to cancel notification", e)
            }
        }

        @SuppressLint("MissingPermission")
        private fun handleAutoSwitch(bestResult: TestResult) {
            val autoSwitchEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SERVER_SWITCH_ENABLED, true)
            val currentServer = MmkvManager.getSelectServer()

            if (!autoSwitchEnabled || !V2RayServiceManager.isRunning() || bestResult.guid == currentServer) {
                return
            }

            val currentDelay = MmkvManager.decodeServerAffiliationInfo(currentServer ?: "")?.testDelayMillis ?: Long.MAX_VALUE
            val shouldSwitch = currentDelay <= 0 || bestResult.delay < currentDelay * SWITCH_THRESHOLD

            if (!shouldSwitch) {
                Log.i(AppConfig.TAG, "Current server is good enough, not switching")
                return
            }

            val switched = V2RayServiceManager.switchServer(applicationContext, bestResult.guid)
            if (switched) {
                showNotification(
                    applicationContext.getString(
                        R.string.notification_server_switched,
                        bestResult.remarks,
                        bestResult.delay
                    )
                )
                Log.i(AppConfig.TAG, "Switched to ${bestResult.remarks}")
            }
        }
    }

    /**
     * Tests all servers and returns results.
     *
     * @param context The application context.
     * @param serverGuids The list of server GUIDs to test.
     * @param testSource The source of the test (manual, background, post-sub).
     * @return A list of test results.
     */
    suspend fun testServers(
        context: Context,
        serverGuids: List<String>,
        testSource: String = "manual"
    ): List<TestResult> = coroutineScope {
        val testUrl = SettingsManager.getDelayTestUrl()

        // Use limited parallelism to be battery-efficient
        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

        serverGuids.chunked(parallelism).flatMap { chunk ->
            chunk.map { guid ->
                async(Dispatchers.IO) {
                    testSingleServer(context, guid, testUrl, testSource)
                }
            }.awaitAll()
        }.filterNotNull()
    }

    /**
     * Tests a single server and returns the result.
     */
    private suspend fun testSingleServer(
        context: Context,
        guid: String,
        testUrl: String,
        testSource: String
    ): TestResult? = withContext(Dispatchers.IO) {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return@withContext null
            val remarks = config.remarks ?: guid

            // Get the speedtest config
            val result = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
            if (!result.status) {
                MmkvManager.encodeServerTestDelayMillis(guid, -1L, testSource)
                return@withContext TestResult(guid, remarks, -1L)
            }

            // Measure delay using native library
            val delay = V2RayNativeManager.measureOutboundDelay(result.content, testUrl)
            MmkvManager.encodeServerTestDelayMillis(guid, delay, testSource)

            TestResult(guid, remarks, delay)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to test server $guid", e)
            MmkvManager.encodeServerTestDelayMillis(guid, -1L, testSource)
            null
        }
    }

    /**
     * Tests servers and returns the best one (for Smart Connect).
     *
     * @param context The application context.
     * @param subscriptionId The subscription ID (empty for all).
     * @return The best server result, or null if none found.
     */
    suspend fun testAndFindBest(context: Context, subscriptionId: String): TestResult? {
        val servers = MmkvManager.getServersBySubscriptionId(subscriptionId)
        if (servers.isEmpty()) return null

        val results = testServers(context, servers, "smart_connect")
        return results.filter { it.delay > 0 }.minByOrNull { it.delay }
    }

    /**
     * Schedules periodic server testing.
     *
     * @param context The application context.
     * @param intervalMinutes The interval in minutes.
     */
    fun schedulePeriodicTest(context: Context, intervalMinutes: Long) {
        val rw = RemoteWorkManager.getInstance(context)
        rw.cancelUniqueWork(AppConfig.AUTO_SERVER_TEST_TASK_NAME)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        rw.enqueueUniquePeriodicWork(
            AppConfig.AUTO_SERVER_TEST_TASK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequest.Builder(
                TestAndSwitchTask::class.java,
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .build()
        )
        Log.i(AppConfig.TAG, "Scheduled background server testing every $intervalMinutes minutes")
    }

    /**
     * Cancels periodic server testing.
     *
     * @param context The application context.
     */
    fun cancelPeriodicTest(context: Context) {
        val rw = RemoteWorkManager.getInstance(context)
        rw.cancelUniqueWork(AppConfig.AUTO_SERVER_TEST_TASK_NAME)
        Log.i(AppConfig.TAG, "Cancelled background server testing")
    }

    /**
     * Triggers an immediate test (e.g., after subscription update).
     *
     * @param context The application context.
     * @param subscriptionId The subscription ID to test.
     */
    fun triggerImmediateTest(context: Context, subscriptionId: String) {
        val rw = RemoteWorkManager.getInstance(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        rw.enqueue(
            OneTimeWorkRequest.Builder(TestAndSwitchTask::class.java)
                .setConstraints(constraints)
                .build()
        )
        Log.i(AppConfig.TAG, "Triggered immediate server test for subscription: $subscriptionId")
    }
}
