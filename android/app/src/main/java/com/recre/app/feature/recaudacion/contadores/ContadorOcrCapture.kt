package com.recre.app.feature.recaudacion.contadores

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.recre.app.ui.components.RecreGhostButton

/**
 * Botón que abre el escáner OCR de contadores **en vivo** (T-100).
 *
 * Gestiona el permiso de cámara runtime y, una vez concedido, invoca
 * [onEscanear] para que la pantalla muestre el preview en directo
 * ([EscanerContadoresOverlay]). Ya no se captura ninguna foto: el OCR analiza
 * los fotogramas de la cámara al vuelo.
 *
 * Neón N7: acción fantasma mini (píldora transparente con icono de cámara), no
 * gasta acento — es una ayuda opcional dentro del flujo de lecturas.
 */
@Composable
fun ContadorOcrBoton(
    label: String,
    testTag: String,
    onEscanear: () -> Unit,
    onPermisoDenegado: () -> Unit,
    fullWidth: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) onEscanear() else onPermisoDenegado()
    }

    RecreGhostButton(
        text = label,
        onClick = {
            if (tienePermisoCamara(context)) {
                onEscanear()
            } else {
                permisoLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        leadingIcon = Icons.Filled.PhotoCamera,
        fullWidth = fullWidth,
        mini = true,
        modifier = modifier.testTag(testTag),
    )
}

private fun tienePermisoCamara(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
