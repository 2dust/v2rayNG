package com.v2ray.ang.dto.entities

/**
 * Account information a subscription panel (3x-ui, Marzban, Remnawave, ...) publishes in the
 * HTTP response headers of the subscription URL. Every field is optional because panels send
 * only the headers they have a value for.
 *
 * Persisted as JSON inside [SubscriptionItem]; only add fields, never rename or remove one.
 */
data class SubscriptionUsageInfo(
    /** Bytes uploaded, from the `Subscription-Userinfo` header. */
    val upload: Long = 0,
    /** Bytes downloaded, from the `Subscription-Userinfo` header. */
    val download: Long = 0,
    /** Traffic quota in bytes; 0 means unlimited or not provided. */
    val total: Long = 0,
    /** Expiry as Unix epoch seconds; 0 means no expiry or not provided. */
    val expire: Long = 0,
    /** Decoded `Profile-Title` header. */
    val title: String? = null,
    /** Validated `Support-Url` header (usually a Telegram link). */
    val supportUrl: String? = null,
    /** Validated `Profile-Web-Page-Url` header. */
    val webPageUrl: String? = null,
) {
    /** Bytes consumed so far (upload + download). */
    val used: Long get() = upload + download
}
