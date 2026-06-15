package com.recre.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom C-KEYPAD-DENOM-AND (Fase 4 · T-232).
// SSOT: .kiro/specs/recre/fase3-component-specs.md (§ Sistema keypad de denominaciones).
//
// El ÚNICO teclado numérico in-app del producto (exclusivo del extracto de
// denominaciones): rejilla 3×4 anclada abajo en la thumb-zone que dirige la fila
// activa de la lista. Captura SOLO enteros (cantidades de piezas); el cálculo
// económico definitivo es SSOT servidor — este átomo no formatea ni recalcula
// dinero, solo emite eventos de dígito/borrado/siguiente.
//
// El resto de inputs numéricos de la app usan el teclado del SISTEMA (FieldNum);
// este keypad NO se reutiliza fuera de denominaciones (anti-patrón del spec).
//
// Tokens (mapeo rol→slot M3 del tema Recre):
//  - Tecla en reposo: surfaceContainer (= surface-2) + borde `outline` (= muted,
//    ≥3:1 sobre surface-1 para WCAG 1.4.11; NO outlineVariant #E3E6EA = 1.25:1).
//  - Dígito: onSurface (foreground), Geist Mono tabular.
//  - Tecla Siguiente (único acento del keypad): primary + onPrimary.
//  - Backspace: icono onSurfaceVariant (muted), contentDescription "Borrar".
//
// Haptic LIGERO tipo tick (KEYBOARD_TAP), NUNCA LongPress (fatiga en 30+ taps):
// gobernado por [hapticsEnabled] (ajuste in-app) además del ajuste del sistema
// (performHapticFeedback respeta el ajuste global salvo que se ignore, y no se
// ignora). La supresión del IME del sistema y el readOnly los aporta la pantalla.

private const val KEY_HEIGHT_DP = 64 // objetivo thumb-zone; muy por encima de 48dp mínimo
private val KEY_SHAPE = RoundedCornerShape(12.dp)

// Dígito de la tecla: Geist Mono tabular, tamaño cómodo para la thumb-zone.
private val KeypadDigitStyle =
    TextStyle(
        fontFamily = GeistMono,
        fontSize = 24.sp,
        fontWeight = FontWeight(500),
        fontFeatureSettings = "tnum", // tabular: el ancho no baila entre dígitos
    )

/**
 * Teclado numérico in-app 3×4 anclado al fondo (R5 del sistema de denominaciones).
 *
 * Filas: [1][2][3] / [4][5][6] / [7][8][9] / [⌫][0][Siguiente →]. La 4ª fila
 * reparte el ancho con weight 1f/1f/2f para que "Siguiente" (texto+icono) no se
 * trunque; toda tecla queda ≥48dp. El estado/valor lo posee la pantalla; aquí
 * solo se emiten eventos. Respeta safe-area inferior (navigationBarsPadding).
 *
 * @param onDigit dígito 0-9 pulsado (la pantalla lo concatena a la fila activa).
 * @param onBackspace borra el último dígito de la fila activa.
 * @param onNext salta a la siguiente denominación (siempre habilitado: mover el
 *   foco no depende de que cuadre — el gate de cuadre vive en el CTA "Continuar").
 * @param nextLabel texto de la tecla Siguiente. i18n por el llamador.
 * @param backspaceContentDescription descripción del backspace ("Borrar"). i18n.
 * @param nextContentDescription descripción de Siguiente ("Siguiente denominación"). i18n.
 * @param hapticsEnabled ajuste in-app de haptics; si false (o el sistema lo tiene
 *   desactivado) no se emite vibración.
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun Keypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    backspaceContentDescription: String,
    nextContentDescription: String,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding() // no quedar bajo la barra de navegación
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1..3, 4..6, 7..9).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    DigitKey(
                        digit = digit,
                        onDigit = onDigit,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // 4ª fila: backspace (1f) · 0 (1f) · Siguiente (2f, texto+icono → no trunca).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadKey(
                onClick = onBackspace,
                container = colors.surfaceContainer,
                border = colors.outline,
                hapticsEnabled = hapticsEnabled,
                contentDescription = backspaceContentDescription,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null, // el contentDescription va en la tecla
                    tint = colors.onSurfaceVariant, // muted
                )
            }
            DigitKey(
                digit = 0,
                onDigit = onDigit,
                hapticsEnabled = hapticsEnabled,
                modifier = Modifier.weight(1f),
            )
            KeypadKey(
                onClick = onNext,
                container = colors.primary, // único acento del keypad
                border = null,
                hapticsEnabled = hapticsEnabled,
                contentDescription = nextContentDescription,
                modifier = Modifier.weight(2f),
            ) {
                Text(text = nextLabel, color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.onPrimary,
                )
            }
        }
    }
}

/** Tecla de dígito 0-9: surfaceContainer + borde outline, dígito onSurface mono. */
@Composable
private fun DigitKey(
    digit: Int,
    onDigit: (Int) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    KeypadKey(
        onClick = { onDigit(digit) },
        container = colors.surfaceContainer,
        border = colors.outline, // ≥3:1; TalkBack anuncia el dígito por el Text
        hapticsEnabled = hapticsEnabled,
        contentDescription = null,
        modifier = modifier,
    ) {
        Text(text = digit.toString(), color = colors.onSurface, style = KeypadDigitStyle)
    }
}

/**
 * Tecla base: caja 64dp, fondo [container], borde 1px [border] (null = sin borde,
 * p. ej. la tecla de acento), feedback de pulsación + haptic tick ligero.
 */
@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    container: Color,
    border: Color?,
    hapticsEnabled: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier =
            modifier
                .height(KEY_HEIGHT_DP.dp)
                .clip(KEY_SHAPE)
                .background(container)
                .then(if (border != null) Modifier.border(1.dp, border, KEY_SHAPE) else Modifier)
                .clickable(role = Role.Button) {
                    // Tick ligero (no LongPress); respeta el ajuste del sistema por defecto.
                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription; role = Role.Button }
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "Keypad · light", showBackground = true)
@Composable
private fun KeypadLightPreview() {
    RecreTheme(darkTheme = false) {
        Keypad(
            onDigit = {},
            onBackspace = {},
            onNext = {},
            nextLabel = "Siguiente",
            backspaceContentDescription = "Borrar",
            nextContentDescription = "Siguiente denominación",
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Preview(name = "Keypad · dark", showBackground = true)
@Composable
private fun KeypadDarkPreview() {
    RecreTheme(darkTheme = true) {
        Keypad(
            onDigit = {},
            onBackspace = {},
            onNext = {},
            nextLabel = "Siguiente",
            backspaceContentDescription = "Borrar",
            nextContentDescription = "Siguiente denominación",
            modifier = Modifier.padding(8.dp),
        )
    }
}
