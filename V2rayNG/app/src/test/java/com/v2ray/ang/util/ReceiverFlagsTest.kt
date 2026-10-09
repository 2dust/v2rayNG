package com.v2ray.ang.util

import androidx.core.content.ContextCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReceiverFlagsTest {
    // The app's receivers stop and restart the VPN; an exported one would let any other app do that.
    @Test
    fun theAppsReceiversAreNotExported() {
        assertEquals(ContextCompat.RECEIVER_NOT_EXPORTED, Utils.receiverFlags())
    }
}
