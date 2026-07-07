package com.recre.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType

// =====================================================================
// Átomo OdometroText · firma «Neón de sala» (N1).
//
// La cifra que rueda: cada dígito sube como el contador mecánico de una
// máquina recreativa. Recibe el texto YA formateado (mismo formateador que
// MoneyText) — este componente NO sabe de BigDecimal ni de € ni de miles: solo
// pinta columnas. Los caracteres fijos (separadores «.» «,», espacio, «€») no
// ruedan; los dígitos animan con retardo escalonado para el efecto de rodillo.
// =====================================================================

/** Columna del odómetro: un dígito que rueda o un carácter fijo (separador, €). */
sealed interface ColumnaOdometro {
    data class Digito(val valor: Int) : ColumnaOdometro

    data class Fijo(val caracter: Char) : ColumnaOdometro
}

/** Pura y JVM-testeable: descompone el texto formateado en columnas. */
fun columnasOdometro(texto: String): List<ColumnaOdometro> =
    texto.map { ch ->
        if (ch.isDigit()) ColumnaOdometro.Digito(ch.digitToInt()) else ColumnaOdometro.Fijo(ch)
    }

/**
 * Cifra que rueda por dígito, como el contador mecánico de una máquina (firma
 * «Neón de sala»). Recibe el texto YA formateado (mismo formateador que MoneyText):
 * este componente no sabe de BigDecimal. Cada dígito anima con un retardo
 * escalonado (45ms/columna) para el efecto de rodillo. Sin `color` explícito
 * hereda `LocalContentColor` (como un `Text` de M3). Accesibilidad: el Row
 * expone el texto completo como una sola descripción; las columnas no son focables.
 */
@Composable
fun OdometroText(
    texto: String,
    modifier: Modifier = Modifier,
    style: TextStyle = RecreType.importe,
    color: Color = Color.Unspecified,
) {
    val colorFinal = if (color == Color.Unspecified) LocalContentColor.current else color
    Row(
        modifier =
            modifier.semantics { contentDescription = texto },
        verticalAlignment = Alignment.Bottom,
    ) {
        columnasOdometro(texto).forEachIndexed { index, columna ->
            when (columna) {
                is ColumnaOdometro.Fijo ->
                    Text(
                        text = columna.caracter.toString(),
                        style = style,
                        color = colorFinal,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                is ColumnaOdometro.Digito ->
                    AnimatedContent(
                        targetState = columna.valor,
                        modifier = Modifier.clipToBounds(), // ventana de odómetro: el dígito no pinta fuera mid-roll
                        transitionSpec = {
                            // Literal 500ms: Motion.kt no expone un token de énfasis que encaje.
                            // LocalRecreMotion/ExpressiveRecreMotion solo dan FiniteAnimationSpec
                            // (springs/tweens) y no pueden inyectar el retardo escalonado por
                            // columna; los Int de RecreMotionDurations (SLOW_MS=400, COUNT_UP_MS=600)
                            // rodean 500 pero ninguno es la duración del rodillo. Se deja el literal.
                            val duracion = 500
                            val retardo = index * 45
                            (
                                slideInVertically(tween(duracion, retardo)) { alto -> alto } togetherWith
                                    slideOutVertically(tween(duracion, retardo)) { alto -> -alto }
                            )
                        },
                        label = "odometro-digito",
                    ) { digito ->
                        Text(
                            text = digito.toString(),
                            style = style,
                            color = colorFinal,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "OdometroText · light", showBackground = true)
@Composable
private fun OdometroTextPreviewLight() {
    RecreTheme(darkTheme = false) {
        OdometroTextPreviewContent()
    }
}

@Preview(name = "OdometroText · dark", showBackground = true)
@Composable
private fun OdometroTextPreviewDark() {
    RecreTheme(darkTheme = true) {
        OdometroTextPreviewContent()
    }
}

@Composable
private fun OdometroTextPreviewContent() {
    // Surface para que los OdometroText con color por defecto resuelvan
    // LocalContentColor a onSurface (sin él quedaría el negro por defecto).
    Surface {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            OdometroText("1.284,50 €")
            OdometroText("99.999,99 €", style = RecreType.importeMedium)
            // El param color acepta el cian de marca (accentBright) para la cifra protagonista.
            OdometroText("840,00 €", color = RecreColors.current.accentBright)
            OdometroText("—")
        }
    }
}
