package com.recre.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// =====================================================================
// Design System "Confianza Industrial" — Átomo F3-CARD-001 (AppCard).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
// Familia de contenedores de superficie:
//   · AppCard      contenedor base de elevación-por-borde (surface-1 + border
//                  1px en light / luminancia en dark; radio 12; SIN sombra:
//                  las sombras se reservan a overlays — dialog/dropdown/popover).
//   · EntidadRow   fila de lista genérica (leading + centro vertical + trailing)
//                  usada en los hubs de Gestión (CRUD); tapeable, ≥56dp.
//   · LocalCard    variante del hub operativo: fila de StatusChips de contexto
//                  cruzado (avería=danger, pendiente=warning, deuda=neutro EUR)
//                  para que el técnico priorice de un vistazo.
// Reglas no negociables del átomo:
//   · Selección marcada SIEMPRE con un indicador NO-color (icono Check primary a
//     la izquierda + borde primary 2px), nunca solo con el fondo secondary.
//   · Chevron SIEMPRE muted y decorativo (contentDescription = null), jamás acento.
//   · El recuento de máquinas NO va en primary: es neutro (no gasta el acento ≤10%).
//   · Dinero money-safe: el importe llega ya formateado en es-ES (lo decide
//     MoneyText desde BigDecimal); aquí jamás se toca Double/Float.
// =====================================================================

// Radio 16 (S2, mockup: .item/.fila-nav/.deuda-card usan 16-18; los controles
// siguen en 12 vía RecreShapes.small). Sube solo el contenedor de superficie.
private val CardShape = RoundedCornerShape(16.dp)

// -------------------------------------------------------------------------
// AppCard — contenedor base de superficie (elevación por borde, sin sombra).
// -------------------------------------------------------------------------

/**
 * Contenedor base de superficie. Variante no-clickable (decorativa o de detalle):
 * surface-1 + border 1px, radio 12, elevación 0 (la profundidad la da el borde,
 * no la sombra). Para una superficie clickable usa [AppCard] con [onClick].
 *
 * @param selected marca la tarjeta como activa: fondo secondary + borde primary
 *   2px + icono Check primary a la izquierda (indicador no-color, regla a11y).
 * @param contentDescription etiqueta compuesta en es-ES para lectores de pantalla;
 *   si se aporta, fusiona los descendientes en un único nodo legible.
 */
@Composable
fun AppCard(
    selected: Boolean = false,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RecreColors.current
    val container = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
    val border =
        if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, colors.border)
        }

    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (selected) this.selected = true
            }
        } else {
            Modifier
        }

    Surface(
        shape = CardShape,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
        // Sin sombra: la elevación es por borde. tonalElevation/shadowElevation = 0.
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth().then(semanticsModifier),
    ) {
        SelectableContentRow(selected = selected, contentPadding = contentPadding, content = content)
    }
}

/**
 * Contenedor base de superficie en variante CLICKABLE: añade onClick, ripple,
 * estado de foco (ring = primary) y `role = Button`. Área tapeable ≥56dp.
 */
@Composable
fun AppCard(
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RecreColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val container = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
    val border =
        when {
            selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            focused -> BorderStroke(2.dp, colors.ring) // foco D-pad/teclado: ring = primary
            else -> BorderStroke(1.dp, colors.border)
        }

    val semanticsModifier =
        if (contentDescription != null) {
            // Fusiona los descendientes en un único nodo legible; añade role/selected.
            Modifier.semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.role = Role.Button
                if (selected) this.selected = true
            }
        } else {
            Modifier.semantics {
                this.role = Role.Button
                if (selected) this.selected = true
            }
        }

    Card(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = container,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        // Sin sombra: elevación por borde, no por shadow (regla anti-patrón).
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp) // touch target Android ≥56dp
                .then(semanticsModifier),
    ) {
        SelectableContentRow(selected = selected, contentPadding = contentPadding, content = content)
    }
}

/**
 * Fila interna compartida por ambas variantes de AppCard: aplica el padding e
 * inserta el indicador no-color de selección (Check primary) cuando procede.
 */
@Composable
private fun SelectableContentRow(
    selected: Boolean,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            // Indicador NO-color de selección (regla a11y): además del fondo
            // secondary, marca SIEMPRE con el icono Check primary a la izquierda.
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
}

// -------------------------------------------------------------------------
// EntidadRow — fila de lista genérica (leading + centro vertical + trailing).
// -------------------------------------------------------------------------

/**
 * Fila de lista genérica de los hubs de Gestión (CRUD): bloque leading opcional
 * (icono/avatar), centro con [titulo] + [subtitulo] alineados verticalmente, y
 * trailing opcional (StatusChip de estado). El chevron muted decorativo cierra
 * la fila cuando es navegable. Toda la fila es el área tapeable (≥56dp).
 *
 * @param titulo título de la entidad (foreground); el llamador aporta el literal
 *   i18n vía stringResource (no se hardcodea aquí).
 * @param subtitulo dirección / metadato secundario (muted); null lo oculta.
 * @param onClick navegación al detalle; null = fila no clickable (sin ripple,
 *   sin chevron, sin role Button).
 * @param contentDescription etiqueta compuesta es-ES para lector de pantalla.
 * @param leading slot inicial (icono/avatar decorativo).
 * @param trailing slot final antes del chevron (p.ej. StatusChip de estado).
 */
