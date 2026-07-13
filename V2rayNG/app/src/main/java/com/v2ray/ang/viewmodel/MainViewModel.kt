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
import com.v2ray.ang.dto.entities.SubscriptionItem
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ===== State definitions =====

    enum class PageChangeSource { INIT, LOCATE, USER_SWIPE }

    private val _pageChangeSource = MutableStateFlow(PageChangeSource.INIT)
    val pageChangeSource: StateFlow<PageChangeSource> = _pageChangeSource.asStateFlow()

    private var serverList = mutableListOf<String>()
    var subscriptionId: String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
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

    val displayText: StateFlow<String> = combine(
        _isRunning,
        _testResultText
    ) { running, testText ->
        when {
            testText != null -> testText
            running -> getApplication<AngApplication>().getString(R.string.connection_connected)
            else -> getApplication<AngApplication>().getString(R.string.connection_not_connected)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = getApplication<AngApplication>().getString(R.string.connection_not_connected)
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _groups = MutableStateFlow<List<GroupMapItem>>(emptyList())
    val groups: StateFlow<List<GroupMapItem>> = _groups.asStateFlow()

    private val _updateListEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val updateListEvent: SharedFlow<Int> = _updateListEvent.asSharedFlow()

    private val _locateEvent = MutableSharedFlow<LocateTarget>(extraBufferCapacity = 1)
    val locateEvent: SharedFlow<LocateTarget> = _locateEvent.asSharedFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    // ===== UI Settings =====

    private val _confirmRemove = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    )
    val confirmRemove: StateFlow<Boolean> = _confirmRemove.asStateFlow()

    private val _doubleColumnDisplay = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    )
    val doubleColumnDisplay: StateFlow<Boolean> = _doubleColumnDisplay.asStateFlow()

    fun refreshUiSettings() {
        _confirmRemove.value = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
        _doubleColumnDisplay.value = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    }

    // ===== Thread-safe cache with Mutex =====

    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val loadedGroups = mutableSetOf<String>()

    private var setupGroupTabJob: Job? = null
    private var reloadJob: Job? = null

    // ===== Core loading helpers =====

    private fun getServerGuidList(groupId: String): List<String> =
        if (groupId.isEmpty()) MmkvManager.decodeAllServerList()
        else MmkvManager.decodeServerList(groupId)

    private fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile,
                testDelayMillis = aff?.testDelayMillis ?: 0L,
                testDelayString = aff?.getTestDelayString() ?: ""
            )
        }

    private suspend fun getOrLoadGroupData(groupId: String, forceRefresh: Boolean = false): List<ServersCache> {
        return cacheMutex.withLock {
            if (!forceRefresh && loadedGroups.contains(groupId)) {
                return@withLock groupDataCache[groupId] ?: emptyList()
            }
            val guids = getServerGuidList(groupId)
            val servers = buildServersCache(guids)
            groupDataCache[groupId] = servers
            loadedGroups.add(groupId)
            if (groupId == subscriptionId) {
                serverList = guids.toMutableList()
            }
            servers
        }
    }

    private fun updateGroupUI(groupId: String, servers: List<ServersCache>) {
        val filtered = applyKeywordFilter(servers)
        if (groupId == subscriptionId || subscriptionId.isEmpty()) {
            _serversCache.value = filtered
        }
        val newMap = _perGroupServers.value.toMutableMap()
        newMap[groupId] = filtered
        _perGroupServers.value = newMap
        _updateListEvent.tryEmit(-1)
    }

    // ===== Group and list loading =====

    fun setupGroupTab(context: Context, forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedGroups.isNotEmpty() && _groups.value.isNotEmpty()) {
            return
        }
        if (forceRefresh) {
            refreshUiSettings()
        }

        setupGroupTabJob?.cancel()
        setupGroupTabJob = viewModelScope.launch(Dispatchers.IO) {
            if (forceRefresh) {
                cacheMutex.withLock {
                    groupDataCache.clear()
                    loadedGroups.clear()
                }
            }

            val subs = getSubscriptionsInternal(context)
            val newGroups = subs.map { GroupMapItem(it.guid, it.subscription.remarks) }
            _groups.value = newGroups

            val currentGroupId = subscriptionId.ifEmpty { newGroups.firstOrNull()?.id ?: "" }
            val currentServers = getOrLoadGroupData(currentGroupId, forceRefresh)
            updateGroupUI(currentGroupId, currentServers)

            ensureActive()
            preloadOtherGroupsSuspend(newGroups.map { it.id }, currentGroupId)
            _pageChangeSource.value = PageChangeSource.INIT
        }
    }

    private suspend fun preloadOtherGroupsSuspend(allGroupIds: List<String>, currentGroupId: String) {
        val toLoad = cacheMutex.withLock {
            allGroupIds.filter { it != currentGroupId && !loadedGroups.contains(it) }
        }
        for ((index, groupId) in toLoad.withIndex()) {
            if (index > 0) delay(50)
            currentCoroutineContext().ensureActive()
            val servers = getOrLoadGroupData(groupId, forceRefresh = false)
            val filtered = applyKeywordFilter(servers)
            val newMap = _perGroupServers.value.toMutableMap()
            newMap[groupId] = filtered
            _perGroupServers.value = newMap
        }
    }

    fun reloadServerList() {
        viewModelScope.launch(Dispatchers.IO) {
            val servers = getOrLoadGroupData(subscriptionId, forceRefresh = false)
            updateGroupUI(subscriptionId, servers)
        }
    }

    fun updateCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val servers = getOrLoadGroupData(subscriptionId, forceRefresh = false)
            updateGroupUI(subscriptionId, servers)
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            val allGroupServers = linkedMapOf<String, List<ServersCache>>()
            for (groupId in groupIds) {
                ensureActive()
                val servers = getOrLoadGroupData(groupId, forceRefresh = false)
                allGroupServers[groupId] = servers
                if (groupId == subscriptionId) {
                    serverList = cacheMutex.withLock {
                        groupDataCache[groupId]?.map { it.guid }?.toMutableList() ?: mutableListOf()
                    }
                }
            }
            val finalMap = allGroupServers.mapValues { (_, v) -> applyKeywordFilter(v) }
            _serversCache.value = finalMap[subscriptionId] ?: emptyList()
            _perGroupServers.value = HashMap(finalMap)
            _updateListEvent.tryEmit(-1)
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val kw = keywordFilter.trim()
        if (kw.isEmpty()) return servers
        val regex = try {
            Regex(kw, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { profile ->
            val p = profile.profile
            p.remarks.matchesPattern(regex, kw) ||
                    p.description.orEmpty().matchesPattern(regex, kw) ||
                    p.server.orEmpty().matchesPattern(regex, kw) ||
                    p.configType.name.matchesPattern(regex, kw)
        }
    }

    // ===== Server selection and loading state =====

    fun updateSelectedGuid(guid: String) {
        MmkvManager.setSelectServer(guid)
        _selectedGuid.value = guid
    }

    fun setLoading(loading: Boolean) { _isLoading.value = loading }

    fun cancelAllPing() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        _isTesting.value = false
        _testState.value = ""
        _testResultText.value = null
    }

    // ===== Server CRUD operations =====

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        viewModelScope.launch(Dispatchers.IO) {
            cacheMutex.withLock {
                groupDataCache.remove(subscriptionId)
                loadedGroups.remove(subscriptionId)
            }
        }
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) return
        val currentCache = _serversCache.value.toMutableList()
        if (fromPosition !in currentCache.indices || toPosition !in currentCache.indices) return
        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(currentCache, fromPosition, toPosition)
        MmkvManager.encodeServerList(serverList, subscriptionId)
        viewModelScope.launch(Dispatchers.IO) {
            cacheMutex.withLock {
                groupDataCache[subscriptionId] = currentCache
            }
        }
        _serversCache.value = currentCache
        val updated = HashMap(_perGroupServers.value)
        updated[subscriptionId] = currentCache
        _perGroupServers.value = updated
    }

    // ===== Testing / Ping =====

    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val currentCache = _serversCache.value
        MmkvManager.clearAllTestDelayResults(currentCache.map { it.guid })
        _isTesting.value = true
        val testingText = getApplication<AngApplication>().getString(R.string.connection_test_testing)
        _testState.value = testingText
        _testResultText.value = testingText

        viewModelScope.launch(Dispatchers.IO) {
            cacheMutex.withLock {
                groupDataCache.remove(subscriptionId)
                loadedGroups.remove(subscriptionId)
            }
        }

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
                cacheMutex.withLock {
                    groupDataCache.remove(subscriptionId)
                    loadedGroups.remove(subscriptionId)
                }
                reloadAllGroups(_groups.value.map { it.id })
                _isTesting.value = false
                _testState.value = ""
                _testResultText.value = null
            }
        }
    }

    // ===== Subscription management =====

    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        if (_pageChangeSource.value == PageChangeSource.INIT ||
            _pageChangeSource.value == PageChangeSource.LOCATE) {
            _pageChangeSource.value = PageChangeSource.USER_SWIPE
        }

        viewModelScope.launch(Dispatchers.IO) {
            val servers = getOrLoadGroupData(id, forceRefresh = false)
            updateGroupUI(id, servers)
        }
    }

    private fun getSubscriptionsInternal(context: Context): List<SubscriptionCache> {
        val subscriptions = MmkvManager.decodeSubscriptions()
        if (subscriptionId.isNotEmpty() && !subscriptions.any { it.guid == subscriptionId }) {
            subscriptionIdChanged("")
        }
        val result = mutableListOf<SubscriptionCache>()
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)) {
            result.add(
                SubscriptionCache(
                    guid = "",
                    subscription = SubscriptionItem().apply {
                        remarks = context.getString(R.string.filter_config_all)
                    }
                )
            )
        }
        result.addAll(subscriptions)
        return result
    }

    fun getSubscriptions(context: Context): List<SubscriptionCache> =
        getSubscriptionsInternal(context)

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        return if (subscriptionId.isEmpty()) {
            AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId)
                ?: return SubscriptionUpdateResult()
            AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    // ===== Export / Delete / Sort =====

    fun exportAllServer(): Int {
        val list = if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            serverList
        } else {
            _serversCache.value.map { it.guid }
        }
        return AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            list
        )
    }

    fun removeDuplicateServer(): Int {
        val servers = _serversCache.value
        val toDelete = mutableListOf<String>()
        for (i in servers.indices) {
            val p1 = servers[i].profile
            if (p1.configType.isComplexType()) continue
            for (j in i + 1 until servers.size) {
                val p2 = servers[j].profile
                if (p2.configType.isComplexType()) continue
                if (p1 == p2 && !toDelete.contains(servers[j].guid)) {
                    toDelete.add(servers[j].guid)
                }
            }
        }
        toDelete.forEach { MmkvManager.removeServer(it) }
        return toDelete.size
    }

    fun removeAllServer(): Int {
        val count = if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            MmkvManager.removeAllServer()
        } else {
            val guids = _serversCache.value.map { it.guid }
            guids.forEach { MmkvManager.removeServer(it) }
            guids.size
        }
        return count
    }

    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += MmkvManager.removeInvalidServer("")
        } else {
            _serversCache.value.forEach {
                count += MmkvManager.removeInvalidServer(it.guid)
            }
        }
        return count
    }

    fun sortByTestResults() {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach { sortByTestResultsForSub(it) }
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    private fun sortByTestResultsForSub(subId: String) {
        val list = MmkvManager.decodeServerList(subId)
        val sorted = list.map { guid ->
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            guid to if (delay <= 0L) 999999L else delay
        }.sortedBy { it.second }.map { it.first }.toMutableList()
        MmkvManager.encodeServerList(sorted, subId)
        viewModelScope.launch(Dispatchers.IO) {
            cacheMutex.withLock {
                groupDataCache.remove(subId)
                loadedGroups.remove(subId)
            }
        }
    }

    // ===== Locate selected server =====

    fun triggerLocateSelectedServer() {
        val selected = MmkvManager.getSelectServer() ?: return
        val profile = MmkvManager.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        val currentGroups = _groups.value
        val groupIndex = currentGroups.indexOfFirst { it.id == groupId }
            .takeIf { it >= 0 } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val servers = getOrLoadGroupData(groupId, forceRefresh = false)
            val itemPosition = servers.indexOfFirst { it.guid == selected }
                .takeIf { it >= 0 } ?: return@launch

            _pageChangeSource.value = PageChangeSource.LOCATE
            _locateEvent.tryEmit(LocateTarget(groupId, groupIndex, itemPosition))
        }
    }

    // ===== Utility =====

    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(300)
            reloadServerList()
        }
    }

    fun getPosition(guid: String): Int =
        _serversCache.value.indexOfFirst { it.guid == guid }

    // ===== Broadcast receiver =====

    fun startListenBroadcast() {
        _isRunning.value = false
        val filter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, filter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        LogUtil.i(AppConfig.TAG, "Main ViewModel cleared")
        super.onCleared()
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> _isRunning.value = true
                AppConfig.MSG_STATE_NOT_RUNNING -> _isRunning.value = false
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toastSuccess(R.string.toast_services_success)
                    _isRunning.value = true
                    _testResultText.value = null
                    _testState.value = ""
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    val error = intent.getStringExtra("content")
                    if (!error.isNullOrBlank()) {
                        getApplication<AngApplication>().toastError(error)
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
                    viewModelScope.launch(Dispatchers.IO) {
                        cacheMutex.withLock {
                            groupDataCache.remove(subscriptionId)
                            loadedGroups.remove(subscriptionId)
                        }
                        val servers = getOrLoadGroupData(subscriptionId, forceRefresh = true)
                        updateGroupUI(subscriptionId, servers)
                    }
                }
                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val content = intent.getStringExtra("content")
                    val progress = getApplication<AngApplication>().getString(
                        R.string.connection_runing_task_left, content
                    )
                    _testState.value = progress
                    _testResultText.value = progress
                }
                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    if (intent.getStringExtra("content") == "0") {
                        onTestsFinished()
                    }
                }
            }
        }
    }
}
