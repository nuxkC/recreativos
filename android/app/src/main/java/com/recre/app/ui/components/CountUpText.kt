package com.recre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.recre.app.ui.theme.RecreMotionDurations
import com.recre.app.ui.theme.RecreStandardEasing
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val ES = Locale("es", "ES")

/**
 * Formatea un importe money-safe (String decimal EXACTO del servidor, p.ej. el campo
 * tal cual viene del JSON) como "1.234,56" en es-ES, sin el símbolo "€" (eso lo decide
 * el componente que lo pinta). Half-up al céntimo desde [BigDecimal]; NUNCA Double.
 */
fun formatearImporteEs(importe: String): String {
    val symbols =
        DecimalFormatSymbols(ES).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
    val df = DecimalFormat("#,##0.00", symbols).apply { isGroupingUsed = true }
    return df.format(BigDecimal(importe).setScale(2, RoundingMode.HALF_UP))
}

/**
 * Cifra de dinero que "cuenta" hasta su valor al aparecer o cambiar (motion de firma:
 * 600 ms, curva de marca). Money-safe: el objetivo es [BigDecimal] exacto y el valor
 * mostrado se deriva en BigDecimal (objetivo × fracción) → el fotograma final muestra
 * el importe íntegro; solo los intermedios interpolan. Delega el pintado en [MoneyText]
 * (dígitos foreground, € muted, Geist Mono tabular).
 *
 * @param importe importe money-safe como String decimal exacto.
 */
@Composable
fun CountUpText(
    importe: String,
    modifier: Modifier = Modifier,
    size: MoneyTextSize = MoneyTextSize.Hero,
    role: MoneyTextRole = MoneyTextRole.Neutral,
    roleLabel: String? = null,
) {
    val objetivo = remember(importe) { BigDecimal(importe).setScale(2, RoundingMode.HALF_UP) }
    // Fracción 0→1 reiniciada al cambiar el importe; al asentar (1f) el valor es exacto.
    val fraccion = remember(importe) { Animatable(0f) }
    LaunchedEffect(importe) {
        fraccion.animateTo(
            targetValue = 1f,
            animationSpec = tween(RecreMotionDurations.COUNT_UP_MS, easing = RecreStandardEasing),
        )
    }
    val mostrado =
        objetivo
            .multiply(fraccion.value.toBigDecimal())
            .setScale(2, RoundingMode.HALF_UP)
    MoneyText(
        amount = mostrado,
        modifier = modifier,
        size = size,
        role = role,
        roleLabel = roleLabel,
    )
}
