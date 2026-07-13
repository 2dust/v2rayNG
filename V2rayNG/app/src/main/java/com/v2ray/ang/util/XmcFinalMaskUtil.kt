package com.v2ray.ang.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Helpers for editing an XMC TCP mask without discarding other FinalMask settings. */
object XmcFinalMaskUtil {
    private const val TYPE_XMC = "xmc"

    data class Settings(
        val hostname: String = "",
        val usernames: List<String> = emptyList(),
        val password: String = "",
    )

    fun extract(finalMask: String?): Settings? {
        val root = parseObject(finalMask) ?: return null
        val tcp = root.get("tcp")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null

        tcp.forEach { element ->
            val mask = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (mask.stringValue("type") != TYPE_XMC) return@forEach

            val settings = mask.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
            return Settings(
                hostname = settings?.stringValue("hostname").orEmpty(),
                usernames = settings?.usernameValues().orEmpty(),
                password = settings?.stringValue("password").orEmpty(),
            )
        }

        return null
    }

    /**
     * Replaces all XMC entries while retaining every other FinalMask field and mask in order.
     * Passing null removes XMC. Invalid input is returned unchanged so callers never lose raw data.
     */
    fun update(finalMask: String?, settings: Settings?): String? {
        val root = if (finalMask.isNullOrBlank()) {
            JsonObject()
        } else {
            parseObject(finalMask) ?: return finalMask
        }

        val currentTcp = root.get("tcp")?.takeIf { it.isJsonArray }?.asJsonArray
        val updatedTcp = JsonArray()
        var foundXmc = false

        currentTcp?.forEach { element ->
            val mask = element.takeIf { it.isJsonObject }?.asJsonObject
            val isXmc = mask?.stringValue("type") == TYPE_XMC
            if (!isXmc) {
                updatedTcp.add(element)
            } else if (!foundXmc) {
                foundXmc = true
                settings?.let { updatedTcp.add(createMask(it)) }
            }
        }

        if (!foundXmc && settings != null) {
            updatedTcp.add(createMask(settings))
        }

        if (updatedTcp.size() == 0) {
            root.remove("tcp")
        } else {
            root.add("tcp", updatedTcp)
        }

        return root.takeUnless { it.size() == 0 }?.toString()
    }

    private fun createMask(settings: Settings): JsonObject {
        val xmcSettings = JsonObject().apply {
            settings.hostname.trim().takeIf { it.isNotEmpty() }?.let {
                addProperty("hostname", it)
            }

            val normalizedUsernames = settings.usernames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (normalizedUsernames.isNotEmpty()) {
                add("usernames", JsonArray().apply {
                    normalizedUsernames.forEach(::add)
                })
            }

            addProperty("password", settings.password)
        }

        return JsonObject().apply {
            addProperty("type", TYPE_XMC)
            add("settings", xmcSettings)
        }
    }

    private fun parseObject(value: String?): JsonObject? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            JsonParser.parseString(value).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull()
    }

    private fun JsonObject.stringValue(key: String): String? {
        return get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }

    private fun JsonObject.usernameValues(): List<String> {
        val value = get("usernames") ?: return emptyList()
        return when {
            value.isJsonArray -> value.asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            }

            value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            else -> emptyList()
        }
    }
}
