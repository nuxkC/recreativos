package com.recre.app.feature.cuadre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.cuadre.domain.LineaCuadre
import com.recre.app.feature.cuadre.domain.VeredictoCuadre
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.FieldNum
import com.recre.app.ui.components.IconAction
import com.recre.app.ui.components.LottieIllustration
import com.recre.app.ui.components.MoneyTextRole
import com.recre.app.ui.components.OdometroText
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusChipSize
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.components.formatearImporteEs
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")
private val SEMANA_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", ES)

/** Subtítulo de la semana ISO mostrada: "dd/MM – dd/MM" (lunes a domingo). */
private fun rangoSemana(semanaInicio: LocalDate): String {
    val fin = semanaInicio.plusDays(6)
    return "${semanaInicio.format(SEMANA_FORMAT)} – ${fin.format(SEMANA_FORMAT)}"
}

/**
 * Pantalla "Mi caja" (cuadre semanal): el técnico cuenta el efectivo físico y la
 * pantalla lo compara con lo que el servidor dice que debería llevar
 * ([CuadreUiState.Listo]). El veredicto (cuadra/falta/sobra) y todas las cifras
 * vienen ya calculados del dominio (SSOT): la UI nunca recalcula dinero.
 *
 * Navegación de semanas con ‹ › en la barra (delega en
 * [CuadreViewModel.onCambiarSemana]); el rango de la semana va en el subtítulo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuadreScreen(
    onBack: () -> Unit,
    viewModel: CuadreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subtitulo = (state as? CuadreUiState.Listo)?.let { rangoSemana(it.semana) }
        ?: (state as? CuadreUiState.Vacio)?.let { rangoSemana(it.semana) }

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.cuadre_titulo),
                subtitulo = subtitulo,
                onBack = onBack,
                actions = {
                    IconAction(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cuadre_semana_anterior),
                        onClick = { viewModel.onCambiarSemana(-1) },
                    )
                    IconAction(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cuadre_semana_siguiente),
                        onClick = { viewModel.onCambiarSemana(1) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (val s = state) {
                is CuadreUiState.Cargando -> EstadoMensaje(stringResource(R.string.locales_vacio_cargando))
                is CuadreUiState.SinConexion -> EstadoMensaje(stringResource(R.string.cuadre_sin_conexion))
                is CuadreUiState.Vacio -> EstadoMensaje(stringResource(R.string.cuadre_vacio))
                is CuadreUiState.BloqueadoPorPendientes -> BloqueadoCard(
                    reintentables = s.reintentables,
                    fallidas = s.fallidas,
                    onSubir = viewModel::onSubirPendientes,
                )
                is CuadreUiState.Listo -> ListoContenido(
                    estado = s,
                    onContar = viewModel::onContarChange,
                )
            }
        }
    }
}

/** Mensaje centrado para los estados sin contenido (cargando/vacío/sin conexión). */
@Composable
private fun EstadoMensaje(texto: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = RecreColors.current.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Cuadre bloqueado: hay recaudaciones sin subir. Las reintentables ofrecen
 * "Subir ahora" (drena la cola); las fallidas solo se pueden resolver desde el
 * panel de subidas (texto informativo, sin acción aquí).
 */
@Composable
private fun BloqueadoCard(reintentables: Int, fallidas: Int, onSubir: () -> Unit) {
    AppCard {
        Column {
            Text(
                text = stringResource(R.string.cuadre_bloqueado_titulo),
                style = MaterialTheme.typography.titleMedium,
            )
            if (reintentables > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.cuadre_bloqueado_reintentables, reintentables),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RecreColors.current.muted,
                )
                Spacer(Modifier.height(4.dp))
                RecreTextButton(
                    text = stringResource(R.string.cuadre_subir_ahora),
                    onClick = onSubir,
                )
            }
            if (fallidas > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.cuadre_bloqueado_fallidas, fallidas),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RecreColors.current.muted,
                )
            }
        }
    }
}

