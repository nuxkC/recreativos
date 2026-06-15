package com.recre.app.feature.deudas

import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.components.formatEur

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import java.math.BigDecimal

/**
 * Índice de la sección Deudas en gestión (T-219): lista de locales con su saldo
 * de deuda. Tocar un local abre su ficha de deudas ([DeudasLocalScreen]), que es
 * el centro de mando (préstamo, pago en efectivo, condonar, %).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeudasGestorScreen(
    onLocalClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DeudasGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deudas_gestor_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
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
                    Text(
                        text = stringResource(R.string.deudas_gestor_vacio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.locales, key = { it.localId }) { local ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLocalClick(local.localId) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = local.nombre, style = MaterialTheme.typography.titleSmall)
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
