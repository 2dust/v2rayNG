package com.v2ray.ang.dto

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

data class Contributor(
    val name: String,
    val displayName: String? = null,
    val url: String? = null
)

data class TranslatorsCredit(
    val language: String,
    val contributors: List<Contributor>
)

object TranslatorsParser {

    private val gson = Gson()

    fun parse(json: String): List<TranslatorsCredit> {
        if (json.isBlank()) return emptyList()

        return try {
            val type = object : TypeToken<List<TranslatorsCredit>>() {}.type
            gson.fromJson<List<TranslatorsCredit>>(json, type).orEmpty()
                .filter { it.language.isNotBlank() && it.contributors.isNotEmpty() }
                .distinctBy { it.language }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse translators.json", e)
            emptyList()
        }
    }
}