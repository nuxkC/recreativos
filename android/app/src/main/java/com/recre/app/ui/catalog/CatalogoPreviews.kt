package com.recre.app.ui.catalog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.MoneyText
import com.recre.app.ui.components.MoneyTextSize
import com.recre.app.ui.components.RecreDottedDivider
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.theme.RecreTheme
import java.math.BigDecimal

// =====================================================================
// Catálogo del sistema de diseño (rediseño F0). Documentación VIVA: @Preview de los
// componentes clave en claro y oscuro, envueltos en RecreTheme, para Android Studio.
// No es código de producción: solo lo rendea el panel de previews del IDE.
// =====================================================================

@Preview(name = "Catálogo · claro", showBackground = true)
@Preview(
    name = "Catálogo · oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CatalogoPreview() {
    RecreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountUpText(importe = "1200.00", size = MoneyTextSize.Hero)
            MoneyText(amount = BigDecimal("1234.56"), size = MoneyTextSize.Medium)
            RecreDottedDivider()
            StatusChip(StatusRole.SUCCESS, "Cuadra", Icons.Filled.Check)
            RecrePrimaryButton(text = "Recaudar", onClick = {})
            AppCard {
                Text("Tarjeta de ejemplo (AppCard)")
            }
            FieldText(
                value = "Bar Pepe",
                onValueChange = {},
                label = "Nombre del local",
            )
        }
    }
}
