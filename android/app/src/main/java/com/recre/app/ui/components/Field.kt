package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import com.recre.app.ui.theme.neonGlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Design System "Neón de sala" — familia de campos `.campo` (S7).
// SSOT: .kiro/specs/recre/fase3-component-specs.md + mockup S7.
//
// Familia de campos que captura datos con el TECLADO DEL SISTEMA (nunca un
// keypad in-app salvo denominaciones, que es T-231). El marco `.campo`
// (CampoFrame) pone la etiqueta eyebrow DENTRO, el valor debajo, borde 1px y
// anillo de foco cian; el mensaje de error/ayuda vive FUERA (debajo).
//  - FieldNum: entero (KeyboardType.Number) o decimal (KeyboardType.Decimal),
//    valor Geist Mono tabular, sufijo €/% en `muted-strong` (≥7:1, NO muted).
//  - FieldText: texto/email/password (toggle ojo con hit-area ≥48dp vía IconButton).
//  - FieldSelect: lista cerrada corta (ExposedDropdownMenuBox, item con check).
//  - ComboboxCcaa: lista larga con filtro + "Sin coincidencias" (aria-live).
//  - FieldDate: selector de fecha read-only → DatePickerDialog M3 (NUNCA se
//    teclea); el valor viaja como String ISO "yyyy-MM-dd" (T-233).
//
// MONEY-SAFE (no negociable): el valor SIEMPRE viaja como String; jamás Double/
// Float. La sanitización es puramente léxica (dígitos + un único separador
// decimal coma es-ES), sin parsear a number; el dominio lo eleva a BigDecimal.
//
// Tokens reales del stack: surface = fondo del marco, borde reposo = `border`,
// foco = primary + halo `accentBright`, error = `danger`, €/%/iconos
// informativos = `mutedStrong`. Cifra en `GeistMono` tabular (tnum).

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

/**
 * Marco `.campo` del mockup (S7): caja surface radio 16 con borde 1px, label
 * eyebrow DENTRO (arriba), valor debajo. Foco = borde primary + halo cian
 * translúcido (anillo del mockup, dibujado con neonGlow porque minSdk 26 no
 * tiene sombras de color). Error = borde danger. El mensaje de error/ayuda vive
 * FUERA del marco (debajo), como `.err`/`.ayuda` del mockup — lo pinta el campo.
 */
