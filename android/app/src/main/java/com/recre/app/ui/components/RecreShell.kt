package com.recre.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.recre.app.R
import com.recre.app.feature.shell.ShellViewModel

// Design System "Confianza Industrial" — app shell de pulgar (Fase 4 · T-234).
// SSOT IA: .kiro/specs/recre/functional-audit-and-ia.md §4 (Android).
//
// Sustituye el menú overflow (⋮) sobrecargado de Locales —el problema
// estructural raíz (T-2)— por una NAVEGACIÓN DE PULGAR de 4 pestañas + un top
// bar global con la campana de alertas (badge) y el botón de sincronizar
// visible. Cada pestaña conserva su propio Scaffold y monta `RecreBottomBar`
// como `bottomBar` y `RecreTopBarActions` en las `actions` de su TopAppBar, de
// modo que solo hay UN Scaffold activo a la vez (sin anidamiento ni doble
// inset). Las pantallas de detalle se abren full-screen encima, sin barra.

/** Las 4 pestañas de pulgar (IA §4). El orden fija el de la barra inferior. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LOCALES("locales", R.string.nav_locales, Icons.Filled.Storefront),
    HISTORICO("historico", R.string.nav_historico, Icons.Filled.History),
    GESTION("gestion", R.string.nav_gestion, Icons.Filled.Build),
    AJUSTES("ajustes", R.string.nav_ajustes, Icons.Filled.Settings),
    ;

    companion object {
        /** La pestaña cuya ruta es [route], o null si la ruta no es top-level. */
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * Navega a una pestaña con el patrón estándar de bottom-nav: salva el estado de
 * la pestaña que se abandona, evita apilar duplicados (`launchSingleTop`) y
 * restaura el estado previo de la pestaña destino. `Locales` es el hogar: el
 * back desde cualquier pestaña vuelve a Locales.
 */
fun NavController.navigateTab(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(TopLevelDestination.LOCALES.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Barra de navegación inferior (thumb-zone) con las 4 pestañas. */
@Composable
fun RecreBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = dest == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = {
                    // La label de abajo nombra el destino (a11y); el icono no
                    // repite contentDescription para no duplicar el anuncio.
                    Icon(imageVector = dest.icon, contentDescription = null)
                },
                label = { Text(text = stringResource(dest.labelRes), maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Acciones GLOBALES del top bar (se colocan en `TopAppBar(actions = { ... })`):
 *  - ↻ Sincronizar: spinner mientras hay sync en curso, si no un botón que la
 *    fuerza (mismo efecto que el pull-to-refresh de Locales).
 *  - 🔔 Campana de alertas con badge de conteo (→ [onAlertasClick]).
 *
 * Lee [ShellViewModel] (Hilt) para el conteo y el estado de sync, y recuenta al
 * volver el foco a la pestaña. Como cada pestaña la instancia por separado, el
 * VM se scopea a cada back-stack entry pero observa los mismos flujos: coherente.
 */
@Composable
fun RecreTopBarActions(
    onAlertasClick: () -> Unit,
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refrescarAlertas()
        onPauseOrDispose {}
    }

    if (state.sincronizando) {
        Box(modifier = Modifier.padding(horizontal = 12.dp).size(20.dp)) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.fillMaxSize())
        }
    } else {
        IconAction(
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.sync_force),
            onClick = viewModel::forzarSync,
        )
    }

    NotificationBadge(
        count = state.totalAlertas,
        contentDescription = stringResource(R.string.nav_alertas_badge),
        onClick = onAlertasClick,
    )
}