@Composable
fun EntidadRow(
    titulo: String,
    subtitulo: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = onClick != null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        EntidadRowContent(
            titulo = titulo,
            subtitulo = subtitulo,
            showChevron = showChevron,
            leading = leading,
            trailing = trailing,
        )
    }

    if (onClick != null) {
        AppCard(
            onClick = onClick,
            selected = selected,
            enabled = enabled,
            contentDescription = contentDescription,
            modifier = modifier,
            content = body,
        )
    } else {
        AppCard(
            selected = selected,
            contentDescription = contentDescription,
            modifier = modifier,
            content = body,
        )
    }
}

@Composable
private fun EntidadRowContent(
    titulo: String,
    subtitulo: String?,
    showChevron: Boolean,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = RecreColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }

        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            // Chevron SIEMPRE muted y decorativo (regla anti-patrón): jamás primary,
            // contentDescription = null para que el lector no lo anuncie.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------
// LocalCard — variante del hub operativo con fila de StatusChips de contexto.
// -------------------------------------------------------------------------

/**
 * Tarjeta del hub operativo (técnico). Muestra el local (nombre + dirección) y,
 * debajo, una fila de chips de contexto cruzado para priorizar de un vistazo:
 *   · recuento de máquinas — neutro (NO primary)
 *   · averías — danger (icono + texto + color, nunca tiñe la card entera)
 *   · pendiente / offline-stale — warning (ámbar, NO el rojo del bloqueo de sync)
 *   · deuda — neutro con icono EUR + importe (NO danger: no es un error)
 *
 * Los chips se aportan ya construidos por el llamador (reutilizando StatusChip y
 * MoneyText del mismo batch), manteniendo este átomo desacoplado y money-safe:
 * el importe de deuda ya viene formateado en es-ES desde BigDecimal.
 *
 * @param nombre nombre del local (título, foreground).
 * @param direccion dirección (muted); null la oculta.
 * @param onClick navegación al detalle del local.
 * @param selected local enfocado: Check primary + borde primary 2px + secondary.
 * @param contentDescription etiqueta compuesta es-ES ("{nombre}, {dirección},
 *   {recuento} máquinas, {estados}, deuda {importe} euros…").
 * @param chips fila de StatusChips de contexto; vacía = sin fila de contexto.
 */
@Composable
fun LocalCard(
    nombre: String,
    direccion: String? = null,
    onClick: () -> Unit,
    selected: Boolean = false,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    chips: (@Composable () -> Unit)? = null,
) {
    AppCard(
        onClick = onClick,
        selected = selected,
        contentDescription = contentDescription,
        modifier = modifier,
    ) {
        val colors = RecreColors.current
        // content lambda de AppCard es @Composable () -> Unit (sin RowScope): aquí
        // weight() no existe; la card ya gobierna el ancho, basta fillMaxWidth.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (direccion != null) {
                Text(
                    text = direccion,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (chips != null) {
                Spacer(Modifier.size(8.dp))
                // Fila de chips de contexto cruzado (StatusChip del mismo batch).
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
            }
        }
    }
}

// =====================================================================
// Previews (light + dark). Los chips de ejemplo son placeholders ligeros para
// el render del átomo; en producción son StatusChip + MoneyText del batch.
// =====================================================================

@Composable
private fun DemoChip(label: String, bg: Color, fg: Color, icon: Boolean = false) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun AppCardPreviewBody() {
    val colors = RecreColors.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EntidadRow(
                titulo = "Bar Manolo",
                subtitulo = "Calle Mayor 12, Madrid",
                onClick = {},
                trailing = {
                    DemoChip("Activa", colors.successContainer, colors.onSuccessContainer)
                },
            )
            EntidadRow(
                titulo = "Recreativos Sol",
                subtitulo = "Av. del Sol 3",
                onClick = {},
                selected = true,
            )
            EntidadRow(
                titulo = "Sin navegación",
                subtitulo = "fila no clickable",
                onClick = null,
            )
            LocalCard(
                nombre = "Cafetería Central",
                direccion = "Plaza España 1",
                onClick = {},
                chips = {
                    DemoChip("8 máquinas", colors.stateNeutralBg, colors.stateNeutralFg)
                    DemoChip("1 avería", colors.dangerContainer, colors.dangerText, icon = true)
                    DemoChip("pendiente", colors.warningContainer, colors.warningText)
                    DemoChip("1.234,56 €", colors.stateNeutralBg, colors.stateNeutralFg, icon = true)
                },
            )
        }
    }
}

@Preview(name = "AppCard · light", showBackground = true)
@Composable
private fun AppCardLightPreview() {
    RecreTheme(darkTheme = false) { AppCardPreviewBody() }
}

@Preview(name = "AppCard · dark", showBackground = true)
@Composable
private fun AppCardDarkPreview() {
    RecreTheme(darkTheme = true) { AppCardPreviewBody() }
}
