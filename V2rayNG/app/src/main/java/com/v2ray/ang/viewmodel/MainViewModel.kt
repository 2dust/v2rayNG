package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.ServersCache
import com.v2ray.ang.dto.SubscriptionCache
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = MmkvManager.decodeServerList()
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    //var keywordFilter: String = MmkvManager.MmkvManager.decodeSettingsString(AppConfig.CACHE_KEYWORD_FILTER, "")?:""
    var keywordFilter = ""
    val serversCache = mutableListOf<ServersCache>()
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     */
    fun startListenBroadcast() {
        isRunning.value = false
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    /**
     * Called when the ViewModel is cleared.
     */
    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestManager.closeAllTcpSockets()
        Log.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    /**
     * Reloads the server list.
     */
    fun reloadServerList() {
        serverList = MmkvManager.decodeServerList()
        updateCache()
        updateListAction.value = -1
    }

    /**
     * Removes a server by its GUID.
     * @param guid The GUID of the server to remove.
     */
    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
        }
    }

//    /**
//     * Appends a custom configuration server.
//     * @param server The server configuration to append.
//     * @return True if the server was successfully appended, false otherwise.
//     */
//    fun appendCustomConfigServer(server: String): Boolean {
//        if (server.contains("inbounds")
//            && server.contains("outbounds")
//            && server.contains("routing")
//        ) {
//            try {
//                val config = CustomFmt.parse(server) ?: return false
//                config.subscriptionId = subscriptionId
//                val key = MmkvManager.encodeServerConfig("", config)
//                MmkvManager.encodeServerRaw(key, server)
//                serverList.add(0, key)
////                val profile = ProfileLiteItem(
////                    configType = config.configType,
////                    subscriptionId = config.subscriptionId,
////                    remarks = config.remarks,
////                    server = config.getProxyOutbound()?.getServerAddress(),
////                    serverPort = config.getProxyOutbound()?.getServerPort(),
////                )
//                serversCache.add(0, ServersCache(key, config))
//                return true
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//        return false
//    }

    /**
     * Swaps the positions of two servers.
     * @param fromPosition The initial position of the server.
     * @param toPosition The target position of the server.
     */
    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            Collections.swap(serverList, fromPosition, toPosition)
        } else {
            val fromPosition2 = serverList.indexOf(serversCache[fromPosition].guid)
            val toPosition2 = serverList.indexOf(serversCache[toPosition].guid)
            Collections.swap(serverList, fromPosition2, toPosition2)
        }
        Collections.swap(serversCache, fromPosition, toPosition)
        MmkvManager.encodeServerList(serverList)
    }

    /**
     * Updates the cache of servers.
     */
    @Synchronized
    fun updateCache() {
        serversCache.clear()
        for (guid in serverList) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue
//            var profile = MmkvManager.decodeProfileConfig(guid)
//            if (profile == null) {
//                val config = MmkvManager.decodeServerConfig(guid) ?: continue
//                profile = ProfileLiteItem(
//                    configType = config.configType,
//                    subscriptionId = config.subscriptionId,
//                    remarks = config.remarks,
//                    server = config.getProxyOutbound()?.getServerAddress(),
//                    serverPort = config.getProxyOutbound()?.getServerPort(),
//                )
//                MmkvManager.encodeServerConfig(guid, config)
//            }

            if (subscriptionId.isNotEmpty() && subscriptionId != profile.subscriptionId) {
                continue
            }

            if (keywordFilter.isEmpty() || profile.remarks.lowercase().contains(keywordFilter.lowercase())) {
                serversCache.add(ServersCache(guid, profile))
            }
        }
    }

    /**
     * Updates the configuration via subscription for all servers.
     * @return The number of updated configurations.
     */
    fun updateConfigViaSubAll(): Int {
        if (subscriptionId.isEmpty()) {
            return AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return 0
            return AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    /**
     * Exports all servers.
     * @return The number of exported servers.
     */
    fun exportAllServer(): Int {
        val serverListCopy =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList
            } else {
                serversCache.map { it.guid }.toList()
            }

        val ret = AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
        return ret
    }

    /**
     * Tests the TCP ping for all servers.
     */
    fun testAllTcping() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestManager.closeAllTcpSockets()
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())

        val serversCopy = serversCache.toList()
        for (item in serversCopy) {
            item.profile.let { outbound ->
                val serverAddress = outbound.server
                val serverPort = outbound.serverPort
                if (serverAddress != null && serverPort != null) {
                    tcpingTestScope.launch {
                        val testResult = SpeedtestManager.tcping(serverAddress, serverPort.toInt())
                        launch(Dispatchers.Main) {
                            MmkvManager.encodeServerTestDelayMillis(item.guid, testResult)
                            updateListAction.value = getPosition(item.guid)
                        }
                    }
                }
            }
        }
    }

    /**
     * Tests the real ping for all servers.
     */
    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG_CANCEL, "")
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1

        val serversCopy = serversCache.toList()
        viewModelScope.launch(Dispatchers.Default) {
            val guids = ArrayList<String>(serversCopy.map { it.guid })
            if (guids.isEmpty()) {
                return@launch
            }
            MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG, guids)
        }
    }

    /**
     * Tests the real ping for the current server.
     */
    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    /**
     * Changes the subscription ID.
     * @param id The new subscription ID.
     */
    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        reloadServerList()
    }

    /**
     * Gets the subscriptions.
     * @param context The context.
     * @return A pair of lists containing the subscription IDs and remarks.
     */
    fun getSubscriptions(context: Context): List<GroupMapItem> {
        val subscriptions = MmkvManager.decodeSubscriptions()
        if (subscriptionId.isNotEmpty()
            && !subscriptions.map { it.guid }.contains(subscriptionId)
        ) {
            subscriptionIdChanged("")
        }

        val groups = mutableListOf<GroupMapItem>()
        groups.add(
            GroupMapItem(
                id = "",
                remarks = context.getString(R.string.filter_config_all)
            )
        )
        subscriptions.forEach { sub ->
            groups.add(GroupMapItem(id = sub.guid, remarks = sub.subscription.remarks))
        }
        return groups
    }

    /**
     * Gets the position of a server by its GUID.
     * @param guid The GUID of the server.
     * @return The position of the server.
     */
    fun getPosition(guid: String): Int {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        return -1
    }

    /**
     * Removes duplicate servers.
     * @return The number of removed servers.
     */
    fun removeDuplicateServer(): Int {
        val serversCacheCopy = serversCache.toList().toMutableList()
        val deleteServer = mutableListOf<String>()
        serversCacheCopy.forEachIndexed { index, sc ->
            val profile = sc.profile
            serversCacheCopy.forEachIndexed { index2, sc2 ->
                if (index2 > index) {
                    val profile2 = sc2.profile
                    if (profile == profile2 && !deleteServer.contains(sc2.guid)) {
                        deleteServer.add(sc2.guid)
                    }
                }
            }
        }
        for (it in deleteServer) {
            MmkvManager.removeServer(it)
        }

        return deleteServer.count()
    }

    /**
     * Removes all servers.
     * @return The number of removed servers.
     */
    fun removeAllServer(): Int {
        val count =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                MmkvManager.removeAllServer()
            } else {
                val serversCopy = serversCache.toList()
                for (item in serversCopy) {
                    MmkvManager.removeServer(item.guid)
                }
                serversCache.toList().count()
            }
        return count
    }

    /**
     * Removes invalid servers.
     * @return The number of removed servers.
     */
    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += MmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = serversCache.toList()
            for (item in serversCopy) {
                count += MmkvManager.removeInvalidServer(item.guid)
            }
        }
        return count
    }

    /**
     * Sorts servers by their test results.
     */
    fun sortByTestResults() {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverList = MmkvManager.decodeServerList()
        serverList.forEach { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        serverDelays.forEach {
            serverList.remove(it.guid)
            serverList.add(it.guid)
        }

        MmkvManager.encodeServerList(serverList)
    }

    /**
     * Initializes assets.
     * @param assets The asset manager.
     */
    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    /**
     * Filters the configuration by a keyword.
     * @param keyword The keyword to filter by.
     */
    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) {
            return
        }
        keywordFilter = keyword
        MmkvManager.encodeSettings(AppConfig.CACHE_KEYWORD_FILTER, keywordFilter)
        reloadServerList()
    }

    fun onTestsFinished() {
        viewModelScope.launch(Dispatchers.Default) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)) {
                removeInvalidServer()
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)) {
                sortByTestResults()
            }

            withContext(Dispatchers.Main) {
                reloadServerList()
            }
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toastSuccess(R.string.toast_services_success)
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    getApplication<AngApplication>().toastError(R.string.toast_services_failure)
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content")
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val resultPair = intent.serializable<Pair<String, Long>>("content") ?: return
                    MmkvManager.encodeServerTestDelayMillis(resultPair.first, resultPair.second)
                    updateListAction.value = getPosition(resultPair.first)
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val content = intent.getStringExtra("content")
                    updateTestResultAction.value =
                        getApplication<AngApplication>().getString(R.string.connection_runing_task_left, content)
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    val content = intent.getStringExtra("content")
                    if (content == "0") {
                        onTestsFinished()
                    }
                }
            }
        }
    }
}
