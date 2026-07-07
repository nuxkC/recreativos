package com.recre.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.recre.app.R
import com.recre.app.feature.shell.ShellViewModel
import com.recre.app.ui.theme.RecreColors

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

/**
 * Barra de navegación inferior (thumb-zone) con las 4 pestañas, re-skin neón:
 * píldora flotante (Surface redondeada a 999dp, surface-1 al 92% + borde) separada
 * del borde inferior en vez de una `NavigationBar` a ancho completo. Icono+label
 * activos en `accentBright`, inactivos en `muted`.
 *
 * Al dejar de usar `NavigationBar` (que aplicaba `WindowInsets.navigationBars` por
 * defecto) hay que reconstruir ese inset a mano con `navigationBarsPadding()`, para
 * que la píldora no quede bajo la barra de gestos / 3 botones del sistema.
 */
@Composable
fun RecreBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recreColors = RecreColors.current
    Surface(
        modifier =
            modifier
                .navigationBarsPadding() // reconstruye el inset que daba NavigationBar
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .fillMaxWidth()
                .height(62.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, recreColors.border),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { dest ->
                val selected = dest == current
                // Icono + label comparten tint: acento vivo si activo, muted si no.
                val tint = if (selected) recreColors.accentBright else recreColors.muted
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight() // touch target = alto de la píldora (62dp ≥ 48dp)
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(dest) },
                                role = Role.Tab,
                            ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // La label de abajo nombra el destino (a11y); el icono no
                    // repite contentDescription para no duplicar el anuncio.
                    Icon(imageVector = dest.icon, contentDescription = null, tint = tint)
                    Text(
                        text = stringResource(dest.labelRes),
                        maxLines = 1,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * Acciones GLOBALES del top bar (se colocan en `TopAppBar(actions = { ... })`):
 *  - ↻ Sincronizar: spinner mientras hay sync en curso, si no un botón que la
 *    fuerza (mismo efecto que el pull-to-refresh de Locales).
 *  - ⚠️ Incidencias (T-261): badge ROJO (DANGER) con el nº de recaudaciones+averías
 *    BLOQUEADAS que el técnico debe resolver (→ [onIncidenciasClick]). Solo aparece
 *    si hay alguna (count==0 lo oculta).
 *  - 🔔 Campana de alertas (→ [onAlertasClick]): ahora SOLO los avisos del gestor
 *    ([ShellUiState.alertasBackend]); lo accionable se separó al badge de incidencias.
 *
 * Lee [ShellViewModel] (Hilt) para los conteos y el estado de sync, y recuenta al
 * volver el foco a la pestaña. Como cada pestaña la instancia por separado, el
 * VM se scopea a cada back-stack entry pero observa los mismos flujos: coherente.
 */
@Composable
fun RecreTopBarActions(
    onAlertasClick: () -> Unit,
    onIncidenciasClick: () -> Unit,
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
        count = state.incidencias,
        contentDescription = stringResource(R.string.nav_incidencias_badge),
        onClick = onIncidenciasClick,
        role = BadgeRole.DANGER,
        icon = Icons.Filled.Warning,
    )

    NotificationBadge(
        count = state.alertasBackend,
        contentDescription = stringResource(R.string.nav_alertas_badge),
        onClick = onAlertasClick,
    )
}
