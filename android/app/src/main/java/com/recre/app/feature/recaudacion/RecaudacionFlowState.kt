package com.recre.app.feature.recaudacion

import androidx.compose.ui.geometry.Offset
import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.CreditoAbierto
import com.recre.app.core.calculo.PlanRecuperacion
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.repository.MaquinaConInstalacion
import com.recre.app.core.locks.LockState
import com.recre.app.core.printer.PrintResult

/**
 * Estado compartido entre las pantallas del flujo de recaudación.
 *
 * Vive en [RecaudacionFlowViewModel] scoped al sub-NavGraph
 * `recaudacion/{instalacionId}`. Sobrevive a las navegaciones internas
 * y muere al popUp del graph.
 */
data class RecaudacionFlowState(
    val cargando: Boolean = true,
    val maquina: MaquinaConInstalacion? = null,
    val empresa: EmpresaParamsEntity? = null,
    val errorCarga: String? = null,

    /**
     * `true` si la última sincronización es > 48 h o nunca se sincronizó.
     * El flujo se bloquea al iniciar si esto se cumple (T-59); el técnico
     * tiene que volver a Locales y forzar sync.
     */
    val syncStale: Boolean = false,

    /**
     * `true` si los contadores BASE de la instalación cambiaron a mitad del
     * flujo —otra recaudación de la misma máquina o una ANULACIÓN desde la web,
     * que un re-sync trajo en vivo— DESPUÉS de que el técnico ya hubiera contado
     * las monedas. El desglose contado deja de cuadrar con el bruto recalculado,
     * así que el guardado se bloquea: hay que rehacer la lectura con la baseline
     * nueva (si no, el server la rechazaría por "el desglose no coincide").
     */
    val baselineCambiada: Boolean = false,

    // T-58 — Lock optimista
    val lockState: LockState = LockState.Inactivo,

    // Paso 1 — contadores
    val contadorEntradasInput: String = "",
    val contadorSalidasInput: String = "",
    val cifras: Cifras? = null,

    // T-215 — recuperación de deuda (espejo offline del SSOT)
    /** Deudas abiertas del local (cacheadas en sync), orden tolva → FIFO. */
    val creditosAbiertos: List<CreditoAbierto> = emptyList(),
    /** % resuelto a aplicar = COALESCE(local.override, empresa.default). */
    val porcentajeRecuperacion: Int = 0,
    /**
     * Orden manual de imputación elegido por el técnico (lista de credito_id).
     * `null` = orden por defecto (tolva → FIFO). Solo cambia a qué deuda se
     * imputa primero, no el total entregado al local.
     */
    val ordenManual: List<String>? = null,
    /**
     * Plan de recuperación calculado sobre [cifras]+[creditosAbiertos]. `null`
     * mientras no hay cifras válidas. Cuando existe, el desglose entregado al
     * local debe cuadrar con `recuperacion.pagadoLocal` (no con parte_local).
     */
    val recuperacion: PlanRecuperacion? = null,

    // OCR de contadores en vivo (T-100, HU-14 fase 2): el escáner
    // (`EscanerContadoresOverlay`) detecta ambos contadores sobre el preview de
    // la cámara y el técnico confirma; los valores caen en los inputs de arriba.
    // No se guarda estado de OCR en el flujo: el ciclo de vida del escáner vive
    // en la propia pantalla de contadores.

    // Paso 2 — denominaciones (key = denominación como String "0.10", "1.00", …)
    val denominacionesTotal: Map<String, Int> = emptyMap(),
    val denominacionesLocal: Map<String, Int> = emptyMap(),

    // Paso 3 — firma. Capturada como una lista de strokes (cada stroke es
    // una secuencia de puntos). La rasterización a Bitmap se hace al
    // guardar (FirmaRenderer).
    val firmaStrokes: List<List<Offset>> = emptyList(),

    // Paso 4 — confirmación
    val guardando: Boolean = false,
    val guardado: Boolean = false,

    /**
     * Tras guardar, indica si la recaudación se subió en línea (true) o
     * quedó en la cola offline (false). La confirmación muestra mensajes
     * distintos.
     */
    val subidoOnline: Boolean = false,

    // T-62 — Impresión Bluetooth tras guardar.
    /**
     * Estado de la impresión tras guardar:
     *  - `null` mientras no se haya intentado o esté en curso.
     *  - [PrintResult.Success] si el ticket se envió a la PT210.
     *  - [PrintResult.Failure] con un [PrinterError] discriminado.
     *
     * Es informativo: la recaudación queda guardada igual aunque la
     * impresión falle. La UI ofrece "Reintentar impresión".
     */
    val printResult: PrintResult? = null,

    /** `true` mientras se ejecuta la operación de impresión. */
    val imprimiendo: Boolean = false,

    /**
     * Modo "Recaudar todas en cadena" (T-60). Si el usuario entró desde
     * el botón del detalle del local, el ViewModel guarda aquí el id del
     * local y la lista ordenada de instalaciones activas. Tras guardar
     * o saltar, la pantalla resuelve el [siguienteInstalacionId] y
     * navega a él en lugar de volver al detalle.
     */
    val cadena: CadenaState? = null,
)

/**
 * Snapshot del estado en cadena. Las pantallas usan estos campos para
 * mostrar "Máquina X de N" en el subtítulo y para decidir el destino
 * tras `Guardar` / `Saltar`.
 */
data class CadenaState(
    val localId: String,
    val instalacionesOrdenadas: List<String>,
    val instalacionActualId: String,
) {
    /** 1-based. Si no encuentra el id, devuelve 1. */
    val posicion: Int
        get() = (instalacionesOrdenadas.indexOf(instalacionActualId).takeIf { it >= 0 } ?: 0) + 1

    val total: Int get() = instalacionesOrdenadas.size

    /** Siguiente id en la cadena, o `null` si era la última. */
    val siguienteInstalacionId: String?
        get() {
            val idx = instalacionesOrdenadas.indexOf(instalacionActualId)
            if (idx < 0) return null
            return instalacionesOrdenadas.getOrNull(idx + 1)
        }
}

/** Identifica el destino dentro del NavGraph del flujo. */
enum class PasoRecaudacion {
    Contadores,
    DenominacionesTotal,
    DenominacionesLocal,
    Confirmacion,
}
