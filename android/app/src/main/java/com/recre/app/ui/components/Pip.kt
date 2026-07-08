package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

/** Rol de tinte del pip. ACCENT = cian de marca (el 90% de los usos del mockup). */
enum class PipRole { ACCENT, SUCCESS, WARNING, DANGER, NEUTRAL }

/**
 * Pip (S5, mockup «Neón de sala»): icono dentro de un tile 40dp radio 14 con
 * fondo translúcido del rol y trazo en su color vivo. SIEMPRE decorativo
 * (contentDescription = null): el texto de la fila ya nombra la entrada; el pip
 * solo da anclaje visual. No es tapeable — vive dentro de filas que ya lo son.
 */
@Composable
fun Pip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    role: PipRole = PipRole.ACCENT,
) {
    val c = RecreColors.current
    val tint =
        when (role) {
            PipRole.ACCENT -> c.accentBright
            PipRole.SUCCESS -> c.successText
            PipRole.WARNING -> c.warningText
            PipRole.DANGER -> c.dangerText
            PipRole.NEUTRAL -> c.muted
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.10f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B24)
@Composable
private fun PipPreview() {
    RecreTheme { Pip(Icons.Filled.AccountBalanceWallet) }
}
