package com.recre.app.feature.averias

import com.recre.app.ui.components.formatEur

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.recre.app.R
import com.recre.app.ui.components.StatusRole
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Categoría de avería: lista FIJA, espejo del CHECK de la tabla `averia`
 * (design.md §3.16 / T-220). El `valor` es el que viaja al servidor; el
 * `labelRes` el texto que ve el técnico.
 */
enum class CategoriaAveria(val valor: String, @StringRes val labelRes: Int) {
    ATASCO_BILLETE("atasco_billete", R.string.averia_categoria_atasco_billete),
    ATASCO_MONEDA("atasco_moneda", R.string.averia_categoria_atasco_moneda),
    ERROR("error", R.string.averia_categoria_error),
    FALTA_PAGO("falta_pago", R.string.averia_categoria_falta_pago),
    NO_ENCIENDE("no_enciende", R.string.averia_categoria_no_enciende),
    OTRO("otro", R.string.averia_categoria_otro),
    ;

    companion object {
        fun fromValor(valor: String): CategoriaAveria? = entries.firstOrNull { it.valor == valor }
    }
}

/** Texto i18n de una categoría a partir de su valor crudo (fallback: el valor). */
@Composable
fun categoriaLabel(valor: String): String =
    CategoriaAveria.fromValor(valor)?.let { stringResource(it.labelRes) } ?: valor

@StringRes
fun estadoAveriaLabelRes(estado: String): Int = when (estado) {
    "abierta" -> R.string.averia_estado_abierta
    "en_reparacion" -> R.string.averia_estado_en_reparacion
    "resuelta" -> R.string.averia_estado_resuelta
    else -> R.string.averia_estado_abierta
}

/** Rol + icono del StatusChip de estado (compartido con la fecha protagonista de la card). */
fun estadoChipRolIcono(estado: String): Pair<StatusRole, ImageVector> = when (estado) {
    "resuelta" -> StatusRole.SUCCESS to Icons.Filled.Check
    "en_reparacion" -> StatusRole.WARNING to Icons.Filled.Warning
    else -> StatusRole.DANGER to Icons.Outlined.ErrorOutline
}

/** Formatea un coste (String con precisión decimal) como "12,50 €". `null` → "". */
fun formatCoste(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return formatEur(value) // canónico money-safe, agrupación es-ES
}

/**
 * Normaliza un coste tecleado ("12,50" / "12.5") a String decimal canónico
 * ("12.50") o `null` si está vacío. Devuelve `null` también si no es un decimal
 * válido (≥ 0, ≤ 2 decimales): la UI no debería dejar guardar en ese caso.
 * Es dinero, así que se mantiene como String (nunca Double).
 */
fun normalizeCoste(raw: String): String? {
    val trimmed = raw.trim().replace(',', '.')
    if (trimmed.isEmpty()) return null
    val decimal = runCatching { BigDecimal(trimmed) }.getOrNull() ?: return null
    if (decimal.signum() < 0 || decimal.scale() > 2) return null
    return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString()
}

/**
 * Resuelve un código de error de averías al `R.string` correspondiente. Los
 * específicos (sin permiso / ya resuelta / no encontrada / guardado offline) se
 * mapean aquí; el resto (red, auth…) se delega en
 * [com.recre.app.feature.gestion.resolveErrorRes].
 */
@StringRes
fun resolveAveriaErrorRes(code: String): Int = when (code) {
    "sin_permiso" -> R.string.averia_error_sin_permiso
    "ya_resuelta" -> R.string.averia_error_ya_resuelta
    "no_encontrada" -> R.string.averia_error_no_encontrada
    "guardar" -> R.string.averia_error_guardar
    else -> com.recre.app.feature.gestion.resolveErrorRes(code)
}
