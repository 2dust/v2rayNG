package com.v2ray.ang.core

/** Only a non-secret phase is retained from native output; raw native lines are never logged. */
enum class AetherStartupStage {
    CORE, REGISTRATION, SCAN, TRANSPORT;

    fun observe(line: String): AetherStartupStage {
        val text = line.lowercase()
        return when {
            "identity ready" in text -> SCAN
            "handshake" in text || "establishing tunnel" in text -> TRANSPORT
            "register" in text || "provision" in text || "enroll" in text -> REGISTRATION
            "scanning" in text || "scan mode" in text || "selected peer" in text -> SCAN
            else -> this
        }
    }
}

class AetherStartupException(val stage: AetherStartupStage, val exitCode: Int? = null) :
    IllegalStateException("Aether startup stage=${stage.name}, exitCode=${exitCode ?: "timeout"}")
