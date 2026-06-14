package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType

// =====================================================================
// NotificationBadge · F3-BADGE-NUM-001 — átomo del design system.
// SSOT: .kiro/specs/recre/fase3-component-specs.md (## NotificationBadge).
//
// Indicador NUMÉRICO de conteo superpuesto a la esquina superior-derecha de un
// icono accionable (campana de la TopBar). Comunica un número (1..99) o, a
// partir de 100, el overflow "+99". La variante DOT (sin número) sólo dice "hay
// algo nuevo" sin cuantificar.
//
// El COLOR codifica SEVERIDAD, no decoración:
//  - DANGER  → SOLO si el conteo son alertas críticas reales (averías,
//              descuadres, conflictos). Nunca para novedades genéricas.
//  - PRIMARY → novedad / no-crítico (acento de marca).
//  - NEUTRAL → informativo (state-neutral): el rol neutro no codifica severidad
//              por color sino por presencia; lleva borde para destacar del halo.
//
// Reglas no negociables del átomo:
//  - El badge es DECORATIVO (clearAndSetSemantics{}): el CONTEO REAL viaja
//    SIEMPRE en el contentDescription del BOTÓN anfitrión (aria-label), aunque
//    el glifo diga "+99". El badge nunca es tapeable por separado.
//  - El target táctil ≥48dp y el foco son del IconButton anfitrión, no del badge.
//  - count == 0 → estado "hidden": el badge NO se renderiza (ni dot ni pill);
//    queda sólo la campana limpia.
//  - Mono TABULAR para el número (los dígitos no bailan al actualizarse).
//
// NOTA DE ADAPTACIÓN (golden rule: no inventar tokens):
//  - El bloque de spec referencia `RecreType.badgeMono`, que NO existe en
//    Type.kt. Se usa el token mono tabular real más cercano —`cifraCaption`
//    (13/500 tabular)— forzado a 12sp, tal como pide la tabla del spec.
//  - El icono base lo dibuja Material Icons (`Outlined.Notifications`), única
//    librería disponible; el spec dibuja Phosphor.Bell (no está en el proyecto).
// =====================================================================

/** Rol semántico del badge. El color codifica severidad, no decoración. */
enum class BadgeRole {
    /** Alertas críticas REALES (averías, descuadres, conflictos). Rojo. */
    DANGER,

    /** Novedad / no-crítico. Acento de marca (primary). */
    PRIMARY,

    /** Informativo (state-neutral). No codifica severidad por color, sino por presencia. */
    NEUTRAL,
}

/** Par fondo/texto + borde resuelto por rol+modo para el overlay del badge. */
private data class BadgeColors(val fill: Color, val fg: Color, val border: Color?)

/** Límite de overflow: a partir de 100 el glifo muestra "+99". El número exacto va al host. */
private const val OVERFLOW_LIMIT = 99

/**
 * Botón-icono con badge numérico superpuesto (campana de la TopBar).
 *
 * El anfitrión es un `IconButton` de ≥48dp: es él quien recibe el foco, el
 * objetivo táctil y el conteo real en su [contentDescription]; el badge es un
 * overlay puramente decorativo.
 *
 * @param count conteo real (≥0). 0 oculta el badge (sólo la campana). El número
 *   exacto se anuncia siempre por el host aunque el glifo muestre "+99".
 * @param contentDescription label accesible COMPLETO del botón (i18n, vía
 *   stringResource). Debe incluir el conteo real ("Notificaciones, 134 sin
 *   leer"), no el "+99" recortado. Lo construye el llamador; aquí no se hardcodea.
 * @param onClick acción al pulsar la campana (abrir el panel de notificaciones).
 * @param role rol semántico/severidad del conteo. DANGER SOLO para alertas
 *   críticas reales; por defecto NEUTRAL (informativo).
 * @param asDot si true, dibuja la variante DOT (8dp, sin número): "hay algo
 *   nuevo" sin cuantificar. Hereda el [role]. Ignora el valor numérico de [count]
 *   para el glifo, pero count==0 sigue ocultando el badge.
 * @param icon glifo base de la campana (24dp, decorativo). Por defecto la campana.
 * @param modifier modificador externo (último parámetro, convención del repo).
 */
