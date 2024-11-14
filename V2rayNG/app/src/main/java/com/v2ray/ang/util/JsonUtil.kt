package com.v2ray.ang.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object JsonUtil {
    private var gson = Gson()

    fun toJson(src: Any?): String {
        return gson.toJson(src)
    }

    fun <T> fromJson(json: String, cls: Class<T>): T {
        return gson.fromJson(json, cls)
    }

    fun toJsonPretty(src: Any?): String {
        val gsonPre = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter( // custom serializer is needed here since JSON by default parse number as Double, core will fail to start
                object : TypeToken<Double>() {}.type,
                JsonSerializer { src: Double?, _: Type?, _: JsonSerializationContext? ->
                    JsonPrimitive(
                        src?.toInt()
                    )
                }
            )
            .create()
        return gsonPre.toJson(src)
    }
}