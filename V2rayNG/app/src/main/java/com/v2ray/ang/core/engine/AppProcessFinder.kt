package com.v2ray.ang.core.engine

interface AppProcessFinder {
    fun findProcessByConnection(
        network: String,
        srcIP: String,
        srcPort: Long,
        destIP: String,
        destPort: Long,
    ): Long
}
