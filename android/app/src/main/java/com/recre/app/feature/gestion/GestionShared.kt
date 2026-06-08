package com.recre.app.feature.gestion

import com.recre.app.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Helpers compartidos por las 4 features del CRUD gestor (T-66..T-69):
 * normalización de inputs, validación de tipos comunes (decimal, fecha
 * ISO, email) y mapeo de códigos de error a recursos i18n.
 *
 * La validación lexica vive aquí (no en un schema declarativo como zod
 * en la web) para evitar añadir una dependencia de validación en
 * Android. Cada ViewModel valida directamente y rellena los errores
 * por campo en su `UiState`.
 */

// ---------------------------------------------------------- normalización

/** Trim + null si queda vacío. */
fun String?.normalizarOpcional(): String? = this?.trim()?.ifEmpty { null }

/**
 * Convierte una entrada decimal (`"0,20"`, `"0.20"`, `" 999.99 "`) en
 * el string canónico `"X.YY"` con dos decimales. Devuelve `null` si la
 * entrada no es un número finito > 0 dentro del rango `[min, max]`.
 *
 * Si `entradaCero=true`, acepta también `"0"` como valor válido (lo que
 * se necesita para `tasaSemanal` y `porcentajeLocal`).
 */
fun normalizarDecimal(
    raw: String,
    min: BigDecimal,
    max: BigDecimal,
    decimales: Int = 2,
): BigDecimal? {
    val limpio = raw.trim().replace(",", ".")
    if (limpio.isEmpty()) return null
    val dec = runCatching { BigDecimal(limpio) }.getOrNull() ?: return null
    if (dec.scale() > decimales) return null
    if (dec < min || dec > max) return null
    return dec.setScale(decimales, RoundingMode.HALF_UP)
}

private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE

/** True si `raw` es una fecha ISO `YYYY-MM-DD` válida. */
fun esFechaIsoValida(raw: String): Boolean = runCatching {
    LocalDate.parse(raw, ISO_DATE)
}.isSuccess

private val EMAIL_REGEX: Pattern =
    Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

/** True si `raw` parece un email razonable. La validación final la hace el server. */
fun esEmailValido(raw: String): Boolean = EMAIL_REGEX.matcher(raw).matches()

// ---------------------------------------------------------- ids

private val UUID_REGEX = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

fun esUuid(raw: String): Boolean = UUID_REGEX.matches(raw)

// ---------------------------------------------------------- enums

val ESTADOS_LICENCIA = listOf("activa", "suspendida", "caducada", "baja")

val ESTADOS_MAQUINA = listOf("instalada", "almacen", "averiada", "baja")

// ---------------------------------------------------------- error codes

/**
 * Resuelve un código de error técnico (devuelto por
 * [com.recre.app.core.data.remote.clasificarErrorGestion] o por la
 * validación local) al `R.string.gestion_error_*` correspondiente.
 *
 * Cualquier código no contemplado cae en `gestion_error_generic`.
 */
fun resolveErrorRes(code: String): Int = when (code) {
    "network" -> R.string.gestion_error_network
    "auth" -> R.string.gestion_error_auth
    "not_found" -> R.string.gestion_error_not_found
    "validation_error" -> R.string.gestion_error_validacion
    "duplicado" -> R.string.gestion_error_duplicado
    "numero_duplicado" -> R.string.gestion_error_numero_duplicado
    "numero_serie_duplicado" -> R.string.gestion_error_numero_serie_duplicado
    "nombre_duplicado" -> R.string.gestion_error_nombre_duplicado
    "instalacion_activa_maquina" -> R.string.gestion_error_instalacion_maquina_activa
    "instalacion_activa_licencia" -> R.string.gestion_error_instalacion_licencia_activa
    "en_uso" -> R.string.gestion_error_en_uso
    "cerrar_fecha_fin_invalida" -> R.string.gestion_cerrar_error_fecha_fin
    "cerrar_ya_cerrada" -> R.string.gestion_cerrar_error_ya_cerrada
    "cerrar_no_encontrada" -> R.string.gestion_cerrar_error_no_encontrada
    "cerrar_sin_permiso" -> R.string.gestion_cerrar_error_sin_permiso
    "cerrar_fallido" -> R.string.gestion_cerrar_error_generic
    else -> R.string.gestion_error_generic
}
