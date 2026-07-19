package com.v2ray.ang.ui.main

sealed class ServiceEvent {
    data object StateRunning : ServiceEvent()
    data object StateNotRunning : ServiceEvent()
    data object StateStartSuccess : ServiceEvent()
    data class StateStartFailure(val errorMessage: String) : ServiceEvent()
    data object StateStopSuccess : ServiceEvent()
    data class MeasureDelaySuccess(val content: String) : ServiceEvent()
    data object MeasureConfigSuccess : ServiceEvent()
    data class MeasureConfigNotify(val progress: String) : ServiceEvent()
    data class MeasureConfigFinish(val finishedCount: String?) : ServiceEvent()
}
