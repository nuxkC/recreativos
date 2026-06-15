package com.recre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType
import java.math.BigDecimal
import java.math.RoundingMode

// =====================================================================
// Átomo MoneyText · C-MoneyText (Design System "Confianza Industrial", Fase 3).
// SSOT visual: .kiro/specs/recre/fase3-component-specs.md (línea 526).
//
// Primitivo de presentación de TODA cifra económica. El importe llega SIEMPRE
// como BigDecimal o String decimal exacto del servidor (SSOT en
// calcular-recaudacion) — NUNCA Double/Float: convertir a Double antes de
// formatear pierde céntimos en importes grandes (99.999.999,99). MoneyText es
// el único lugar donde se decide cómo se ve el dinero: dígitos en foreground
// (onSurface), símbolo € + separadores de miles/decimales en muted, vía
// AnnotatedString. Tipografía mono tabular ("tnum" ya en la fuente Geist Mono).
//
// REGLA DE COLOR: el dígito permanece en foreground por defecto. Los roles
// (success/warning/danger) NO tiñen el texto en general — se exponen con
// icono + texto + color (a11y: estado nunca solo por color). success se reserva
// a confirmación con icono; warning a provisional/offline-stale; danger SOLO a
// descuadre/error real. Un negativo (tasa, descuento, retención) es un «−»
// neutro en foreground, jamás rojo.
// =====================================================================

/** Tamaño semántico de la cifra. Mapea a los estilos nombrados de RecreType. */
enum class MoneyTextSize {
    /** Héroe / KPI (cabecera de recaudación). RecreType.importe 40/700. */
    Hero,

    /** Importe en card/fila (totales de denominaciones). RecreType.importeMedium 22/600. */
    Medium,

    /** Cifra secundaria inline (subtotales, %). RecreType.cifra 16/500. */
    Inline,

    /** Cifra menor / metadato numérico. RecreType.cifraCaption 13/500. */
    Caption,
}

/**
 * Rol semántico de la cifra. Decide color e icono según el dominio.
 *
 * INVARIANTE A11Y: ningún rol coloreado se muestra sin icono+texto. Por eso
 * Success/Warning/Danger llevan un icono asociado; el color por sí solo está
 * prohibido por el componente.
 */
enum class MoneyTextRole {
    /** Por defecto: dato confirmado del servidor. Dígito en foreground, sin icono. */
    Neutral,

    /** Confirmado/cuadra/sincronizado. Dígito en foreground + icono+color success. */
    Success,

    /** Provisional/estimación local/offline-stale (sin confirmar). warning + reloj. */
    Pending,

    /** Descuadre/saldo negativo real/conflicto. danger + alerta. SOLO error real. */
    Error,

    /** Offline: último dato conocido del servidor (real, solo viejo). Foreground + nube tachada. */
    Offline,
}

/**
 * Primitivo de cifra económica.
 *
 * @param amount importe exacto como BigDecimal. NUNCA Double/Float.
 * @param size tamaño semántico (Hero/Medium/Inline/Caption).
 * @param role rol semántico (color + icono). Por defecto neutro (foreground).
 * @param roleLabel etiqueta textual del rol para accesibilidad (p.ej. «provisional»,
 *   «descuadre», «sin conexión»). El llamador la pasa con stringResource. Obligatoria
 *   en roles coloreados para cumplir «estado nunca solo por color».
 * @param suffix sufijo de unidad opcional ya localizado («/sem», «neto»). Va en muted,
 *   fuera del bloque tabular. El llamador lo pasa con stringResource.
 * @param contentDescription descripción accesible completa de la cifra. Si es null se
 *   deriva del texto formateado; recomendado pasarla localizada incluyendo el rol.
 * @param modifier modifier estándar (último parámetro).
 */
@Composable
fun MoneyText(
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    size: MoneyTextSize = MoneyTextSize.Inline,
    role: MoneyTextRole = MoneyTextRole.Neutral,
    roleLabel: String? = null,
    suffix: String? = null,
    contentDescription: String? = null,
) {
    MoneyTextFormatted(
        formatted = formatEur(amount),
        modifier = modifier,
        size = size,
        role = role,
        roleLabel = roleLabel,
        suffix = suffix,
        contentDescription = contentDescription,
    )
}

/**
 * Sobrecarga que recibe el importe como String decimal exacto del servidor
 * (p.ej. el campo tal cual viene del JSON, sin pasar por Double). Equivalente a
 * la variante BigDecimal: formatea money-safe.
 */
@Composable
fun MoneyText(
    amount: String,
    modifier: Modifier = Modifier,
    size: MoneyTextSize = MoneyTextSize.Inline,
    role: MoneyTextRole = MoneyTextRole.Neutral,
    roleLabel: String? = null,
    suffix: String? = null,
    contentDescription: String? = null,
) {
    MoneyTextFormatted(
        formatted = formatEur(amount),
        modifier = modifier,
        size = size,
        role = role,
        roleLabel = roleLabel,
        suffix = suffix,
        contentDescription = contentDescription,
    )
}

