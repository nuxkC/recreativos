package com.recre.app.feature.gestion.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.recre.app.ui.components.FieldSelect
import com.recre.app.ui.components.FieldText

/**
 * Adaptadores de los 4 formularios del CRUD gestor (T-66..T-69) a la
 * librería propia. Mantienen la firma que ya usan los formularios
 * (`label, value, error…`) pero delegan en `FieldText`/`FieldSelect`
 * del design system (rediseño F3·P5), de modo que el reestilo cascada a
 * los cuatro forms sin tocar sus call-sites.
 */
@Composable
fun GestionTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    FieldText(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        isError = error != null,
        errorText = error,
        enabled = enabled,
        keyboardType = keyboardType,
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier,
    )
}

/**
 * Dropdown sencillo (no autocompletable). Útil para enumeraciones cortas
 * (`estado`) o selección de FKs ya cargadas (máquina, licencia, local).
 * Delega en `FieldSelect` del design system.
 */
@Composable
fun <T> GestionDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    FieldSelect(
        value = selected,
        onValueChange = onSelected,
        options = options,
        optionLabel = optionLabel,
        label = label,
        enabled = enabled,
        isError = error != null,
        errorText = error,
        modifier = modifier,
    )
}
