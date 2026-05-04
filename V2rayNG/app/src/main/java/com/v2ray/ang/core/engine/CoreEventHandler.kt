package com.v2ray.ang.core.engine

interface CoreEventHandler {
    fun startup(): Long

    fun shutdown(): Long

    fun onEmitStatus(statusCode: Long, message: String?): Long
}
