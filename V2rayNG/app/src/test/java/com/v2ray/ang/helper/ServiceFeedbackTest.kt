package com.v2ray.ang.helper

import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceFeedbackTest {
    @Test
    fun terminalEventsUseTheSameMessagesForForegroundAndBackgroundDelivery() {
        assertEquals(R.string.toast_services_success, MessageHelper.serviceMessageResource(AppConfig.MSG_STATE_START_SUCCESS))
        assertEquals(R.string.toast_services_failure, MessageHelper.serviceMessageResource(AppConfig.MSG_STATE_START_FAILURE))
        assertEquals(R.string.toast_services_stop, MessageHelper.serviceMessageResource(AppConfig.MSG_STATE_STOP_SUCCESS))
    }

    @Test
    fun stateQueriesAndProgressDoNotProduceTerminalFeedback() {
        listOf(
            AppConfig.MSG_STATE_RUNNING,
            AppConfig.MSG_STATE_NOT_RUNNING,
            AppConfig.MSG_STATE_START,
            AppConfig.MSG_STATE_STOP,
            AppConfig.MSG_MEASURE_CONFIG_NOTIFY,
            AppConfig.MSG_MEASURE_DELAY_RESULT,
        ).forEach { assertNull(MessageHelper.serviceMessageResource(it)) }
    }
}
