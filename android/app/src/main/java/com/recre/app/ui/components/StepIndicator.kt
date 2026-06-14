package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType

// =====================================================================
// Átomo StepIndicator · C-STEP-01 (Design System "Confianza Industrial", Fase 3).
// SSOT visual: .kiro/specs/recre/fase3-component-specs.md (línea 5392).
//
// Comunica la posición dentro de un flujo lineal acotado y reanudable
// ("Paso 1 de 3" / "Máquina 2 de 3"). Es METADATO de cabecera, NO el dato héroe
// ni un CTA: vive en la franja de subtítulo de la TopAppBar sobre su surface.
// NO es navegable (no se salta de paso tocándolo): no interactivo, sin hit-area
// 48dp, no entra en el orden de foco; sólo se anuncia como status (liveRegion
// Polite) con un único contentDescription "Paso n de N".
//
// Adaptaciones al stack real (el bloque de código del spec usa nombres que NO
// existen aquí):
//  - Textos por parámetro String (i18n del llamador), NO R.string.* del spec:
//    el repo prohíbe hardcode y los recursos los inyecta quien llama.
//  - Tipografía: RecreType.cifraCaption (13sp/500 Geist Mono tabular "tnum"),
//    que ES el estilo "caption mono tabular" que pide el spec; no se copia el
//    labelSmall.copy(...) del pseudocódigo.
//  - Color foreground = MaterialTheme.colorScheme.onSurface; muted/surface-2/
//    primary = RecreColors.current.* (el `RecreTokens.surface2` del spec no
//    existe; el token real es RecreColors.current.surface2).
//  - Easing del motion = CubicBezierEasing(0.2,0,0,1) del spec (160ms barra);
//    `EmphasizedDecelerate` del pseudocódigo no es API estable aquí.
//
// REGLA DE COLOR: el ÚNICO token enfatizado es `current` (foreground + peso del
// estilo); etiqueta, conector y total van en muted (jamás foreground pleno, no
// compite con el título). NUNCA danger/rojo: el progreso no es un error.
// =====================================================================

/** Qué cuenta el indicador: pasos del flujo o máquinas de la ronda. */
enum class StepKind {
    /** "Paso n de N" — flujo de recaudación (Contadores → Denominaciones → Confirmación). */
    Step,

    /** "Máquina n de N" — ronda multi-máquina de un local. */
    Machine,
}

/** Forma del indicador. */
enum class StepVariant {
    /** Sólo la línea de texto "Paso n de N". Por defecto (el 95% de los casos). */
    Numeric,

    /** La línea numeric + una barra de N segmentos (sub-flujo de denominaciones). */
    Bar,
}

// Motion: relleno del segmento 160ms cubic-bezier(0.2,0,0,1) (spec · barra).
private const val BAR_FILL_DURATION_MS = 160
private val StepEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// La barra deja de ser legible con muchos segmentos: N>6 colapsa a sólo numeric.
private const val BAR_MAX_SEGMENTS = 6

/**
 * Indicador de progreso de cadena. Puramente presentacional: sin estado interno
 * ni interacción.
 *
 * Construye la cadena por spans: [label] + [current foreground] + [connector +
 * total muted]. Cuando [total] es null (kind=machine cargando, o total
 * indeterminado) degrada a sólo "[label] [current]" — NUNCA "de 0"/"de NaN" — y
 * fuerza variante numeric (sin barra).
 *
 * @param current paso/máquina actual (1-based).
 * @param total total de pasos/máquinas; null = aún desconocido (machine cargando)
 *   o indeterminado → se omite el "de N" y no se pinta barra.
 * @param label etiqueta inicial ya localizada, p. ej. "Paso " o "Máquina "
 *   (incluye el espacio de separación con el número). El llamador la pasa con
 *   stringResource según [kind].
 * @param connector conector ya localizado entre current y total, p. ej. " de "
 *   (con sus espacios). El llamador lo pasa con stringResource. Sólo se usa si
 *   [total] != null.
 * @param contentDescription descripción accesible COMPLETA y única para el lector
 *   (i18n), p. ej. "Paso 1 de 3" / "Máquina 2 de 3" / "Paso 1" (sin total). Si es
 *   null se deriva de label+current(+connector+total); recomendado pasarla.
 * @param kind qué cuenta (Step | Machine). Hoy sólo afecta a la semántica del
 *   llamador (qué label/desc pasa); el átomo no decide el texto.
 * @param variant Numeric (por defecto) | Bar (sólo sub-flujo de denominaciones).
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun StepIndicator(
    current: Int,
    total: Int?,
    label: String,
    connector: String,
    contentDescription: String? = null,
    @Suppress("UNUSED_PARAMETER") kind: StepKind = StepKind.Step,
    variant: StepVariant = StepVariant.Numeric,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = colors.muted

    // contentDescription único: el lector anuncia "Paso n de N" entero, nunca
    // dígitos sueltos. Si total es null se omite el "de N" (no "de 0"/"de NaN").
    val a11y =
        contentDescription
            ?: buildString {
                append(label)
                append(current)
                if (total != null) {
                    append(connector)
                    append(total)
                }
            }.trim()

    // Línea numeric: label + conector + total en muted; sólo current en foreground.
    val line =
        remember(label, current, total, connector, foreground, muted) {
            buildAnnotatedString {
                val mutedStyle = SpanStyle(color = muted)
                withStyle(mutedStyle) { append(label) }
                withStyle(SpanStyle(color = foreground)) { append(current.toString()) }
                if (total != null) {
                    withStyle(mutedStyle) {
                        append(connector)
                        append(total.toString())
                    }
                }
            }
        }

    Column(
        // Un único nodo de status: contentDescription completo + liveRegion Polite
        // (anuncia el nuevo paso al avanzar). No focusable, no es botón/enlace.
        modifier =
            modifier.semantics(mergeDescendants = true) {
                this.contentDescription = a11y
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(
            text = line,
            // RecreType.cifraCaption: 13sp/500 Geist Mono tabular ("tnum") — current
            // y total no saltan de ancho al pasar de 1 a 2 dígitos (no desplaza el título).
            style = RecreType.cifraCaption,
        )

        // Barra sólo en variante Bar y con total conocido y legible (2..6 segmentos).
        if (variant == StepVariant.Bar && total != null && total in 2..BAR_MAX_SEGMENTS) {
            Spacer(Modifier.height(6.dp))
            StepBar(current = current, total = total)
        }
    }
}

/**
 * Fila de [total] segmentos iguales (track de progreso). Completados (i<current)
 * y activo (i==current) en `primary`; pendientes en `surface-2`. Decorativa: el
 * conteo ya va en la línea de texto, así que se marca clearAndSetSemantics{}
 * (aria-hidden) para no leer la barra ni los dígitos sueltos.
 */
