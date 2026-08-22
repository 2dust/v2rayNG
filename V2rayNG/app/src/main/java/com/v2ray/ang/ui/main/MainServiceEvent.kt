package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.RealPingProgress
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.RealPingSummary

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateStartSuccess : MainServiceEvent()
    data object StateStartFailure : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelayResult(val result: ConnectionTestResult) : MainServiceEvent()
    data class MeasureConfigSuccess(val result: RealPingResult) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: RealPingProgress) : MainServiceEvent()
    data class MeasureConfigFinish(val summary: RealPingSummary) : MainServiceEvent()
}
