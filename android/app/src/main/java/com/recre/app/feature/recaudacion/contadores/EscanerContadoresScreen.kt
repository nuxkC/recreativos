package com.recre.app.feature.recaudacion.contadores

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.recre.app.ui.components.IconActionTile
import com.recre.app.ui.components.RecreGhostButton
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusChipSize
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreColors
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

        ChromeEscaner(
            deteccion = deteccion,
            onUsarLectura = onUsarLectura,
            onCerrar = onCerrar,
        )
    }
}

/**
 * Chrome del mockup «Neón de sala» (N7) superpuesto a la cámara, SIN obturador
 * (decisión 2026-07-09): cabecera con back-tile + título, marco de esquinas con
 * la lectura E/S en vivo dentro, guía, chip de lectura estable y el CTA «Usar
 * lectura» al pie. Toda la lógica OCR viva se conserva aguas arriba; aquí solo
 * se presenta la detección estabilizada.
 */
@Composable
private fun ChromeEscaner(
    deteccion: ContadorOcrAmbosResult?,
    onUsarLectura: (Long, Long) -> Unit,
    onCerrar: () -> Unit,
) {
    val entradas = deteccion?.entradas
    val salidas = deteccion?.salidas
    val ambos = entradas != null && salidas != null

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Cabecera: back-tile + título. El título es blanco fijo sobre la cámara.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconActionTile(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.recaudacion_ocr_escaner_cancelar),
                onClick = onCerrar,
            )
            Text(
                text = stringResource(R.string.recaudacion_ocr_escanear_corto),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Centro: marco de esquinas con la lectura E/S en vivo dentro + guía.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MarcoEsquinas {
                if (entradas != null || salidas != null) {
                    LecturaEnVivo(entradas = entradas, salidas = salidas)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Guía sobre scrim translúcido para legibilidad sobre la cámara.
            Text(
                text = stringResource(R.string.recaudacion_ocr_escaner_guia),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // Pie: chip de lectura estable + CTA glow + cancelar fantasma.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ambos) {
                StatusChip(
                    role = StatusRole.SUCCESS,
                    label = stringResource(
                        R.string.recaudacion_ocr_escaner_detectado,
                        entradas.toString(),
                        salidas.toString(),
                    ),
                    icon = Icons.Filled.Check,
                    size = StatusChipSize.SM,
                )
                if (deteccion?.confianza == Confianza.BAJA) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.recaudacion_ocr_aviso_baja_confianza),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            RecrePrimaryButton(
                text = stringResource(R.string.recaudacion_ocr_escaner_usar),
                onClick = { if (entradas != null && salidas != null) onUsarLectura(entradas, salidas) },
                enabled = ambos,
                fullWidth = true,
                modifier = Modifier.testTag(RecaudacionTestTags.OCR_ESCANER_USAR),
            )
            Spacer(Modifier.height(8.dp))
            RecreGhostButton(
                text = stringResource(R.string.recaudacion_ocr_escaner_cancelar),
                onClick = onCerrar,
                fullWidth = true,
                mini = true,
            )
        }
    }
}

/**
 * Lectura E/S en vivo dentro del marco: dos líneas «E …» / «S …» en Geist Mono
 * grande (~21sp). Solo presentación de la detección estabilizada.
 */
@Composable
private fun LecturaEnVivo(entradas: Long?, salidas: Long?) {
    val estilo = MaterialTheme.typography.titleLarge.copy(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W600,
        fontSize = 21.sp,
        fontFeatureSettings = "tnum",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "E ${entradas?.toString() ?: "—"}",
            style = estilo,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "S ${salidas?.toString() ?: "—"}",
            style = estilo,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Marco de encuadre `.cam-marco` del mockup: 4 esquinas en L dibujadas con
 * [Canvas] (trazo `accentBright`, ~22dp cada segmento), sobre un scrim suave que
 * da contraste a la lectura en vivo. [contenido] se centra dentro del marco.
 */
@Composable
private fun MarcoEsquinas(contenido: @Composable () -> Unit) {
    val trazo = RecreColors.current.accentBright
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val largo = 22.dp.toPx()
            val grosor = 3.dp.toPx()
            val w = size.width
            val h = size.height
            // Cada esquina = dos segmentos en L; cap redondeado para un trazo limpio.
            fun l(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(trazo, Offset(x1, y1), Offset(x2, y2), grosor, StrokeCap.Round)
            // Superior izquierda.
            l(0f, 0f, largo, 0f); l(0f, 0f, 0f, largo)
            // Superior derecha.
            l(w, 0f, w - largo, 0f); l(w, 0f, w, largo)
            // Inferior izquierda.
            l(0f, h, largo, h); l(0f, h, 0f, h - largo)
            // Inferior derecha.
            l(w, h, w - largo, h); l(w, h, w, h - largo)
        }
        contenido()
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
