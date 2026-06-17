package com.recre.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// =====================================================================
// PasoTopBar · chrome propio del flujo de recaudación (rediseño F1, P1+M1).
// SSOT visual: docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md
// (§4 P1 cabecera de detalle, §5 M1 indicador de pasos animado).
//
// Sustituye al TopAppBar M3 "gris" genérico de los tres pasos del flujo
// (Contadores → Denominaciones → Confirmación) por una cabecera de la identidad
// "Confianza Industrial": back + título a la izquierda y, bajo el título, el
// [StepIndicator] en variante barra ("Paso n de 3") que marca el avance, más un
// subtítulo opcional (serie de la máquina / objetivo / cadena).
//
// Pensada para el slot `topBar` de un Scaffold: gestiona su propio inset de
// barra de estado (statusBarsPadding), igual que hacía el TopAppBar. El nº de
// paso es constante por pantalla (cada destino sabe si es 1, 2 o 3): la barra se
// pinta llena hasta ese segmento; el "morph" continuo entre pantallas es pulido
// diferido (shared element, Fase 5).
// =====================================================================

// El IconAction reserva su propio target táctil de 48dp; el contenido (indicador
// + subtítulo) se sangra para alinearse con el texto del título, no con el icono.
private val ContentIndent = 48.dp

/**
 * Cabecera de un paso del flujo de recaudación.
 *
 * @param titulo título del paso (ya localizado por el llamador).
 * @param pasoActual índice 1-based del paso (Contadores=1, Denominaciones=2,
 *   Confirmación=3); el StepIndicator pinta la barra llena hasta aquí.
 * @param onBack acción del back (la flecha). El llamador decide su semántica
 *   (liberar lock, confirmar descarte, etc.).
 * @param totalPasos total de pasos del flujo (3 por defecto).
 * @param subtitulo línea secundaria muted bajo el indicador (serie de máquina,
 *   objetivo, "Máquina X de N"…); null la oculta.
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
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                    enabled = backEnabled,
                )
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(modifier = Modifier.padding(start = ContentIndent, end = 4.dp)) {
                StepIndicator(
                    current = pasoActual,
                    total = totalPasos,
                    label = stringResource(R.string.recaudacion_step_label),
                    connector = stringResource(R.string.recaudacion_step_connector),
                    contentDescription =
                        stringResource(R.string.recaudacion_step_descripcion, pasoActual, totalPasos),
                    variant = StepVariant.Bar,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (subtitulo != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
