package com.recre.app.core.data.remote

import com.recre.app.core.util.DomainError
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

/**
 * Excepción tipada que reportan los `*GestorRemoteDataSource` (T-66..T-69)
 * tras una llamada a PostgREST. La capa repositorio la mapea a un código
 * i18n local + un [DomainError] para que la UI muestre el copy adecuado.
 *
 * Convenciones:
 * - `code` reproduce el patrón de la web (PRs #11..#14): cuando un fallo
 *   PostgreSQL viaja con `SQLSTATE` reconocido (`23505`, `23503`, `23514`,
 *   `23502`...) lo guardamos crudo para que la capa repositorio pueda
 *   distinguir, además, qué constraint disparó el error a partir de
 *   [details].
 * - `details` lleva el detalle textual completo de PostgREST (incluyendo
 *   el nombre de la constraint), utilizado para discriminar p. ej. la
 *   unicidad de `(empresa_id, numero_serie)` en máquinas vs el índice
 *   único parcial `uq_instalacion_maquina_activa` en instalaciones.
 */
class GestionRemoteError(
    val code: String?,
    val details: String?,
    message: String,
) : RuntimeException(message)

/**
 * Convierte cualquier excepción lanzada por PostgREST/Ktor en una pareja
 * `(DomainError, codeI18n)` que los ViewModels puedan pintar.
 *
 * Códigos de retorno comunes:
 * - `network`           — sin red, timeout, DNS.
 * - `auth`              — JWT inválido/expirado o RLS denegada.
 * - `not_found`         — la fila no existía al hacer update/delete.
 * - `validation_error`  — CHECK violado (`23514`, `23502`).
 * - `numero_duplicado` / `numero_serie_duplicado` / `nombre_duplicado` —
 *   `23505` discriminado por nombre de constraint (uq_*).
 * - `instalacion_activa_maquina` / `instalacion_activa_licencia` —
 *   `23505` sobre los índices únicos parciales de instalaciones.
 * - `en_uso`            — `23503` (FK violation): la fila tiene
 *   instalaciones, recaudaciones o cambios de placa que la referencian.
 * - `unknown`           — cualquier otro fallo no clasificado.
 */
fun clasificarErrorGestion(throwable: Throwable): Pair<DomainError, String> {
    return when (throwable) {
        is GestionRemoteError -> mapGestionRemote(throwable)
        is HttpRequestTimeoutException -> DomainError.Network(throwable.message) to "network"
        is IOException -> DomainError.Network(throwable.message) to "network"
        else -> {
            // supabase-kt 3.0 lanza `RestException` (clase abstracta) y
            // subclases por código de estado HTTP. No importamos los tipos
            // para no acoplarnos a internals; clasificamos por contenido del
            // mensaje (que incluye el JSON de PostgREST con `code` y
            // `details`).
            val msg = throwable.message ?: "unknown"
            val code = extraerCodigoSql(msg)
            if (code != null) {
                mapGestionRemote(GestionRemoteError(code = code, details = msg, message = msg))
            } else if (
                msg.contains("network", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("unable to resolve", ignoreCase = true) ||
                msg.contains("connect", ignoreCase = true) && msg.contains("fail", ignoreCase = true)
            ) {
                DomainError.Network(msg) to "network"
            } else if (
                msg.contains("jwt", ignoreCase = true) ||
                msg.contains("unauthorized", ignoreCase = true) ||
                msg.contains("forbidden", ignoreCase = true) ||
                msg.contains("permission denied", ignoreCase = true)
            ) {
                DomainError.Auth(msg) to "auth"
            } else {
                DomainError.Unknown(msg) to "unknown"
            }
        }
    }
}

private fun mapGestionRemote(error: GestionRemoteError): Pair<DomainError, String> {
    val code = error.code
    val details = (error.details ?: "").lowercase()
    return when (code) {
        "23505" -> {
            // Unicidad. Discriminar por nombre de constraint o columna.
            when {
                details.contains("uq_instalacion_maquina") ||
                    details.contains("instalacion_maquina_activa") ->
                    DomainError.Conflict(error.message) to "instalacion_activa_maquina"
                details.contains("uq_instalacion_licencia") ||
                    details.contains("instalacion_licencia_activa") ->
                    DomainError.Conflict(error.message) to "instalacion_activa_licencia"
                details.contains("numero_serie") ->
                    DomainError.Conflict(error.message) to "numero_serie_duplicado"
                details.contains("(numero)") || details.contains("idx_licencia_numero") ->
                    DomainError.Conflict(error.message) to "numero_duplicado"
                details.contains("nombre") ->
                    DomainError.Conflict(error.message) to "nombre_duplicado"
                else -> DomainError.Conflict(error.message) to "duplicado"
            }
        }
        "23503" -> DomainError.Conflict(error.message) to "en_uso"
        "23514", "23502" -> DomainError.Validation(error.message) to "validation_error"
        "PGRST116" -> DomainError.NotFound(error.message) to "not_found"
        else -> DomainError.Unknown(error.message) to (code ?: "unknown")
    }
}

private val SQLSTATE_REGEX = Regex("""\b(2[35]\d{3})\b""")

/** Extrae un código `SQLSTATE` (5 dígitos) si aparece textualmente. */
private fun extraerCodigoSql(text: String): String? =
    SQLSTATE_REGEX.find(text)?.value
