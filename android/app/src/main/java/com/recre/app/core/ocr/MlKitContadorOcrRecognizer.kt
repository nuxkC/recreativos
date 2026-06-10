package com.recre.app.core.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

/**
 * Implementación on-device del [ContadorOcrRecognizer] con ML Kit Text
 * Recognition (modelo latino empaquetado en la app, sin red).
 *
 * Solo se encarga de obtener el texto crudo de la imagen; la extracción del
 * número del contador la hace [ContadorOcrParser]. No persiste ni sube la
 * foto: la imagen se usa únicamente para el reconocimiento local (ver nota de
 * alcance en el PR de T-100).
 */
@Singleton
class MlKitContadorOcrRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContadorOcrRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun reconocerTexto(imagen: Uri): OcrTextoResult {
        val input = try {
            InputImage.fromFilePath(context, imagen)
        } catch (e: IOException) {
            Timber.w(e, "No se pudo abrir la imagen para OCR")
            return OcrTextoResult.Fallo(OcrError.ImagenNoAccesible)
        }

        return suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { texto ->
                    cont.resume(OcrTextoResult.Exito(texto.text))
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "Fallo el reconocimiento de texto OCR")
                    cont.resume(OcrTextoResult.Fallo(OcrError.FalloReconocimiento(e.message)))
                }
        }
    }
}
