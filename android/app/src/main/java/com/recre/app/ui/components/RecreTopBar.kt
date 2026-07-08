package com.recre.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreType

// =====================================================================
// Chrome de app (P1) — Design System "Confianza Industrial".
// SSOT: docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md §4 (P1).
//
// Envuelve TopAppBar M3 DENTRO de la librería para que las pantallas de feature/
// no importen Material pelado (el guardarraíl SinMaterialPeladoTest solo prohíbe
// TopAppBar en feature/, no en ui/). Dos variantes:
//  - RecreTopBar:       top-level (tabs). Título + slot actions (RecreTopBarActions).
//  - RecreDetailTopBar: secundaria (detalle). Back + título + slot actions (overflow).
// Sobre surface, sin sombra (la elevación es por borde, regla del sistema).
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun recreTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = RecreColors.current.muted,
        actionIconContentColor = RecreColors.current.muted,
    )

@Composable
private fun TopBarTitle(titulo: String, subtitulo: String?, tituloModifier: Modifier) {
    Column {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = tituloModifier,
        )
        if (subtitulo != null) {
            // S9: el contexto de la cabecera va en eyebrow mono uppercase
            // («KONG · A1B123 — BAR GIPUZKOA», «12 REGISTRADAS»), como el mockup.
            Text(
                text = subtitulo.uppercase(),
                style = RecreType.eyebrow,
                color = RecreColors.current.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Cabecera de pantalla top-level (pestaña). [titulo] como marca/contexto y un
 * [subtitulo] muted opcional; [actions] aloja los iconos globales del shell
 * (sync, incidencias, alertas) vía RecreTopBarActions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecreTopBar(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { TopBarTitle(titulo = titulo, subtitulo = subtitulo, tituloModifier = Modifier) },
        actions = actions,
        colors = recreTopBarColors(),
    )
}

/**
 * Cabecera de pantalla secundaria (detalle): flecha de back + [titulo]. El
 * [tituloModifier] permite enganchar el shared element del título (T-244). El
 * slot [actions] aloja el overflow ⋮ u otras acciones de la entidad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecreDetailTopBar(
    titulo: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tituloModifier: Modifier = Modifier,
    subtitulo: String? = null,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { TopBarTitle(titulo = titulo, subtitulo = subtitulo, tituloModifier = tituloModifier) },
        navigationIcon = {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconActionTile(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                    enabled = backEnabled,
                )
            }
        },
        actions = actions,
        colors = recreTopBarColors(),
    )
}
