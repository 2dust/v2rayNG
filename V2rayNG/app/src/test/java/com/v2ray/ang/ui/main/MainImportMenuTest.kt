package com.v2ray.ang.ui.main

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
