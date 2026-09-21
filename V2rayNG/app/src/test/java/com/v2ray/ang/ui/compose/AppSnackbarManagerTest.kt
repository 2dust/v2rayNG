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
    fun explicitLongDurationExtendsTransientFeedbackWithoutRequiringDismissal() {
        manager.setForeground(true)
        for (isError in listOf(false, true)) {
            manager.show(UserMessage("Brief message", isError = isError, long = true))
            val snackbar = manager.hostState.currentSnackbarData!!
            assertEquals(SnackbarDuration.Long, snackbar.visuals.duration)
            assertNull(snackbar.visuals.actionLabel)
            snackbar.dismiss()
        }
    }

    @Test
    fun lengthyErrorsRequireDismissalRegardlessOfExplicitDuration() {
        manager.setForeground(true)
        for (long in listOf(false, true)) {
            manager.show(UserMessage("Error\nDetails", isError = true, long = long))
            val snackbar = manager.hostState.currentSnackbarData!!
            assertEquals(SnackbarDuration.Indefinite, snackbar.visuals.duration)
            assertEquals("Close", snackbar.visuals.actionLabel)
            snackbar.performAction()
        }
    }

    @Test
    fun navigationDoesNotReplayShownLongDurationFeedbackEither() {
        manager.setForeground(true)
        manager.register("updates")
        manager.show(UserMessage("Update available", long = true))
        manager.onPresented("updates", manager.hostState.currentSnackbarData!!)
        manager.unregister("updates")
        manager.register("main")

        assertNull(manager.hostState.currentSnackbarData)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun closingAnEditorTransfersItsErrorAndQueuedConfirmationToTheNextHost() {
        manager.setForeground(true)
        val editor = "editor"
        val main = "main"
        manager.register(editor)
        manager.show(UserMessage("Long error\ndetails", isError = true))
        manager.show(UserMessage("Saved"))
        val error = manager.hostState.currentSnackbarData!!
        assertEquals(SnackbarDuration.Indefinite, error.visuals.duration)
        assertEquals("Close", error.visuals.actionLabel)
        manager.onPresented(editor, error)

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
    fun navigationDismissesShownTransientFeedbackWithoutDroppingTheQueue() {
        manager.setForeground(true)
        manager.register("updates")
        manager.show(UserMessage("Checking for update"))
        manager.onPresented("updates", manager.hostState.currentSnackbarData!!)
        manager.show(UserMessage("Finished"))

        manager.unregister("updates")
        manager.register("main")

        assertEquals("Finished", manager.hostState.currentSnackbarData?.visuals?.message)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun finishingBeforePresentationKeepsTheMessageForTheNextScreen() {
        manager.setForeground(true)
        manager.register("editor")
        manager.show(UserMessage("Saved"))
        val message = manager.hostState.currentSnackbarData

        manager.unregister("editor")
        manager.register("main")

        assertSame(message, manager.hostState.currentSnackbarData)
    }

    @Test
    fun recreationRetainsShownFeedbackWithTheRestoredHostIdentity() {
        manager.setForeground(true)
        manager.register("updates")
        manager.show(UserMessage("Checking for update"))
        val message = manager.hostState.currentSnackbarData!!
        manager.onPresented("updates", message)

        manager.unregister("updates")
        manager.register(charArrayOf('u', 'p', 'd', 'a', 't', 'e', 's').concatToString())

        assertSame(message, manager.hostState.currentSnackbarData)
    }

    @Test
    fun aNewMessageWithTheSameTextIsNotDeduplicated() {
        manager.setForeground(true)
        manager.register("updates")
        manager.show(UserMessage("Success"))
        manager.onPresented("updates", manager.hostState.currentSnackbarData!!)
        manager.unregister("updates")
        manager.register("main")
        assertNull(manager.hostState.currentSnackbarData)

        manager.show(UserMessage("Success"))
        val message = manager.hostState.currentSnackbarData!!
        manager.unregister("main")
        manager.register("editor")
        assertSame(message, manager.hostState.currentSnackbarData)
    }

    @Test
    fun anOutgoingHostCannotClaimTheNextMessage() {
        manager.setForeground(true)
        manager.register("updates")
        manager.show(UserMessage("First"))
        val first = manager.hostState.currentSnackbarData!!
        manager.onPresented("updates", first)
        manager.register("main")
        manager.show(UserMessage("Second"))
        val second = manager.hostState.currentSnackbarData!!
        manager.onPresented("updates", second)
        manager.onPresented("main", first)
        manager.unregister("updates")
        manager.unregister("main")
        manager.register("editor")

        assertSame(second, manager.hostState.currentSnackbarData)
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
        val first = "first"
        val second = "second"
        manager.register(first)
        manager.register(second)
        assertSame(second, manager.activeHost.value)
        manager.unregister(second)
        assertSame(first, manager.activeHost.value)
        manager.unregister(first)
        assertNull(manager.activeHost.value)
    }
}
