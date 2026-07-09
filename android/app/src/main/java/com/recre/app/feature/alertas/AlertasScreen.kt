package com.recre.app.feature.alertas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.Eyebrow
import com.recre.app.ui.components.Pip
import com.recre.app.ui.components.PipRole
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreGhostButton
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreShapes
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.Alerta
import com.recre.app.core.data.repository.TipoAlerta
import com.recre.app.feature.shell.ShellViewModel
import java.time.Duration
import java.time.Instant

/**
 * Pantalla "Alertas" / centro de alertas (T-64 · T-236).
 *
 * Lista las alertas in-app pendientes de la empresa activa (incluidos los
 * **descuadres**, que llegan como conflictos). Cada alerta tiene un icono
 * semántico según `tipo`, un mensaje y "Marcar como leída" (UPDATE leida=true);
 * "Marcar todas" vacía de un golpe.
 *
 * Sobre la lista, si hay elementos creados offline aún sin subir (recaudaciones
 * o averías), un aviso de "sin sincronizar" con "Sincronizar ahora" (T-236): así
 * el centro refleja lo mismo que suma el badge de la campana (alertas + sync
 * pendiente).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    onBack: () -> Unit,
    onAlertaClick: (Alerta) -> Unit,
    viewModel: AlertasViewModel = hiltViewModel(),
    shellViewModel: ShellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shell by shellViewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.alertas_titulo),
                onBack = onBack,
                // Subtítulo mono «N SIN LEER» (N8): el top bar lo pinta en eyebrow
                // uppercase. Solo si hay no-leídas; si todo está leído, sin subtítulo.
                subtitulo =
                    if (state.sinLeer > 0) {
                        pluralStringResource(R.plurals.alertas_sin_leer, state.sinLeer, state.sinLeer)
                    } else {
                        null
                    },
                actions = {
                    // «Marcar todas» solo tiene sentido si queda algo por leer (N8).
                    if (state.sinLeer > 0) {
                        RecreTextButton(
                            text = stringResource(R.string.alertas_marcar_todas),
                            onClick = viewModel::marcarTodasLeidas,
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.cargando,
            onRefresh = viewModel::refrescar,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Centro de alertas (T-236): además de las alertas del backend,
                // avisa de lo creado offline aún sin subir (recaudaciones +
                // averías) = lo que el badge de la campana suma como "pendiente".
                if (shell.pendientesSync > 0) {
                    SyncPendienteBanner(
                        pendientes = shell.pendientesSync,
                        sincronizando = shell.sincronizando,
                        onSincronizar = shellViewModel::forzarSync,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                state.error?.let { code ->
                    ErrorBanner(
                        textRes = errorTextoRes(code),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (state.alertas.isEmpty()) {
                    EmptyState(
                        cargando = state.cargando,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.alertas, key = { it.id }) { alerta ->
                            AlertaCard(
                                alerta = alerta,
                                onClick = { onAlertaClick(alerta) },
                                onMarcarLeida = { viewModel.marcarLeida(alerta.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Aviso "sin sincronizar" del centro de alertas (T-236): cuántos elementos
 * creados offline siguen sin subir (recaudaciones + averías) + acción para
 * forzar la sync. Es estado PENDIENTE, no error → tono neutro-info
 * (secondaryContainer), nunca danger (regla T-1 de la auditoría de color).
 */
@Composable
private fun SyncPendienteBanner(
    pendientes: Int,
    sincronizando: Boolean,
    onSincronizar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RecreShapes.large,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.CloudOff, contentDescription = null)
            Text(
                text = pluralStringResource(R.plurals.alertas_sync_pendiente, pendientes, pendientes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            RecreTonalButton(
                text = stringResource(R.string.sync_force),
                onClick = onSincronizar,
                enabled = !sincronizando,
            )
        }
    }
}

