package com.recre.app.feature.deudas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.EmptyState
import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.components.recreSharedBounds
import com.recre.app.ui.theme.RecreShapes
import java.math.BigDecimal

/**
 * Índice de la sección Deudas en gestión (T-219): lista de locales con su saldo
 * de deuda. Tocar un local abre su ficha de deudas ([DeudasLocalScreen]), que es
 * el centro de mando (préstamo, pago en efectivo, condonar, %).
 *
 * Rediseño (F3): chrome propio (`RecreDetailTopBar`), héroe del capital total
 * (`Surface` acentuado) y filas con `AppCard`. Las cifras siguen siendo
 * money-safe (`formatEur` sobre `BigDecimal`); nada se recalcula en cliente.
 */
@Composable
fun DeudasGestorScreen(
    onLocalClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DeudasGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.deudas_gestor_titulo),
                onBack = onBack,
            )
        },
    ) { padding ->
        if (!state.cargado) {
            // T-241: esqueleto de carga en vez de spinner (plan §3.1).
            ListSkeleton(
                loadingLabel = stringResource(R.string.cargando),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("capital") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RecreShapes.large,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.deudas_gestor_capital),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = eur(state.capitalTotal),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }

            if (state.locales.isEmpty()) {
                item("vacio") {
                    EmptyState(
                        icon = Icons.Filled.Payments,
                        title = stringResource(R.string.deudas_gestor_vacio),
                        description = stringResource(R.string.deudas_gestor_vacio_desc),
                        compact = true,
                    )
                }
            } else {
                items(state.locales, key = { it.localId }) { local ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLocalClick(local.localId) },
                    ) {
                        Column {
                            Text(
                                text = local.nombre,
                                style = MaterialTheme.typography.titleSmall,
                                // T-244: comparte el nombre con la cabecera del detalle de deudas.
                                modifier = Modifier.recreSharedBounds("deuda-nombre-${local.localId}"),
                            )
                            Text(
                                text = eur(local.saldoTotal),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Text(
                                text = stringResource(
                                    R.string.deudas_gestor_desglose,
                                    eur(local.saldoTolva),
                                    eur(local.saldoPrestamo),
                                    local.numDeudas,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Formato de euros es-ES: «1.234,50 €». Delega en el canónico money-safe. */
private fun eur(v: BigDecimal): String = formatEur(v)
