package com.v2ray.ang.core.engine

object CoreEngineFactory {
    fun create(coreType: CoreType, eventHandler: CoreEventHandler): CoreEngine {
        return when (coreType) {
            CoreType.XRAY -> XrayCoreEngine(eventHandler)
            CoreType.SING_BOX -> throw UnsupportedOperationException("Sing-box engine is not implemented yet")
        }
    }
}
