package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerRowAccessibilityDescriptionTest {
    private val row = ServerRowUiModel(
        guid = "server-guid",
        profile = ProfileItem(configType = EConfigType.VMESS),
        remarks = "Example",
        statistics = "Description",
        typeDescription = "VMESS / tls",
        testDelayMillis = 21,
        subscriptionBadge = "l",
    )

    @Test
    fun ordinaryRowHasNoStatePrefixAndKeepsLocalizedDelay() {
        assertEquals(
            "Example. L. Description. VMESS / tls. 21 millisecondes",
            row.accessibilityDescription("21 millisecondes", prefix = null),
        )
    }

    @Test
    fun selectedRowStartsWithLocalizedPrefix() {
        assertEquals(
            "Выбрано. Example. L. Description. VMESS / tls. 21 миллисекунда",
            row.accessibilityDescription("21 миллисекунда", prefix = "Выбрано"),
        )
    }

    @Test
    fun absentDetailsDoNotCreateEmptyPhrases() {
        assertEquals(
            "Example",
            row.copy(subscriptionBadge = "", statistics = " ", typeDescription = "")
                .accessibilityDescription("", prefix = null),
        )
    }
}
