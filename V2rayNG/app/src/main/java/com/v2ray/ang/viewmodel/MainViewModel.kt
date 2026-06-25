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
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.ui.GroupTabItem
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.regex.PatternSyntaxException

/**
 * LocateTarget: 一次性定位事件的完整信息
 * 包含目标 groupIndex 和 itemPosition，避免在 UI 层再做查找
 */
data class LocateTarget(
    val groupIndex: Int,
    val groupId: String,
    val itemPosition: Int,
    val timestamp: Long = System.nanoTime()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = mutableListOf<String>()
    var subscriptionId: String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
    var keywordFilter = ""

    // 全局 serversCache - 当前活跃 tab 的数据
    val serversCache = mutableListOf<ServersCache>()

    // ===== Compose StateFlow 驱动 =====

    // 每个 group 的服务器缓存
    private val _perGroupServers = MutableStateFlow<Map<String, List<ServersCache>>>(emptyMap())
    val perGroupServers: StateFlow<Map<String, List<ServersCache>>> = _perGroupServers.asStateFlow()

    // 选中的服务器 GUID
    private val _selectedGuid = MutableStateFlow<String?>(MmkvManager.getSelectServer())
    val selectedGuid: StateFlow<String?> = _selectedGuid.asStateFlow()

    // 运行状态 - 使用 StateFlow 替代 LiveData
    private val _isRunningFlow = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

    // 测试状态文本
    private val _testState = MutableStateFlow("")
    val testState: StateFlow<String> = _testState.asStateFlow()

    // ===== 定位事件：用 SharedFlow(replay=0) 实现一次性事件 =====
    private val _locateEvent = MutableSharedFlow<LocateTarget>(extraBufferCapacity = 1)
    val locateEvent: SharedFlow<LocateTarget> = _locateEvent.asSharedFlow()

    // 当前 groups 列表的快照（由 Activity 设置）
    private var currentGroups: List<GroupTabItem> = emptyList()

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun setCurrentGroups(groups: List<GroupTabItem>) {
        currentGroups = groups
    }

    /**
     * 触发定位到选中服务器
     */
    fun triggerLocateSelectedServer() {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) return

        val config = MmkvManager.decodeServerConfig(selectedGuid) ?: return
        val targetSubId = config.subscriptionId ?: return

        val groupIndex = currentGroups.indexOfFirst { it.id == targetSubId }
        if (groupIndex < 0) return

        val servers = _perGroupServers.value[targetSubId] ?: return
        val itemPosition = servers.indexOfFirst { it.guid == selectedGuid }
        if (itemPosition < 0) return

        _locateEvent.tryEmit(
            LocateTarget(
                groupIndex = groupIndex,
                groupId = targetSubId,
                itemPosition = itemPosition
            )
        )
    }

    fun updateSelectedGuid(guid: String) {
        MmkvManager.setSelectServer(guid)
        _selectedGuid.value = guid
    }

    fun startListenBroadcast() {
        _isRunningFlow.value = false
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(
            getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags()
        )
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun reloadServerList() {
        serverList = if (subscriptionId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(subscriptionId)
        }
        updateCache()
        // 移除 LiveData 更新，UI 重组由 perGroupServers 驱动
    }

    fun reloadAllGroups(groupIds: List<String>) {
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (_: PatternSyntaxException) {
            null
        }

        val newMap = mutableMapOf<String, List<ServersCache>>()
        for (gid in groupIds) {
            val list = if (gid.isEmpty()) MmkvManager.decodeAllServerList()
            else MmkvManager.decodeServerList(gid)

            val cache = mutableListOf<ServersCache>()
            for (guid in list) {
                val profile = MmkvManager.decodeServerConfig(guid) ?: continue
                if (kw.isEmpty()) {
                    cache.add(ServersCache(guid, profile))
                    continue
                }
                if (listOf(
                        profile.remarks,
                        profile.description.orEmpty(),
                        profile.server.orEmpty(),
                        profile.configType.name
                    ).any { it.matchesPattern(searchRegex, kw) }
                ) {
                    cache.add(ServersCache(guid, profile))
                }
            }
            newMap[gid] = cache
        }
        _perGroupServers.value = newMap

        // 同步全局 serversCache
        serversCache.clear()
        newMap[subscriptionId]?.let { serversCache.addAll(it) }
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) serversCache.removeAt(index)

        val current = _perGroupServers.value.toMutableMap()
        for ((key, list) in current) {
            val filtered = list.filter { it.guid != guid }
            if (filtered.size != list.size) current[key] = filtered
        }
        _perGroupServers.value = current
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) return
        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        MmkvManager.encodeServerList(serverList, subscriptionId)

        val current = _perGroupServers.value.toMutableMap()
        val groupList = current[subscriptionId]?.toMutableList()
        if (groupList != null && fromPosition in groupList.indices && toPosition in groupList.indices) {
            Collections.swap(groupList, fromPosition, toPosition)
            current[subscriptionId] = groupList
            _perGroupServers.value = current
        }
    }

    @Synchronized
    fun updateCache() {
        serversCache.clear()
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (_: PatternSyntaxException) {
            null
        }
        for (guid in serverList) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue
            if (kw.isEmpty()) {
                serversCache.add(ServersCache(guid, profile))
                continue
            }
            if (listOf(
                    profile.remarks,
                    profile.description.orEmpty(),
                    profile.server.orEmpty(),
                    profile.configType.name
                ).any { it.matchesPattern(searchRegex, kw) }
            ) {
                serversCache.add(ServersCache(guid, profile))
            }
        }
        val current = _perGroupServers.value.toMutableMap()
        current[subscriptionId] = serversCache.toList()
        _perGroupServers.value = current
    }

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        return if (subscriptionId.isEmpty()) {
            AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem =
                MmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    fun exportAllServer(): Int {
        val serverListCopy = if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            serverList
        } else {
            serversCache.map { it.guid }
        }
        return AngConfigManager.shareNonCustomConfigsToClipboard(getApplication(), serverListCopy)
    }

    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid })
        // 移除 LiveData 更新，UI 通过 perGroupServers 重组
        viewModelScope.launch(Dispatchers.Default) {
            if (serversCache.isEmpty()) return@launch
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) serversCache.map { it.guid } else emptyList()
                )
            )
        }
    }

    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        // 从已预加载的缓存中取数据，避免重新加载
        val cached = _perGroupServers.value[id]
        if (cached != null) {
            serversCache.clear()
            serversCache.addAll(cached)
            serverList = cached.map { it.guid }.toMutableList()
        } else {
            reloadServerList()
        }
    }

    fun getSubscriptions(context: Context): List<GroupMapItem> {
        val subscriptions = MmkvManager.decodeSubscriptions()
        if (subscriptionId.isNotEmpty() && subscriptions.none { it.guid == subscriptionId }) {
            subscriptionIdChanged("")
        }
        return buildList {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)) {
                add(GroupMapItem(id = "", remarks = context.getString(R.string.filter_config_all)))
            }
            subscriptions.forEach { sub ->
                add(GroupMapItem(id = sub.guid, remarks = sub.subscription.remarks))
            }
        }
    }

    fun getPosition(guid: String): Int = serversCache.indexOfFirst { it.guid == guid }

    fun removeDuplicateServer(): Int {
        val serversCacheCopy = serversCache.toList()
        val deleteServer = mutableListOf<String>()
        serversCacheCopy.forEachIndexed { index, sc ->
            if (sc.profile.configType.isComplexType()) return@forEachIndexed
            for (index2 in (index + 1) until serversCacheCopy.size) {
                val sc2 = serversCacheCopy[index2]
                if (sc2.profile.configType.isComplexType()) continue
                if (sc.profile == sc2.profile && sc2.guid !in deleteServer) {
                    deleteServer.add(sc2.guid)
                }
            }
        }
        deleteServer.forEach { MmkvManager.removeServer(it) }
        return deleteServer.size
    }

    fun removeAllServer(): Int {
        return if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            MmkvManager.removeAllServer()
        } else {
            serversCache.toList().also { copy ->
                copy.forEach { MmkvManager.removeServer(it.guid) }
            }.size
        }
    }

    fun removeInvalidServer(): Int {
        return if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            MmkvManager.removeInvalidServer("")
        } else {
            serversCache.toList().sumOf { MmkvManager.removeInvalidServer(it.guid) }
        }
    }

    fun sortByTestResults() {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach(::sortByTestResultsForSub)
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    private fun sortByTestResultsForSub(subId: String) {
        val serverListToSort = MmkvManager.decodeServerList(subId)
        val sorted = serverListToSort.sortedBy { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            if (delay <= 0L) 999999L else delay
        }.toMutableList()
        MmkvManager.encodeServerList(sorted, subId)
    }

    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication(), assets)
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
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
            withContext(Dispatchers.Main) { reloadServerList() }
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    _isRunningFlow.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    _isRunningFlow.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toastSuccess(R.string.toast_services_success)
                    _isRunningFlow.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    val errorMessage = intent.getStringExtra("content")
                    if (!errorMessage.isNullOrBlank()) getApplication<AngApplication>().toastError(
                        errorMessage
                    )
                    else getApplication<AngApplication>().toastError(R.string.toast_services_failure)
                    _isRunningFlow.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    _isRunningFlow.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    val content = intent.getStringExtra("content")
                    // 直接更新测试状态文本，LiveData 已移除
                    _testState.value = content ?: ""
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    // 原本通过 updateListAction 通知滚动，现在 UI 由 perGroupServers 驱动，
                    // 定位功能已由 locateEvent 实现，此处忽略或可扩展为事件
                    // 但为保持兼容，保留逻辑（仅更新状态）
                    val content = intent.getStringExtra("content")
                    // 如果需要滚动，可通过 _locateEvent 发送事件，但这里不额外添加
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val content = intent.getStringExtra("content")
                    val msg = getApplication<AngApplication>().getString(
                        R.string.connection_runing_task_left, content
                    )
                    _testState.value = msg
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    if (intent.getStringExtra("content") == "0") onTestsFinished()
                }
            }
        }
    }
}
