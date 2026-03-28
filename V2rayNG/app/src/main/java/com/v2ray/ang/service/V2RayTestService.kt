package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_FAST_IP_PURITY
import com.v2ray.ang.AppConfig.MSG_MEASURE_FAST_IP_PURITY_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_IP_PURITY
import com.v2ray.ang.AppConfig.MSG_MEASURE_IP_PURITY_CANCEL
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayNativeManager
import com.v2ray.ang.util.MessageUtil
import java.util.Collections

class V2RayTestService : Service() {
    private val activeWorkers = Collections.synchronizedList(mutableListOf<RealPingWorkerService>())
    private val activePurityWorkers = Collections.synchronizedList(mutableListOf<IpPurityWorkerService>())
    private val activeFastPurityWorkers = Collections.synchronizedList(mutableListOf<FastIpPurityWorkerService>())

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
        val snapshot = ArrayList(activeWorkers)
        snapshot.forEach { it.cancel() }
        activeWorkers.clear()
        val puritySnapshot = ArrayList(activePurityWorkers)
        puritySnapshot.forEach { it.cancel() }
        activePurityWorkers.clear()
        val fastPuritySnapshot = ArrayList(activeFastPurityWorkers)
        fastPuritySnapshot.forEach { it.cancel() }
        activeFastPurityWorkers.clear()
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
                        MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_CONFIG_FINISH, status)
                        activeWorkers.remove(worker)
                    }
                    activeWorkers.add(worker)
                    worker.start()
                }
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                val snapshot = ArrayList(activeWorkers)
                snapshot.forEach { it.cancel() }
                activeWorkers.clear()
            }

            MSG_MEASURE_IP_PURITY -> {
                val guidsList = if (message.serverGuids.isNotEmpty()) {
                    message.serverGuids
                } else if (message.subscriptionId.isNotEmpty()) {
                    MmkvManager.decodeServerList(message.subscriptionId)
                } else {
                    MmkvManager.decodeAllServerList()
                }

                if (guidsList.isNotEmpty()) {
                    lateinit var worker: IpPurityWorkerService
                    worker = IpPurityWorkerService(this, guidsList) { status ->
                        MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_IP_PURITY_FINISH, status)
                        activePurityWorkers.remove(worker)
                    }
                    activePurityWorkers.add(worker)
                    worker.start()
                }
            }

            MSG_MEASURE_IP_PURITY_CANCEL -> {
                val snapshot = ArrayList(activePurityWorkers)
                snapshot.forEach { it.cancel() }
                activePurityWorkers.clear()
            }

            MSG_MEASURE_FAST_IP_PURITY -> {
                val guidsList = if (message.serverGuids.isNotEmpty()) {
                    message.serverGuids
                } else if (message.subscriptionId.isNotEmpty()) {
                    MmkvManager.decodeServerList(message.subscriptionId)
                } else {
                    MmkvManager.decodeAllServerList()
                }

                if (guidsList.isNotEmpty()) {
                    lateinit var worker: FastIpPurityWorkerService
                    worker = FastIpPurityWorkerService(this, guidsList) { status ->
                        MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_FAST_IP_PURITY_FINISH, status)
                        activeFastPurityWorkers.remove(worker)
                    }
                    activeFastPurityWorkers.add(worker)
                    worker.start()
                }
            }

            MSG_MEASURE_FAST_IP_PURITY_CANCEL -> {
                val snapshot = ArrayList(activeFastPurityWorkers)
                snapshot.forEach { it.cancel() }
                activeFastPurityWorkers.clear()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
