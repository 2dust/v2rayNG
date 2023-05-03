package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.SpeedtestUtil
import com.v2ray.ang.util.Utils
import go.Seq
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import java.util.concurrent.Executors

class V2RayTestService : Service() {
    private val realTestScope by lazy { CoroutineScope(Executors.newFixedThreadPool(10).asCoroutineDispatcher()) }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initV2Env(Utils.userAssetPath(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val contentPair = intent.getSerializableExtra("content") as Pair<String, String>
                realTestScope.launch {
                    for (i in 0 until 3) {
                        val result = SpeedtestUtil.realPing(contentPair.second)
                        if (result>0||i==2) {
                            MessageUtil.sendMsg2UI(
                                this@V2RayTestService,
                                MSG_MEASURE_CONFIG_SUCCESS,
                                Pair(contentPair.first, result)
                            )
                            break
                        }
                        if (i<2){
                            Thread.sleep(500)
                        }
                    }
                }
            }
            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestScope.coroutineContext[Job]?.cancelChildren()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
