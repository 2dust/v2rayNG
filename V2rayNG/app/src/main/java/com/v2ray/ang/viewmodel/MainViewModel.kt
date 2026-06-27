package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = mutableListOf<String>() // MmkvManager.decodeServerList()
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
    var keywordFilter = ""
    val serversCache = mutableListOf<ServersCache>()
    private val _isRunningFlow = MutableStateFlow(false)
    val isRunningFlow = _isRunningFlow.asStateFlow()

    @Deprecated("Use isRunningFlow in Compose")
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }

    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    fun setLoading(loading: Boolean) {
        _isLoadingFlow.value = loading
    }

    private val _serversCacheFlow = MutableStateFlow<List<ServersCache>>(emptyList())
    val serversCacheFlow = _serversCacheFlow.asStateFlow()

    private val _selectedGuidFlow = MutableStateFlow(MmkvManager.getSelectServer() ?: "")
    val selectedGuidFlow = _selectedGuidFlow.asStateFlow()

    private val _testResultsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val testResultsFlow = _testResultsFlow.asStateFlow()

    fun updateSelectedGuid() {
        _selectedGuidFlow.value = MmkvManager.getSelectServer() ?: ""
    }

    fun updateTestResults() {
        val results = mutableMapOf<String, String>()
        val currentCache = synchronized(this) { serversCache.toList() }
        currentCache.forEach {
            val aff = MmkvManager.decodeServerAffiliationInfo(it.guid)
            results[it.guid] = aff?.getTestDelayString().orEmpty()
        }
        _testResultsFlow.value = results
    }

    private fun updateTestResult(guid: String) {
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        val currentResults = _testResultsFlow.value.toMutableMap()
        currentResults[guid] = aff?.getTestDelayString().orEmpty()
        _testResultsFlow.value = currentResults
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     */
    fun startListenBroadcast() {
        isRunning.postValue(false)
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    /**
     * Called when the ViewModel is cleared.
     */
    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(mMsgReceiver)
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    /**
     * Reloads the server list based on current subscription filter.
     */
    fun reloadServerList() {
        viewModelScope.launch(Dispatchers.IO) {
            serverList = if (subscriptionId.isEmpty()) {
                MmkvManager.decodeAllServerList()
            } else {
                MmkvManager.decodeServerList(subscriptionId)
            }

            updateCache()
            withContext(Dispatchers.Main) {
                updateListAction.value = -1
            }
        }
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
            _serversCacheFlow.value = serversCache.toList()
        }
    }

    /**
     * Swaps the positions of two servers.
     * @param fromPosition The initial position of the server.
     * @param toPosition The target position of the server.
     */
    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            return
        }

        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        _serversCacheFlow.value = serversCache.toList()

        MmkvManager.encodeServerList(serverList, subscriptionId)
    }

    /**
     * Updates the cache of servers.
     */
    @Synchronized
    fun updateCache() {
        serversCache.clear()
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (e: PatternSyntaxException) {
            null // Fallback to literal search if regex is invalid
        }
        for (guid in serverList) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue
            if (kw.isEmpty()) {
                serversCache.add(ServersCache(guid, profile))
                continue
            }

            val remarks = profile.remarks
            val description = profile.description.orEmpty()
            val server = profile.server.orEmpty()
            val protocol = profile.configType.name
            if (remarks.matchesPattern(searchRegex, kw)
                || description.matchesPattern(searchRegex, kw)
                || server.matchesPattern(searchRegex, kw)
                || protocol.matchesPattern(searchRegex, kw)
            ) {
                serversCache.add(ServersCache(guid, profile))
            }
        }
        _serversCacheFlow.value = serversCache.toList()
        updateSelectedGuid()
        updateTestResults()
    }

    /**
     * Updates the configuration via subscription for all servers.
     * @return Detailed result of the subscription update operation.
     */
    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        if (subscriptionId.isEmpty()) {
            return AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            return AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    /**
     * Exports all servers.
     * @return The number of exported servers.
     */
    fun exportAllServer(): Int {
        val serverListCopy = synchronized(this) {
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList.toList()
            } else {
                serversCache.map { it.guid }.toList()
            }
        }

        val ret = AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
        return ret
    }

    /**
     * Tests the real ping for all servers.
     */
    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val guids = synchronized(this) { serversCache.map { it.guid }.toList() }
        MmkvManager.clearAllTestDelayResults(guids)
        updateListAction.value = -1
        updateTestResults()

        viewModelScope.launch(Dispatchers.Default) {
            if (guids.isEmpty()) {
                return@launch
            }
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) guids else emptyList()
                )
            )
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
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)) {
            groups.add(
                GroupMapItem(
                    id = "",
                    remarks = context.getString(R.string.filter_config_all)
                )
            )
        }
        subscriptions.forEach { sub ->
            groups.add(
                GroupMapItem(
                    id = sub.guid,
                    remarks = sub.subscription.remarks
                )
            )
        }
        return groups
    }

    /**
     * Gets the position of a server by its GUID.
     * @param guid The GUID of the server.
     * @return The position of the server.
     */
    fun getPosition(guid: String): Int = synchronized(this) {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        -1
    }

    /**
     * Removes duplicate servers.
     * Excludes servers with complex types (Custom, PolicyGroup, or ProxyChain) from duplicate comparison.
     * @return The number of removed servers.
     */
    fun removeDuplicateServer(): Int {
        val serversCacheCopy = synchronized(this) { serversCache.toList() }
        val deleteServer = mutableListOf<String>()

        serversCacheCopy.forEachIndexed { index, sc ->
            val profile = sc.profile
            // Skip if this profile has a complex config type
            if (profile.configType.isComplexType()) {
                return@forEachIndexed
            }

            serversCacheCopy.forEachIndexed { index2, sc2 ->
                if (index2 > index) {
                    val profile2 = sc2.profile
                    // Skip if the second profile has a complex config type
                    if (profile2.configType.isComplexType()) {
                        return@forEachIndexed
                    }

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
                val serversCopy = synchronized(this) { serversCache.toList() }
                for (item in serversCopy) {
                    MmkvManager.removeServer(item.guid)
                }
                serversCopy.count()
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
            val serversCopy = synchronized(this) { serversCache.toList() }
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
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach { guid ->
                sortByTestResultsForSub(guid)
            }
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    /**
     * Sorts servers by their test results for a specific subscription.
     * @param subId The subscription ID to sort servers for.
     */
    private fun sortByTestResultsForSub(subId: String) {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverListToSort = MmkvManager.decodeServerList(subId)

        serverListToSort.forEach { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        val sortedServerList = serverDelays.map { it.guid }.toMutableList()

        // Save the sorted list for this subscription
        MmkvManager.encodeServerList(sortedServerList, subId)
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
        reloadServerList()
    }

    fun findSubscriptionIdBySelect(): String? {
        // Get the selected server GUID
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            return null
        }

        val config = MmkvManager.decodeServerConfig(selectedGuid)
        return config?.subscriptionId
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
            val key = intent?.getIntExtra("key", 0) ?: return
            val content = intent.getStringExtra("content")
            viewModelScope.launch(Dispatchers.Main) {
                when (key) {
                    AppConfig.MSG_STATE_RUNNING -> {
                        isRunning.value = true
                        _isRunningFlow.value = true
                    }

                    AppConfig.MSG_STATE_NOT_RUNNING -> {
                        isRunning.value = false
                        _isRunningFlow.value = false
                    }

                    AppConfig.MSG_STATE_START_SUCCESS -> {
                        getApplication<Application>().toastSuccess(R.string.toast_services_success)
                        isRunning.value = true
                        _isRunningFlow.value = true
                    }

                    AppConfig.MSG_STATE_START_FAILURE -> {
                        if (!content.isNullOrBlank()) {
                            getApplication<Application>().toastError(content)
                        } else {
                            getApplication<Application>().toastError(R.string.toast_services_failure)
                        }
                        isRunning.value = false
                        _isRunningFlow.value = false
                    }

                    AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        isRunning.value = false
                        _isRunningFlow.value = false
                    }

                    AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                        updateTestResultAction.value = content
                        // For current server real ping, we might need to update the specific item if it's in the list
                        MmkvManager.getSelectServer()?.let { updateTestResult(it) }
                    }

                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                        LogUtil.d(AppConfig.TAG, "Ping success: $content")
                        updateListAction.value = getPosition(content ?: "")
                        updateTestResult(content ?: "")
                    }

                    AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                        updateTestResultAction.value =
                            getApplication<Application>().getString(R.string.connection_runing_task_left, content)
                    }

                    AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                        if (content == "0") {
                            onTestsFinished()
                        }
                    }
                }
            }
        }
    }
}
