package com.recre.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.neonGlow

// =====================================================================
// Átomo "Botones" (C-01) — Design System "Confianza Industrial" (Fase 3).
// Spec: .kiro/specs/recre/fase3-component-specs.md (línea 821).
//
// Familia de 4 jerarquías estrictas (evita competencia de acentos):
//  1) PrimaryCTA   — acción principal de avance, UNA por vista, color marca
//                    (primary). Sobre Material3 Button. CTA de pantalla 56dp.
//  2) Tonal        — apoyo importante sin acento saturado (secondaryContainer).
//                    Sobre Material3 FilledTonalButton.
//  3) Texto        — acción de bajo peso (cancelar, "ver más"). TextButton.
//  4) Destructivo  — borra/da de baja/revierte dinero, en danger y SIEMPRE
//                    tras confirmación explícita (AlertDialog gestionado aquí).
//
// Tokens: lo canónico (primary/onPrimary/secondaryContainer) vía
// MaterialTheme.colorScheme; danger/onDanger/ring vía RecreColors.current
// (M3 no tiene slot para danger ni ring). Textos UI llegan como parámetros
// (i18n lo pone el llamador con stringResource); nunca se hardcodean.
//
// Reglas no negociables del spec aplicadas:
//  - touch target >= 48dp (acción) / 56dp (CTA de pantalla). Nunca por debajo.
//  - sin shadow por defecto (la elevación es borde 1px light / luminancia dark).
//  - loading: spinner reemplaza al icono leading + stateDescription "Cargando"
//    (aria-busy); el label se mantiene; el ancho NO cambia (no salta).
//  - destructivo: icono (papelera) + texto + confirmación. Nunca solo color.
//  - foco: anillo 2px ring(=primary); lo provee el foco por defecto de M3
//    sobre el contenedor; ring = primary garantiza contraste >=3:1.
// =====================================================================

/** Altura mínima para un CTA anclado de pantalla (sticky full-width). */
private val CtaHeight = 56.dp

/** Altura mínima de una acción dentro de contenido (touch target). */
private val ActionHeight = 48.dp

/** Tamaño del spinner/icono leading (igual al icono para no alterar el ancho). */
private val IconSize = 20.dp

/**
 * (1) PrimaryCTA — acción principal e irreversible-de-avance de la pantalla.
 * UNA por vista. Relleno color de marca (primary), label/icono on-primary.
 *
 * @param text label visible (ya localizado por el llamador).
 * @param onClick callback de acción.
 * @param enabled gate de la acción; en `loading` se deshabilita el click.
 * @param loading muestra spinner en lugar del icono leading; el ancho no salta.
 * @param leadingIcon icono decorativo opcional (contentDescription = null).
 * @param fullWidth ancla el botón a todo el ancho (CTA de pantalla); por
 *        defecto `true` porque el PrimaryCTA suele ir sticky al fondo.
 * @param glow halo neón cian tras el botón. En el mockup TODO CTA sólido brilla
 *        (S1): on por defecto; se apaga solo dentro de diálogos o superficies ya
 *        acentuadas.
 */
@Composable
fun RecrePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = true,
    glow: Boolean = true,
) {
    // Glow opt-in: el halo se pinta detrás del botón sin ocupar layout (neonGlow).
    val modifierConGlow =
        if (glow) {
            modifier.neonGlow(RecreColors.current.accentBright, radius = 20.dp, alpha = 0.3f)
        } else {
            modifier
        }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier =
            modifierConGlow
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = CtaHeight)
                .semantics { if (loading) stateDescription = "Cargando" },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        // Sin elevación: la jerarquía es el color, no la sombra (spec).
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        RecreButtonContent(
            text = text,
            loading = loading,
            leadingIcon = leadingIcon,
            spinnerColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * (2) Tonal/secundario — acciones de apoyo importantes pero no dominantes,
 * sobre superficie secondaryContainer sin acento saturado.
 */
@Composable
fun RecreTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier =
            modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = ActionHeight)
                .semantics { if (loading) stateDescription = "Cargando" },
        colors =
            ButtonDefaults.filledTonalButtonColors(
                // Tonal de marca del spec: secondary (tint petróleo) + on-secondary
                // (contraste verificado 4.69:1), NO secondaryContainer.
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        RecreButtonContent(
            text = text,
            loading = loading,
            leadingIcon = leadingIcon,
            spinnerColor = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

/**
 * (3) Texto/terciario — acción de bajo peso (cancelar, "ver más", navegación
 * lateral) sin relleno. Solo color de marca (primary), sin contenedor.
 * Mantiene touch target >=48dp aunque no tenga relleno (spec).
 */
@Composable
fun RecreTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier =
            modifier
                .heightIn(min = ActionHeight)
                .semantics { if (loading) stateDescription = "Cargando" },
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        RecreButtonContent(
            text = text,
            loading = loading,
            leadingIcon = leadingIcon,
            spinnerColor = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * (4) Destructivo — acciones que borran/dan de baja/revierten dinero, en
 * danger. La confirmación NO se delega al llamador: este componente gestiona
 * su propio AlertDialog (back/escape cancela). El icono de papelera + el texto
 * + la confirmación garantizan que el estado no se comunica solo con color.
 *
 * @param text label de la acción destructiva (ya localizado).
 * @param confirmTitle título del diálogo de confirmación.
 * @param confirmMessage cuerpo del diálogo (explica la irreversibilidad).
 * @param confirmLabel texto del botón que confirma (rojo, dentro del diálogo).
 * @param cancelLabel texto del botón que cancela.
 * @param onConfirm callback ejecutado SOLO tras confirmación explícita.
 */
@Composable
fun RecreDestructiveButton(
    text: String,
    confirmTitle: String,
    confirmMessage: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector = Icons.Filled.Delete,
) {
    val recreColors = RecreColors.current
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        enabled = enabled,
        modifier =
            modifier
                .heightIn(min = ActionHeight)
                .semantics { stateDescription = "Acción destructiva" },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = recreColors.danger,
                contentColor = recreColors.onDanger,
            ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        // Destructivo SIEMPRE con icono + texto (a11y: nunca solo color).
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(IconSize))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }

    if (showDialog) {
        // Foco inicial en la opción SEGURA (Cancelar): el botón destructivo nunca
        // queda bajo el foco al abrir (a11y, evita confirmaciones accidentales).
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        AlertDialog(
            onDismissRequest = { showDialog = false }, // back/escape cancela
            title = { Text(confirmTitle) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onConfirm()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = recreColors.danger),
                ) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.focusRequester(cancelFocus),
                ) {
                    Text(cancelLabel)
                }
            },
        )
    }
}

