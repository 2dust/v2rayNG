package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MainSubscriptionRefreshTest {

    @Test
    fun populateSubscriptionGroupData_refreshesChangedGroupsAndAggregateState() = runBlocking {
        val fixture = Fixture()
        fixture.loadInitialState()

        assertEquals(2, fixture.viewModel.serverGroupState(ALL).value.servers.size)
        assertEquals(1, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
        assertEquals(1, fixture.viewModel.serverGroupState(GROUP_B).value.servers.size)

        fixture.addProfile(GROUP_A, "a-2")
        fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A))

        assertEquals(3, fixture.viewModel.serverGroupState(ALL).value.servers.size)
        assertEquals(2, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
        assertEquals(1, fixture.viewModel.serverGroupState(GROUP_B).value.servers.size)
    }

    @Test
    fun subscriptionGroupRefreshOrder_prioritizesSelectedGroupAndDeduplicatesIds() {
        assertEquals(
            listOf(GROUP_B, GROUP_A, ALL),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(ALL, GROUP_A, GROUP_B),
                selectedGroupId = GROUP_B,
                changedSubscriptionIds = listOf(GROUP_A, GROUP_B, GROUP_A),
            )
        )
        assertEquals(
            listOf(ALL),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(ALL, GROUP_A, GROUP_B),
                selectedGroupId = GROUP_A,
                changedSubscriptionIds = listOf("not-visible"),
            )
        )
        assertEquals(
            emptyList<String>(),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(ALL, GROUP_A, GROUP_B),
                selectedGroupId = GROUP_A,
                changedSubscriptionIds = emptyList(),
            )
        )
    }

    private class Fixture {
        private val groupGuids = linkedMapOf(
            GROUP_A to mutableListOf("a-1"),
            GROUP_B to mutableListOf("b-1"),
        )
        private val profiles = linkedMapOf(
            "a-1" to profile(GROUP_A, "a-1"),
            "b-1" to profile(GROUP_B, "b-1"),
        )
        private val dataSource = mock<MainDataSource>()

        val viewModel: MainViewModel

        init {
            whenever(dataSource.mainServiceEvent).thenReturn(emptyFlow())
            whenever(dataSource.getSelectedSubscriptionId()).thenReturn(GROUP_A)
            whenever(dataSource.getSelectServer()).thenReturn(null)
            whenever(dataSource.getConfirmRemove()).thenReturn(false)
            whenever(dataSource.getDoubleColumnDisplay()).thenReturn(false)
            whenever(dataSource.getSubscriptions()).thenReturn(
                listOf(
                    SubscriptionCache(ALL, SubscriptionItem(remarks = "All")),
                    SubscriptionCache(GROUP_A, SubscriptionItem(remarks = "Group A")),
                    SubscriptionCache(GROUP_B, SubscriptionItem(remarks = "Group B")),
                )
            )
            whenever(dataSource.getServerGuidList(any())).thenAnswer { invocation ->
                val groupId = invocation.getArgument<String>(0)
                if (groupId == ALL) profiles.keys.toList()
                else groupGuids[groupId]?.toList().orEmpty()
            }
            whenever(dataSource.decodeServerConfig(any())).thenAnswer { invocation ->
                profiles[invocation.getArgument<String>(0)]
            }
            whenever(dataSource.getSubscriptionItem(any())).thenAnswer { invocation ->
                SubscriptionItem(remarks = invocation.getArgument<String>(0))
            }

            viewModel = MainViewModel(
                application = mock<Application>(),
                dataSource = dataSource,
                serviceEventDispatcher = Dispatchers.Unconfined,
            )
        }

        suspend fun loadInitialState() {
            viewModel.setupGroupTab(forceRefresh = true).join()
            viewModel.populateSubscriptionGroupData(listOf(GROUP_A, GROUP_B))
        }

        fun addProfile(groupId: String, guid: String) {
            groupGuids.getValue(groupId) += guid
            profiles[guid] = profile(groupId, guid)
        }
    }

    companion object {
        private const val ALL = ""
        private const val GROUP_A = "group-a"
        private const val GROUP_B = "group-b"

        private fun profile(groupId: String, remarks: String) = ProfileItem(
            configType = EConfigType.VMESS,
            subscriptionId = groupId,
            remarks = remarks,
            description = "description",
        )
    }
}
