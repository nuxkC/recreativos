package com.recre.app.feature.gestion.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost

/**
 * Andamiaje común de los formularios del CRUD gestor (T-66..T-69) —
 * rediseño F3·P5 / neón N9: chrome propio (`RecreDetailTopBar`), columna con
 * scroll para los campos y "Guardar" (`RecrePrimaryButton`, único primario) en
 * un PIE FIJO fuera del scroll (mockup de sala: la acción principal siempre
 * visible al fondo). El banner offline queda en cabecera del contenido.
 * Cada formulario solo aporta sus campos (`Field*`/`GestionTextField`).
 */
@Composable
fun GestionFormScaffold(
    titulo: String,
    onBack: () -> Unit,
    cargando: Boolean,
    online: Boolean,
    snackbarHost: SnackbarHostState,
    guardarLabel: String,
    guardando: Boolean,
    onGuardar: () -> Unit,
    guardarEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { RecreDetailTopBar(titulo = titulo, onBack = onBack) },
        snackbarHost = { RecreSnackbarHost(snackbarHost) },
        bottomBar = {
            // Pie fijo: "Guardar" nunca entra en el scroll, siempre visible al
            // fondo. navigationBarsPadding lo separa de la barra de sistema.
            // Se oculta mientras carga (aún no hay formulario que guardar).
            if (!cargando) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RecrePrimaryButton(
                        text = guardarLabel,
                        onClick = onGuardar,
                        enabled = guardarEnabled && online && !guardando,
                        loading = guardando,
                        fullWidth = true,
                        glow = true,
                    )
                }
            }
        },
    ) { padding ->
        if (cargando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) {
                OfflineBanner(modifier = Modifier.fillMaxWidth())
            }
            content()
        }
    }
}
