package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.ServerRowItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.repository.MainRepository
import com.v2ray.ang.repository.MainServiceEvent
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.PatternSyntaxException

private const val PREFETCH_RADIUS = 1
private const val PREFETCH_DELAY_MS = 32
private const val SEARCH_DEBOUNCE_MS = 300
private const val DELAY_REFRESH_INTERVAL_MS = 400

class MainViewModel(
    private val repo: MainRepository,
) : BaseViewModel<MainUiState, MainAction>(
    MainUiState(
        selectedGroupId = repo.selectedGroupId(),
        selectedGuid = repo.selectedGuid(),
        confirmRemove = repo.confirmRemove(),
        doubleColumnDisplay = repo.doubleColumnDisplay(),
    )
) {

    private val cpu = Dispatchers.Default
    private val serial = Dispatchers.IO.limitedParallelism(1)

    private val serverFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServerRowItem>>>()
    private val countFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()
    private val loadedGroups: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun servers(groupId: String): StateFlow<List<ServerRowItem>> = mutableServers(groupId).asStateFlow()
    fun serverCount(groupId: String): StateFlow<Int> = mutableCount(groupId).asStateFlow()

    private fun mutableServers(groupId: String) = serverFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }
    private fun mutableCount(groupId: String) = countFlows.computeIfAbsent(groupId) { MutableStateFlow(0) }
    private fun currentServers(): List<ServerRowItem> = mutableServers(state.selectedGroupId).value

    private var setupJob: Job? = null
    private var prefetchJob: Job? = null
    private var filterJob: Job? = null
    private var delayJob: Job? = null
    private val groupJobs = ConcurrentHashMap<String, Job>()
    private val orderJobs = ConcurrentHashMap<String, Job>()

    @Volatile private var testingGroupId: String? = null
    private val delayDirty = AtomicBoolean(false)
    private var initialized = false
    private val firstPageReady = CompletableDeferred<Unit>()

    init {
        observeServiceEvents()
        rebuildGroups(invalidateCache = false, bootstrap = true)
    }

    override fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> rebuildGroups(invalidateCache = true)
            MainAction.ToggleService -> if (state.isRunning) platform(MainEvent.StopService) else startCore()
            MainAction.RestartService -> restartCore()
            MainAction.StatusBarClick -> when {
                state.isTesting -> cancelTesting()
                state.isRunning -> testCurrentServer()
                else -> Unit
            }
            MainAction.TestAllServers -> testAll(onlyTcp = true)
            MainAction.TestRealAllServers -> testAll(onlyTcp = false)
            MainAction.CancelTesting -> cancelTesting()
            MainAction.RemoveAllServers -> removeAllServers()
            MainAction.RemoveDuplicateServers -> removeDuplicateServers()
            MainAction.RemoveInvalidServers -> removeInvalidServers()
            MainAction.SortByTestResults -> sortByTestResults()
            MainAction.UpdateSubscriptions -> updateSubscriptions()
            MainAction.ExportAll -> exportAll()
            MainAction.ImportFromQrCode -> platform(MainEvent.ScanQrCode)
            MainAction.ImportFromFile -> platform(MainEvent.PickConfigFile)
            MainAction.ImportFromClipboard -> launch(loading = true) { importBatchConfig(repo.readClipboard()) }
            is MainAction.ConfigFileSelected -> launch(loading = true) { importBatchConfig(repo.readTextFromUri(action.uri).orEmpty()) }
            is MainAction.ImportBatchConfig -> launch(loading = true) { importBatchConfig(action.configText) }
            is MainAction.SelectGroup -> selectGroup(action.groupId)
            is MainAction.SelectServer -> selectServer(action.guid)
            is MainAction.RemoveServer -> removeServer(action.guid)
            is MainAction.MoveServer -> moveServer(action.groupId, action.from, action.to)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.SetSearchActive -> setSearchActive(action.active)
            MainAction.LocateSelectedServer -> locateSelectedServer()
            MainAction.LocateFailed -> toastError()
            is MainAction.AddServer -> navigate(
                AppRoute.ServerEdit(
                    configType = action.configType,
                    subscriptionId = state.selectedGroupId,
                    isRunning = state.isRunning,
                )
            )
            is MainAction.EditServer -> navigate(
                AppRoute.ServerEdit(
                    configType = action.configType,
                    guid = action.guid,
                    subscriptionId = state.selectedGroupId,
                    isRunning = state.isRunning,
                )
            )
            is MainAction.ShareQrCode -> launch {
                val bitmap = repo.share2QRCode(action.guid)
                if (bitmap == null) toastError() else platform(MainEvent.ShowQrCode(bitmap))
            }
            is MainAction.ShareClipboard -> launch {
                if (repo.share2Clipboard(action.guid)) toastSuccess() else toastError()
            }
            is MainAction.ShareFullContent -> launch {
                if (repo.shareFullContent(action.guid)) toastSuccess() else toastError()
            }
            is MainAction.Navigate -> navigate(action.route)
            MainAction.OpenPromotion -> navigate(AppRoute.OpenUrl(repo.promotionUrl()))
            is MainAction.ResultReceived -> handleResult(action.result)
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        launch(context = serial, onError = {}) {
            firstPageReady.await()
            delay(PREFETCH_DELAY_MS)
            repo.prepare()
        }
    }

    private fun handleResult(result: BaseResult) {
        val confirmRemove = repo.confirmRemove()
        val doubleColumn = repo.doubleColumnDisplay()
        setState { copy(confirmRemove = confirmRemove, doubleColumnDisplay = doubleColumn) }
        if (result.refreshList) rebuildGroups(invalidateCache = true)
        if (result.restartService && state.isRunning) restartCore()
    }

    private fun startCore() {
        if (state.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        platform(
            MainEvent.StartService(
                requireVpnPermission = repo.isVpnMode(),
                requireLocalNetwork = repo.isProxySharing(),
            )
        )
    }

    private fun restartCore() {
        if (state.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        platform(
            MainEvent.RestartService(
                stopFirst = state.isRunning,
                requireVpnPermission = repo.isVpnMode(),
                requireLocalNetwork = repo.isProxySharing(),
            )
        )
    }

    private fun observeServiceEvents() = launch(onError = {}) {
        repo.serviceEvents.collect(::handleServiceEvent)
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunning(true, keepTestingText = true)
            MainServiceEvent.StateNotRunning -> updateRunning(false, keepTestingText = true)
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                updateRunning(true)
            }
            is MainServiceEvent.StateStartFailure -> {
                if (event.errorMessage.isNotBlank()) toastError(event.errorMessage)
                else toastError(R.string.toast_services_failure)
                updateRunning(false)
            }
            MainServiceEvent.StateStopSuccess -> updateRunning(false)
            is MainServiceEvent.MeasureDelayResult -> {
                setState { copy(status = MainStatus.ConnectionTest(event.result)) }
            }
            is MainServiceEvent.MeasureConfigNotify -> {
                setState { copy(status = MainStatus.TestProgress(event.progress)) }
            }
            MainServiceEvent.MeasureConfigSuccess -> scheduleDelayRefresh(testingGroupId ?: state.selectedGroupId)
            is MainServiceEvent.MeasureConfigFinish -> onTestsFinished()
        }
    }

    private fun updateRunning(running: Boolean, keepTestingText: Boolean = false) = setState {
        copy(
            isRunning = running,
            status = when {
                keepTestingText && isTesting -> status
                running -> MainStatus.Connected
                else -> MainStatus.Disconnected
            },
        )
    }

    private fun publish(groupId: String, rows: List<ServerRowItem>) {
        val filtered = applyFilter(rows)
        mutableServers(groupId).value = filtered
        mutableCount(groupId).value = filtered.size
    }

    private fun publishCounts(counts: Map<String, Int>) {
        if (state.searchQuery.isNotEmpty()) return
        counts.forEach { (groupId, count) -> mutableCount(groupId).value = count }
    }

    private fun applyFilter(rows: List<ServerRowItem>): List<ServerRowItem> {
        val key = state.searchQuery.trim()
        if (key.isEmpty()) return rows
        val regex = try { Regex(key, RegexOption.IGNORE_CASE) } catch (_: PatternSyntaxException) { return rows }
        return rows.filter { row ->
            row.remarks.matchesPattern(regex, key) ||
            row.statistics.matchesPattern(regex, key) ||
            row.typeDescription.matchesPattern(regex, key)
        }
    }

    private suspend fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = state.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) repo.setSelectedGroupId(resolved)
        return resolved
    }

    private fun rebuildGroups(invalidateCache: Boolean, bootstrap: Boolean = false): Job {
        setupJob?.cancel()
        prefetchJob?.cancel()
        val job = launch(onError = { LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", it) }) {
            try {
                if (invalidateCache) repo.invalidate()
                loadedGroups.clear()
                val groups = repo.loadGroups()
                val selected = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                serverFlows.keys.removeAll { it !in validIds }
                countFlows.keys.removeAll { it !in validIds }
                loadedGroups.removeAll { it !in validIds }
                val selectedGuid = repo.selectedGuid()
                setState { copy(groups = groups, selectedGroupId = selected, selectedGuid = selectedGuid) }
                if (groups.isEmpty()) return@launch
                publishCounts(repo.groupCounts())
                loadGroup(selected, force = true, progressive = true)
                if (!firstPageReady.isCompleted) firstPageReady.complete(Unit)
                prefetch(groups, selected)
            } finally {
                if (bootstrap && !firstPageReady.isCompleted) firstPageReady.complete(Unit)
            }
        }
        setupJob = job
        return job
    }

    private suspend fun loadGroup(groupId: String, force: Boolean = false, progressive: Boolean = false) {
        if (!force && groupId in loadedGroups) return
        val rows = if (progressive) {
            repo.loadServers(groupId, force) { partial -> publish(groupId, partial) }
        } else {
            repo.loadServers(groupId, force)
        }
        publish(groupId, rows)
        loadedGroups.add(groupId)
    }

    private fun prefetch(groups: List<GroupMapItem>, selected: String) {
        prefetchJob?.cancel()
        val index = groups.indexOfFirst { it.id == selected }.coerceAtLeast(0)
        val neighbours = buildList {
            for (distance in 1..PREFETCH_RADIUS) {
                groups.getOrNull(index + distance)?.let { add(it.id) }
                groups.getOrNull(index - distance)?.let { add(it.id) }
            }
        }.filter { it !in loadedGroups }
        if (neighbours.isEmpty()) return
        prefetchJob = launch(context = serial, onError = {}) {
            neighbours.forEach { groupId ->
                currentCoroutineContext().ensureActive()
                delay(PREFETCH_DELAY_MS)
                loadGroup(groupId)
            }
        }
    }

    private fun selectGroup(id: String) {
        val groups = state.groups
        if (groups.none { it.id == id }) return
        if (state.selectedGroupId != id) {
            setState { copy(selectedGroupId = id) }
            launch(onError = {}) { repo.setSelectedGroupId(id) }
        }
        if (id in loadedGroups) {
            prefetch(groups, id)
            return
        }
        if (groupJobs[id]?.isActive == true) return
        groupJobs[id] = launch(onError = { LogUtil.e(AppConfig.TAG, "Failed to load group: $id", it) }) {
            loadGroup(id, progressive = true)
            prefetch(groups, id)
        }.also { job -> job.invokeOnCompletion { groupJobs.remove(id, job) } }
    }

    private fun setSearchActive(active: Boolean) {
        if (active == state.isSearchActive) return
        setState { copy(isSearchActive = active) }
        if (!active) filterConfig("")
    }

    private fun filterConfig(query: String) {
        if (query == state.searchQuery) return
        setState { copy(searchQuery = query) }
        filterJob?.cancel()
        filterJob = launch(context = cpu, onError = {}) {
            delay(SEARCH_DEBOUNCE_MS)
            repo.cachedServers().forEach { (groupId, rows) ->
                currentCoroutineContext().ensureActive()
                publish(groupId, rows)
            }
            if (state.searchQuery.isEmpty()) {
                publishCounts(repo.groupCounts())
                return@launch
            }
            state.groups.map { it.id }.filter { it !in loadedGroups }.forEach { groupId ->
                currentCoroutineContext().ensureActive()
                loadGroup(groupId)
            }
        }
    }

    private fun selectServer(guid: String) {
        if (guid == state.selectedGuid) return
        launch(onError = {}) {
            withContext(NonCancellable) { repo.setSelectedGuid(guid) }
            setState { copy(selectedGuid = guid) }
            if (state.isRunning) restartCore()
        }
    }

    private fun moveServer(groupId: String, from: Int, to: Int) {
        val rows = mutableServers(groupId).value.toMutableList()
        if (!rows.moveItem(from, to)) return
        mutableServers(groupId).value = rows
        val previous = orderJobs[groupId]
        orderJobs[groupId] = launch(onError = {}) {
            previous?.join()
            withContext(NonCancellable) { repo.saveServerOrder(groupId, rows) }
        }.also { job -> job.invokeOnCompletion { orderJobs.remove(groupId, job) } }
    }

    private fun removeServer(guid: String) {
        if (guid == state.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        launch(loading = true) {
            repo.removeServers(listOf(guid))
            rebuildGroups(invalidateCache = false).join()
            toastSuccess()
        }
    }

    private fun removeAllServers() = launch(loading = true) {
        val count = if (state.selectedGroupId.isEmpty() && state.searchQuery.isEmpty()) {
            repo.removeAllServers()
        } else {
            repo.removeServers(currentServers().map { it.guid })
        }
        rebuildGroups(invalidateCache = false).join()
        toast(BaseText.of(R.string.title_del_config_count, count))
    }

    private fun removeDuplicateServers() = launch(loading = true) {
        val count = repo.removeDuplicateServers(currentServers().map { it.guid })
        rebuildGroups(invalidateCache = false).join()
        toast(BaseText.of(R.string.title_del_duplicate_config_count, count))
    }

    private fun removeInvalidServers() = launch(loading = true) {
        val visibleOnly = state.selectedGroupId.isNotEmpty() || state.searchQuery.isNotBlank()
        val count = repo.removeInvalidServers(
            if (visibleOnly) currentServers().map { it.guid } else null
        )
        rebuildGroups(invalidateCache = false).join()
        toast(BaseText.of(R.string.title_del_config_count, count))
    }

    private fun sortByTestResults() = launch(loading = true) {
        val groups = if (state.selectedGroupId.isEmpty()) emptyList() else listOf(state.selectedGroupId)
        repo.sortByTestResults(groups)
        rebuildGroups(invalidateCache = false).join()
        toastSuccess()
    }

    private fun exportAll() = launch(loading = true) {
        val guids = if (state.selectedGroupId.isEmpty() && state.searchQuery.isEmpty()) {
            repo.allGuids()
        } else {
            currentServers().map { it.guid }
        }
        val count = repo.exportToClipboard(guids)
        if (count > 0) toast(BaseText.of(R.string.title_export_config_count, count)) else toastError()
    }

    private suspend fun importBatchConfig(configText: String) {
        if (configText.isBlank()) { toastError(); return }
        val (count, countSub) = repo.importBatchConfig(configText, state.selectedGroupId)
        when {
            count > 0 -> {
                toast(BaseText.of(R.string.title_import_config_count, count))
                rebuildGroups(invalidateCache = false)
            }
            countSub > 0 -> rebuildGroups(invalidateCache = false)
            else -> toastError()
        }
    }

    private fun updateSubscriptions() = launch(loading = true) {
        val result = repo.updateSubscriptions(state.selectedGroupId)
        val total = result.successCount + result.failureCount + result.skipCount
        when {
            total == 0 -> toast(R.string.title_update_subscription_no_subscription)
            result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                toast(BaseText.of(R.string.title_update_config_count, result.configCount))
            else -> toast(
                BaseText.of(
                    R.string.title_update_subscription_result,
                    result.configCount, result.successCount, result.failureCount, result.skipCount,
                )
            )
        }
        if (result.configCount > 0) {
            rebuildGroups(invalidateCache = false).join()
        }
    }

    private fun testCurrentServer() {
        setState { copy(status = MainStatus.Testing) }
        launch(onError = {}) { repo.testCurrentServer() }
    }

    private fun testAll(onlyTcp: Boolean) {
        val groupId = state.selectedGroupId
        val rows = currentServers()
        if (rows.isEmpty()) { setState { copy(isTesting = false) }; return }
        testingGroupId = groupId
        setState { copy(isTesting = true, status = MainStatus.Testing) }
        launch(onError = {}) {
            repo.cancelBatchTest()
            repo.clearTestResults(rows.map { it.guid })
            repo.startBatchTest(
                groupId = groupId,
                guids = if (state.searchQuery.isNotEmpty()) rows.map { it.guid } else emptyList(),
                onlyTcp = onlyTcp,
            )
        }
    }

    private fun scheduleDelayRefresh(groupId: String) {
        delayDirty.set(true)
        if (delayJob?.isActive == true) return
        delayJob = launch(onError = {}) {
            while (delayDirty.getAndSet(false)) {
                currentCoroutineContext().ensureActive()
                repo.refreshDelays(groupId)?.let { publish(groupId, it) }
                delay(DELAY_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun cancelTesting() {
        delayJob?.cancel()
        testingGroupId = null
        setState {
            copy(
                isTesting = false,
                status = if (isRunning) MainStatus.Connected else MainStatus.Disconnected,
            )
        }
        launch(onError = {}) { repo.cancelBatchTest() }
    }

    private fun onTestsFinished() = launch(onError = {}) {
        val groupId = testingGroupId ?: state.selectedGroupId
        delayJob?.cancel()
        delayDirty.set(false)
        testingGroupId = null
        setState {
            copy(
                isTesting = false,
                status = if (isRunning) MainStatus.Connected else MainStatus.Disconnected,
            )
        }
        repo.refreshDelays(groupId)?.let { publish(groupId, it) }
    }

    private fun locateSelectedServer() = launch(onError = {}) {
        val guid = repo.selectedGuid() ?: return@launch
        val groups = state.groups
        if (groups.isEmpty()) return@launch
        val ownerId = repo.subscriptionIdOf(guid)
        val groupIndex = groups.indexOfFirst { it.id.isNotEmpty() && it.id == ownerId }
            .takeIf { it >= 0 } ?: groups.indexOfFirst { it.id.isEmpty() }
        if (groupIndex < 0) { toastError(); return@launch }
        val groupId = groups[groupIndex].id
        loadGroup(groupId)
        platform(MainEvent.LocateProfile(LocateTarget(serverGuid = guid, groupId = groupId)))
    }

    override fun onCleared() {
        setupJob?.cancel()
        prefetchJob?.cancel()
        filterJob?.cancel()
        delayJob?.cancel()
        groupJobs.values.forEach { it.cancel() }
        orderJobs.values.forEach { it.cancel() }
        repo.close()
        super.onCleared()
    }
}
