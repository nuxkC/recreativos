package com.recre.app.core.data.remote

import com.recre.app.core.util.DomainError

/**
 * Clasificador de errores de las RPCs de averías (T-222).
 *
 * Las funciones `crear/resolver_averia` y `crear_recambio` lanzan SQLSTATEs
 * que [clasificarErrorGestion] no contempla (su regex solo cubre `23xxx`/
 * `25xxx`): `42501` (sin permiso), `22023` (avería ya resuelta) y `P0002`
 * (`no_data_found`: máquina/avería inexistente). Los detectamos aquí primero
 * y delegamos el resto (red, timeout, FK 23503…) en el clasificador de gestión.
 */
private val AVERIA_SQLSTATE = Regex("""\b(42501|22023|P0002)\b""")

fun clasificarErrorAveria(throwable: Throwable): Pair<DomainError, String> {
    val msg = throwable.message
    if (msg != null) {
        when (AVERIA_SQLSTATE.find(msg)?.value) {
            "42501" -> return DomainError.Auth(msg) to "sin_permiso"
            "22023" -> return DomainError.Conflict(msg) to "ya_resuelta"
            "P0002" -> return DomainError.NotFound(msg) to "no_encontrada"
        }
    }
    return clasificarErrorGestion(throwable)
}
