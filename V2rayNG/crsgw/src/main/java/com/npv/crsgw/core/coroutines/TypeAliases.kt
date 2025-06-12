package com.npv.crsgw.core.coroutines

typealias action<T> = suspend () -> T

typealias mapper<T,R> = (T) -> R

typealias zipper<T1, T2, R> = (T1, T2) -> R

typealias coroutineErrorListener = (throwable: Throwable) -> Unit
