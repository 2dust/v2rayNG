package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Test

class MainImportMenuTest {

    @Test
    fun accessibilityActionsPutManagementBeforeShareVariants() {
        assertEquals(
            listOf(
                ServerMenuAction.Edit,
                ServerMenuAction.Delete,
                ServerMenuAction.ShareQRCode,
                ServerMenuAction.ShareClipboard,
                ServerMenuAction.ShareFullContent,
            ),
            serverAccessibilityActions(isComplexProfile = false),
        )
    }

    @Test
    fun complexAccessibilityActionsKeepOnlySupportedShareVariant() {
        assertEquals(
            listOf(ServerMenuAction.Edit, ServerMenuAction.Delete, ServerMenuAction.ShareFullContent),
            serverAccessibilityActions(isComplexProfile = true),
        )
    }

    @Test
    fun actionsUseExistingDispatchAndDeleteConfirmationPath() {
        val guid = "server-guid"
        val profile = ProfileItem(configType = EConfigType.VMESS, remarks = "Example")
        val dispatched = mutableListOf<MainAction>()
        val removalRequests = mutableListOf<Pair<String, String>>()
        serverAccessibilityActions(isComplexProfile = false).forEach {
            it.perform(guid, profile, dispatched::add) { id, name -> removalRequests.add(id to name) }
        }
        assertEquals(
            listOf(
                MainAction.EditServer(guid, profile),
                MainAction.ShareQRCode(guid),
                MainAction.ShareClipboard(guid),
                MainAction.ShareFullContent(guid),
            ),
            dispatched,
        )
        assertEquals(listOf(guid to profile.remarks), removalRequests)
    }

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = false, includeManagementActions = false))
    }

    @Test
    fun regularMoreMenuContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true),
        )
    }

    @Test
    fun complexShareMenuContainsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = true, includeManagementActions = true))
    }
}
