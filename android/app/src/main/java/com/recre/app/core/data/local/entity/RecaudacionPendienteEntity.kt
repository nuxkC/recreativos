package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Recaudación pendiente de subir al backend (T-57).
 *
 * El técnico siempre puede guardar — incluso sin red. La fila vive aquí
 * hasta que el [com.recre.app.core.sync.RecaudacionUploadWorker] la suba
 * vía Edge Function `crear-recaudacion`. Tras éxito se marca como `enviada`
 * (no se borra: queremos histórico local para `T-63` "mis recaudaciones").
 *
 * Decisiones:
 * - **Sin FKs Room**: la `instalacion` puede haber cambiado en cache (sync
 *   posterior) sin que la pendiente sea inválida. La fila lleva un snapshot
 *   completo del momento de la recaudación.
 * - **Importes como `String`**: precisión `BigDecimal`, igual que el resto
 *   del SSOT.
 * - **Desgloses como `String`** (JSON serializado): se convierten a/desde
 *   `List<DenominacionItem>` en el repository. Evita complicar Room con
 *   converters de tipos genéricos.
 * - **Firma como `ByteArray`** (PNG ya rasterizado al guardar). Cuando se
 *   suba se codifica a base64.
 * - **`idempotency_key`** generado client-side garantiza que reintentos no
 *   creen filas duplicadas server-side aunque el Worker reintente.
 */
@Entity(
    tableName = "recaudacion_pendiente",
    indices = [
        Index(name = "idx_recaudacion_pendiente_empresa", value = ["empresa_id"]),
        Index(name = "idx_recaudacion_pendiente_estado", value = ["estado"]),
        Index(name = "idx_recaudacion_pendiente_idempotency", value = ["idempotency_key"], unique = true),
    ],
)
data class RecaudacionPendienteEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    @ColumnInfo(name = "instalacion_id")
    val instalacionId: String,
    @ColumnInfo(name = "tecnico_id")
    val tecnicoId: String,
    val fecha: Instant,

    @ColumnInfo(name = "contador_entradas_actual")
    val contadorEntradasActual: Long,
    @ColumnInfo(name = "contador_salidas_actual")
    val contadorSalidasActual: Long,

    // Snapshot de la baseline al momento de la recaudación. Se envía al
    // server para que detecte conflictos con la baseline real.
    @ColumnInfo(name = "baseline_entradas")
    val baselineEntradas: Long,
    @ColumnInfo(name = "baseline_salidas")
    val baselineSalidas: Long,
    @ColumnInfo(name = "baseline_origen")
    val baselineOrigen: String,
    @ColumnInfo(name = "baseline_referencia_id")
    val baselineReferenciaId: String?,

    // Cifras snapshot — solo informativas en el cliente; el server las
    // recalcula en `crear-recaudacion`. Se cachean para mostrar en la
    // pantalla de "mis recaudaciones" sin tener que pedirlas al server.
    @ColumnInfo(name = "valor_credito")
    val valorCredito: String,
    @ColumnInfo(name = "tasa_semanal")
    val tasaSemanal: String,
    val semanas: Int,
    @ColumnInfo(name = "tasa_total")
    val tasaTotal: String,
    val bruto: String,
    val neto: String,
    @ColumnInfo(name = "porcentaje_local")
    val porcentajeLocal: String,
    @ColumnInfo(name = "parte_local")
    val parteLocal: String,
    @ColumnInfo(name = "parte_empresa")
    val parteEmpresa: String,

    // JSON serializado de `List<DenominacionItem>`. Mapeado en el repository.
    @ColumnInfo(name = "desglose_total_json")
    val desgloseTotalJson: String,
    @ColumnInfo(name = "desglose_local_json")
    val desgloseLocalJson: String,

    /**
     * Orden manual de imputación de la recuperación (T-215): JSON con una lista
     * de `credito_id`. `null` = orden por defecto (tolva → FIFO). El servidor
     * recalcula el plan de recuperación como SSOT respetando este orden; solo
     * afecta a QUÉ deuda se amortiza primero, no al total entregado al local.
     */
    @ColumnInfo(name = "orden_recuperacion_json")
    val ordenRecuperacionJson: String?,

    /** PNG ya rasterizado de la firma. Se codifica a base64 al subir. */
    @ColumnInfo(name = "firma_png", typeAffinity = ColumnInfo.BLOB)
    val firmaPng: ByteArray,

    val observaciones: String?,
    @ColumnInfo(name = "dispositivo_id")
    val dispositivoId: String?,

    /** UUID generado en cliente. La Edge Function lo respeta y deduplica. */
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    val estado: String,
    val intentos: Int,
    @ColumnInfo(name = "ultimo_error")
    val ultimoError: String?,
    @ColumnInfo(name = "ultimo_intento_at")
    val ultimoIntentoAt: Instant?,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "subida_at")
    val subidaAt: Instant?,
    @ColumnInfo(name = "recaudacion_id_remoto")
    val recaudacionIdRemoto: String?,
    @ColumnInfo(name = "conflicto")
    val conflicto: Boolean,
) {
    // Room usa equals/hashCode para el cache. ByteArray no implementa equals
    // por contenido, así que sobrescribimos para evitar falsos positivos
    // cuando comparamos dos pendientes con la misma firma binaria.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecaudacionPendienteEntity) return false
        if (id != other.id) return false
        if (!firmaPng.contentEquals(other.firmaPng)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + firmaPng.contentHashCode()
        return result
    }
}

object EstadoRecaudacionPendiente {
    const val PENDIENTE = "pendiente"
    const val SUBIENDO = "subiendo"
    const val ENVIADA = "enviada"

    /**
     * Fallo TRANSITORIO (red caída). La fila sigue elegible para el drenado y se
     * reintenta automáticamente con backoff.
     */
    const val ERROR = "error"

    /**
     * Terminal: la subida falló por una causa que NO se arregla reintentando el
     * mismo payload congelado (validación de desglose, auth, not_found, conflicto,
     * o agotó los reintentos de red). Sale del drenado para no bloquear a las
     * recaudaciones VÁLIDAS que tiene detrás (cabeza de cola), y se muestra en el
     * panel de subidas con acciones Reintentar/Descartar.
     */
    const val FALLIDA = "fallida"
}
