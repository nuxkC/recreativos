package com.recre.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom C-FIELDNUM family (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Familia de campos que captura datos con el TECLADO DEL SISTEMA (nunca un
// keypad in-app salvo denominaciones, que es T-231). Sobre OutlinedTextField M3:
//  - FieldNum: entero (KeyboardType.Number) o decimal (KeyboardType.Decimal),
//    valor Geist Mono tabular, sufijo €/% en `muted-strong` (≥7:1, NO muted).
//  - FieldText: texto/email/password (toggle ojo con hit-area ≥48dp vía IconButton).
//  - FieldSelect: lista cerrada corta (ExposedDropdownMenuBox, item con check).
//  - ComboboxCcaa: lista larga con filtro + "Sin coincidencias" (aria-live).
//
// MONEY-SAFE (no negociable): el valor SIEMPRE viaja como String; jamás Double/
// Float. La sanitización es puramente léxica (dígitos + un único separador
// decimal coma es-ES), sin parsear a number; el dominio lo eleva a BigDecimal.
//
// Tokens reales del stack: surface-2 = `surface2`, borde reposo = `border`,
// foco = `ring`, error = `danger`, €/%/iconos informativos = `mutedStrong`
// (añadido a Color.kt para paridad con la web; muted no llega a ~7:1 sobre
// surface-2). Cifra en `GeistMono` tabular (tnum).
//
// DIFERIDO: DatePicker (trigger read-only + DatePickerDialog M3) — se añade en
// la pantalla que lo consuma, igual que la web lo difirió.

/**
 * Sanitiza una entrada numérica de forma puramente léxica (money-safe): conserva
 * solo dígitos y, si [isDecimal], un único separador decimal coma (es-ES; los
 * puntos tecleados se normalizan a coma). NUNCA convierte a number/Double — el
 * dominio eleva el String a BigDecimal.
 */
private fun String.sanitizeNumeric(isDecimal: Boolean): String {
    if (!isDecimal) return filter { it.isDigit() }
    val sb = StringBuilder()
    var hasSeparator = false
    for (c in this) {
        when {
            c.isDigit() -> sb.append(c)
            (c == ',' || c == '.') && !hasSeparator -> {
                hasSeparator = true
                sb.append(',')
            }
        }
    }
    return sb.toString()
}

/** Colores del control unificados por rol: surface-2, border, ring, danger. */
@Composable
private fun recreFieldColors(): TextFieldColors {
    val c = RecreColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.ring, // = primary petróleo
        unfocusedBorderColor = c.border,
        errorBorderColor = c.danger,
        focusedContainerColor = c.surface2,
        unfocusedContainerColor = c.surface2,
        disabledContainerColor = c.surface2,
        errorContainerColor = c.surface2,
        focusedLabelColor = c.ring,
        errorLabelColor = c.danger,
        cursorColor = c.ring,
    )
}

/**
 * Campo numérico (entero o decimal/importe) con teclado del sistema correcto.
 *
 * El estado nunca es solo-color: el error añade icono CircleAlert + texto en el
 * supportingText (no solo el borde danger). El read-only se distingue del
 * disabled (read-only no se atenúa y sigue enfocable). El sufijo €/% va en
 * `muted-strong` y el dígito en foreground, en Geist Mono tabular.
 *
 * @param value valor como String (money-safe; el dominio lo eleva a BigDecimal).
 * @param onValueChange recibe el String ya sanitizado léxicamente.
 * @param label etiqueta del campo. i18n por el llamador.
 * @param isDecimal true ⇒ teclado decimal; false ⇒ entero.
 * @param suffix "€"/"%" en muted-strong; null = sin sufijo.
 * @param placeholder texto fantasma (muted); no porta información única.
 * @param description ayuda en muted-strong (se lee al sol); null = sin ayuda.
 * @param isError / errorText estado de error inline (borde+label+mensaje danger).
 * @param enabled false ⇒ disabled (atenuado, sin foco/teclado).
 * @param readOnly true ⇒ read-only (visual = default, enfocable, sin teclado).
 * @param isLoading true ⇒ spinner en el adorno (aria-busy).
 * @param imeAction acción del IME (Next por defecto).
 * @param modifier modificador del campo (último parámetro).
 */
@Composable
fun FieldNum(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isDecimal: Boolean = true,
    suffix: String? = null,
    placeholder: String? = null,
    description: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isLoading: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.sanitizeNumeric(isDecimal)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
        singleLine = true,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(
                fontFamily = GeistMono,
                fontFeatureSettings = "tnum", // tabular
            ),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = imeAction,
            ),
        trailingIcon =
            when {
                isLoading -> {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { },
                            strokeWidth = 2.dp,
                            color = colors.mutedStrong,
                        )
                    }
                }
                suffix != null -> {
                    { Text(suffix, color = colors.mutedStrong) } // €/% ≥7:1, no muted
                }
                isError -> {
                    {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = colors.danger,
                        )
                    }
                }
                else -> null
            },
        supportingText =
            when {
                isError && errorText != null -> {
                    {
                        Text(
                            errorText,
                            color = colors.danger,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                description != null -> {
                    {
                        Text(
                            description,
                            color = colors.mutedStrong, // ayuda legible al sol
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                else -> null
            },
        shape = RoundedCornerShape(12.dp),
        colors = recreFieldColors(),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

/**
 * Campo de texto libre (serie, modelo, nombre, email, password).
 *
 * password añade un toggle de visibilidad (ojo) como adorno con hit-area ≥48dp
 * (IconButton) manteniendo el glifo a 20dp. El estado de error no se transmite
 * solo por color (borde + icono + texto).
 *
 * @param keyboardType teclado del sistema (Text/Email/…); QWERTY salvo numéricos.
 * @param isPassword oculta el valor y muestra el toggle de visibilidad.
 * @see FieldNum para campos numéricos/importe.
 */
@Composable
fun FieldText(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    description: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    var revealed by remember { mutableStateOf(false) }
    val hidden = isPassword && !revealed

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
        singleLine = true,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        visualTransformation =
            if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
        trailingIcon =
            when {
                isPassword -> {
                    {
                        // Hit-area ≥48dp vía IconButton; el glifo queda a 20dp.
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector =
                                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription =
                                    if (revealed) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = colors.mutedStrong,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                isError -> {
                    {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = colors.danger,
                        )
                    }
                }
                else -> null
            },
        supportingText =
            when {
                isError && errorText != null -> {
                    {
                        Text(
                            errorText,
                            color = colors.danger,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                description != null -> {
                    { Text(description, color = colors.mutedStrong, style = MaterialTheme.typography.labelMedium) }
                }
                else -> null
            },
        shape = RoundedCornerShape(12.dp),
        colors = recreFieldColors(),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

/**
 * Select de lista cerrada CORTA (p. ej. estado de máquina): trigger read-only +
 * menú; el item elegido muestra un check `primary`. Para listas largas con filtro
 * (CCAA) usar [ComboboxCcaa].
 *
 * @param value valor seleccionado; null = sin selección (muestra placeholder).
 * @param onValueChange callback al elegir un item.
 * @param options opciones de la lista.
 * @param optionLabel etiqueta visible de cada opción. i18n por el llamador.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FieldSelect(
    value: T?,
    onValueChange: (T) -> Unit,
    options: List<T>,
    optionLabel: (T) -> String,
    label: String,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true, // se elige en el menú, no se teclea
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText =
                if (isError && errorText != null) {
                    {
                        Text(
                            errorText,
                            color = colors.danger,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                } else {
                    null
                },
            shape = RoundedCornerShape(12.dp),
            colors = recreFieldColors(),
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val selected = opt == value
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    onClick = {
                        onValueChange(opt)
                        expanded = false
                    },
                    trailingIcon =
                        if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.ring, // primary
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/**
 * Combobox de lista LARGA con filtro (CCAA, 19 items): input de búsqueda + menú
 * filtrado; cuando el filtro no casa, muestra "Sin coincidencias" anunciado por
 * el lector (aria-live). El item elegido muestra check `primary`.
 *
 * @param value valor seleccionado (texto).
 * @param onValueChange callback al elegir una opción.
 * @param options opciones completas (sin filtrar).
 * @param emptyText mensaje cuando el filtro no casa nada. i18n.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboboxCcaa(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    emptyText: String,
    placeholder: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(query, options) {
            if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
        }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (expanded) query else value,
            onValueChange = { query = it },
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = recreFieldColors(),
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text(
                            emptyText,
                            color = colors.mutedStrong,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    },
                    onClick = {},
                )
            } else {
                filtered.forEach { opt ->
                    val selected = opt == value
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onValueChange(opt)
                            query = ""
                            expanded = false
                        },
                        trailingIcon =
                            if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = colors.ring,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "FieldNum importe · light", showBackground = true)
@Composable
private fun FieldNumLightPreview() {
    RecreTheme(darkTheme = false) {
        FieldNum(
            value = "1234,50",
            onValueChange = {},
            label = "Importe de avería",
            suffix = "€",
            description = "Coste estimado de la reparación.",
        )
    }
}

@Preview(name = "FieldNum error · dark", showBackground = true)
@Composable
private fun FieldNumErrorDarkPreview() {
    RecreTheme(darkTheme = true) {
        FieldNum(
            value = "",
            onValueChange = {},
            label = "Contador",
            isDecimal = false,
            isError = true,
            errorText = "Obligatorio",
        )
    }
}

@Preview(name = "FieldText password · light", showBackground = true)
@Composable
private fun FieldTextPasswordLightPreview() {
    RecreTheme(darkTheme = false) {
        FieldText(
            value = "secreto",
            onValueChange = {},
            label = "Contraseña",
            isPassword = true,
        )
    }
}

@Preview(name = "FieldSelect · light", showBackground = true)
@Composable
private fun FieldSelectLightPreview() {
    RecreTheme(darkTheme = false) {
        FieldSelect(
            value = "Activa",
            onValueChange = {},
            options = listOf("Activa", "Avería", "Retirada"),
            optionLabel = { it },
            label = "Estado de la máquina",
        )
    }
}