@Composable
private fun AlertaCard(
    alerta: Alerta,
    onClick: () -> Unit,
    onMarcarLeida: () -> Unit,
) {
    // N8: la card vive sobre superficie + borde (AppCard), NO con el container
    // entero tintado. La severidad la comunica el Pip a la izquierda (color +
    // forma del icono), no el fondo. Las leídas se atenúan al 55 %.
    val (icon, role) = pipDeAlerta(alerta.tipo)
    AppCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (alerta.leida) Modifier.alpha(0.55f) else Modifier),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Pip(icon = icon, role = role)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(tipoTextoRes(alerta.tipo)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // Hora relativa arriba-derecha, muted (labelSmall).
                Text(
                    text = formatRelativo(alerta.creadaEn),
                    style = MaterialTheme.typography.labelSmall,
                    color = RecreColors.current.muted,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = alerta.mensaje,
                style = MaterialTheme.typography.bodyMedium,
            )
            // «Marcar como leída» como enlace (ghost mini), NO botón tonal. Las
            // ya leídas no lo muestran: no hay nada que marcar.
            if (!alerta.leida) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    RecreGhostButton(
                        text = stringResource(R.string.alertas_marcar_leida),
                        onClick = onMarcarLeida,
                        mini = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(textRes: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.large,
    ) {
        Text(
            text = stringResource(textRes),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun EmptyState(cargando: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (cargando) {
                    stringResource(R.string.alertas_vacio_cargando)
                } else {
                    stringResource(R.string.alertas_vacio_sin_alertas)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Icono + rol de severidad del Pip por tipo de alerta (mockup «Neón de sala», N8):
 * - conflicto/descuadre → cian (ACCENT), triángulo: dato a revisar, no fallo.
 * - licencia próxima a caducar → ámbar (WARNING), calendario.
 * - recaudación anulada → rojo (DANGER), info: acción destructiva ya aplicada.
 * - local sin recaudar → ámbar (WARNING), tienda.
 * - otro → neutro (NEUTRAL), campana.
 */
private fun pipDeAlerta(tipo: TipoAlerta): Pair<ImageVector, PipRole> = when (tipo) {
    TipoAlerta.RecaudacionConflicto -> Icons.Filled.Warning to PipRole.ACCENT
    TipoAlerta.LicenciaCaducidad -> Icons.Filled.DateRange to PipRole.WARNING
    TipoAlerta.RecaudacionAnulada -> Icons.Filled.Info to PipRole.DANGER
    TipoAlerta.LocalSinRecaudar -> Icons.Filled.Storefront to PipRole.WARNING
    TipoAlerta.Otro -> Icons.Filled.Notifications to PipRole.NEUTRAL
}

@Composable
private fun tipoTextoRes(tipo: TipoAlerta): Int = when (tipo) {
    TipoAlerta.RecaudacionConflicto -> R.string.alertas_tipo_conflicto
    TipoAlerta.RecaudacionAnulada -> R.string.alertas_tipo_anulada
    TipoAlerta.LicenciaCaducidad -> R.string.alertas_tipo_licencia
    TipoAlerta.LocalSinRecaudar -> R.string.alertas_tipo_local_sin_recaudar
    TipoAlerta.Otro -> R.string.alertas_tipo_otro
}

@Composable
private fun errorTextoRes(code: AlertasErrorCode): Int = when (code) {
    AlertasErrorCode.Network -> R.string.alertas_error_network
    AlertasErrorCode.Auth -> R.string.alertas_error_auth
    AlertasErrorCode.Unknown -> R.string.alertas_error_generic
}

@Composable
private fun formatRelativo(instant: Instant): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> stringResource(R.string.relativo_ahora)
        minutes < 60 -> context.getString(R.string.relativo_minutos, minutes)
        minutes < 60 * 24 -> context.getString(R.string.relativo_horas, minutes / 60)
        else -> context.getString(R.string.relativo_dias, minutes / (60 * 24))
    }
}
