package com.recre.app.core.ocr

import android.net.Uri

/**
 * Contrato y tipos del subsistema de OCR de contadores (T-100, HU-14 fase 2).
 *
 * El OCR es una **ayuda**, nunca la fuente de verdad: detecta el número del
 * display de la máquina a partir de una foto y pre-rellena el campo
 * correspondiente, pero el técnico siempre confirma o corrige el valor antes
 * de continuar. El cálculo de la recaudación sigue siendo server-side; aquí
 * solo rellenamos inputs locales.
 *
 * Sigue el mismo patrón que el subsistema de impresión ([com.recre.app.core.printer]):
 * interfaz de dominio + implementación de datos en el mismo paquete `core`,
 * con tipos de error y resultado discriminados para que la UI mapee cada caso
 * a un copy concreto.
 */

/**
 * Reconocedor de texto sobre una imagen. La implementación on-device
 * ([MlKitContadorOcrRecognizer]) usa ML Kit Text Recognition.
 *
 * Devuelve el texto crudo reconocido; la extracción del número del contador
 * es responsabilidad de [ContadorOcrParser] (lógica pura y testeable).
 */
interface ContadorOcrRecognizer {

    /**
     * Reconoce el texto presente en la imagen apuntada por [imagen].
     *
     * Nunca lanza excepciones: los fallos técnicos se mapean a
     * [OcrTextoResult.Fallo] con un [OcrError] discriminado.
     */
    suspend fun reconocerTexto(imagen: Uri): OcrTextoResult
}

/** Resultado del reconocimiento de texto sobre la imagen. */
sealed interface OcrTextoResult {

    /** Reconocimiento correcto. [textoCrudo] puede venir vacío si no había texto. */
    data class Exito(val textoCrudo: String) : OcrTextoResult

    /** Fallo técnico al acceder a la imagen o al reconocer. */
    data class Fallo(val error: OcrError) : OcrTextoResult
}

/**
 * Modos de fallo del OCR. No reutilizamos `DomainError` (red/auth) porque los
 * casos son distintos y la UI necesita diferenciarlos para mostrar el aviso
 * adecuado y dejar siempre la edición manual disponible.
 */
sealed interface OcrError {

    /** No se pudo abrir/decodificar la imagen capturada. */
    data object ImagenNoAccesible : OcrError

    /** El reconocedor terminó sin texto o sin ningún número plausible. */
    data object SinTextoDetectable : OcrError

    /** Falló el motor de reconocimiento (excepción interna de ML Kit). */
    data class FalloReconocimiento(val mensaje: String?) : OcrError
}
