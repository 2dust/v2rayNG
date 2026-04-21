package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_SPEED
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayNativeManager
import com.v2ray.ang.util.MessageUtil
import java.util.Collections

class V2RayTestService : Service() {

    // manage active batch workers so each batch is independent and cancellable
    private val activeRealPingWorkers = Collections.synchronizedList(mutableListOf<RealPingWorkerService>())
    private val activeSpeedTestWorkers = Collections.synchronizedList(mutableListOf<SpeedTestWorkerService>())

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        V2RayNativeManager.initCoreEnv(this)
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
        super.onDestroy()
        // cancel any active workers
        cancelAllWorkers()
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<TestServiceMessage>("content") ?: return super.onStartCommand(intent, flags, startId)
        when (message.key) {
            MSG_MEASURE_CONFIG -> {
                val guidsList = if (message.serverGuids.isNotEmpty()) {
                    message.serverGuids
                } else if (message.subscriptionId.isNotEmpty()) {
                    MmkvManager.decodeServerList(message.subscriptionId)
                } else {
                    MmkvManager.decodeAllServerList()
                }

                if (guidsList.isNotEmpty()) {
                    lateinit var worker: RealPingWorkerService
                    worker = RealPingWorkerService(this, guidsList) { status ->
                        // notify UI and remove the worker from active list when finished
                        MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_CONFIG_FINISH, status)
                        activeRealPingWorkers.remove(worker)
                    }
                    activeRealPingWorkers.add(worker)
                    worker.start()
                }
            }

            MSG_MEASURE_CONFIG_SPEED -> {
                val guidsList = if (message.serverGuids.isNotEmpty()) {
                    message.serverGuids
                } else if (message.subscriptionId.isNotEmpty()) {
                    MmkvManager.decodeServerList(message.subscriptionId)
                } else {
                    MmkvManager.decodeAllServerList()
                }

                if (guidsList.isNotEmpty()) {
                    lateinit var worker: SpeedTestWorkerService
                    worker = SpeedTestWorkerService(this, guidsList) { status ->
                        MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_CONFIG_FINISH, status)
                        activeSpeedTestWorkers.remove(worker)
                    }
                    activeSpeedTestWorkers.add(worker)
                    worker.start()
                }
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                cancelAllWorkers()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelAllWorkers() {
        val realPingSnapshot = ArrayList(activeRealPingWorkers)
        realPingSnapshot.forEach { it.cancel() }
        activeRealPingWorkers.clear()

        val speedTestSnapshot = ArrayList(activeSpeedTestWorkers)
        speedTestSnapshot.forEach { it.cancel() }
        activeSpeedTestWorkers.clear()
    }
}
