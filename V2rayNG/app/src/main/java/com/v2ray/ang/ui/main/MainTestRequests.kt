package com.v2ray.ang.ui.main

import java.util.UUID

/** Main-thread request ownership; a late reply must not complete a newer test. */
internal class MainTestRequests {
    data class Bulk(val id: String, val groupId: String)

    private var current: String? = null
    var bulk: Bulk? = null
        private set
    val isTesting: Boolean get() = current != null || bulk != null

    fun beginCurrent(): String = UUID.randomUUID().toString().also { current = it }

    fun acceptCurrentResult(id: String, isFinal: Boolean): Boolean {
        if (id != current) return false
        if (isFinal) current = null
        return true
    }

    fun completeCurrent(id: String): Boolean = acceptCurrentResult(id, isFinal = true)

    fun invalidateCurrent() {
        current = null
    }

    fun beginBulk(groupId: String): Bulk = Bulk(UUID.randomUUID().toString(), groupId).also { bulk = it }

    fun completeBulk(id: String): Bulk? {
        val request = bulk?.takeIf { it.id == id } ?: return null
        bulk = null
        return request
    }

    fun cancelBulk() {
        bulk = null
    }
}
