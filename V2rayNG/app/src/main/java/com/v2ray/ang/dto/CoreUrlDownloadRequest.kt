package com.v2ray.ang.dto

import java.io.Serializable

data class CoreUrlDownloadRequest(
    val requestId: String,
    val url: String,
    val headersJson: String,
    val timeoutMillis: Long,
) : Serializable {
    companion object {
        const val EXTRA_RESULT_RECEIVER = "core_url_result_receiver"
    }
}