/**
 * Estado listo: héroe "Deberías llevar" (OdometroText), veredicto (StatusChip) y
 * la tabla por denominación con el conteo físico editable. Al cuadrar, remata con
 * la ilustración de éxito. Todas las cifras vienen del dominio (no se recalculan).
 */
@Composable
private fun ListoContenido(
    estado: CuadreUiState.Listo,
    onContar: (BigDecimal, Long) -> Unit,
) {
    val dif = estado.diferencia
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.cuadre_deberias_llevar),
                        style = MaterialTheme.typography.labelLarge,
                        color = RecreColors.current.muted,
                    )
                    Spacer(Modifier.height(4.dp))
                    OdometroText(
                        texto = formatEur(dif.totalEsperado),
                        style = RecreType.importe,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.cuadre_de_n_recaudaciones,
                            estado.numRecaudaciones,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RecreColors.current.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.cuadre_llevas,
                            formatearImporteEs(dif.totalContado.toPlainString()) + " €",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    VeredictoChip(estado)
                    if (dif.veredicto == VeredictoCuadre.CUADRA) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            LottieIllustration(rawRes = R.raw.cuadre_ok)
                        }
                    }
                }
            }
        }

        item { CabeceraTabla() }

        items(dif.lineas, key = { it.denominacion.toPlainString() }) { linea ->
            FilaDenominacion(linea = linea, onContar = onContar)
        }
    }
}

/** Chip del veredicto: success=cuadra, danger=falta, warning=sobra (no solo color). */
@Composable
private fun VeredictoChip(estado: CuadreUiState.Listo) {
    val dif = estado.diferencia
    val diferenciaAbs = formatearImporteEs(dif.diferencia.abs().toPlainString()) + " €"
    when (dif.veredicto) {
        VeredictoCuadre.CUADRA -> StatusChip(
            role = StatusRole.SUCCESS,
            label = stringResource(R.string.cuadre_cuadra),
            icon = Icons.Filled.Check,
            size = StatusChipSize.LG,
        )
        VeredictoCuadre.FALTA -> StatusChip(
            role = StatusRole.DANGER,
            label = stringResource(R.string.cuadre_faltan, diferenciaAbs),
            icon = Icons.Outlined.ErrorOutline,
            size = StatusChipSize.LG,
        )
        VeredictoCuadre.SOBRA -> StatusChip(
            role = StatusRole.WARNING,
            label = stringResource(R.string.cuadre_sobran, diferenciaAbs),
            icon = Icons.Filled.Warning,
            size = StatusChipSize.LG,
        )
    }
}

/** Cabecera de la tabla de denominaciones (Denominación · Deberías · Tú cuentas). */
@Composable
private fun CabeceraTabla() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.cuadre_col_denominacion),
            style = MaterialTheme.typography.labelMedium,
            color = RecreColors.current.muted,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = stringResource(R.string.cuadre_col_deberias),
            style = MaterialTheme.typography.labelMedium,
            color = RecreColors.current.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.cuadre_col_cuentas),
            style = MaterialTheme.typography.labelMedium,
            color = RecreColors.current.muted,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * Una fila de la tabla: denominación, lo esperado y el campo numérico donde el
 * técnico teclea cuántos billetes/monedas cuenta. El conteo va por el teclado
 * numérico del sistema (FieldNum entero) y el delta se muestra al lado.
 */
@Composable
private fun FilaDenominacion(linea: LineaCuadre, onContar: (BigDecimal, Long) -> Unit) {
    val denominacion = formatearImporteEs(linea.denominacion.toPlainString()) + " €"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = denominacion,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = linea.cantidadEsperada.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f),
        )
        Spacer(Modifier.width(8.dp))
        FieldNum(
            value = if (linea.cantidadContada == 0L) "" else linea.cantidadContada.toString(),
            onValueChange = { txt -> onContar(linea.denominacion, txt.toLongOrNull() ?: 0L) },
            label = stringResource(R.string.cuadre_cantidad_denominacion, denominacion),
            isDecimal = false,
            modifier = Modifier.weight(1.4f),
        )
    }
}