/**
 * Estado de carga: antes de la respuesta del servidor NO se muestra «0,00 €»
 * como si fuera dato real. Placeholder «—» en muted (variante simple, sin
 * shimmer; el skeleton con shimmer lo aporta el contenedor si procede).
 */
@Composable
fun MoneyTextLoading(
    modifier: Modifier = Modifier,
    size: MoneyTextSize = MoneyTextSize.Inline,
    contentDescription: String? = null,
) {
    val colors = RecreColors.current
    val cd = contentDescription
    Text(
        text = "—",
        style = styleFor(size),
        color = colors.muted,
        maxLines = 1,
        modifier =
            modifier.semantics {
                if (cd != null) this.contentDescription = cd
            },
    )
}

// ---------------------------------------------------------------------
// Implementación común (texto ya formateado).
// ---------------------------------------------------------------------

@Composable
private fun MoneyTextFormatted(
    formatted: String,
    modifier: Modifier,
    size: MoneyTextSize,
    role: MoneyTextRole,
    roleLabel: String?,
    suffix: String?,
    contentDescription: String?,
) {
    val colors = RecreColors.current
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = colors.muted

    // Color del icono de rol (el dígito permanece en foreground por contraste AA;
    // ver anti-patrón: success/warning como TEXTO falla AA en algunos modos).
    val roleColor: Color? =
        when (role) {
            MoneyTextRole.Neutral -> null
            MoneyTextRole.Success -> colors.success
            MoneyTextRole.Pending -> colors.warning
            MoneyTextRole.Error -> colors.danger
            MoneyTextRole.Offline -> muted
        }
    val roleIcon =
        when (role) {
            MoneyTextRole.Neutral -> null
            MoneyTextRole.Success -> null // confirmado: el realce es del contenedor (flash), no del texto
            MoneyTextRole.Pending -> Icons.Filled.Schedule
            MoneyTextRole.Error -> Icons.Filled.ErrorOutline
            MoneyTextRole.Offline -> Icons.Filled.CloudOff
        }

    val annotated =
        remember(formatted, foreground, muted) {
            buildMoneyAnnotated(formatted, foreground = foreground, muted = muted)
        }

    val numberStyle = styleFor(size)
    val iconSizeDp = iconSizeFor(size)

    // contentDescription accesible: texto + etiqueta de rol si la hay. El «−» NO
    // es decorativo (se lee). El icono es decorativo (cubierto por la etiqueta).
    val cd =
        contentDescription
            ?: buildString {
                append(formatted)
                if (suffix != null) {
                    append(' ')
                    append(suffix)
                }
                if (roleLabel != null) {
                    append(", ")
                    append(roleLabel)
                }
            }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier.semantics(mergeDescendants = true) {
                this.contentDescription = cd
            },
    ) {
        if (roleIcon != null && roleColor != null) {
            Icon(
                imageVector = roleIcon,
                contentDescription = null, // significado en el contentDescription del Row
                tint = roleColor,
                modifier = Modifier.size(iconSizeDp),
            )
        }
        // Dinero nunca se trunca: maxLines=1 controla layout, los dígitos no se recortan
        // (la columna debe dimensionarse al dominio; aquí no aplicamos ellipsis).
        Text(
            text = annotated,
            style = numberStyle,
            maxLines = 1,
        )
        if (suffix != null) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                maxLines = 1,
            )
        }
    }
}

/** Mapa tamaño → estilo nombrado de cifra (RecreType, Geist Mono tabular). */
private fun styleFor(size: MoneyTextSize): TextStyle =
    when (size) {
        MoneyTextSize.Hero -> RecreType.importe
        MoneyTextSize.Medium -> RecreType.importeMedium
        MoneyTextSize.Inline -> RecreType.cifra
        MoneyTextSize.Caption -> RecreType.cifraCaption
    }

private fun iconSizeFor(size: MoneyTextSize) =
    when (size) {
        MoneyTextSize.Hero -> 24.dp
        MoneyTextSize.Medium -> 20.dp
        MoneyTextSize.Inline -> 16.dp
        MoneyTextSize.Caption -> 14.dp
    }

/**
 * Descompone el importe ya formateado («−1.234,56 €») en fragmentos teñidos:
 * dígitos y signo «−» en foreground; separadores de miles «.», separador
 * decimal «,», espacio y símbolo «€» en muted. Tabular ("tnum") ya viene de la
 * fuente, así que los dígitos no bailan entre frames.
 */
