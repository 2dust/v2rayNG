package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.RealPingResult

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateStartSuccess : MainServiceEvent()
    data class StateStartFailure(val message: String? = null) : MainServiceEvent()
    data class StateConnecting(val message: String) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelayResult(val result: ConnectionTestResult, val requestId: String) : MainServiceEvent()
    data class MeasureDelayCancelled(val requestId: String) : MainServiceEvent()
    data class MeasureConfigSuccess(val result: RealPingResult, val requestId: String) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: String, val requestId: String) : MainServiceEvent()
    data class MeasureConfigFinish(val requestId: String) : MainServiceEvent()
    data class MeasureConfigCancelled(val requestId: String) : MainServiceEvent()

    /** Servers, subscriptions or test results changed away from this screen. */
    data object ServersChanged : MainServiceEvent()

    companion object {

        /**
         * What the outcome of a state query means. A service that [acknowledged] the query reports
         * its state with an event of its own, so nothing is added; a query nobody acknowledged found
         * no service, whatever the screen was told last, and that is the state.
         */
        fun forStateQuery(acknowledged: Boolean): MainServiceEvent? = if (acknowledged) null else StateNotRunning
    }
}
