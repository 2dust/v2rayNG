package com.v2ray.ang.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.V2rayConfigUtil
import kotlinx.coroutines.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val updateListAction by lazy { MutableLiveData<Int>() }

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    override fun onCleared() {
        Log.i(AppConfig.ANG_PACKAGE, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun testAllTcping() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        Utils.closeAllTcpSockets()
        for (k in 0 until AngConfigManager.configs.vmess.count()) {
            AngConfigManager.configs.vmess[k].testResult = ""
            updateListAction.value = -1 // update all
        }
        for (k in 0 until AngConfigManager.configs.vmess.count()) {
            var serverAddress = AngConfigManager.configs.vmess[k].address
            var serverPort = AngConfigManager.configs.vmess[k].port
            if (AngConfigManager.configs.vmess[k].configType == EConfigType.CUSTOM.value) {
                val serverOutbound = V2rayConfigUtil.getCustomConfigServerOutbound(getApplication(),
                        AngConfigManager.configs.vmess[k].guid) ?: continue
                serverAddress = serverOutbound.getServerAddress() ?: continue
                serverPort = serverOutbound.getServerPort() ?: continue
            }
            tcpingTestScope.launch {
                AngConfigManager.configs.vmess.getOrNull(k)?.let {  // check null in case array is modified during testing
                    it.testResult = Utils.tcping(serverAddress, serverPort)
                    launch(Dispatchers.Main) {
                        updateListAction.value = k
                    }
                }
            }
        }
    }
}
