package com.npv.crsgw.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthEventBus {
    private val _authExpiredFlow = MutableSharedFlow<Unit>(replay = 0)
    val authExpiredFlow = _authExpiredFlow.asSharedFlow()

    suspend fun notifyAuthExpired() {
        _authExpiredFlow.emit(Unit)
    }
}