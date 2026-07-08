package com.recre.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.recre.app.R

// =====================================================================
// Design System "Confianza Industrial" — Grupo Tipografía (Fase 3 · T-228).
// SSOT: .kiro/specs/recre/fase3-design-tokens.md.
//  - Geist Sans para TODA la UI (texto, labels, títulos).
//  - Geist Mono con cifras tabulares ("tnum") para TODO importe/contador/%:
//    los dígitos no bailan al actualizarse (count-up, descuadres).
// Fuentes variables (un único fichero por familia) en res/font/; el peso se
// selecciona por el eje de variación "wght" (minSdk 26 ⇒ FontVariation OK).
// Escala Android = escala web +1 paso (pantalla a un brazo, en mano).
// =====================================================================

@OptIn(ExperimentalTextApi::class)
private fun geistVariable(weight: FontWeight) =
    Font(
        R.font.geist_variable,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

@OptIn(ExperimentalTextApi::class)
private fun geistMonoVariable(weight: FontWeight) =
    Font(
        R.font.geist_mono_variable,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** Familia de interfaz: todo lo que NO es una cifra. */
val GeistSans =
    FontFamily(
        geistVariable(FontWeight.W400),
        geistVariable(FontWeight(450)), // peso no estándar (body); el eje wght interpola
        geistVariable(FontWeight.W500),
        geistVariable(FontWeight.W600),
        geistVariable(FontWeight.W700),
    )

/** Familia monoespaciada: reservada a cifras (importes, contadores, %). */
val GeistMono =
    FontFamily(
        geistMonoVariable(FontWeight.W500),
        geistMonoVariable(FontWeight.W600),
        geistMonoVariable(FontWeight.W700),
    )

@OptIn(ExperimentalTextApi::class)
private fun bricolageVariable(weight: FontWeight) =
    Font(
        R.font.bricolage_grotesque_variable,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** Familia display «Neón de sala»: titulares y héroes. NUNCA para cuerpo ni cifras. */
val BricolageDisplay =
    FontFamily(
        bricolageVariable(FontWeight.W600),
        bricolageVariable(FontWeight.W700),
    )

/** Dígitos tabulares de Geist Mono ("tnum"): ancho fijo por glifo. */
private const val tabularNums = "tnum"

private val lineHeightTrim =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

/**
 * Estilos NOMBRADOS de dinero/cifras. No forman parte del Typography de M3 (que
 * no tiene slots para esto); se consumen directamente:
 *   Text(formatEur(importe), style = RecreType.importe)
 * El símbolo € va en color muted y el dígito en foreground — eso lo decide el
 * componente con AnnotatedString; aquí sólo se fija forma y métrica.
 */
object RecreType {
    /** Importe protagonista (cabecera de recaudación, KPI). 40/700 tabular. */
    val importe =
        TextStyle(
            fontFamily = GeistMono,
            fontWeight = FontWeight.W700,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.em,
            fontFeatureSettings = tabularNums,
            lineHeightStyle = lineHeightTrim,
        )

    /** Importe en lista/fila (cards de cifras, totales de denominaciones). 22/600. */
    val importeMedium =
        TextStyle(
            fontFamily = GeistMono,
            fontWeight = FontWeight.W600,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontFeatureSettings = tabularNums,
            lineHeightStyle = lineHeightTrim,
        )

    /** Cifra secundaria inline (subtotales, contadores, %). 16/500 tabular. */
    val cifra =
        TextStyle(
            fontFamily = GeistMono,
            fontWeight = FontWeight.W500,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontFeatureSettings = tabularNums,
            lineHeightStyle = lineHeightTrim,
        )

    /** Cifra menor / metadato numérico (fechas mono, ids). 13/500 tabular. */
    val cifraCaption =
        TextStyle(
            fontFamily = GeistMono,
            fontWeight = FontWeight.W500,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = tabularNums,
            lineHeightStyle = lineHeightTrim,
        )

    /** Héroe display (conteo del home, titulares de tramo). Bricolage, NO cifras de dinero. */
    val displayHero =
        TextStyle(
            fontFamily = BricolageDisplay,
            fontWeight = FontWeight.W700,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.02).em,
            lineHeightStyle = lineHeightTrim,
        )

    /**
     * Eyebrow «Neón de sala» (S4): etiqueta mono MAYÚSCULAS con tracking ancho.
     * Es EL título de sección, el label de campo y el subtítulo de cabecera del
     * mockup (CSS: Geist Mono ~10px, +0.18em, --texto-3). El uppercase lo aplica
     * el átomo Eyebrow, no el estilo.
     */
    val eyebrow =
        TextStyle(
            fontFamily = GeistMono,
            fontWeight = FontWeight.W600,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.18.em,
            lineHeightStyle = lineHeightTrim,
        )
}

/**
 * Typography de Material 3 mapeado a Geist Sans con la escala Android (+1 paso).
 *   headlineMedium → H1 24/700 · titleLarge → H2 20/600 · titleMedium → 16/600
 *   titleSmall → 14/600 · bodyLarge → 16/450 (texto por defecto) · bodyMedium →
 *   14/440 · bodySmall → 13/440 · labelLarge → botón 14/600 · labelMedium →
 *   caption 13/500 · labelSmall → badge/overline 12/600.
 */
val Typography =
    Typography(
        headlineMedium =
            TextStyle(
                fontFamily = BricolageDisplay,
                fontWeight = FontWeight.W700,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.02).em,
                lineHeightStyle = lineHeightTrim,
            ),
        titleLarge =
            TextStyle(
                fontFamily = BricolageDisplay,
                fontWeight = FontWeight.W600,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        titleMedium =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight.W600,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        titleSmall =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight.W600,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight(450),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight(440),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        bodySmall =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight(440),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        labelLarge =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight.W600,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        labelMedium =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight.W500,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                lineHeightStyle = lineHeightTrim,
            ),
        labelSmall =
            TextStyle(
                fontFamily = GeistSans,
                fontWeight = FontWeight.W600,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.02.em,
                lineHeightStyle = lineHeightTrim,
            ),
    )
