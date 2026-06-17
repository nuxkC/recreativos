package com.recre.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors

/**
 * Banner global discreto de "sin conexión" (patrón P8 del rediseño). Ocupa el ancho
 * y avisa de que lo pendiente se subirá al volver la red. NO es un error (no va en
 * rojo): usa el rol warning (ámbar) — informativo, no alarmante. "Estado nunca solo
 * por color": lleva icono + texto. Mostrar solo cuando [visible]; entra y sale animado
 * y se anuncia a TalkBack como región viva educada.
 */
@Composable
fun OfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        val colors = RecreColors.current
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.warningContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null, // el texto contiguo ya lo describe
                tint = colors.onWarningContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onWarningContainer,
            )
        }
    }
}
