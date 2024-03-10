package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.*
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogConfigFilterBinding
import com.v2ray.ang.dto.*
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.IpScannerActivity
import com.v2ray.ang.ui.IpScannerRecyclerAdapter
import com.v2ray.ang.util.*
import com.v2ray.ang.util.MmkvManager.KEY_ANG_CONFIGS
import kotlinx.coroutines.*
import java.util.*

class IpScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val ipTestScope by lazy { CoroutineScope(Dispatchers.IO) }
    val stopScan by lazy { MutableLiveData<Boolean>() }
    var cleanIps: MutableList<Pair<String, Long>> = mutableListOf()
    val insertListAction by lazy { MutableLiveData<Int>() }
    val removeListAction by lazy { MutableLiveData<Int>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    var maxIps: Int = 0
    var maxLatency: Long = 0

    fun startListenBroadcast() {
        stopScan.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<AngApplication>().registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Context.RECEIVER_EXPORTED
            )
        } else {
            getApplication<AngApplication>().registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
            )
        }
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
//        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        ipTestScope.coroutineContext[Job]?.cancelChildren()
        Log.i(ANG_PACKAGE, "IP Scanner ViewModel is cleared")
        super.onCleared()
    }

    fun getPosition(ip: String): Int {
        cleanIps.forEachIndexed { index, it ->
            if (it.first == ip)
                return index
        }
        return -1
    }

    fun findCleanIps(cidrList: Array<String>, config: V2rayConfig, ips: Int, latency: Long) {
        updateListAction.value = -1
        maxIps = ips
        maxLatency = latency
        MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_IP_CANCEL, "")
        getApplication<AngApplication>().toast(R.string.ip_scanner_preparing)
        val cdnIPList = IpUtil.getRandomIpList(cidrList)
        getApplication<AngApplication>().toast(R.string.ip_scanner_testing)

        ipTestScope.launch(Dispatchers.Default) {
            for (ip in cdnIPList) {
                if (stopScan.value == true) {
                    break
                }
                config.getProxyOutbound()?.settings?.vnext?.get(0)?.address = ip
                val content = config.toPrettyPrinting()
                MessageUtil.sendMsg2TestService(
                    getApplication(),
                    AppConfig.MSG_MEASURE_IP,
                    Pair(ip, content)
                )
            }
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_MEASURE_IP_TESTING -> {
                    val result = intent.getSerializableExtra("content") as Pair<String, Long>
                    cleanIps += result
                    insertListAction.value = cleanIps.size - 1
                }

                AppConfig.MSG_MEASURE_IP_SUCCESS -> {
                    val result = intent.getSerializableExtra("content") as Pair<String, Long>
                    val index = getPosition(result.first)

                    if (index >= 0) {
                        if (result.second in 1..maxLatency) {
                            cleanIps[index] = result
                            updateListAction.value = index
                        } else {
                            cleanIps.removeAt(index)
                            removeListAction.value = index
                        }
                    }
                }

                AppConfig.MSG_MEASURE_IP_CANCELED -> {
                    cleanIps = cleanIps.filter { it.second > 0L }.sortedBy { it.second }.take(maxIps).toMutableList()
                    updateListAction.value = -1
                }
            }
        }
    }
}
