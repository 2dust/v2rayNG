package com.v2ray.ang.core.engine

import android.content.Context
import com.v2ray.ang.core.CoreNativeManager
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder

class XrayCoreEngine(
    private val eventHandler: CoreEventHandler,
) : CoreEngine {
    override val type = CoreType.XRAY
    override val supportedCapabilities = setOf(
        CoreCapability.TUN,
        CoreCapability.PER_APP_PROXY,
        CoreCapability.RULE_SET,
        CoreCapability.PROXY_CHAIN,
        CoreCapability.REAL_DELAY,
        CoreCapability.DNS_MODULE,
    )

    private val coreController: CoreController = CoreNativeManager.newCoreController(object : CoreCallbackHandler {
        override fun startup(): Long = eventHandler.startup()

        override fun shutdown(): Long = eventHandler.shutdown()

        override fun onEmitStatus(statusCode: Long, message: String?): Long {
            return eventHandler.onEmitStatus(statusCode, message)
        }
    })

    override val isRunning: Boolean
        get() = coreController.isRunning

    override fun initCoreEnv(context: Context?) {
        CoreNativeManager.initCoreEnv(context)
    }

    override fun startLoop(configContent: String, tunFd: Int) {
        coreController.startLoop(configContent, tunFd)
    }

    override fun stopLoop() {
        coreController.stopLoop()
    }

    override fun queryStats(tag: String, link: String): Long {
        return coreController.queryStats(tag, link)
    }

    override fun measureDelay(testUrl: String): Long {
        return coreController.measureDelay(testUrl)
    }

    override fun registerProcessFinder(processFinder: AppProcessFinder) {
        coreController.registerProcessFinder(object : ProcessFinder {
            override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
                return processFinder.findProcessByConnection(network, srcIP, srcPort, destIP, destPort)
            }
        })
    }
}
