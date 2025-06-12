package com.npv.crsgw.core.coroutines.extension

import com.npv.crsgw.core.coroutines.uiScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun Job.safeCancel() {
    if (isActive) {
        cancel()
    }
}

fun cancelAllJobs(vararg jobs:Job) {

    jobs.forEach {
        it.safeCancel()
    }
}

inline fun Job.then(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.() -> Unit
): Job = uiScope().launch(context) {
    join()
    block()
}