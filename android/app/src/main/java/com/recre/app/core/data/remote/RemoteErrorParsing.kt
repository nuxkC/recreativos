package com.recre.app.core.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val ERROR_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Las Edge Functions devuelven los errores como `{ error: { code, message,
 * details? } }` (ver `_shared/errors.ts#jsonResponse`). Devuelve el objeto
 * `error` interno, o el propio objeto raíz como fallback (por si en algún
 * punto llega ya desenvuelto). `null` si el cuerpo no es JSON.
 */
private fun objetoError(body: String?): JsonObject? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val raiz = ERROR_JSON.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
        (raiz["error"] as? JsonObject) ?: raiz
    }.getOrNull()
}

private fun JsonObject.campoTexto(nombre: String): String? {
    val primitive = this[nombre] as? JsonPrimitive ?: return null
    return if (primitive is JsonNull) null else primitive.content
}

/**
 * Extrae el `code` del cuerpo de error `{ error: { code, message } }` que
 * devuelven las Edge Functions. Compartido por los data sources del dominio
 * de recaudación. Devuelve `null` si el cuerpo no es JSON válido o no trae
 * código.
 */
internal fun parseErrorCode(body: String?): String? =
    objetoError(body)?.campoTexto("code")

/**
 * Extrae el `message` legible del cuerpo de error `{ error: { code, message } }`.
 * Es el texto que se le muestra al técnico (p. ej. "El desglose total no
 * coincide con la recaudación bruta"). `null` si no se puede parsear.
 */
internal fun parseErrorMessage(body: String?): String? =
    objetoError(body)?.campoTexto("message")
