package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Avería reportada por el técnico, pendiente de subir al backend (T-222).
 *
 * Mismo patrón de cola offline que [RecaudacionPendienteEntity] (T-57): el
 * técnico siempre puede registrar una avería — incluso sin red. La fila vive
 * aquí hasta que el [com.recre.app.core.sync.AveriaUploadWorker] la suba vía
 * las RPCs `SECURITY DEFINER` `crear_averia` (+ `crear_recambio` por pieza).
 * Tras éxito se marca como `enviada` (no se borra: es el registro local de lo
 * que el técnico reportó, igual que «mis recaudaciones»).
 *
 * Decisiones:
 * - **Sin FKs Room**: la fila lleva un snapshot del momento del reporte; que la
 *   cache de máquinas cambie luego no la invalida.
 * - **Recambios como JSON** (`recambios_json`): una avería lleva 0..N recambios
 *   (pieza/cantidad/coste). Se serializan en el repositorio para no complicar
 *   Room con relaciones; viajan al subir como llamadas a `crear_recambio`.
 * - **`coste` de recambio como String** dentro del JSON: es dinero
 *   (`numeric(10,2)`), nunca `Double` (regla de oro del repo).
 * - **Subida reanudable e idempotente sin clave server-side**: `crear_averia`
 *   no es idempotente, así que en cuanto crea la avería guardamos
 *   `averia_id_remoto`; un reintento posterior NO vuelve a crearla, solo sube
 *   los recambios que falten (a partir de `recambios_subidos`). Así un corte de
 *   red a mitad de subida nunca duplica la avería ni los recambios.
 */
@Entity(
    tableName = "averia_pendiente",
    indices = [
        Index(name = "idx_averia_pendiente_empresa", value = ["empresa_id"]),
        Index(name = "idx_averia_pendiente_estado", value = ["estado"]),
        Index(name = "idx_averia_pendiente_maquina", value = ["maquina_id"]),
    ],
)
data class AveriaPendienteEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    @ColumnInfo(name = "maquina_id")
    val maquinaId: String,
    /** Snapshot del nº de serie para listar lo reportado sin re-consultar. */
    @ColumnInfo(name = "maquina_numero_serie")
    val maquinaNumeroSerie: String,

    val categoria: String,
    val descripcion: String?,
    @ColumnInfo(name = "pone_maquina_fuera_servicio")
    val poneMaquinaFueraServicio: Boolean,
    val notas: String?,

    /**
     * La avería pagó un premio de la tolva (§5.6): la máquina, al averiarse,
     * soltó dinero de la tolva que luego se repone en la próxima recaudación.
     * `crear_averia` inserta la `merma` cuando esto es `true` (exige instalación
     * activa + [importeTolva] > 0). Default `false`: la mayoría no afecta tolva.
     */
    @ColumnInfo(name = "afecta_tolva")
    val afectaTolva: Boolean = false,
    /** Importe pagado de la tolva (dinero → String, nunca Double). `null` si no aplica. */
    @ColumnInfo(name = "importe_tolva")
    val importeTolva: String? = null,

    /** JSON serializado de `List<RecambioPendiente>` (mapeado en el repository). */
    @ColumnInfo(name = "recambios_json")
    val recambiosJson: String,

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

    /** Id remoto de la avería una vez creada; ancla la reanudación sin duplicar. */
    @ColumnInfo(name = "averia_id_remoto")
    val averiaIdRemoto: String?,
    /** Nº de recambios ya creados en el servidor (para reanudar la subida). */
    @ColumnInfo(name = "recambios_subidos")
    val recambiosSubidos: Int,
)

object EstadoAveriaPendiente {
    const val PENDIENTE = "pendiente"
    const val SUBIENDO = "subiendo"
    const val ENVIADA = "enviada"
    const val ERROR = "error"
}
