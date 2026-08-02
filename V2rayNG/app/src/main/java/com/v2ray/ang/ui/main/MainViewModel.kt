package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val disconnectedText: String = dataSource.getString(R.string.connection_not_connected)
    private val connectedText: String = dataSource.getString(R.string.connection_connected)

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            statusText = disconnectedText,
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    @Volatile
    private var keywordFilter: String = ""

    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupPageFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null

    @Volatile
    private var testingGroupId: String? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    class Factory(
        private val application: Application,
        private val dataSource: MainDataSource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    init {
        collectServiceEvents()
        setupGroupTab()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                updateRunningState(true)
            }
            is MainServiceEvent.StateStartFailure -> {
                val error = event.errorMessage
                if (error.isNotBlank()) toastError(error) else toastError(R.string.toast_services_failure)
                updateRunningState(false)
            }
            MainServiceEvent.StateStopSuccess -> updateRunningState(false)
            is MainServiceEvent.MeasureDelaySuccess -> {
                _uiState.update { it.copy(statusText = event.content) }
            }
            MainServiceEvent.MeasureConfigSuccess -> {
                viewModelScope.launch(ioDispatcher) {
                    val gid = testingGroupId ?: uiState.value.selectedGroupId
                    cacheMutex.withLock { groupDataCache.remove(gid) }
                    updateGroupUi(gid, loadGroup(gid, forceRefresh = true))
                }
            }
            is MainServiceEvent.MeasureConfigNotify -> {
                _uiState.update {
                    it.copy(statusText = dataSource.getString(R.string.connection_runing_task_left, event.progress))
                }
            }
            is MainServiceEvent.MeasureConfigFinish -> {
                if (event.finishedCount == "0") {
                    onTestsFinished()
                }
            }
        }
    }

    private fun updateRunningState(isRunning: Boolean, clearTestingText: Boolean = true) {
        _uiState.update { state ->
            state.copy(
                isRunning = isRunning,
                statusText = if (isRunning) connectedText else disconnectedText,
                serviceStartTime = if (isRunning) (state.serviceStartTime ?: System.currentTimeMillis()) else null,
                isTesting = if (clearTestingText) false else state.isTesting
            )
        }
    }

    private fun testProfileTcpPing(subscriptionId: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val guids = dataSource.getServerGuidList(subscriptionId)
                if (guids.isEmpty()) return@launch
                _uiState.update { it.copy(isTesting = true, statusText = dataSource.getString(R.string.connection_test_testing)) }
                
                dataSource.clearAllTestDelayResults(guids)
                
                cacheMutex.withLock { groupDataCache.remove(subscriptionId) }
                updateGroupUi(subscriptionId, loadGroup(subscriptionId, forceRefresh = true))
            } finally {
                _uiState.update { it.copy(isTesting = false, statusText = if (uiState.value.isRunning) connectedText else disconnectedText) }
            }
        }
    }

    fun moveServer(groupId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(ioDispatcher) {
            val guids = dataSource.getServerGuidList(groupId).toMutableList()
            if (fromIndex in guids.indices && toIndex in guids.indices) {
                val item = guids.removeAt(fromIndex)
                guids.add(toIndex, item)
                dataSource.encodeServerList(guids, groupId)
                cacheMutex.withLock { groupDataCache.remove(groupId) }
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun sortByTestResultsInternal() {
        viewModelScope.launch(ioDispatcher) {
            val currentGroup = uiState.value.selectedGroupId
            dataSource.sortByTestResultsForSub(currentGroup)
            cacheMutex.withLock { groupDataCache.remove(currentGroup) }
            updateGroupUi(currentGroup, loadGroup(currentGroup, forceRefresh = true))
        }
    }

    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }.asStateFlow()

    private fun mutableServersForGroup(groupId: String): MutableStateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }

    // Скрываем профиль "default", чтобы он никогда не отображался
    fun getSubscriptions(): List<SubscriptionCache> {
        return dataSource.getSubscriptions().filter { 
            it.subscription.remarks?.lowercase() != "default"
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            is MainAction.TestProfileTcpPing -> testProfileTcpPing(action.subscriptionId)
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsInternal()
            MainAction.UpdateSubscriptions -> importConfigViaSub()
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            is MainAction.LocateHandled -> consumeLocateTarget(action.target)
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }
            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }
            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent -> {}
        }
    }

    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32L)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L,
                testDelayString = affiliation?.getTestDelayString().orEmpty()
            )
        }

    private suspend fun loadGroup(groupId: String, forceRefresh: Boolean = false): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
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
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        mutableServersForGroup(groupId).value = applyKeywordFilter(servers)
    }

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        
        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val subs = getSubscriptions()
                val groups = subs.map { GroupMapItem(id = it.guid, remarks = it.subscription.remarks) }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupPageFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer()
                    )
                }
                groups.forEach { mutableServersForGroup(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex = groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32L)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    private fun importBatchConfig(configText: String) {
        val isUrl = configText.startsWith("http://", true) || configText.startsWith("https://", true)
        val targetGroupId = if (isUrl) "" else uiState.value.selectedGroupId

        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, targetGroupId, true
                    )
                    when {
                        countSub > 0 -> {
                            // Автоматически обновляем и качаем сервера для новой ссылки
                            dataSource.updateConfigViaSubAll()
                            setupGroupTab(forceRefresh = true)
                        }
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub() {
        val subId = uiState.value.selectedGroupId
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        viewModelScope.launch(ioDispatcher) {
            try {
                // Export stub
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Export failed", e)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        _uiState.update { it.copy(selectedGuid = guid) }
        dataSource.setSelectServer(guid)
    }

    fun triggerLocateSelectedServer() {}
    fun testCurrentServerRealPing() {
        dataSource.testCurrentServerRealPing()
    }

    private fun testAllRealPing(isTcp: Boolean = false) {}
    private fun cancelAllPing() {
        dataSource.cancelAllPing()
    }
    private fun removeAllServerAsync() {
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeAllServer()
            setupGroupTab(forceRefresh = true)
        }
    }
    private fun removeDuplicateServerAsync() {}
    private fun removeInvalidServerAsync() {
        viewModelScope.launch(ioDispatcher) {
            val currentGroup = uiState.value.selectedGroupId
            dataSource.removeInvalidServersInGroup(currentGroup)
            setupGroupTab(forceRefresh = true)
        }
    }
    
    private fun subscriptionIdChanged(groupId: String) {
        _uiState.update { it.copy(selectedGroupId = groupId) }
        dataSource.setSelectedSubscriptionId(groupId)
        viewModelScope.launch(ioDispatcher) {
            val servers = loadGroup(groupId)
            updateGroupUi(groupId, servers)
        }
    }

    private fun removeServerAndRefresh(guid: String) {
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            val currentGroup = uiState.value.selectedGroupId
            cacheMutex.withLock { groupDataCache.remove(currentGroup) }
            updateGroupUi(currentGroup, loadGroup(currentGroup, forceRefresh = true))
        }
    }

    private fun filterConfig(query: String) {
        keywordFilter = query
        val currentGroup = uiState.value.selectedGroupId
        viewModelScope.launch(ioDispatcher) {
            val cached = cacheMutex.withLock { groupDataCache[currentGroup] } ?: loadGroup(currentGroup)
            updateGroupUi(currentGroup, cached)
        }
    }

    private fun consumeLocateTarget(target: LocateTarget) {}
    private fun onTestsFinished() {}
    
    private fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }
}
