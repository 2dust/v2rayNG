package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ==================== 状态定义 ====================

    private var serverList = mutableListOf<String>()
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
        private set
    var keywordFilter = ""
        private set

    private val _serversCache = MutableStateFlow<List<ServersCache>>(emptyList())
    val serversCache: StateFlow<List<ServersCache>> = _serversCache.asStateFlow()

    private val _perGroupServers = MutableStateFlow<Map<String, List<ServersCache>>>(emptyMap())
    val perGroupServers: StateFlow<Map<String, List<ServersCache>>> = _perGroupServers.asStateFlow()

    private val _selectedGuid = MutableStateFlow<String?>(MmkvManager.getSelectServer())
    val selectedGuid: StateFlow<String?> = _selectedGuid.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _testState = MutableStateFlow("")
    val testState: StateFlow<String> = _testState.asStateFlow()

    private val _testResultText = MutableStateFlow<String?>(null)
    val testResultText: StateFlow<String?> = _testResultText.asStateFlow()

    private val _displayText = MutableStateFlow(application.getString(R.string.connection_not_connected))
    val displayText: StateFlow<String> = _displayText

    init {
        viewModelScope.launch {
            combine(_isRunning, _testResultText) { running, testText ->
                when {
                    testText != null -> testText
                    running -> getApplication<AngApplication>().getString(R.string.connection_connected)
                    else -> getApplication<AngApplication>().getString(R.string.connection_not_connected)
                }
            }.collect { text ->
                _displayText.value = text
            }
        }
    }

    private val _updateListEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val updateListEvent: SharedFlow<Int> = _updateListEvent.asSharedFlow()

    private val _locateEvent = MutableSharedFlow<LocateTarget>(extraBufferCapacity = 1)
    val locateEvent: SharedFlow<LocateTarget> = _locateEvent.asSharedFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    // ==================== 广播监听 ====================

    fun startListenBroadcast() {
        _isRunning.value = false
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    // ==================== 分组与列表加载 ====================

    fun reloadAllGroups(groupIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val allGroupServers = linkedMapOf<String, List<ServersCache>>()
            for (groupId in groupIds) {
                val list = if (groupId.isEmpty()) {
                    MmkvManager.decodeAllServerList()
                } else {
                    MmkvManager.decodeServerList(groupId)
                }
                val servers = list.mapNotNull { guid ->
                    val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                    val aff = MmkvManager.decodeServerAffiliationInfo(guid)
                    val delayMillis = aff?.testDelayMillis ?: 0L
                    val delayString = aff?.getTestDelayString() ?: ""
                    ServersCache(
                        guid = guid,
                        profile = profile,
                        testDelayMillis = delayMillis,
                        testDelayString = delayString
                    )
                }
                allGroupServers[groupId] = servers
                if (groupId == subscriptionId) {
                    serverList = list.toMutableList()
                }
            }
            val current = applyKeywordFilter(allGroupServers[subscriptionId] ?: emptyList())
            _serversCache.value = current
            _perGroupServers.value = allGroupServers.toMutableMap().apply {
                put(subscriptionId, current)
            }
            _updateListEvent.tryEmit(-1)
        }
    }

    fun reloadServerList() {
        serverList = if (subscriptionId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(subscriptionId)
        }
        updateCache()
        _updateListEvent.tryEmit(-1)
    }

    @Synchronized
    fun updateCache() {
        val newCache = applyKeywordFilter(serverList.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            val delayMillis = aff?.testDelayMillis ?: 0L
            val delayString = aff?.getTestDelayString() ?: ""
            ServersCache(
                guid = guid,
                profile = profile,
                testDelayMillis = delayMillis,
                testDelayString = delayString
            )
        })
        _serversCache.value = newCache

        val updated = _perGroupServers.value.toMutableMap()
        updated[subscriptionId] = _serversCache.value
        _perGroupServers.value = updated
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val kw = keywordFilter.trim()
        if (kw.isEmpty()) return servers
        val regex = try {
            Regex(kw, setOf(RegexOption.IGNORE_CASE))
        } catch (e: PatternSyntaxException) {
            null
        }
        return if (regex == null) servers else servers.filter {
            matchesFilter(it.profile, regex, kw)
        }
    }

    private fun matchesFilter(profile: ProfileItem, regex: Regex?, keyword: String): Boolean {
        if (regex == null) return true
        val remarks = profile.remarks
        val description = profile.description.orEmpty()
        val server = profile.server.orEmpty()
        val protocol = profile.configType.name
        return remarks.matchesPattern(regex, keyword)
                || description.matchesPattern(regex, keyword)
                || server.matchesPattern(regex, keyword)
                || protocol.matchesPattern(regex, keyword)
    }

    fun updateSelectedGuid(guid: String) {
        MmkvManager.setSelectServer(guid)
        _selectedGuid.value = guid
    }

    // ==================== 服务器操作 ====================

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        updateCache()
        _updateListEvent.tryEmit(-1)
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) return
        Collections.swap(serverList, fromPosition, toPosition)
        MmkvManager.encodeServerList(serverList, subscriptionId)
        updateCache()
        _updateListEvent.tryEmit(-1)
    }

    // ==================== 测试相关 ====================

    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )

        val currentCache = _serversCache.value
        MmkvManager.clearAllTestDelayResults(currentCache.map { it.guid })
        _isTesting.value = true
        _testState.value = getApplication<AngApplication>().getString(R.string.connection_test_testing)
        _testResultText.value = _testState.value
        updateCache()

        viewModelScope.launch(Dispatchers.Default) {
            if (currentCache.isEmpty()) {
                _isTesting.value = false
                _testState.value = ""
                _testResultText.value = null
                return@launch
            }
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) currentCache.map { it.guid } else emptyList()
                )
            )
        }
    }

    fun testCurrentServerRealPing() {
        val testingText = getApplication<AngApplication>().getString(R.string.connection_test_testing)
        _testState.value = testingText
        _testResultText.value = testingText
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
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
                reloadAllGroups(_perGroupServers.value.keys.toList())
                _isTesting.value = false
                _testState.value = ""
                _testResultText.value = null
            }
        }
    }

    // ==================== 订阅管理 ====================

    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        reloadServerList()
        _updateListEvent.tryEmit(-1)
    }

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

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        return if (subscriptionId.isEmpty()) {
            AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    // ==================== 导出/删除/排序 ====================

    fun exportAllServer(): Int {
        val serverListCopy =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList
            } else {
                _serversCache.value.map { it.guid }
            }
        return AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
    }

    fun removeDuplicateServer(): Int {
        val serversCacheCopy = _serversCache.value.toMutableList()
        val deleteServer = mutableListOf<String>()

        serversCacheCopy.forEachIndexed { index, sc ->
            val profile = sc.profile
            if (profile.configType.isComplexType()) return@forEachIndexed

            serversCacheCopy.forEachIndexed { index2, sc2 ->
                if (index2 > index) {
                    val profile2 = sc2.profile
                    if (profile2.configType.isComplexType()) return@forEachIndexed
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

    fun removeAllServer(): Int {
        val count = if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            MmkvManager.removeAllServer()
        } else {
            val serversCopy = _serversCache.value
            for (item in serversCopy) {
                MmkvManager.removeServer(item.guid)
            }
            serversCopy.count()
        }
        return count
    }

    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += MmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = _serversCache.value
            for (item in serversCopy) {
                count += MmkvManager.removeInvalidServer(item.guid)
            }
        }
        return count
    }

    fun sortByTestResults() {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach { guid ->
                sortByTestResultsForSub(guid)
            }
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    private fun sortByTestResultsForSub(subId: String) {
        data class ServerDelay(val guid: String, val testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverListToSort = MmkvManager.decodeServerList(subId)

        serverListToSort.forEach { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        val sortedServerList = serverDelays.map { it.guid }.toMutableList()
        MmkvManager.encodeServerList(sortedServerList, subId)
    }

    // ==================== 定位 ====================

    fun triggerLocateSelectedServer() {
        val selectedGuid = MmkvManager.getSelectServer() ?: return
        val groupId = MmkvManager.decodeServerConfig(selectedGuid)?.subscriptionId ?: return
        val groupIndex = _perGroupServers.value.keys.indexOf(groupId).takeIf { it >= 0 } ?: return
        val servers = _perGroupServers.value[groupId] ?: return
        val itemPosition = servers.indexOfFirst { it.guid == selectedGuid }.takeIf { it >= 0 } ?: return
        _locateEvent.tryEmit(LocateTarget(groupId, groupIndex, itemPosition))
    }

    // ==================== 工具方法 ====================

    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        reloadServerList()
    }

    fun getPosition(guid: String): Int {
        return _serversCache.value.indexOfFirst { it.guid == guid }
    }

    // ==================== 广播接收器 ====================

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    _isRunning.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    _isRunning.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toastSuccess(R.string.toast_services_success)
                    _isRunning.value = true
                    _testResultText.value = null
                    _testState.value = ""
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    val errorMessage = intent.getStringExtra("content")
                    if (!errorMessage.isNullOrBlank()) {
                        getApplication<AngApplication>().toastError(errorMessage)
                    } else {
                        getApplication<AngApplication>().toastError(R.string.toast_services_failure)
                    }
                    _isRunning.value = false
                    _testResultText.value = null
                    _testState.value = ""
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    _isRunning.value = false
                    _testResultText.value = null
                    _testState.value = ""
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    val result = intent.getStringExtra("content").orEmpty()
                    _testState.value = result
                    _testResultText.value = result
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val content = intent.getStringExtra("content")
                    updateCache()
                    _updateListEvent.tryEmit(getPosition(content ?: ""))
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val content = intent.getStringExtra("content")
                    val progressText =
                        getApplication<AngApplication>().getString(R.string.connection_runing_task_left, content)
                    _testState.value = progressText
                    _testResultText.value = progressText
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
