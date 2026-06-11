package com.recre.app.feature.recaudacion.contadores

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Botón que abre el escáner OCR de contadores **en vivo** (T-100).
 *
 * Gestiona el permiso de cámara runtime y, una vez concedido, invoca
 * [onEscanear] para que la pantalla muestre el preview en directo
 * ([EscanerContadoresOverlay]). Ya no se captura ninguna foto: el OCR analiza
 * los fotogramas de la cámara al vuelo.
 */
@Composable
fun ContadorOcrBoton(
    label: String,
    testTag: String,
    onEscanear: () -> Unit,
    onPermisoDenegado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) onEscanear() else onPermisoDenegado()
    }

    OutlinedButton(
        onClick = {
            if (tienePermisoCamara(context)) {
                onEscanear()
            } else {
                permisoLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

private fun tienePermisoCamara(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
