package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    val statusText: String = "",
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app: AngApplication
        get() = getApplication()

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    private val preloadDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)

    private val disconnectedText: String
        get() = app.getString(R.string.connection_not_connected)

    private val connectedText: String
        get() = app.getString(R.string.connection_connected)

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = MmkvManager.decodeSettingsString(
                AppConfig.CACHE_SUBSCRIPTION_ID,
                ""
            ).orEmpty(),
            selectedGuid = MmkvManager.getSelectServer(),
            statusText = app.getString(R.string.connection_not_connected),
            confirmRemove = MmkvManager.decodeSettingsBool(
                AppConfig.PREF_CONFIRM_REMOVE,
                false
            ),
            doubleColumnDisplay = MmkvManager.decodeSettingsBool(
                AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
                false
            )
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val subscriptionId: String
        get() = _uiState.value.selectedGroupId

    @Volatile
    var keywordFilter: String = ""
        private set

    private val _locateEvent = MutableSharedFlow<LocateTarget>(
        extraBufferCapacity = 1
    )
    val locateEvent: SharedFlow<LocateTarget> = _locateEvent.asSharedFlow()

    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()

    private val groupPageFlows =
        ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()

    private val groupLoadMutexes =
        ConcurrentHashMap<String, Mutex>()

    private val loadingCount = AtomicInteger(0)
    private val initialPageReady = CompletableDeferred<Unit>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var filterJob: Job? = null
    private var reloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null

    private var receiverRegistered = false
    private var initialized = false

    @Volatile
    private var testingGroupId: String? = null

    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupPageFlows
            .computeIfAbsent(groupId) {
                MutableStateFlow(emptyList())
            }
            .asStateFlow()

    private fun mutableServersForGroup(
        groupId: String
    ): MutableStateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) {
            MutableStateFlow(emptyList())
        }

    private fun currentServers(): List<ServersCache> =
        mutableServersForGroup(subscriptionId).value

    fun initialize() {
        if (initialized) return
        initialized = true

        startListenBroadcast()
        setupGroupTab()

        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32L)

                SettingsManager.initAssets(app, app.assets)
                SubscriptionUpdater.sync(app)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(
                    AppConfig.TAG,
                    "Main background initialization failed",
                    error
                )
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = MmkvManager.decodeSettingsBool(
                    AppConfig.PREF_CONFIRM_REMOVE,
                    false
                ),
                doubleColumnDisplay = MmkvManager.decodeSettingsBool(
                    AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
                    false
                )
            )
        }
    }

    fun setLoading(loading: Boolean) {
        if (loading) {
            val count = loadingCount.incrementAndGet()
            if (count == 1) {
                _uiState.update { it.copy(isLoading = true) }
            }
        } else {
            val count = loadingCount.updateAndGet {
                if (it > 0) it - 1 else 0
            }
            if (count == 0) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun getServerGuidList(groupId: String): List<String> =
        if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }

    private suspend fun buildServersCache(
        guids: List<String>
    ): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()

            val profile = MmkvManager.decodeServerConfig(guid)
                ?: return@mapNotNull null

            val affiliation =
                MmkvManager.decodeServerAffiliationInfo(guid)

            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis =
                    affiliation?.testDelayMillis ?: 0L,
                testDelayString =
                    affiliation?.getTestDelayString().orEmpty()
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) {
            Mutex()
        }

        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock {
                    groupDataCache[groupId]?.let {
                        return@withLock it
                    }
                }
            }

            val servers = buildServersCache(
                getServerGuidList(groupId)
            )

            currentCoroutineContext().ensureActive()

            cacheMutex.withLock {
                groupDataCache[groupId] = servers
            }

            servers
        }
    }

    private fun applyKeywordFilter(
        servers: List<ServersCache>
    ): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers

        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }

        return servers.filter { cache ->
            val profile = cache.profile

            profile.remarks.matchesPattern(regex, keyword) ||
                profile.description.orEmpty()
                    .matchesPattern(regex, keyword) ||
                profile.server.orEmpty()
                    .matchesPattern(regex, keyword) ||
                profile.configType.name
                    .matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(
        groupId: String,
        servers: List<ServersCache>
    ) {
        mutableServersForGroup(groupId).value =
            applyKeywordFilter(servers)
    }

    private fun getSubscriptionsInternal():
        List<SubscriptionCache> {
        val result = mutableListOf<SubscriptionCache>()

        if (
            MmkvManager.decodeSettingsBool(
                AppConfig.PREF_GROUP_ALL_DISPLAY
            )
        ) {
            result += SubscriptionCache(
                guid = "",
                subscription = SubscriptionItem().apply {
                    remarks = app.getString(
                        R.string.filter_config_all
                    )
                }
            )
        }

        result += MmkvManager.decodeSubscriptions()
        return result
    }

    fun getSubscriptions(): List<SubscriptionCache> =
        getSubscriptionsInternal()

    private fun resolveSelectedGroup(
        groups: List<GroupMapItem>
    ): String {
        val current = subscriptionId

        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }

        if (resolved != current) {
            MmkvManager.encodeSettings(
                AppConfig.CACHE_SUBSCRIPTION_ID,
                resolved
            )
        }

        return resolved
    }

    private fun radialPreloadOrder(
        groups: List<GroupMapItem>,
        selectedIndex: Int
    ): List<String> {
        if (groups.isEmpty()) return emptyList()

        val result = ArrayList<String>(
            (groups.size - 1).coerceAtLeast(0)
        )

        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance

            if (right in groups.indices) {
                result += groups[right].id
            }
            if (left in groups.indices) {
                result += groups[left].id
            }
        }

        return result
    }

    fun setupGroupTab(
        forceRefresh: Boolean = false
    ): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock {
                        groupDataCache.clear()
                    }
                }

                val groups = getSubscriptionsInternal().map {
                    GroupMapItem(
                        id = it.guid,
                        remarks = it.subscription.remarks
                    )
                }

                val selectedGroup =
                    resolveSelectedGroup(groups)

                val validIds =
                    groups.mapTo(HashSet()) { it.id }

                groupPageFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid =
                            MmkvManager.getSelectServer()
                    )
                }
                groups.forEach { mutableServersForGroup(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock {
                        groupDataCache.clear()
                    }
                    return@launch
                }

                val selectedServers = loadGroup(
                    groupId = selectedGroup,
                    forceRefresh = forceRefresh
                )
                updateGroupUi(
                    selectedGroup,
                    selectedServers
                )

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex = groups.indexOfFirst {
                    it.id == selectedGroup
                }.coerceAtLeast(0)

                val preloadOrder = radialPreloadOrder(
                    groups,
                    selectedIndex
                )

                preloadJob = viewModelScope.launch(
                    preloadDispatcher
                ) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()

                        delay(32L)

                        val servers = loadGroup(
                            groupId = groupId,
                            forceRefresh = forceRefresh
                        )
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(
                    AppConfig.TAG,
                    "Failed to set up group tabs",
                    error
                )
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also {
            setupGroupJob = it
        }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) {
            return
        }
        mutableServersForGroup(id)

        if (subscriptionId != id) {
            MmkvManager.encodeSettings(
                AppConfig.CACHE_SUBSCRIPTION_ID,
                id
            )
            _uiState.update {
                it.copy(selectedGroupId = id)
            }
        }

        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(
            ioDispatcher
        ) {
            try {
                updateGroupUi(
                    id,
                    loadGroup(id)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(
                    AppConfig.TAG,
                    "Failed to load selected group: $id",
                    error
                )
            }
        }
    }

    fun reloadServerList() {
        val groupId = subscriptionId

        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(
            ioDispatcher
        ) {
            updateGroupUi(
                groupId,
                loadGroup(
                    groupId,
                    forceRefresh = true
                )
            )
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()

        reloadJob = viewModelScope.launch(
            preloadDispatcher
        ) {
            val selected = subscriptionId

            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }

            order.forEachIndexed { index, groupId ->
                ensureActive()

                if (index > 0) {
                    delay(32L)
                }

                updateGroupUi(
                    groupId,
                    loadGroup(
                        groupId,
                        forceRefresh = true
                    )
                )
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return

        keywordFilter = keyword
        filterJob?.cancel()

        filterJob = viewModelScope.launch(
            defaultDispatcher
        ) {
            delay(300L)

            val snapshot = cacheMutex.withLock {
                groupDataCache.toMap()
            }

            ensureActive()

            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        MmkvManager.setSelectServer(guid)
        _uiState.update {
            it.copy(selectedGuid = guid)
        }
    }

    fun refreshSelectedGuid() {
        _uiState.update {
            it.copy(
                selectedGuid =
                    MmkvManager.getSelectServer()
            )
        }
    }

    fun removeServerAndRefresh(guid: String) {
        viewModelScope.launch(ioDispatcher) {
            MmkvManager.removeServer(guid)

            cacheMutex.withLock {
                groupDataCache.clear()
            }

            setupGroupTab(
                forceRefresh = true
            ).join()
        }
    }

    fun swapServer(
        fromPosition: Int,
        toPosition: Int
    ) {
        val groupId = subscriptionId
        if (groupId.isEmpty()) return

        val servers = currentServers().toMutableList()

        if (
            fromPosition !in servers.indices ||
            toPosition !in servers.indices
        ) {
            return
        }

        Collections.swap(
            servers,
            fromPosition,
            toPosition
        )

        val guids = servers.mapTo(
            ArrayList(servers.size)
        ) { it.guid }

        mutableServersForGroup(groupId).value = servers

        viewModelScope.launch(ioDispatcher) {
            MmkvManager.encodeServerList(
                guids,
                groupId
            )

            cacheMutex.withLock {
                groupDataCache[groupId] = servers
            }
        }
    }

    fun cancelAllPing() {
        MessageUtil.sendMsg2TestService(
            app,
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL
            )
        )

        testingGroupId = null

        _uiState.update {
            it.copy(
                isTesting = false,
                statusText = if (it.isRunning) {
                    connectedText
                } else {
                    disconnectedText
                }
            )
        }
    }

    fun testAllRealPing() {
        val groupId = subscriptionId
        val servers = currentServers()

        MmkvManager.clearAllTestDelayResults(
            servers.map { it.guid }
        )

        if (servers.isEmpty()) {
            _uiState.update {
                it.copy(isTesting = false)
            }
            return
        }

        testingGroupId = groupId

        _uiState.update {
            it.copy(
                isTesting = true,
                statusText = app.getString(
                    R.string.connection_test_testing
                )
            )
        }

        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock {
                groupDataCache.remove(groupId)
            }

            MessageUtil.sendMsg2TestService(
                app,
                TestServiceMessage(
                    key =
                        AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = groupId,
                    serverGuids =
                        if (keywordFilter.isNotEmpty()) {
                            servers.map { it.guid }
                        } else {
                            emptyList()
                        }
                )
            )
        }
    }

    fun testCurrentServerRealPing() {
        _uiState.update {
            it.copy(
                statusText = app.getString(
                    R.string.connection_test_testing
                )
            )
        }

        MessageUtil.sendMsg2Service(
            app,
            AppConfig.MSG_MEASURE_DELAY,
            ""
        )
    }

    fun onTestsFinished() {
        viewModelScope.launch(ioDispatcher) {
            if (
                MmkvManager.decodeSettingsBool(
                    AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST
                )
            ) {
                removeInvalidServerInternal()
            }

            if (
                MmkvManager.decodeSettingsBool(
                    AppConfig.PREF_AUTO_SORT_AFTER_TEST
                )
            ) {
                sortByTestResultsInternal()
            }

            cacheMutex.withLock {
                groupDataCache.clear()
            }

            testingGroupId = null

            _uiState.update {
                it.copy(
                    isTesting = false,
                    statusText = if (it.isRunning) {
                        connectedText
                    } else {
                        disconnectedText
                    }
                )
            }

            reloadAllGroups(
                _uiState.value.groups.map { it.id }
            )
        }
    }

    fun updateConfigViaSubAll():
        SubscriptionUpdateResult =
        if (subscriptionId.isEmpty()) {
            AngConfigManager.updateConfigViaSubAll()
        } else {
            val item = MmkvManager.decodeSubscription(
                subscriptionId
            ) ?: return SubscriptionUpdateResult()

            AngConfigManager.updateConfigViaSub(
                SubscriptionCache(
                    subscriptionId,
                    item
                )
            )
        }

    fun exportAllServer(): Int {
        val list =
            if (
                subscriptionId.isEmpty() &&
                keywordFilter.isEmpty()
            ) {
                MmkvManager.decodeAllServerList()
            } else {
                currentServers().map { it.guid }
            }

        return AngConfigManager
            .shareNonCustomConfigsToClipboard(
                app,
                list
            )
    }

    fun removeDuplicateServer(): Int {
        val seen = HashSet<ProfileItem>()
        val duplicates = ArrayList<String>()

        currentServers().forEach { server ->
            val profile = server.profile

            if (!profile.configType.isComplexType()) {
                val identity =
                    profile.duplicateIdentity()

                if (!seen.add(identity)) {
                    duplicates += server.guid
                }
            }
        }

        duplicates.forEach(
            MmkvManager::removeServer
        )

        return duplicates.size
    }

    fun removeAllServer(): Int {
        val count =
            if (
                subscriptionId.isEmpty() &&
                keywordFilter.isEmpty()
            ) {
                MmkvManager.removeAllServer()
            } else {
                val guids =
                    currentServers().map { it.guid }

                guids.forEach(
                    MmkvManager::removeServer
                )

                guids.size
            }

        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock {
                groupDataCache.clear()
            }
        }

        return count
    }

    private fun removeInvalidServerInternal(): Int =
        if (
            subscriptionId.isEmpty() &&
            keywordFilter.isEmpty()
        ) {
            MmkvManager.removeInvalidServer("")
        } else {
            currentServers().sumOf {
                MmkvManager.removeInvalidServer(
                    it.guid
                )
            }
        }

    fun removeInvalidServer(): Int {
        val count = removeInvalidServerInternal()

        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock {
                groupDataCache.clear()
            }

            setupGroupTab(
                forceRefresh = true
            )
        }

        return count
    }

    private fun sortByTestResultsForSub(
        subId: String
    ) {
        val sorted =
            MmkvManager.decodeServerList(subId)
                .map { guid ->
                    val delay =
                        MmkvManager
                            .decodeServerAffiliationInfo(guid)
                            ?.testDelayMillis
                            ?: 0L

                    guid to if (delay <= 0L) {
                        Long.MAX_VALUE
                    } else {
                        delay
                    }
                }
                .sortedBy { it.second }
                .mapTo(ArrayList()) { it.first }

        MmkvManager.encodeServerList(
            sorted,
            subId
        )
    }

    private fun sortByTestResultsInternal() {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList()
                .forEach(
                    ::sortByTestResultsForSub
                )
        } else {
            sortByTestResultsForSub(
                subscriptionId
            )
        }
    }

    fun sortByTestResults() {
        sortByTestResultsInternal()

        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock {
                groupDataCache.clear()
            }

            setupGroupTab(
                forceRefresh = true
            )
        }
    }

    fun triggerLocateSelectedServer() {
        val selected =
            MmkvManager.getSelectServer() ?: return

        val profile =
            MmkvManager.decodeServerConfig(selected)
                ?: return

        val groupId = profile.subscriptionId

        val groupIndex =
            _uiState.value.groups
                .indexOfFirst { it.id == groupId }
                .takeIf { it >= 0 }
                ?: return

        viewModelScope.launch(ioDispatcher) {
            val position =
                loadGroup(groupId)
                    .indexOfFirst {
                        it.guid == selected
                    }
                    .takeIf { it >= 0 }
                    ?: return@launch

            _locateEvent.emit(
                LocateTarget(
                    groupId = groupId,
                    groupIndex = groupIndex,
                    itemPosition = position
                )
            )
        }
    }

    fun getPosition(guid: String): Int =
        currentServers()
            .indexOfFirst { it.guid == guid }

    fun startListenBroadcast() {
        if (receiverRegistered) return

        ContextCompat.registerReceiver(
            app,
            messageReceiver,
            IntentFilter(
                AppConfig.BROADCAST_ACTION_ACTIVITY
            ),
            Utils.receiverFlags()
        )

        receiverRegistered = true

        MessageUtil.sendMsg2Service(
            app,
            AppConfig.MSG_REGISTER_CLIENT,
            ""
        )
    }

    private fun updateRunningState(
        running: Boolean,
        clearTestingText: Boolean = true
    ) {
        _uiState.update { state ->
            state.copy(
                isRunning = running,
                statusText =
                    if (
                        !clearTestingText &&
                        state.isTesting
                    ) {
                        state.statusText
                    } else if (running) {
                        connectedText
                    } else {
                        disconnectedText
                    }
            )
        }
    }

    private val messageReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                when (
                    intent?.getIntExtra("key", 0)
                ) {
                    AppConfig.MSG_STATE_RUNNING -> {
                        updateRunningState(
                            running = true,
                            clearTestingText = false
                        )
                    }

                    AppConfig.MSG_STATE_NOT_RUNNING -> {
                        updateRunningState(
                            running = false,
                            clearTestingText = false
                        )
                    }

                    AppConfig.MSG_STATE_START_SUCCESS -> {
                        app.toastSuccess(
                            R.string.toast_services_success
                        )
                        updateRunningState(true)
                    }

                    AppConfig.MSG_STATE_START_FAILURE -> {
                        val error =
                            intent.getStringExtra("content")
                                .orEmpty()

                        if (error.isNotBlank()) {
                            app.toastError(error)
                        } else {
                            app.toastError(
                                R.string.toast_services_failure
                            )
                        }

                        updateRunningState(false)
                    }

                    AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        updateRunningState(false)
                    }

                    AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                        _uiState.update {
                            it.copy(
                                statusText =
                                    intent.getStringExtra(
                                        "content"
                                    ).orEmpty()
                            )
                        }
                    }

                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                        val groupId =
                            testingGroupId
                                ?: subscriptionId

                        viewModelScope.launch(
                            ioDispatcher
                        ) {
                            cacheMutex.withLock {
                                groupDataCache.remove(
                                    groupId
                                )
                            }

                            updateGroupUi(
                                groupId,
                                loadGroup(
                                    groupId,
                                    forceRefresh = true
                                )
                            )
                        }
                    }

                    AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                        val progress = app.getString(
                            R.string.connection_runing_task_left,
                            intent.getStringExtra(
                                "content"
                            )
                        )

                        _uiState.update {
                            it.copy(
                                statusText = progress
                            )
                        }
                    }

                    AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                        if (
                            intent.getStringExtra(
                                "content"
                            ) == "0"
                        ) {
                            onTestsFinished()
                        }
                    }
                }
            }
        }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()

        if (receiverRegistered) {
            runCatching {
                app.unregisterReceiver(
                    messageReceiver
                )
            }.onFailure {
                LogUtil.e(
                    AppConfig.TAG,
                    "Failed to unregister MainViewModel receiver",
                    it
                )
            }

            receiverRegistered = false
        }

        super.onCleared()
    }
}