@Composable
private fun CampoFrame(
    label: String,
    focused: Boolean,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = RecreColors.current
    val shape = RoundedCornerShape(16.dp)
    val borderColor =
        when {
            isError -> c.danger
            focused -> MaterialTheme.colorScheme.primary
            else -> c.border
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (focused && !isError) {
                        Modifier.neonGlow(c.accentBright, radius = 5.dp, alpha = 0.30f)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .alpha(if (enabled) 1f else 0.5f)
                // El lector anuncia label + valor como un solo campo.
                .semantics(mergeDescendants = true) {},
    ) {
        Eyebrow(label)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Mensaje bajo el marco: error danger (aria-live) o ayuda muted-strong. */
@Composable
private fun CampoMensaje(isError: Boolean, errorText: String?, description: String?) {
    val c = RecreColors.current
    when {
        isError && errorText != null ->
            Text(
                errorText,
                color = c.danger,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier
                        .padding(start = 4.dp, top = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        description != null ->
            Text(
                description,
                color = c.mutedStrong,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
    }
}

/**
 * Campo numérico (entero o decimal/importe) con teclado del sistema correcto.
 *
 * El estado nunca es solo-color: el error añade icono CircleAlert + texto bajo el
 * marco (no solo el borde danger). El read-only se distingue del disabled
 * (read-only no se atenúa y sigue enfocable). El sufijo €/% va en `muted-strong`
 * y el dígito en foreground, en Geist Mono tabular.
 *
 * @param value valor como String (money-safe; el dominio lo eleva a BigDecimal).
 * @param onValueChange recibe el String ya sanitizado léxicamente.
 * @param label etiqueta del campo. i18n por el llamador.
 * @param isDecimal true ⇒ teclado decimal; false ⇒ entero.
 * @param suffix "€"/"%" en muted-strong; null = sin sufijo.
 * @param placeholder texto fantasma (muted); no porta información única.
 * @param description ayuda en muted-strong (se lee al sol); null = sin ayuda.
 * @param isError / errorText estado de error inline (borde+mensaje danger).
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        CampoFrame(label = label, focused = focused, isError = isError, enabled = enabled) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.sanitizeNumeric(isDecimal)) },
                enabled = enabled,
                readOnly = readOnly,
                singleLine = true,
                interactionSource = interaction,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = GeistMono,
                        fontFeatureSettings = "tnum", // tabular
                    ),
                cursorBrush = SolidColor(colors.ring),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = imeAction,
                    ),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).semantics { },
                        strokeWidth = 2.dp,
                        color = colors.mutedStrong,
                    )
                }
                suffix != null -> {
                    Text(suffix, color = colors.mutedStrong) // €/% ≥7:1, no muted
                }
                isError -> {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = colors.danger,
                    )
                }
            }
        }
        CampoMensaje(isError = isError, errorText = errorText, description = description)
    }
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
    // Multilínea para notas/observaciones (CRUD gestor). Por defecto monolínea:
    // todos los callers previos conservan su comportamiento exacto.
    singleLine: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    var revealed by remember { mutableStateOf(false) }
    val hidden = isPassword && !revealed
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        CampoFrame(label = label, focused = focused, isError = isError, enabled = enabled) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                minLines = minLines,
                interactionSource = interaction,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(colors.ring),
                visualTransformation =
                    if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                        imeAction = imeAction,
                    ),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            when {
                isPassword -> {
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
                isError -> {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = colors.danger,
                    )
                }
            }
        }
        CampoMensaje(isError = isError, errorText = errorText, description = description)
    }
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
    val texto = value?.let(optionLabel).orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CampoFrame(
                label = label,
                focused = expanded,
                isError = isError,
                enabled = enabled,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            ) {
                Text(
                    text = texto.ifEmpty { placeholder.orEmpty() },
                    color =
                        if (texto.isEmpty()) colors.muted else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.mutedStrong,
                )
            }
            CampoMensaje(isError = isError, errorText = errorText, description = null)
        }
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

// ---------------------------------------------------------------------------
// FieldDate — selector de fecha (DatePicker M3). Cierra T-233 en Android.
// ---------------------------------------------------------------------------

/** ISO "yyyy-MM-dd" → millis UTC a medianoche (lo que consume el DatePicker). */
private fun dateIsoToUtcMillis(iso: String): Long? =
    runCatching {
        LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

/** millis UTC del DatePicker → ISO "yyyy-MM-dd" (modelo estable, sin TZ ambigua). */
private fun utcMillisToDateIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

private val DATE_DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Selector de fecha: trigger read-only (label + fecha es-ES dd/MM/aaaa o
 * placeholder) que abre un [DatePickerDialog] M3. La fecha NUNCA se teclea —se
 * elige en el calendario—, así que no aparece IME ni hace falta saneado. El
 * modelo viaja como String ISO "yyyy-MM-dd" (estable, sin zona horaria
 * ambigua), idéntico al átomo web; el orden entre fechas lo valida el VM (SSOT).
 *
 * @param value fecha ISO "yyyy-MM-dd"; "" o inválida ⇒ sin fecha (placeholder).
 * @param onValueChange ISO "yyyy-MM-dd" de la fecha confirmada.
 * @param minIso/maxIso límites inclusivos; los días fuera de rango se
 *   deshabilitan en el calendario (no se pueden elegir).
 * @param confirmLabel/dismissLabel textos del diálogo; por defecto los del
 *   sistema (`android.R.string.ok`/`cancel`, ya localizados a es-ES).
 * @param modifier modificador del contenedor (último parámetro).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDate(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    minIso: String? = null,
    maxIso: String? = null,
    confirmLabel: String = stringResource(android.R.string.ok),
    dismissLabel: String = stringResource(android.R.string.cancel),
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    var showDialog by remember { mutableStateOf(false) }

    val display =
        remember(value) {
            runCatching { LocalDate.parse(value) }.getOrNull()?.format(DATE_DISPLAY_FORMAT).orEmpty()
        }

    Column(modifier = modifier.fillMaxWidth()) {
        CampoFrame(
            label = label,
            focused = showDialog,
            isError = isError,
            enabled = enabled,
            // El marco read-only no recibe foco de IME; el clic abre el calendario.
            modifier =
                Modifier.clickable(enabled = enabled, role = Role.Button) { showDialog = true },
        ) {
            Text(
                text = display.ifEmpty { placeholder.orEmpty() },
                color =
                    if (display.isEmpty()) colors.muted else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null, // el control se anuncia por label + valor
                tint = colors.mutedStrong,
            )
        }
        CampoMensaje(isError = isError, errorText = errorText, description = null)
    }

    if (showDialog) {
        val minMillis = remember(minIso) { minIso?.let(::dateIsoToUtcMillis) }
        val maxMillis = remember(maxIso) { maxIso?.let(::dateIsoToUtcMillis) }
        val selectable =
            remember(minMillis, maxMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        (minMillis == null || utcTimeMillis >= minMillis) &&
                            (maxMillis == null || utcTimeMillis <= maxMillis)
                }
            }
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = dateIsoToUtcMillis(value),
                selectableDates = selectable,
            )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(utcMillisToDateIso(it)) }
                        showDialog = false
                    },
                ) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(dismissLabel) }
            },
        ) {
            DatePicker(state = pickerState)
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val filtered =
        remember(query, options) {
            if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
        }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        CampoFrame(
            label = label,
            focused = expanded || focused,
            isError = false,
            enabled = enabled,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled),
        ) {
            BasicTextField(
                value = if (expanded) query else value,
                onValueChange = { query = it },
                enabled = enabled,
                singleLine = true,
                interactionSource = interaction,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(colors.ring),
                decorationBox = { inner ->
                    Box {
                        val actual = if (expanded) query else value
                        if (actual.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.mutedStrong,
            )
        }
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

/**
 * Combobox editable con filtro y ALTA opcional. Extiende el patrón de
 * [ComboboxCcaa] (input de búsqueda + menú filtrado + "Sin coincidencias") con
 * un ítem "crear" cuando `createLabel != null` y lo tecleado no casa ninguna
 * opción: el valor emitido es el propio texto (el back-end lo resuelve/crea al
 * guardar). Editable: `MenuAnchorType.PrimaryEditable`.
 *
 * @param value valor confirmado (texto). Cambia SOLO al elegir/crear, no al teclear.
 * @param onValueChange se invoca con la etiqueta elegida o el texto a crear.
 * @param options etiquetas visibles a filtrar.
 * @param createLabel construye el texto del ítem de alta a partir de lo tecleado;
 *   `null` desactiva el alta (lista cerrada, como [ComboboxCcaa]).
 * @param emptyText mensaje cuando no hay coincidencias NI alta que ofrecer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    createLabel: ((String) -> String)? = null,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    val colors = RecreColors.current
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val queryTrim = query.trim()
    val filtered =
        remember(queryTrim, options) {
            if (queryTrim.isEmpty()) options else options.filter { it.contains(queryTrim, ignoreCase = true) }
        }
    val hayExacta =
        remember(queryTrim, options) {
            options.any { it.trim().equals(queryTrim, ignoreCase = true) }
        }
    val ofrecerCrear = createLabel != null && queryTrim.isNotEmpty() && !hayExacta

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        CampoFrame(
            label = label,
            focused = expanded || focused,
            isError = false,
            enabled = enabled,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled),
        ) {
            BasicTextField(
                value = if (expanded) query else value,
                onValueChange = { query = it },
                enabled = enabled,
                singleLine = true,
                interactionSource = interaction,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(colors.ring),
                decorationBox = { inner ->
                    Box {
                        val actual = if (expanded) query else value
                        if (actual.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.mutedStrong,
            )
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
            if (ofrecerCrear) {
                DropdownMenuItem(
                    text = { Text(createLabel!!(queryTrim)) },
                    onClick = {
                        onValueChange(queryTrim)
                        query = ""
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = colors.ring)
                    },
                )
            } else if (filtered.isEmpty()) {
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

@Preview(name = "FieldDate · con fecha", showBackground = true)
@Composable
private fun FieldDateLightPreview() {
    RecreTheme(darkTheme = false) {
        FieldDate(value = "2026-03-14", onValueChange = {}, label = "Fecha de expedición")
    }
}

@Preview(name = "FieldDate · vacío dark", showBackground = true)
@Composable
private fun FieldDateEmptyDarkPreview() {
    RecreTheme(darkTheme = true) {
        FieldDate(
            value = "",
            onValueChange = {},
            label = "Fecha de caducidad",
            placeholder = "Elegir fecha",
        )
    }
}