private fun buildMoneyAnnotated(
    formatted: String,
    foreground: Color,
    muted: Color,
): AnnotatedString {
    val fgStyle = SpanStyle(color = foreground)
    val mutedStyle = SpanStyle(color = muted)
    return buildAnnotatedString {
        for (ch in formatted) {
            // Dígitos y el menos van en foreground; el resto (.,€ espacio) en muted.
            val isForeground = ch.isDigit() || ch == '-' || ch == '−' // '−' U+2212
            withStyle(if (isForeground) fgStyle else mutedStyle) {
                append(ch)
            }
        }
    }
}

// =====================================================================
// FORMATEADOR CANÓNICO money-safe (es-ES). Único formateador de euros del
// proyecto Android. Hoy hay 5 formatEur duplicados en pantallas (historico,
// recaudacion/components, denominaciones, locales, printer) con agrupación de
// miles inconsistente o ausente; este es el reemplazo canónico — migrar los
// consumidores en una tarea posterior, no tocarlos aún.
//
// money-safe: deriva SIEMPRE del valor decimal exacto (BigDecimal /
// toPlainString), NUNCA de Double/Float. setScale(2, HALF_UP) al céntimo;
// agrupación de miles manual (no Intl con Double) para no perder precisión.
// =====================================================================

/** Formatea un BigDecimal a euros es-ES: «1.234,56 €». Money-safe. */
fun formatEur(amount: BigDecimal): String = formatEurPlain(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())

/**
 * Formatea un importe recibido como String decimal exacto del servidor a euros
 * es-ES: «1.234,56 €». Money-safe (sin pasar por Double). Si el String no es un
 * decimal válido se devuelve tal cual (defensivo; no se inventa una cifra).
 */
fun formatEur(amount: String): String {
    val plain =
        runCatching {
            BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()
        }.getOrElse { return amount }
    return formatEurPlain(plain)
}

/**
 * Núcleo de formato es-ES a partir de un toPlainString() ya escalado a 2
 * decimales («-1234.56»): signo, agrupación de miles con «.», separador decimal
 * «,» y sufijo « €». Trabaja solo sobre el String exacto (money-safe).
 */
private fun formatEurPlain(plain: String): String {
    val negative = plain.startsWith("-")
    val unsigned = if (negative) plain.substring(1) else plain
    val dot = unsigned.indexOf('.')
    val integerPart = if (dot >= 0) unsigned.substring(0, dot) else unsigned
    val decimalPart = if (dot >= 0) unsigned.substring(dot + 1) else "00"

    val grouped = groupThousands(integerPart)
    val sign = if (negative) "−" else "" // «−» U+2212 (menos tipográfico)
    return "$sign$grouped,$decimalPart €"
}

/** Agrupa la parte entera en miles con «.» (es-ES), desde la derecha. */
private fun groupThousands(integerPart: String): String {
    if (integerPart.length <= 3) return integerPart
    val sb = StringBuilder()
    val firstGroup = integerPart.length % 3
    var index = 0
    if (firstGroup > 0) {
        sb.append(integerPart, 0, firstGroup)
        index = firstGroup
    }
    while (index < integerPart.length) {
        if (sb.isNotEmpty()) sb.append('.')
        sb.append(integerPart, index, index + 3)
        index += 3
    }
    return sb.toString()
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "MoneyText · light", showBackground = true)
@Composable
private fun MoneyTextPreviewLight() {
    RecreTheme(darkTheme = false) {
        MoneyTextPreviewContent()
    }
}

@Preview(name = "MoneyText · dark", showBackground = true)
@Composable
private fun MoneyTextPreviewDark() {
    RecreTheme(darkTheme = true) {
        MoneyTextPreviewContent()
    }
}

@Composable
private fun MoneyTextPreviewContent() {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.size(width = 320.dp, height = 360.dp),
    ) {
        MoneyText(amount = BigDecimal("1234.5"), size = MoneyTextSize.Hero)
        MoneyText(amount = BigDecimal("99999999.99"), size = MoneyTextSize.Medium)
        MoneyText(amount = "12.50", size = MoneyTextSize.Inline, suffix = "/sem")
        MoneyText(amount = BigDecimal("-12.50"), size = MoneyTextSize.Inline)
        MoneyText(
            amount = BigDecimal("840.00"),
            size = MoneyTextSize.Inline,
            role = MoneyTextRole.Pending,
            roleLabel = "provisional",
        )
        MoneyText(
            amount = BigDecimal("-12.50"),
            size = MoneyTextSize.Inline,
            role = MoneyTextRole.Error,
            roleLabel = "descuadre",
        )
        MoneyText(
            amount = BigDecimal("1500.00"),
            size = MoneyTextSize.Inline,
            role = MoneyTextRole.Offline,
            roleLabel = "sin conexión",
        )
        MoneyTextLoading(size = MoneyTextSize.Inline)
    }
}
