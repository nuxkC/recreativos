package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// =====================================================================
// IconAction · icon-action (Fase 3 · átomo del design system).
// SSOT: .kiro/specs/recre/fase3-component-specs.md (## IconAction).
//
// Botón-icono que dispara una intención del SO sobre un dato de contacto de
// una entidad concreta (teléfono→llamar, dirección→mapa, email→redactar).
//
// Reglas del átomo (no negociables):
//  - color NEUTRO por defecto (muted), NUNCA acento/success/danger. Marcar un
//    teléfono no es dinero+ ni error ni pendiente: no gasta del ≤10% de acento.
//  - contentDescription OBLIGATORIO y EXPLÍCITO: nombra la acción Y la entidad
//    destino ("Llamar a Bar Pepe"), nunca el genérico "Llamar". Lo construye el
//    llamador con stringResource (i18n); aquí no se hardcodea texto.
//  - sin PII en el label: nombra la entidad de negocio, no el tel/email crudo.
//  - target táctil ≥48dp (Material); glifo 24dp centrado.
//  - el fallo del intent NO torna el botón a danger: se comunica por Snackbar
//    neutro fuera de este átomo. disabled solo cuando el dato existe pero la
//    acción está bloqueada (RBAC/permiso); 'dato ausente' = NO renderizar.
//
// NOTA DE ADAPTACIÓN: el spec dibuja con Phosphor (Phone/MapPin/Envelope), que
// no está en el proyecto. Se adapta a Material Icons (Phone/Place/Email), única
// librería de iconos disponible (material-icons-extended).
// =====================================================================

/** Tipo de acción de contacto: fija el glifo por defecto. El verbo/entidad del
 *  contentDescription lo aporta el llamador (i18n), no este enum. */
enum class TipoAccionContacto {
    LLAMAR,
    MAPA,
    EMAIL,
}

/** Glifo Material por defecto para cada tipo de acción. */
private fun TipoAccionContacto.icono(): ImageVector =
    when (this) {
        TipoAccionContacto.LLAMAR -> Icons.Filled.Phone
        TipoAccionContacto.MAPA -> Icons.Filled.Place
        TipoAccionContacto.EMAIL -> Icons.Filled.Email
    }

/**
 * IconAction — botón de acción de icono contextual.
 *
 * @param icon glifo a mostrar (24dp). Para la mayoría de casos usa la sobrecarga
 *   con [TipoAccionContacto], que ya lo fija.
 * @param contentDescription OBLIGATORIO. Label accesible explícito que nombra la
 *   acción y la entidad destino ("Llamar a Bar Pepe"). Sin PII (no el número ni
 *   el email crudo). Lo aporta el llamador vía stringResource.
 * @param onClick intención a disparar (Intent ACTION_DIAL / geo / mailto). El
 *   átomo no construye el Intent: lo recibe ya resuelto por el llamador.
 * @param tonal si true, dibuja una caja visual surface-2 redondeada (40dp) bajo
 *   el glifo; por defecto false (sin caja, solo el glifo muted).
 * @param enabled si false, glifo muted @0.5 sin ripple. Úsalo SOLO cuando el
 *   dato existe pero la acción está bloqueada (RBAC/permiso); para 'dato
 *   ausente' simplemente no renderices este botón.
 * @param modifier modificador externo.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tonal: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current

    // Color NEUTRO: muted en idle; foreground (onSurface) no se usa como base
    // porque el átomo de contacto vive en muted (state-neutral). disabled =
    // muted @0.5. El ripple se deja al state-layer neutro de IconButton (no
    // ripple de primary): el átomo no codifica acento.
    val iconButtonColors =
        IconButtonDefaults.iconButtonColors(
            contentColor = colors.muted,
            disabledContentColor = colors.muted.copy(alpha = 0.5f),
        )

    // semantics: role = Button. contentDescription cuelga del Icon (un único
    // glifo significativo); el IconButton hereda la acción y el estado disabled.
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = iconButtonColors,
        // IconButton ya reserva un target táctil ≥48dp (minimumInteractiveComponentSize);
        // garantizamos el suelo de forma explícita por si el llamador pasa un size menor.
        modifier =
            modifier
                .size(48.dp)
                .semantics { role = Role.Button },
    ) {
        if (tonal) {
            // Caja visual tonal de 40dp, radio 12dp, fondo surface-2; el glifo
            // muted de 24dp queda centrado. La caja NO es de marca (secondary):
            // es state-neutral, sólo refuerza el affordance.
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .padding(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Sobrecarga de conveniencia: el glifo por defecto sale del [tipo] de acción de
 * contacto. El [contentDescription] sigue siendo OBLIGATORIO y debe nombrar la
 * entidad destino ("Cómo llegar a Bar Pepe"); este átomo nunca lo deriva del
 * tipo a secas (sería el genérico prohibido "Mapa").
 */
@Composable
fun IconAction(
    tipo: TipoAccionContacto,
    contentDescription: String,
    onClick: () -> Unit,
    tonal: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    IconAction(
        icon = tipo.icono(),
        contentDescription = contentDescription,
        onClick = onClick,
        tonal = tonal,
        enabled = enabled,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------
// Previews (light / dark). Los labels van explícitos solo para la preview;
// en producción los inyecta el llamador con stringResource (i18n).
// ---------------------------------------------------------------------

@Composable
private fun IconActionPreviewBody() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp),
    ) {
        // idle plano (muted)
        IconAction(
            tipo = TipoAccionContacto.LLAMAR,
            contentDescription = "Llamar a Bar Pepe",
            onClick = {},
        )
        // tonal (caja surface-2)
        IconAction(
            tipo = TipoAccionContacto.MAPA,
            contentDescription = "Cómo llegar a Bar Pepe",
            onClick = {},
            tonal = true,
        )
        // email plano
        IconAction(
            tipo = TipoAccionContacto.EMAIL,
            contentDescription = "Enviar email a Bar Pepe",
            onClick = {},
        )
        // disabled (acción bloqueada por permiso): muted @0.5
        IconAction(
            tipo = TipoAccionContacto.LLAMAR,
            contentDescription = "Llamar a Bar Pepe (sin permiso)",
            onClick = {},
            enabled = false,
        )
    }
}

@Preview(name = "IconAction · light", showBackground = true)
@Composable
private fun IconActionPreviewLight() {
    RecreTheme(darkTheme = false) { IconActionPreviewBody() }
}

@Preview(name = "IconAction · dark", showBackground = true)
@Composable
private fun IconActionPreviewDark() {
    RecreTheme(darkTheme = true) { IconActionPreviewBody() }
}
