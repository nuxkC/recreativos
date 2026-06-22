package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// =====================================================================
// PasoTopBar · chrome propio del flujo de recaudación (rediseño F1, P1+M1).
// SSOT visual: docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md
// (§4 P1 cabecera de detalle, §5 M1 indicador de pasos).
//
// Cabecera de la identidad "Confianza Industrial" para los tres pasos del flujo
// (Contadores → Denominaciones → Confirmación). Para no malgastar altura, todo
// vive en UNA fila: back + (título / subtítulo) y, a la derecha, un indicador
// COMPACTO de segmentos ("• ▬ •") que marca el paso actual. Sustituye al bloque
// "Paso n de 3" que ocupaba una segunda línea entera bajo el título.
//
// Pensada para el slot `topBar` de un Scaffold: gestiona su propio inset de
// barra de estado (statusBarsPadding).
// =====================================================================

/**
 * Cabecera de un paso del flujo de recaudación.
 *
 * @param titulo título del paso (ya localizado por el llamador).
 * @param pasoActual índice 1-based del paso (Contadores=1, Denominaciones=2,
 *   Confirmación=3); los segmentos se rellenan hasta aquí.
 * @param onBack acción del back (la flecha). El llamador decide su semántica
 *   (liberar lock, confirmar descarte, etc.).
 * @param totalPasos total de pasos del flujo (3 por defecto).
 * @param subtitulo línea secundaria muted bajo el título (serie de máquina,
 *   "Máquina X de N"…); null la oculta.
 * @param backEnabled si false, la flecha queda deshabilitada (p. ej. mientras se
 *   guarda/imprime en Confirmación) — el back no debe interrumpir esas fases.
 */
@Composable
fun PasoTopBar(
    titulo: String,
    pasoActual: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    totalPasos: Int = 3,
    subtitulo: String? = null,
    backEnabled: Boolean = true,
) {
    val colors = RecreColors.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                enabled = backEnabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitulo != null) {
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            PasosSegmentos(
                current = pasoActual,
                total = totalPasos,
                contentDescription =
                    stringResource(R.string.recaudacion_step_descripcion, pasoActual, totalPasos),
            )
        }
    }
}

/**
 * Indicador compacto de pasos: una hilera de segmentos. Los pasos hechos/actual
 * van en `primary`; el actual es un pill más largo para fijar la posición; los
 * pendientes en muted tenue. Decorativo: el progreso real lo anuncia el
 * [contentDescription] único (no se leen los segmentos sueltos).
 */
@Composable
private fun PasosSegmentos(
    current: Int,
    total: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val activo = MaterialTheme.colorScheme.primary
    val inactivo = RecreColors.current.muted.copy(alpha = 0.3f)
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val esActual = i == current - 1
            val hecho = i < current
            Box(
                modifier =
                    Modifier
                        .height(6.dp)
                        .width(if (esActual) 22.dp else 8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (hecho) activo else inactivo),
            )
        }
    }
}

// ---------------------------------------------------------------------
// Previews (light / dark). Los literales van explícitos solo para la preview.
// ---------------------------------------------------------------------

@Composable
private fun PasoTopBarPreviewBody() {
    Column {
        PasoTopBar(
            titulo = "Contadores",
            pasoActual = 1,
            onBack = {},
            subtitulo = "Serie 0042-AB",
        )
        Spacer(Modifier.height(16.dp))
        PasoTopBar(
            titulo = "Desglose del total",
            pasoActual = 2,
            onBack = {},
            subtitulo = "Objetivo: 1.234,56 €",
        )
        Spacer(Modifier.height(16.dp))
        PasoTopBar(
            titulo = "Confirmar y firmar",
            pasoActual = 3,
            onBack = {},
        )
    }
}

@Preview(name = "PasoTopBar · light", showBackground = true)
@Composable
private fun PasoTopBarLightPreview() {
    RecreTheme(darkTheme = false) { PasoTopBarPreviewBody() }
}

@Preview(name = "PasoTopBar · dark", showBackground = true)
@Composable
private fun PasoTopBarDarkPreview() {
    RecreTheme(darkTheme = true) { PasoTopBarPreviewBody() }
}
