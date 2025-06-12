package com.npv.crsgw.core.coroutines.exception

import com.npv.crsgw.core.coroutines.coroutineErrorListener
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class UncaughtCoroutineExceptionHandler(private val errorHandler: coroutineErrorListener?=null)  :
    CoroutineExceptionHandler, AbstractCoroutineContextElement(CoroutineExceptionHandler.Key) {

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        exception.printStackTrace()

        errorHandler?.let {
            it(exception)
        }
    }
}