package com.recre.app.core.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val ERROR_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Extrae el `code` del cuerpo de error `{ error: { code, message } }` que
 * devuelven las Edge Functions. Compartido por los data sources del
 * dominio de recaudación. Devuelve `null` si el cuerpo no es JSON válido
 * o no trae código.
 */
internal fun parseErrorCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val element = ERROR_JSON.parseToJsonElement(body)
        val obj = element as? JsonObject ?: return@runCatching null
        val codeElement = obj["code"] ?: return@runCatching null
        val primitive = codeElement as? JsonPrimitive ?: return@runCatching null
        if (primitive is JsonNull) null else primitive.content
    }.getOrNull()
}
