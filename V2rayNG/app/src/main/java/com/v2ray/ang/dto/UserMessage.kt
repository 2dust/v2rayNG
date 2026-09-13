package com.v2ray.ang.dto

/** Feedback shared by the foreground snackbar and background notification paths. */
data class UserMessage(val text: String, val isError: Boolean = false) {
    // A conservative reading-length policy, independent of the current screen or process.
    val requiresDismissal: Boolean =
        isError && (text.codePointCount(0, text.length) > 120 || '\n' in text || '\r' in text)
}
