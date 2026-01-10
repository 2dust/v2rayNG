package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.PluginServiceManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import go.Seq
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicInteger

class V2RayTestService : Service() {
    private val realTestJob = SupervisorJob()
    private val realDispatcher = Dispatchers.IO.limitedParallelism(
        Runtime.getRuntime().availableProcessors() * 3
    )
    private val realTestScope = CoroutineScope(
        realTestJob + realDispatcher + CoroutineName("RealTest")
    )

    // simple counter for currently running tasks
    private val realTestRunningCount = AtomicInteger(0)
    private val realTestCount = AtomicInteger(0)

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initCoreEnv(Utils.userAssetPath(this), Utils.getDeviceIdForXUDPBaseKey())
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val guidsList = intent.serializable<ArrayList<String>>("content")
                if (guidsList == null || guidsList.isEmpty()) {
                    return super.onStartCommand(intent, flags, startId)
                }
                for (guid in guidsList) {
                    realTestCount.incrementAndGet()
                    realTestScope.launch {
                        realTestRunningCount.incrementAndGet()
                        try {
                            val result = startRealPing(guid)
                            MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                         } finally {
                            val count = realTestCount.decrementAndGet()
                            val left = realTestRunningCount.decrementAndGet()
                            MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_CONFIG_FINISH, "$left / $count")
                        }
                    }
                }
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestJob.cancelChildren()
            }
        }
        return super.onStartCommand(intent, flags, startId)
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
        realTestJob.cancel()
    }

    /**
     * Starts the real ping test.
     * @param guid The GUID of the configuration.
     * @return The ping result.
     */
    private fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.HYSTERIA2) {
            val delay = PluginServiceManager.realPingHy2(this, config)
            return delay
        } else {
            val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(this, guid)
            if (!configResult.status) {
                return retFailure
            }
            return SpeedtestManager.realPing(configResult.content)
        }
    }
}
