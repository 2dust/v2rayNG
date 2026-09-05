package com.v2ray.ang.repository

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

abstract class BaseRepository(
    protected val io: CoroutineDispatcher = Dispatchers.IO
) {
    protected suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T =
        withContext(io, block)

    protected fun <T> Flow<T>.flowIO(): Flow<T> = flowOn(io)

    protected suspend fun <T> runIO(fallback: T, block: suspend CoroutineScope.() -> T): T =
        try {
            withContext(io, block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "${javaClass.simpleName} failed", e)
            fallback
        }
}