/**
 * (5) Fantasma — `.cta.fantasma` del mockup (S1): píldora transparente con borde
 * 1px `border` y contenido onSurface; NO gasta acento. [danger] la vuelve
 * `.peligrosa` (borde y texto danger) para acciones arriesgadas no destructivas
 * inmediatas (p. ej. «Cerrar instalación», que ya confirma con diálogo aguas
 * arriba). [mini] compacta la altura para acciones de pie de pantalla.
 */
@Composable
fun RecreGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
    mini: Boolean = false,
    danger: Boolean = false,
) {
    val recreColors = RecreColors.current
    val contentColor =
        if (danger) recreColors.danger else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (danger) recreColors.danger.copy(alpha = 0.55f) else recreColors.border
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier =
            modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = if (mini) 40.dp else ActionHeight)
                .semantics { if (loading) stateDescription = "Cargando" },
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = if (mini) 14.dp else 16.dp),
    ) {
        RecreButtonContent(
            text = text,
            loading = loading,
            leadingIcon = leadingIcon,
            spinnerColor = contentColor,
        )
    }
}

/**
 * Contenido interno compartido (label + slot leading icono/spinner). En
 * `loading` el spinner sustituye al icono leading con el MISMO tamaño, de modo
 * que el ancho del botón no cambia al entrar/salir de loading (spec: "NO
 * cambiar el ancho del botón al entrar en loading").
 */
@Composable
private fun RecreButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: ImageVector?,
    spinnerColor: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize),
                    strokeWidth = 2.dp,
                    color = spinnerColor,
                )
                Spacer(Modifier.width(8.dp))
            }
            leadingIcon != null -> {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(IconSize))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "Botones · light", showBackground = true)
@Composable
private fun RecreButtonPreviewLight() {
    RecreTheme(darkTheme = false) {
        ButtonGallery()
    }
}

@Preview(name = "Botones · dark", showBackground = true)
@Composable
private fun RecreButtonPreviewDark() {
    RecreTheme(darkTheme = true) {
        ButtonGallery()
    }
}

@Composable
private fun ButtonGallery() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        RecrePrimaryButton(
            text = "Guardar recaudación",
            onClick = {},
            leadingIcon = Icons.Filled.Add,
        )
        Spacer(Modifier.size(12.dp))
        RecrePrimaryButton(
            text = "Confirmar (con glow)",
            onClick = {},
            glow = true,
        )
        Spacer(Modifier.size(12.dp))
        RecrePrimaryButton(
            text = "Guardando…",
            onClick = {},
            loading = true,
        )
        Spacer(Modifier.size(12.dp))
        RecreTonalButton(
            text = "Añadir denominación",
            onClick = {},
            leadingIcon = Icons.Filled.Add,
        )
        Spacer(Modifier.size(12.dp))
        RecreTextButton(
            text = "Cancelar",
            onClick = {},
        )
        Spacer(Modifier.size(12.dp))
        RecreDestructiveButton(
            text = "Dar de baja máquina",
            confirmTitle = "¿Dar de baja la máquina?",
            confirmMessage = "Esta acción revierte la instalación y no se puede deshacer.",
            confirmLabel = "Dar de baja",
            cancelLabel = "Cancelar",
            onConfirm = {},
        )
        Spacer(Modifier.size(12.dp))
        RecrePrimaryButton(
            text = "Acción deshabilitada",
            onClick = {},
            enabled = false,
        )
    }
}
