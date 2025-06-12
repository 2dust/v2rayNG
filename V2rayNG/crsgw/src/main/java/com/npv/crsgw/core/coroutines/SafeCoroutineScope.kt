package com.npv.crsgw.core.coroutines

import com.npv.crsgw.core.coroutines.exception.UncaughtCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

class SafeCoroutineScope(context: CoroutineContext, errorHandler: coroutineErrorListener?=null) : CoroutineScope,
    Closeable {

    override val coroutineContext: CoroutineContext = SupervisorJob() + context + UncaughtCoroutineExceptionHandler(errorHandler)

    override fun close() {
        coroutineContext.cancelChildren()
    }
}