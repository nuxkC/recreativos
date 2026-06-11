package com.recre.app.core.ocr

/**
 * Estabiliza temporalmente las lecturas del OCR en vivo de contadores (T-100).
 *
 * El reconocedor corre sobre cada fotograma y, con el display borroso o en
 * movimiento, reinterpreta los dígitos: en un mismo segundo puede emitir dos o
 * tres valores parecidos (parpadeo). Publicar cada lectura cruda haría que el
 * panel saltara sin parar.
 *
 * Esta clase acumula las lecturas recientes dentro de una ventana temporal y
 * solo da por buena la pareja (entradas, salidas) que domina por **consenso**:
 * la más frecuente de la ventana, siempre que alcance un mínimo de muestras y
 * acapare al menos [minFraccion] de ellas. Una vez fijada una lectura estable,
 * se mantiene hasta que otra pareja distinta gana el consenso (**histéresis**):
 * así un fotograma borroso aislado no la tumba y al técnico le da tiempo a
 * confirmarla con "Usar lectura".
 *
 * Lógica **pura** (sin Android ni ML Kit): el llamador aporta la marca de tiempo
 * de cada fotograma, lo que la hace determinista y testeable en `src/test/`.
 *
 * No es thread-safe: se asume que [estabilizar] se invoca siempre desde el mismo
 * hilo (en la pantalla, el executor del analizador de CameraX).
 *
 * @param ventanaMs duración de la ventana deslizante de lecturas consideradas.
 * @param minMuestras mínimo de lecturas en la ventana para decidir un ganador.
 * @param minFraccion fracción de la ventana que debe acaparar la pareja ganadora.
 */
class EstabilizadorContadorOcr(
    private val ventanaMs: Long = VENTANA_MS_DEFECTO,
    private val minMuestras: Int = MIN_MUESTRAS_DEFECTO,
    private val minFraccion: Double = MIN_FRACCION_DEFECTO,
) {
    private data class Muestra(val lectura: ContadorOcrAmbosResult, val instanteMs: Long)
    private data class Pareja(val entradas: Long?, val salidas: Long?)

    private val ventana = ArrayDeque<Muestra>()
    private var estable: ContadorOcrAmbosResult? = null

    /**
     * Registra la [lectura] del fotograma capturado en [instanteMs] (monótono,
     * en milisegundos) y devuelve la lectura **estable** vigente, o `null` si
     * todavía no hay consenso suficiente.
     *
     * Las lecturas sin entradas identificadas ([ContadorOcrAmbosResult.entradas]
     * nulo) se ignoran: un fotograma que no detecta nada no debe resetear ni
     * competir contra un valor ya estabilizado, solo dejar que la ventana avance.
     */
    fun estabilizar(lectura: ContadorOcrAmbosResult, instanteMs: Long): ContadorOcrAmbosResult? {
        if (lectura.entradas != null) {
            ventana.addLast(Muestra(lectura, instanteMs))
        }
        purgar(instanteMs)
        votar()
        return estable
    }

    /** Olvida el historial y la lectura estable (al reabrir el escáner). */
    fun reiniciar() {
        ventana.clear()
        estable = null
    }

    /** Descarta las muestras que caen fuera de la ventana deslizante. */
    private fun purgar(instanteMs: Long) {
        val limite = instanteMs - ventanaMs
        while (ventana.isNotEmpty() && ventana.first().instanteMs < limite) {
            ventana.removeFirst()
        }
    }

    /** Fija [estable] si una pareja domina la ventana por mayoría suficiente. */
    private fun votar() {
        if (ventana.size < minMuestras) return
        val ganadora = ventana
            .groupBy { Pareja(it.lectura.entradas, it.lectura.salidas) }
            .maxByOrNull { it.value.size }
            ?: return
        if (ganadora.value.size >= ventana.size * minFraccion) {
            // Conserva la lectura más reciente de la pareja ganadora: mantiene la
            // confianza tal y como la estimó el parser para ese conjunto.
            estable = ganadora.value.last().lectura
        }
    }

    private companion object {
        /** ~1,2 s: suficiente para promediar el parpadeo sin notar latencia. */
        const val VENTANA_MS_DEFECTO = 1_200L

        /** Por debajo de esto no decidimos: poca evidencia todavía. */
        const val MIN_MUESTRAS_DEFECTO = 4

        /** Mayoría clara (60 %) para evitar fijar una lectura dudosa. */
        const val MIN_FRACCION_DEFECTO = 0.6
    }
}
