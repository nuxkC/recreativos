package com.recre.app.feature.recaudacion

/**
 * Identificadores estables (`testTag`) de los nodos del flujo de
 * recaudación, compartidos entre los Composables de producción y las
 * pruebas instrumentadas (T-81).
 *
 * Se mantienen fuera de los `strings.xml` a propósito: los textos de UI
 * pueden cambiar por copy/i18n sin que las pruebas se rompan, mientras
 * que estos tags son un contrato de testabilidad. No se muestran al
 * usuario.
 */
object RecaudacionTestTags {

    // Paso 1 — contadores
    const val CONTADOR_ENTRADAS = "recaudacion_contador_entradas"
    const val CONTADOR_SALIDAS = "recaudacion_contador_salidas"
    const val CIFRAS_RESUMEN = "recaudacion_cifras_resumen"
    const val CONTADORES_CONTINUAR = "recaudacion_contadores_continuar"
    const val CONTADORES_LECTURA_NO_RECAUDADA = "recaudacion_contadores_lectura_no_recaudada"

    // Paso 1 — OCR foto de contadores (T-100)
    const val OCR_FOTO_ENTRADAS = "recaudacion_ocr_foto_entradas"
    const val OCR_FOTO_SALIDAS = "recaudacion_ocr_foto_salidas"
    const val OCR_AVISO = "recaudacion_ocr_aviso"

    // Paso 2 — denominaciones
    const val DENOMINACIONES_CONTINUAR = "recaudacion_denominaciones_continuar"
    const val DENOMINACIONES_DIFERENCIA = "recaudacion_denominaciones_diferencia"

    /** Campo de cantidad de una denominación concreta (key = "0.20", "10.00", …). */
    fun denominacionCantidad(denominacionKey: String): String =
        "recaudacion_denominacion_$denominacionKey"

    // Paso 4 — confirmación
    const val CONFIRMACION_GUARDAR = "recaudacion_confirmacion_guardar"
}
