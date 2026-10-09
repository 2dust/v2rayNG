package com.v2ray.ang.ui.main

import android.app.Application
import android.graphics.Bitmap
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MainViewModelServersChangedTest {

    /** One subscription, whose server list [serverGuids] a change away from the screen replaces. */
    private class FakeSource : MainDataSource {
        val events = Channel<MainServiceEvent>(Channel.UNLIMITED)

        @Volatile
        var serverGuids = listOf("a", "b")
        private var selectedSubscriptionId = ""

        override val mainServiceEvent: Flow<MainServiceEvent> = events.receiveAsFlow()
        override fun getSelectedSubscriptionId() = selectedSubscriptionId
        override fun setSelectedSubscriptionId(id: String) {
            selectedSubscriptionId = id
        }

        override fun getSelectServer(): String? = null
        override fun setSelectServer(guid: String) = Unit
        override fun getConfirmRemove() = false
        override fun getDoubleColumnDisplay() = false
        override fun isGroupAllDisplayEnabled() = false
        override fun getString(resId: Int) = ""
        override fun getString(resId: Int, vararg formatArgs: Any) = ""
        override fun getSubscriptions() = listOf(SubscriptionCache(SUB, SubscriptionItem(remarks = "Sub")))
        override fun getSubscriptionItem(id: String): SubscriptionItem? = null
        override fun getServerGuidList(groupId: String) = serverGuids
        override fun decodeServerConfig(guid: String): ProfileItem? =
            ProfileItem.create(EConfigType.VLESS).apply {
                subscriptionId = SUB
                remarks = guid
                description = guid
            }

        override fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo? = null
        override fun encodeServerList(guids: List<String>, groupId: String) = Unit
        override fun removeServer(guid: String) = Unit
        override fun removeAllServer() = 0
        override fun removeInvalidServerByGuid(guid: String) = 0
        override fun removeInvalidServersInGroup(groupId: String) = 0
        override fun clearAllTestDelayResults(guids: List<String>) = Unit
        override fun sortByTestResultsForSub(subId: String) = Unit
        override fun getSubsList() = listOf(SUB)
        override suspend fun importBatchConfig(server: String?, subscriptionId: String, updateUI: Boolean) = 0 to 0
        override fun updateConfigViaSubAll() = SubscriptionUpdateResult()
        override fun updateConfigViaSub(subscriptionCache: SubscriptionCache) = SubscriptionUpdateResult()
        override fun shareNonCustomConfigsToClipboard(guids: List<String>) = 0
        override fun share2QRCode(guid: String): Bitmap? = null
        override fun share2Clipboard(guid: String) = false
        override fun sendMsg2Service(msgId: Int, content: String) = Unit
        override fun queryServiceState() = Unit
        override fun sendMsg2TestService(msg: TestServiceMessage, requestId: String?) = Unit
        override fun cancelAllPing() = Unit
        override fun testCurrentServerRealPing(requestId: String) = Unit
        override fun syncSubscriptions() = Unit
        override fun initAssets() = Unit
        override fun close() = Unit
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun serversChangedAwayFromTheScreenReloadTheirTab() = runBlocking {
        val source = FakeSource()
        val viewModel = MainViewModel(mock<Application>(), source)
        viewModel.awaitServers("a", "b")

        // An update on the subscription page or in the subscription service, or an import, replaced the configs
        source.serverGuids = listOf("a", "c", "d")
        source.events.send(MainServiceEvent.ServersChanged)

        viewModel.awaitServers("a", "c", "d")
    }

    private suspend fun MainViewModel.awaitServers(vararg guids: String) {
        withTimeout(5_000) {
            serverGroupState(SUB).first { state -> state.servers.map(ServersCache::guid) == guids.toList() }
        }
    }

    private companion object {
        const val SUB = "sub"
    }
}
