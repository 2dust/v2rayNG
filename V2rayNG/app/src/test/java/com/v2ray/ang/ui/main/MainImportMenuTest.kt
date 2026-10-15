package com.v2ray.ang.ui.main

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainImportMenuTest {

    @Test
    fun shareMenuForSimpleProfileListsEveryShareAction() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(expected, serverMenuActions(configType = EConfigType.VLESS, includeManagementActions = false))
    }

    @Test
    fun moreMenuForSimpleProfileContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(configType = EConfigType.VLESS, includeManagementActions = true),
        )
    }

    @Test
    fun shareMenuForGroupProfilesKeepsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(configType = EConfigType.POLICYGROUP, includeManagementActions = false),
        )
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(configType = EConfigType.PROXYCHAIN, includeManagementActions = false),
        )
    }

    @Test
    fun shareMenuForCustomConfigKeepsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(configType = EConfigType.CUSTOM, includeManagementActions = false),
        )
    }

    @Test
    fun shareMenuForCustomOutboundListsEveryShareAction() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(expected, serverMenuActions(configType = EConfigType.CUSTOM_OUTBOUND, includeManagementActions = false))
    }

    @Test
    fun moreMenuForCustomOutboundContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(configType = EConfigType.CUSTOM_OUTBOUND, includeManagementActions = true),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(configType = EConfigType.POLICYGROUP, includeManagementActions = true),
        )
    }
}
