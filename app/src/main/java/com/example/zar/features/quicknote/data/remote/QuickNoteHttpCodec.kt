package com.example.zar.features.quicknote.data.remote

import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal object QuickNoteHttpCodec {
    fun encode(payload: Map<String, Any>): String = toJson(payload).toString()

    fun decode(status: Int, body: String): Any? {
        val raw = runCatching { fromJson(Json.parseToJsonElement(body)) }.getOrNull()
        if (status in 200..299) return raw ?: throw QuickNoteDraftError.InvalidResponse()
        val error = (raw as? Map<*, *>)?.get("error") as? Map<*, *>
        val reason = error?.get("reason") as? String
        val code = when (status) {
            401 -> "UNAUTHENTICATED"
            403 -> "PERMISSION_DENIED"
            400, 413, 415 -> "INVALID_ARGUMENT"
            429 -> "RESOURCE_EXHAUSTED"
            408, 504 -> "DEADLINE_EXCEEDED"
            else -> "INTERNAL"
        }
        throw CallableErrorMapper.map(code, reason)
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
        is List<*> -> JsonArray(value.map(::toJson))
        else -> error("Unsupported quick-note field")
    }

    private fun fromJson(value: JsonElement): Any? = when (value) {
        JsonNull -> null
        is JsonObject -> value.mapValues { fromJson(it.value) }
        is JsonArray -> value.map(::fromJson)
        is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
    }
}
