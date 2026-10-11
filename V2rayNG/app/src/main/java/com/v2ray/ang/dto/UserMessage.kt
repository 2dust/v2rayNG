package com.v2ray.ang.dto

/** Feedback shared by the foreground snackbar and background notification paths. */
data class UserMessage(val text: String, val type: Type = Type.NORMAL, val long: Boolean = false) {
    enum class Type { NORMAL, SUCCESS, ERROR, INFO }

    // A conservative reading-length policy, independent of the current screen or process.
    val requiresDismissal: Boolean =
        type == Type.ERROR && (text.codePointCount(0, text.length) > 120 || '\n' in text || '\r' in text)
}
