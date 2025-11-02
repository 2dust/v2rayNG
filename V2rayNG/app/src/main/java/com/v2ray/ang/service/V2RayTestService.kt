package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.util.concurrent.Executors

class V2RayTestService : Service() {
    private val realTestScope by lazy { CoroutineScope(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()) }

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
                val guid = intent.serializable<String>("content") ?: ""
                realTestScope.launch {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                }
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestScope.coroutineContext[Job]?.cancelChildren()
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
