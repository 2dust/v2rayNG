package com.v2ray.ang.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserMessageTest {
    @Test
    fun ordinaryInformationAlwaysUsesATimeout() {
        for (type in listOf(UserMessage.Type.NORMAL, UserMessage.Type.SUCCESS, UserMessage.Type.INFO)) {
            assertFalse(UserMessage("Updated", type).requiresDismissal)
            assertFalse(UserMessage("Message\n".repeat(100), type).requiresDismissal)
        }
    }

    @Test
    fun onlyLongOrMultilineErrorsRequireDismissal() {
        assertFalse(UserMessage("", type = UserMessage.Type.ERROR).requiresDismissal)
        assertFalse(UserMessage("x".repeat(120), type = UserMessage.Type.ERROR).requiresDismissal)
        assertTrue(UserMessage("x".repeat(121), type = UserMessage.Type.ERROR).requiresDismissal)
        assertTrue(UserMessage("Error\nDetails", type = UserMessage.Type.ERROR).requiresDismissal)
        assertTrue(UserMessage("Error\rDetails", type = UserMessage.Type.ERROR).requiresDismissal)
    }

    @Test
    fun supplementaryCharactersCountAsOneCharacter() {
        assertFalse(UserMessage("\uD83D\uDEAB".repeat(120), type = UserMessage.Type.ERROR).requiresDismissal)
        assertTrue(UserMessage("\uD83D\uDEAB".repeat(121), type = UserMessage.Type.ERROR).requiresDismissal)
    }
}
