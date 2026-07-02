package com.recre.app.feature.gestion.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.recre.app.feature.gestion.FkOption
import com.recre.app.ui.components.ComboboxCcaa
import com.recre.app.ui.components.FieldAutocomplete
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

/**
 * Autocomplete editable con alta al vuelo, sobre opciones de FK (`FkOption`).
 * Muestra/emite la **etiqueta** (texto); el `id` lo usa el ViewModel para la
 * cascada. Delega en `FieldAutocomplete` del design system. Con `createLabel`
 * ofrece "Crear «lo tecleado»" cuando no casa ninguna opción.
 */
@Composable
fun GestionAutocomplete(
    label: String,
    value: String,
    options: List<FkOption>,
    onValueChange: (String) -> Unit,
    emptyText: String,
    createLabel: (String) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    FieldAutocomplete(
        value = value,
        onValueChange = onValueChange,
        options = options.map { it.label },
        label = label,
        emptyText = emptyText,
        createLabel = createLabel,
        enabled = enabled,
        placeholder = placeholder,
        modifier = modifier,
    )
}

/**
 * Combo CERRADO sobre opciones de clave foránea (`FkOption`), a diferencia de
 * [GestionAutocomplete] (que permite alta y emite la etiqueta). El estado guarda
 * el `id` (p. ej. el código INE de provincia/municipio) y se muestra el `label`.
 * Reutiliza [ComboboxCcaa] (lista cerrada con filtro) traduciendo id <-> etiqueta:
 * la etiqueta es estado DERIVADO de `value` + `options`, así que en edición, al
 * cargar las opciones tras el prefill, el nombre aparece solo (sin carrera).
 */
@Composable
fun GestionComboboxFk(
    label: String,
    value: String,
    options: List<FkOption>,
    onValueChange: (String) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    val etiquetaActual = options.firstOrNull { it.id == value }?.label.orEmpty()
    ComboboxCcaa(
        value = etiquetaActual,
        onValueChange = { etiqueta ->
            onValueChange(options.firstOrNull { it.label == etiqueta }?.id.orEmpty())
        },
        options = options.map { it.label },
        label = label,
        emptyText = emptyText,
        enabled = enabled,
        placeholder = placeholder,
        modifier = modifier,
    )
}
