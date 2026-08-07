package com.v2ray.ang.dto

data class ConfigResult(
    var status: Boolean,
    var guid: String? = null,
    var content: String = "",
    /** Stable English diagnostic text suitable for Logcat. */
    var errorMessage: String = "",
    /** Localized message suitable for the UI. */
    var displayMessage: String = "",
)

