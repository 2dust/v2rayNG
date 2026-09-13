package com.v2ray.ang.ui.compose

import androidx.compose.material3.SnackbarDuration
import com.v2ray.ang.dto.UserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSnackbarManagerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val notifications = mutableListOf<UserMessage>()
    private var clears = 0
    private val manager = AppSnackbarManager(scope, { notifications.add(it) }, { clears++ }, { "Close" })

    @After
    fun close() = scope.cancel()

    @Test
    fun closingAnEditorTransfersItsErrorAndQueuedConfirmationToTheNextHost() {
        manager.setForeground(true)
        val editor = Any()
        val main = Any()
        manager.register(editor)
        manager.show(UserMessage("Long error\ndetails", isError = true))
        manager.show(UserMessage("Saved"))
        val error = manager.hostState.currentSnackbarData!!
        assertEquals(SnackbarDuration.Indefinite, error.visuals.duration)
        assertEquals("Close", error.visuals.actionLabel)

        manager.unregister(editor)
        assertNull(manager.activeHost.value)
        manager.register(main)
        assertSame(main, manager.activeHost.value)
        assertSame(error, manager.hostState.currentSnackbarData)
        assertTrue(notifications.isEmpty())

        error.performAction()
        assertEquals("Saved", manager.hostState.currentSnackbarData?.visuals?.message)
        assertEquals(SnackbarDuration.Short, manager.hostState.currentSnackbarData?.visuals?.duration)
        manager.hostState.currentSnackbarData!!.dismiss()
        assertNull(manager.hostState.currentSnackbarData)
    }

    @Test
    fun backgroundingTransfersEveryUnreadMessageAndCancelsTheHiddenQueue() {
        manager.setForeground(true)
        val first = UserMessage("First")
        val second = UserMessage("Second")
        manager.show(first)
        manager.show(second)
        manager.setForeground(false)

        assertEquals(listOf(first, second), notifications)
        assertNull(manager.hostState.currentSnackbarData)
        manager.setForeground(false)
        assertEquals(2, notifications.size)
        manager.setForeground(true)
        assertNull(manager.hostState.currentSnackbarData)
    }

    @Test
    fun completedMessagesAreNotReplayedWhenBackgrounded() {
        manager.setForeground(true)
        manager.show(UserMessage("Saved"))
        manager.hostState.currentSnackbarData!!.dismiss()
        manager.setForeground(false)
        assertTrue(notifications.isEmpty())
        assertEquals(1, clears)
    }

    @Test
    fun backgroundMessagesGoStraightToNotificationsAndBlanksAreIgnored() {
        assertFalse(manager.isForeground)
        manager.show(UserMessage(" \n"))
        val error = UserMessage("Error", isError = true)
        manager.show(error)
        assertEquals(listOf(error), notifications)
        assertNull(manager.hostState.currentSnackbarData)
        assertEquals(0, clears)
    }

    @Test
    fun onlyOneHostRendersDuringOverlappingActivityLifecycles() {
        val first = Any()
        val second = Any()
        manager.register(first)
        manager.register(second)
        assertSame(second, manager.activeHost.value)
        manager.unregister(second)
        assertSame(first, manager.activeHost.value)
        manager.unregister(first)
        assertNull(manager.activeHost.value)
    }
}
