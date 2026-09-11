package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    // Snapshot supplied by the daemon; current selection may already refer to another server.
    data class StateStartSuccess(val serverName: String) : MainServiceEvent()
    data object StateStartFailure : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelayResult(val result: ConnectionTestResult, val requestId: String) : MainServiceEvent()
    data class MeasureDelayCancelled(val requestId: String) : MainServiceEvent()
    data class MeasureConfigSuccess(val requestId: String) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: String, val requestId: String) : MainServiceEvent()
    data class MeasureConfigFinish(val requestId: String) : MainServiceEvent()
    data class MeasureConfigCancelled(val requestId: String) : MainServiceEvent()
}
