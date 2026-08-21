package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateRestarting : MainServiceEvent()
    data object StateStartSuccess : MainServiceEvent()
    data object StateStartFailure : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelayResult(val result: ConnectionTestResult) : MainServiceEvent()
    data object MeasureConfigSuccess : MainServiceEvent()
    data class MeasureConfigNotify(val progress: String) : MainServiceEvent()
    data class MeasureConfigFinish(val finishedCount: String?) : MainServiceEvent()
}
