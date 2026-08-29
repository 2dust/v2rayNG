package com.v2ray.ang.extension

import com.v2ray.ang.ui.compose.AppSnackbarMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientMessageDeliveryTest {

    private val event = AppSnackbarMessage(message = "Message")

    @Test
    fun `foreground delivery does not also post a notification`() {
        var backgroundCalled = false

        val result = deliverTransientMessage(
            event = event,
            foregroundDelivery = { true },
            backgroundDelivery = {
                backgroundCalled = true
                true
            }
        )

        assertEquals(TransientMessageDelivery.FOREGROUND_SNACKBAR, result)
        assertFalse(backgroundCalled)
    }

    @Test
    fun `background notification is used without an active snackbar host`() {
        var backgroundCalled = false

        val result = deliverTransientMessage(
            event = event,
            foregroundDelivery = { false },
            backgroundDelivery = {
                backgroundCalled = true
                true
            }
        )

        assertEquals(TransientMessageDelivery.BACKGROUND_NOTIFICATION, result)
        assertTrue(backgroundCalled)
    }

    @Test
    fun `delivery is unavailable when notifications cannot be posted`() {
        val result = deliverTransientMessage(
            event = event,
            foregroundDelivery = { false },
            backgroundDelivery = { false }
        )

        assertEquals(TransientMessageDelivery.UNAVAILABLE, result)
    }
}
