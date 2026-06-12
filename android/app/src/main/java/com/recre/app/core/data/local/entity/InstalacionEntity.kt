package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Instalación activa con baseline pre-calculada en local.
 *
 * Se hidrata desde la vista `public.v_instalacion_actual`, que ya hace el
 * `LATERAL JOIN obtener_baseline(...)`. En T-51 solo cacheamos las
 * instalaciones con `estado='activa'` (las cerradas no las necesita ningún
 * flujo de la app del técnico).
 *
 * Decisiones:
 * - `tasa_semanal` y `porcentaje_local` se guardan como `String` para
 *   preservar la precisión `numeric(8,2)` y `numeric(5,2)`. Antes de
 *   cualquier cálculo monetario se envuelven en `BigDecimal`.
 * - Los FKs (`maquina_id`, `licencia_id`, `local_id`) NO se declaran como
 *   `@ForeignKey` de Room para que el sync pueda escribir cada tabla en
 *   el orden que prefiera dentro de la transacción sin tener que ordenar
 *   topológicamente.
 * - La baseline va denormalizada como columnas planas. Cuando llegue una
 *   recaudación nueva, el sync rehidrata la fila completa con la baseline
 *   nueva calculada por el servidor.
 */
@Entity(
    tableName = "instalacion",
    indices = [
        Index(name = "idx_instalacion_empresa", value = ["empresa_id"]),
        Index(name = "idx_instalacion_local", value = ["local_id"]),
        Index(name = "idx_instalacion_maquina", value = ["maquina_id"]),
    ],
)
data class InstalacionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    @ColumnInfo(name = "maquina_id")
    val maquinaId: String,
    @ColumnInfo(name = "licencia_id")
    val licenciaId: String,
    @ColumnInfo(name = "local_id")
    val localId: String,
    @ColumnInfo(name = "fecha_inicio")
    val fechaInicio: String,
    @ColumnInfo(name = "tasa_semanal")
    val tasaSemanal: String,
    @ColumnInfo(name = "porcentaje_local")
    val porcentajeLocal: String,
    @ColumnInfo(name = "contador_entradas_base")
    val contadorEntradasBase: Long,
    @ColumnInfo(name = "contador_salidas_base")
    val contadorSalidasBase: Long,
    val estado: String,
    @ColumnInfo(name = "baseline_entradas")
    val baselineEntradas: Long,
    @ColumnInfo(name = "baseline_salidas")
    val baselineSalidas: Long,
    @ColumnInfo(name = "baseline_fecha")
    val baselineFecha: Instant,
    @ColumnInfo(name = "baseline_origen")
    val baselineOrigen: String,
    @ColumnInfo(name = "baseline_referencia_id")
    val baselineReferenciaId: String?,
    // Merma de tolva pendiente de reponer (de v_instalacion_tolva). La previa de
    // recaudación la descuenta antes del reparto (§5.6) para que las cifras que ve
    // el técnico —y el desglose que separa— cuadren con lo que persiste el servidor.
    @ColumnInfo(name = "pendiente_tolva")
    val pendienteTolva: String = "0",
)