@Composable
fun NotificationBadge(
    count: Int,
    contentDescription: String,
    onClick: () -> Unit,
    role: BadgeRole = BadgeRole.NEUTRAL,
    asDot: Boolean = false,
    icon: ImageVector = Icons.Outlined.Notifications,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current

    // Par de color por rol+modo. on-danger es OSCURO en dark (#3A0A0A): el blanco
    // sobre #F87171 falla AA — los tokens ya lo resuelven (onDanger según modo).
    // El neutral lleva borde porque su fill (surface-2) apenas contrasta con el
    // halo del TopBar; el borde da el ≥3:1 no-textual que el color plano no da.
    val badgeColors =
        when (role) {
            BadgeRole.DANGER ->
                BadgeColors(colors.danger, colors.onDanger, border = null)
            BadgeRole.PRIMARY ->
                BadgeColors(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    border = null,
                )
            BadgeRole.NEUTRAL ->
                BadgeColors(
                    colors.stateNeutralBg,
                    colors.stateNeutralFg,
                    border = colors.stateNeutralBorder,
                )
        }

    // El badge se renderiza sólo si hay algo que mostrar (estado "hidden" si 0).
    val showBadge = count > 0
    val overflow = count > OVERFLOW_LIMIT
    val label = if (overflow) "+$OVERFLOW_LIMIT" else "$count"

    // Halo / anillo de recorte = fondo del TopBar (background): separa el badge
    // del glifo de la campana cuando se solapan.
    val halo = MaterialTheme.colorScheme.background

    // El IconButton anfitrión es el nodo accesible: rol Button, conteo real en el
    // contentDescription y liveRegion Polite para anunciar los saltos de conteo
    // (p. ej. "ahora 5 sin leer") sin interrumpir al usuario. El badge cuelga
    // dentro como decoración (clearAndSetSemantics) y no añade ruido al lector.
    IconButton(
        onClick = onClick,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.role = Role.Button
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        // Box ancla el overlay en la esquina superior-derecha del glifo de 24dp.
        Box(contentAlignment = Alignment.Center) {
            // Icono base de la campana: 24dp, decorativo (aria-hidden). Su
            // significado y el conteo los aporta el contentDescription del host.
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            if (showBadge) {
                // Overlay anclado arriba-derecha, ~1/3 fuera del glifo. Marcado
                // decorativo: el lector NO lo lee (el conteo ya está en el host).
                val overlayModifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        // Desplaza ~1/3 hacia fuera del glifo (arriba-derecha).
                        .padding(start = 12.dp, bottom = 12.dp)
                        .clearAndSetSemantics {}

                if (asDot) {
                    // Variante DOT: 8dp, sin texto. Hereda el rol del contexto
                    // (rojo sólo si lo que hay es crítico; primary para novedad).
                    Box(
                        modifier =
                            overlayModifier
                                .size(8.dp)
                                .clip(CircleShape)
                                // Halo: anillo del color del TopBar para separar del glifo.
                                .background(halo)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(badgeColors.fill)
                                .then(
                                    if (badgeColors.border != null) {
                                        Modifier.border(1.dp, badgeColors.border, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                } else {
                    // Variante NUMÉRICA: pill ≥18dp, "+99" en overflow. Cápsula
                    // completa (CircleShape), nunca radio de card.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            overlayModifier
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .clip(CircleShape)
                                .background(badgeColors.fill)
                                .then(
                                    if (badgeColors.border != null) {
                                        Modifier.border(1.dp, badgeColors.border, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 4.dp),
                    ) {
                        // Mono TABULAR a 12sp: los dígitos no bailan al actualizarse.
                        Text(
                            text = label,
                            color = badgeColors.fg,
                            style = RecreType.cifraCaption.copy(fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Previews — light y dark. Cubren: hidden (0), numérico 1/2 dígitos, overflow
// (+99), los tres roles y la variante dot.
// ---------------------------------------------------------------------

@Composable
private fun NotificationBadgeShowcase() {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // hidden (count = 0): sólo la campana, sin badge.
            NotificationBadge(
                count = 0,
                contentDescription = "Notificaciones, sin nuevas",
                onClick = {},
            )
            // crítico: 3 averías sin atender.
            NotificationBadge(
                count = 3,
                contentDescription = "Alertas, 3 averías sin atender",
                onClick = {},
                role = BadgeRole.DANGER,
            )
            // novedad: 12 novedades.
            NotificationBadge(
                count = 12,
                contentDescription = "Notificaciones, 12 novedades",
                onClick = {},
                role = BadgeRole.PRIMARY,
            )
            // informativo neutral.
            NotificationBadge(
                count = 7,
                contentDescription = "Notificaciones, 7 sin leer",
                onClick = {},
                role = BadgeRole.NEUTRAL,
            )
        }
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // overflow: el glifo dice "+99", el host anuncia el real (134).
            NotificationBadge(
                count = 134,
                contentDescription = "Alertas, 134 sin atender",
                onClick = {},
                role = BadgeRole.DANGER,
            )
            // dot primary: "hay algo nuevo" sin cuantificar.
            NotificationBadge(
                count = 1,
                contentDescription = "Notificaciones, hay novedades",
                onClick = {},
                role = BadgeRole.PRIMARY,
                asDot = true,
            )
            // dot neutral (con borde).
            NotificationBadge(
                count = 1,
                contentDescription = "Notificaciones, hay algo nuevo",
                onClick = {},
                role = BadgeRole.NEUTRAL,
                asDot = true,
            )
        }
    }
}

@Preview(name = "NotificationBadge · light", showBackground = true)
@Composable
private fun NotificationBadgeLightPreview() {
    RecreTheme(darkTheme = false) { NotificationBadgeShowcase() }
}

@Preview(name = "NotificationBadge · dark", showBackground = true)
@Composable
private fun NotificationBadgeDarkPreview() {
    RecreTheme(darkTheme = true) { NotificationBadgeShowcase() }
}
