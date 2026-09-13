package com.v2ray.ang.ui.compose

import com.v2ray.ang.dto.UserMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSnackbarManagerTest {
    @Test
    fun messagesGoToOneResumedHostAndFallBackAfterItLeaves() {
        val first = mutableListOf<UserMessage>()
        val second = mutableListOf<UserMessage>()
        val firstHost: (UserMessage) -> Unit = { first.add(it) }
        val secondHost: (UserMessage) -> Unit = { second.add(it) }
        val message = UserMessage("Saved")
        assertFalse(AppSnackbarManager.show(message))
        try {
            AppSnackbarManager.register(firstHost)
            AppSnackbarManager.register(secondHost)
            assertTrue(AppSnackbarManager.show(message))
            assertEquals(emptyList<UserMessage>(), first)
            assertEquals(listOf(message), second)

            AppSnackbarManager.unregister(secondHost)
            assertTrue(AppSnackbarManager.show(message))
            assertEquals(listOf(message), first)
        } finally {
            AppSnackbarManager.unregister(firstHost)
            AppSnackbarManager.unregister(secondHost)
        }
        assertFalse(AppSnackbarManager.show(message))
    }
}
