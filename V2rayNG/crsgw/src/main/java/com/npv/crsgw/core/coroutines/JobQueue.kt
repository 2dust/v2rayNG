package com.npv.crsgw.core.coroutines

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class JobQueue {

    private var scope = CoroutineScope(Dispatchers.IO)
    private var queue = MutableSharedFlow<Job>(extraBufferCapacity = Int.MAX_VALUE)

    init {
        initQueue()
    }

    private fun initQueue() {
        queue.onEach { it.join() }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    fun submit(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ) {
        val job = scope.launch(context, CoroutineStart.LAZY, block)
        queue.tryEmit(job)
    }

    fun cancel() {
        Log.i("JobQueue", "cancel")
        scope.cancel()
    }

    fun toActive(): Boolean {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO)
            queue = MutableSharedFlow<Job>(extraBufferCapacity = Int.MAX_VALUE)
            initQueue()
        }
        return scope.isActive
    }

    fun resetActive() {
        if (scope.isActive) {
            cancel()
            scope = CoroutineScope(Dispatchers.IO)
            queue = MutableSharedFlow<Job>(extraBufferCapacity = Int.MAX_VALUE)
            initQueue()
        }
    }

}