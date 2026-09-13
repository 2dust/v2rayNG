package com.v2ray.ang.dto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessageTest {
    @Test
    fun ordinaryInformationAlwaysUsesATimeout() {
        assertFalse(UserMessage("Updated").requiresDismissal)
        assertFalse(UserMessage("Message\n".repeat(100)).requiresDismissal)
    }

    @Test
    fun onlyLongOrMultilineErrorsRequireDismissal() {
        assertFalse(UserMessage("", isError = true).requiresDismissal)
        assertFalse(UserMessage("x".repeat(120), isError = true).requiresDismissal)
        assertTrue(UserMessage("x".repeat(121), isError = true).requiresDismissal)
        assertTrue(UserMessage("Error\nDetails", isError = true).requiresDismissal)
        assertTrue(UserMessage("Error\rDetails", isError = true).requiresDismissal)
    }

    @Test
    fun supplementaryCharactersCountAsOneCharacter() {
        assertFalse(UserMessage("\uD83D\uDEAB".repeat(120), isError = true).requiresDismissal)
        assertTrue(UserMessage("\uD83D\uDEAB".repeat(121), isError = true).requiresDismissal)
    }
}
