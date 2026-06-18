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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
            if (!state.sinBloqueadas) {
                item {
                    SeccionHeader(stringResource(R.string.incidencias_seccion_bloqueadas))
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
                        detalle = stringResource(R.string.cola_bloqueadas_importe, r.importe),
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
                item { SeccionHeader(stringResource(R.string.incidencias_seccion_encola)) }
                item { EnColaInfo(state.enColaCount) }
            }
        }
    }
}

@Composable
private fun SeccionHeader(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
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
            Text(
                text = tipo.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = detalle, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = motivo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (intentos > 0) {
                Text(
                    text = pluralStringResource(R.plurals.incidencias_intentos, intentos, intentos),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (terminal) {
                Text(
                    text = hintTerminal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RecreTextButton(
                    text = stringResource(R.string.cola_bloqueadas_descartar),
                    onClick = onDescartar,
                )
                Spacer(Modifier.width(8.dp))
                if (terminal) {
                    FilledTonalButton(onClick = onRehacer) {
                        Text(stringResource(R.string.incidencias_rehacer))
                    }
                } else {
                    FilledTonalButton(onClick = onReintentar) {
                        Text(stringResource(R.string.cola_bloqueadas_reintentar))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnColaInfo(count: Int) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = pluralStringResource(R.plurals.incidencias_encola, count, count),
            style = MaterialTheme.typography.bodyMedium,
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
