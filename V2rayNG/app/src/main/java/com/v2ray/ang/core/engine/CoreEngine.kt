package com.v2ray.ang.core.engine

import android.content.Context

interface CoreEngine {
    val type: CoreType
    val isRunning: Boolean
    val supportedCapabilities: Set<CoreCapability>

    fun initCoreEnv(context: Context?)

    fun startLoop(configContent: String, tunFd: Int)

    fun stopLoop()

    fun queryStats(tag: String, link: String): Long

    fun measureDelay(testUrl: String): Long

    fun registerProcessFinder(processFinder: AppProcessFinder)
}
