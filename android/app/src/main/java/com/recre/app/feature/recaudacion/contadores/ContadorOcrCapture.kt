package com.recre.app.feature.recaudacion.contadores

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Botón único de captura de foto de los contadores para OCR (T-100).
 *
 * Una sola foto contiene ambos contadores (entradas y salidas); el ViewModel
 * los identifica con [com.recre.app.core.ocr.ContadorOcrParser.parseAmbos].
 *
 * Gestiona el permiso de cámara runtime y la captura con un intent de cámara
 * (`ActivityResultContracts.TakePicture`), evitando depender de CameraX. La
 * foto se guarda en un fichero temporal de la caché compartido vía
 * `FileProvider`; tras capturarla se invoca [onFotoCapturada] con su `Uri`
 * para que el ViewModel lance el reconocimiento.
 *
 * Mientras [procesando] es `true` el botón se deshabilita y muestra un
 * indicador de progreso.
 */
@Composable
fun ContadorOcrBoton(
    label: String,
    testTag: String,
    procesando: Boolean,
    onFotoCapturada: (Uri) -> Unit,
    onPermisoDenegado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Guardamos la Uri de la captura en curso para entregarla al callback
    // cuando la cámara confirme que la foto se guardó.
    val capturaUri = remember { CapturaUriHolder() }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { exito ->
        val uri = capturaUri.value
        if (exito && uri != null) {
            onFotoCapturada(uri)
        }
    }

    fun lanzarCamara() {
        val uri = crearImagenTemporalUri(context)
        capturaUri.value = uri
        takePictureLauncher.launch(uri)
    }

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) lanzarCamara() else onPermisoDenegado()
    }

    OutlinedButton(
        onClick = {
            if (tienePermisoCamara(context)) {
                lanzarCamara()
            } else {
                permisoLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        enabled = !procesando,
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (procesando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

/** Contenedor mutable simple para conservar la Uri entre recomposiciones. */
private class CapturaUriHolder {
    var value: Uri? = null
}

private fun tienePermisoCamara(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun crearImagenTemporalUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
    val archivo = File.createTempFile("contador_", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        archivo,
    )
}
