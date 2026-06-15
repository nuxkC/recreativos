package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom F3-A-SearchField (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Filtro de texto libre sobre una lista: lupa (leading, decorativa, muted) +
// campo de una línea + botón limpiar (trailing, solo con texto). NO valida ni
// hace submit; es un filtro reactivo CONTROLADO (value/onValueChange). El
// debounce, el fetch y el ?q= viven AGUAS ARRIBA (en el contenedor de lista),
// nunca en el átomo. El error/empty se muestran en la LISTA, no aquí: el campo
// JAMÁS se pinta de rojo.
//
// Adaptaciones al stack real:
//  - El spec pide Phosphor `MagnifyingGlass`/`X`; esa lib NO está. Se usan
//    `Icons.Filled.Search` y `Icons.Filled.Close` (material-icons core).
//  - La lupa va en `muted`, NUNCA primary (presupuesto de acento ≤10%); el único
//    acento es el ring de foco / caret / spinner.
//  - reduce-motion real (ANIMATOR_DURATION_SCALE): sin fade+scale del clear ni
//    spinner (lupa estática); el significado se preserva por iconos + aria.

/**
 * Barra de búsqueda con lupa y botón limpiar.
 *
 * Sin etiqueta visible: la etiqueta accesible es [placeholder] (vía
 * contentDescription), no solo el texto fantasma. El botón limpiar aparece solo
 * con texto y, al pulsarlo, vacía el campo manteniendo el foco. Ningún estado se
 * codifica por color (lupa/X/spinner son iconos con significado).
 *
 * @param value término actual (controlado).
 * @param onValueChange emite el nuevo término (el debounce lo aplica el contenedor).
 * @param placeholder texto fantasma Y etiqueta accesible. i18n por el llamador.
 * @param clearContentDescription descripción del botón limpiar. i18n.
 * @param isLoading true ⇒ la lupa se sustituye por spinner (búsqueda server-side).
 * @param enabled false ⇒ campo atenuado y no enfocable (caso raro).
 * @param imeAction acción del IME (Search por defecto).
 * @param modifier modificador del campo (último parámetro).
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Search,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val animate = rememberAnimationsEnabled()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge, // 16/450 Geist Sans
        placeholder = { Text(placeholder, color = colors.muted) },
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = imeAction),
        leadingIcon = {
            if (isLoading && animate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary, // único acento permitido
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null, // decorativa; la etiqueta es el placeholder
                    tint = colors.muted, // nunca primary
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter =
                    if (animate) {
                        fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.85f)
                    } else {
                        EnterTransition.None
                    },
                exit = if (animate) fadeOut(tween(120)) else ExitTransition.None,
            ) {
                IconButton(onClick = { onValueChange("") }) { // vacía y mantiene foco
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = clearContentDescription,
                        tint = colors.muted, // limpiar NO es destructivo: nunca danger
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp), // campo, no pill 50
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.ring, // = primary
                unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.surface2,
                unfocusedContainerColor = colors.surface2,
                disabledContainerColor = colors.surface2,
                cursorColor = colors.ring,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = placeholder },
    )
}

/**
 * ¿Animaciones activas? Falso en preview o con animaciones del sistema
 * desactivadas (ANIMATOR_DURATION_SCALE = 0). (Función privada por fichero.)
 */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale != 0f
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "SearchField vacío · light", showBackground = true)
@Composable
private fun SearchFieldEmptyLightPreview() {
    RecreTheme(darkTheme = false) {
        SearchField(
            value = "",
            onValueChange = {},
            placeholder = "Buscar local o titular…",
            clearContentDescription = "Limpiar búsqueda",
        )
    }
}

@Preview(name = "SearchField con texto · dark", showBackground = true)
@Composable
private fun SearchFieldFilledDarkPreview() {
    RecreTheme(darkTheme = true) {
        SearchField(
            value = "Bar Pepe",
            onValueChange = {},
            placeholder = "Buscar local o titular…",
            clearContentDescription = "Limpiar búsqueda",
        )
    }
}
