package com.recre.app.feature.recaudacion.contadores

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.recre.app.R
import com.recre.app.core.ocr.Confianza
import com.recre.app.core.ocr.ContadorOcrAmbosResult
import com.recre.app.core.ocr.ContadorOcrParser
import com.recre.app.core.ocr.EstabilizadorContadorOcr
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import timber.log.Timber

/**
 * Escáner OCR de contadores **en directo** (T-100, HU-14 fase 2).
 *
 * Sustituye a la captura por foto: abre el preview de la cámara con CameraX y
 * analiza cada fotograma con ML Kit Text Recognition, sin guardar imágenes. La
 * identificación de qué número es entradas y cuál salidas es 100 % la lógica
 * pura de [ContadorOcrParser.parseAmbos] (mismos filtros de dominio), de modo
 * que el SSOT del parseo no se duplica.
 *
 * El OCR sigue siendo una ayuda: la lectura detectada se muestra en vivo y el
 * técnico la confirma con "Usar lectura"; los valores caen en los inputs, que
 * puede corregir antes de continuar. Nunca rellena sin confirmación.
 *
 * @param baselineEntradas última lectura de entradas (filtro: entradas ≥ esto).
 * @param baselineSalidas última lectura de salidas (filtro: salidas ≥ esto).
 * @param onUsarLectura el técnico acepta la lectura detectada (entradas, salidas).
 * @param onCerrar cierra el escáner sin aplicar nada.
 */
@Composable
fun EscanerContadoresOverlay(
    baselineEntradas: Long,
    baselineSalidas: Long,
    onUsarLectura: (entradas: Long, salidas: Long) -> Unit,
    onCerrar: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lectura estable que se muestra al técnico. El analizador emite una lectura
    // por fotograma; el estabilizador las consolida por consenso para eliminar el
    // parpadeo del reconocedor (ver EstabilizadorContadorOcr).
    var deteccion by remember { mutableStateOf<ContadorOcrAmbosResult?>(null) }
    val estabilizador = remember { EstabilizadorContadorOcr() }

    // Reconocedor ML Kit y proveedor de cámara: se liberan al cerrar el escáner.
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraProviderHolder = remember { CameraProviderHolder() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderHolder.value?.unbindAll() }
            runCatching { recognizer.close() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(RecaudacionTestTags.OCR_ESCANER),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val cameraProvider = future.get()
                    cameraProviderHolder.value = cameraProvider

                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analisis = ImageAnalysis.Builder()
                        // Solo el fotograma más reciente: descarta los atrasados
                        // para no acumular latencia mientras el OCR procesa.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(
                                ContextCompat.getMainExecutor(ctx),
                                ContadoresAnalyzer(
                                    recognizer = recognizer,
                                    baselineEntradas = baselineEntradas,
                                    baselineSalidas = baselineSalidas,
                                    onResultado = { cruda, instanteMs ->
                                        deteccion = estabilizador.estabilizar(cruda, instanteMs)
                                    },
                                ),
                            )
                        }

                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analisis,
                        )
                    }.onFailure { Timber.w(it, "No se pudo abrir la cámara para el OCR en vivo") }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        PanelDeteccion(
            deteccion = deteccion,
            onUsarLectura = onUsarLectura,
            onCerrar = onCerrar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

/**
 * Panel inferior: muestra la guía o la lectura detectada en vivo y los botones
 * de confirmar/cancelar. "Usar lectura" se habilita en cuanto se identifican
 * ambos contadores; si la confianza es baja, avisa para que el técnico revise.
 */
@Composable
private fun PanelDeteccion(
    deteccion: ContadorOcrAmbosResult?,
    onUsarLectura: (Long, Long) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entradas = deteccion?.entradas
    val salidas = deteccion?.salidas
    val ambos = entradas != null && salidas != null

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (ambos) {
                Text(
                    text = stringResource(
                        R.string.recaudacion_ocr_escaner_detectado,
                        entradas.toString(),
                        salidas.toString(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (deteccion?.confianza == Confianza.BAJA) {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = stringResource(R.string.recaudacion_ocr_aviso_baja_confianza),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.recaudacion_ocr_escaner_guia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.padding(top = 12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                RecreTonalButton(
                    text = stringResource(R.string.recaudacion_ocr_escaner_cancelar),
                    onClick = onCerrar,
                    modifier = Modifier.width(140.dp),
                )
                Spacer(Modifier.width(12.dp))
                RecrePrimaryButton(
                    text = stringResource(R.string.recaudacion_ocr_escaner_usar),
                    onClick = { if (entradas != null && salidas != null) onUsarLectura(entradas, salidas) },
                    enabled = ambos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RecaudacionTestTags.OCR_ESCANER_USAR),
                )
            }
        }
    }
}

/** Contenedor mutable simple para conservar el proveedor de cámara y poder desligarlo. */
private class CameraProviderHolder {
    var value: ProcessCameraProvider? = null
}

/**
 * [ImageAnalysis.Analyzer] que corre ML Kit sobre cada fotograma y publica la
 * lectura de ambos contadores ([ContadorOcrParser.parseAmbos]). Cierra siempre
 * el [ImageProxy] al terminar para no bloquear el flujo de fotogramas.
 */
private class ContadoresAnalyzer(
    private val recognizer: TextRecognizer,
    private val baselineEntradas: Long,
    private val baselineSalidas: Long,
    private val onResultado: (lectura: ContadorOcrAmbosResult, instanteMs: Long) -> Unit,
) : ImageAnalysis.Analyzer {

    // ExperimentalGetImage es un @RequiresOptIn de AndroidX (Java), no de
    // Kotlin: hay que optar con androidx.annotation.OptIn; el kotlin.OptIn "no
    // tiene efecto" (warning del compilador) y lint marca UnsafeOptInUsageError.
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        // Marca de tiempo del fotograma (monótona, ns -> ms): el estabilizador la
        // usa para su ventana temporal. Se captura antes del proceso asíncrono.
        val instanteMs = imageProxy.imageInfo.timestamp / 1_000_000
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { texto ->
                onResultado(
                    ContadorOcrParser.parseAmbos(
                        textoCrudo = texto.text,
                        baselineEntradas = baselineEntradas,
                        baselineSalidas = baselineSalidas,
                    ),
                    instanteMs,
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