@Composable
private fun StepBar(
    current: Int,
    total: Int,
) {
    val colors = RecreColors.current
    // primary es slot core de M3 (acento petróleo); surface-2 es token de dominio.
    // La barra es la EXCEPCIÓN al límite de acento ≤10% (spec): segmento hecho/activo
    // en primary, pendiente en surface-2.
    val primary = MaterialTheme.colorScheme.primary
    val animate = rememberAnimationsEnabled()
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { i ->
            // Segmentos 1-based vs índice 0-based: el segmento i (1..total) está
            // hecho/activo si su número (i+1) <= current.
            val filled = (i + 1) <= current
            val target = if (filled) primary else colors.surface2
            // Avance de paso: relleno surface-2 → primary 160ms. Con reduce-motion,
            // sin animación de duración (cambio instantáneo) — respeta el spec.
            val barColor by animateColorAsState(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = if (animate) BAR_FILL_DURATION_MS else 0,
                    easing = StepEasing,
                ),
                label = "step-bar-segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
    }
}

/**
 * ¿Están activas las animaciones? Falso en preview o si el usuario desactivó las
 * animaciones del sistema (ANIMATOR_DURATION_SCALE = 0). Equivale al
 * prefers-reduced-motion de la web. (Mismo criterio que OfflineBadge/Skeleton.)
 */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale != 0f
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Composable
private fun StepIndicatorPreviewContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
    ) {
        // numeric · "Paso 1 de 3" (default): current en foreground, resto muted.
        StepIndicator(
            current = 1,
            total = 3,
            label = "Paso ",
            connector = " de ",
            contentDescription = "Paso 1 de 3",
        )
        // numeric · "Paso 3 de 3" (avanzado).
        StepIndicator(
            current = 3,
            total = 3,
            label = "Paso ",
            connector = " de ",
            contentDescription = "Paso 3 de 3",
        )
        // machine · total desconocido (cargando): degrada a "Máquina 2".
        StepIndicator(
            current = 2,
            total = null,
            label = "Máquina ",
            connector = " de ",
            contentDescription = "Máquina 2",
            kind = StepKind.Machine,
        )
        // bar · "Paso 2 de 3": 1 segmento completado + activo en primary.
        StepIndicator(
            current = 2,
            total = 3,
            label = "Paso ",
            connector = " de ",
            contentDescription = "Paso 2 de 3",
            variant = StepVariant.Bar,
        )
        // bar · "Paso 3 de 4" (keypad denominaciones).
        StepIndicator(
            current = 3,
            total = 4,
            label = "Paso ",
            connector = " de ",
            contentDescription = "Paso 3 de 4",
            variant = StepVariant.Bar,
        )
    }
}

@Preview(name = "StepIndicator · light", showBackground = true)
@Composable
private fun StepIndicatorLightPreview() {
    RecreTheme(darkTheme = false) {
        StepIndicatorPreviewContent()
    }
}

@Preview(name = "StepIndicator · dark", showBackground = true)
@Composable
private fun StepIndicatorDarkPreview() {
    RecreTheme(darkTheme = true) {
        StepIndicatorPreviewContent()
    }
}
