package com.recre.app.feature.incidencias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.Banda
import com.recre.app.ui.components.BandaTono
import com.recre.app.ui.components.Eyebrow
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreGhostButton
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusChipSize
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.theme.RecreColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R

/**
 * Centro de Incidencias del técnico (T-260). Ruta secundaria (no pestaña) con botón
 * atrás, calcada del patrón de [com.recre.app.feature.alertas.AlertasScreen]. Lista
 * en una sola pantalla las recaudaciones y averías que NO se subieron, con acciones
 * honestas según el motivo, y un recuento de lo que sigue en cola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidenciasScreen(
    onBack: () -> Unit,
    onRehacerRecaudacion: (instalacionId: String) -> Unit,
    onRehacerAveria: (maquinaId: String) -> Unit,
    viewModel: IncidenciasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.incidencias_titulo),
                onBack = onBack,
            )
        },
    ) { padding ->
        if (state.vacio) {
            VacioIncidencias(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Banda de cola pendiente (N8): mientras hay elementos en cola de red,
            // avisamos de que se subirán solos. No hay señal de conectividad en este
            // VM y la cola puede vaciarse estando online, así que el copy/icono no
            // afirman falta de red; el gate honesto es «queda algo en cola».
            if (state.enColaCount > 0) {
                item {
                    Banda(
                        texto = AnnotatedString(stringResource(R.string.incidencias_banda_offline)),
                        icon = Icons.Filled.CloudQueue,
                        tono = BandaTono.INFO,
                    )
                }
            }

            if (!state.sinBloqueadas) {
                val sinResolver = state.recaudaciones.size + state.averias.size
                item {
                    // Cabecera de sección en eyebrow mono con conteo (N8).
                    Eyebrow(
                        text = stringResource(
                            R.string.incidencias_seccion_sinresolver_conteo,
                            sinResolver,
                        ),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.incidencias_bloqueadas_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(state.recaudaciones, key = { "r-${it.id}" }) { r ->
                    IncidenciaCard(
                        tipo = stringResource(R.string.incidencias_tipo_recaudacion),
                        etiqueta = r.etiqueta,
                        // Importe money-safe vía el formateador canónico (incluye €).
                        detalle = formatEur(r.importe),
                        motivo = r.motivo,
                        intentos = r.intentos,
                        terminal = r.terminal,
                        hintTerminal = stringResource(R.string.incidencias_terminal_recaudacion),
                        onReintentar = { viewModel.reintentarRecaudacion(r.id) },
                        onRehacer = { onRehacerRecaudacion(r.instalacionId) },
                        onDescartar = { viewModel.descartarRecaudacion(r.id) },
                    )
                }
                items(state.averias, key = { "a-${it.id}" }) { a ->
                    IncidenciaCard(
                        tipo = stringResource(R.string.incidencias_tipo_averia),
                        etiqueta = a.etiqueta,
                        detalle = stringResource(R.string.incidencias_averia_categoria, a.categoria),
                        motivo = a.motivo,
                        intentos = a.intentos,
                        terminal = a.terminal,
                        hintTerminal = stringResource(R.string.incidencias_terminal_averia),
                        onReintentar = { viewModel.reintentarAveria(a.id) },
                        onRehacer = { onRehacerAveria(a.maquinaId) },
                        onDescartar = { viewModel.descartarAveria(a.id) },
                    )
                }
            }

            if (state.enColaCount > 0) {
                item {
                    Eyebrow(
                        text = stringResource(
                            R.string.incidencias_seccion_encola_conteo,
                            state.enColaCount,
                        ),
                    )
                }
                item { EnColaInfo(state.enColaCount) }
            }
        }
    }
}

/**
 * Tarjeta de una incidencia con BOTONES HONESTOS según el motivo:
 * - terminal ('fallida'): reintentar el mismo dato no la arregla → "Rehacer"
 *   (recontar / re-reportar) + "Descartar".
 * - no terminal ('error', red): "Reintentar" + "Descartar".
 */
@Composable
private fun IncidenciaCard(
    tipo: String,
    etiqueta: String,
    detalle: String,
    motivo: String,
    intentos: Int,
    terminal: Boolean,
    hintTerminal: String,
    onReintentar: () -> Unit,
    onRehacer: () -> Unit,
    onDescartar: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tipo coloreado por gravedad (N8): las bloqueadas son rechazos del
                // servidor → danger, no el acento de marca (primary).
                Text(
                    text = tipo.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = RecreColors.current.dangerText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Estado por ítem: terminal → «Rechazada» (danger); en reintento de
                // red → «Esperando red» (neutro, nunca rojo: no es un error).
                if (terminal) {
                    StatusChip(
                        role = StatusRole.DANGER,
                        label = stringResource(R.string.incidencias_estado_rechazada),
                        icon = Icons.Outlined.ErrorOutline,
                        size = StatusChipSize.SM,
                    )
                } else {
                    StatusChip(
                        role = StatusRole.NEUTRAL,
                        label = stringResource(R.string.incidencias_estado_esperando),
                        icon = Icons.Filled.Schedule,
                        size = StatusChipSize.SM,
                    )
                }
            }
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = detalle, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = motivo,
                style = MaterialTheme.typography.bodySmall,
                color = RecreColors.current.dangerText,
            )
            if (intentos > 0) {
                Text(
                    text = pluralStringResource(R.plurals.incidencias_intentos, intentos, intentos),
                    style = MaterialTheme.typography.labelSmall,
                    color = RecreColors.current.muted,
                )
            }
            if (terminal) {
                Text(
                    text = hintTerminal,
                    style = MaterialTheme.typography.bodySmall,
                    color = RecreColors.current.muted,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Descartar = acción arriesgada de bajo peso → fantasma peligroso
                // (borde/texto danger), sin gastar el acento sólido.
                RecreGhostButton(
                    text = stringResource(R.string.cola_bloqueadas_descartar),
                    onClick = onDescartar,
                    mini = true,
                    danger = true,
                )
                Spacer(Modifier.width(8.dp))
                // Acción de avance = CTA sólido: «Rehacer» si es terminal (reintentar
                // el mismo dato no la arregla), «Reintentar» si es de red.
                if (terminal) {
                    RecrePrimaryButton(
                        text = stringResource(R.string.incidencias_rehacer),
                        onClick = onRehacer,
                        fullWidth = false,
                        glow = false,
                    )
                } else {
                    RecrePrimaryButton(
                        text = stringResource(R.string.cola_bloqueadas_reintentar),
                        onClick = onReintentar,
                        fullWidth = false,
                        glow = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnColaInfo(count: Int) {
    // La cola local (Room) no expone los ítems individuales ni su importe en el
    // UiState (solo el recuento agregado): mostramos el recuento honesto. Ver el
    // report para la limitación (requiere ampliar IncidenciasViewModel).
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = pluralStringResource(R.plurals.incidencias_encola, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = RecreColors.current.infoText,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VacioIncidencias(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.incidencias_vacio),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
