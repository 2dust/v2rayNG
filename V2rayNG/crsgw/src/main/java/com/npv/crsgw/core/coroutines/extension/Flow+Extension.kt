package com.npv.crsgw.core.coroutines.extension

import com.npv.crsgw.core.coroutines.action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun <T> emitFlow(block: action<T>): Flow<T> =
    flow {
        emit(block())
    }

suspend fun <T> Flow<T>.toSuspend(): T {
    val scope = CoroutineScope(coroutineContext)
    return suspendCancellableCoroutine<T> { continuation ->
        catch { continuation.resumeWithException(it) }
            .onEach { continuation.resume(it) }
            .launchIn(scope)
    }
}

fun <T> Flow<T>.onCompleted(action: () -> Unit) = flow {

    collect { value -> emit(value) }

    action()
}

suspend fun <T> Flow<T>.awaitFirst(): T? {
    var t: T? = null
    take(1).collect { t = it }
    return t
}

fun <T> mergeFlows(vararg flows: Flow<T>): Flow<T> = channelFlow {
    coroutineScope {
        for (f in flows) {
            launch {
                f.collect { channel.send(it) }
            }
        }
    }
}

//fun <T> flowError(block: () -> Throwable) = flow<T> { throw block() }

//fun <T> Flow<T>.resumeOnError(errorBlock: () -> Throwable): Flow<T> = flatMapLatest {
//    flowError<T> {
//        errorBlock()
//    }
//}