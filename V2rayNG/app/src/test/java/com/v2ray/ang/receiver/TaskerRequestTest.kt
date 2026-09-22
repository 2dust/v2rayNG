package com.v2ray.ang.receiver

import com.v2ray.ang.AppConfig
import org.junit.Assert.*
import org.junit.Test

class TaskerRequestTest {
    @Test fun missingOrEmptyFieldsNeverBecomeStopCommands() {
        assertNull(TaskerRequest.parse(null, AppConfig.TASKER_DEFAULT_GUID))
        for (guid in listOf(null, "", " ")) {
            assertNull(TaskerRequest.parse(true, guid))
            assertNull(TaskerRequest.parse(false, guid))
        }
    }

    @Test fun existingDefaultAndProfileContractsRemainReadable() {
        assertEquals(TaskerRequest(true, "Default"), TaskerRequest.parse(true, "Default"))
        assertEquals(TaskerRequest(false, "Default"), TaskerRequest.parse(false, "Default"))
        assertEquals(TaskerRequest(true, "profile-guid"), TaskerRequest.parse(true, "profile-guid"))
    }
}
