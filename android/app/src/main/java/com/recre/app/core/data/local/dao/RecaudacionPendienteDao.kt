package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.RecaudacionPendienteEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface RecaudacionPendienteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: RecaudacionPendienteEntity)

    @Query("SELECT * FROM recaudacion_pendiente WHERE id = :id LIMIT 1")
    suspend fun obtener(id: String): RecaudacionPendienteEntity?

    @Query(
        """
        SELECT * FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId
        ORDER BY created_at DESC
        """,
    )
    fun observarPorEmpresa(empresaId: String): Flow<List<RecaudacionPendienteEntity>>

    @Query(
        """
        SELECT * FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error')
        ORDER BY created_at ASC
        """,
    )
    suspend fun listarPendientes(empresaId: String): List<RecaudacionPendienteEntity>

    @Query(
        """
        SELECT * FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error')
        ORDER BY created_at ASC
        LIMIT 1
        """,
    )
    suspend fun siguientePendiente(empresaId: String): RecaudacionPendienteEntity?

    @Query(
        """
        UPDATE recaudacion_pendiente
        SET estado = 'subiendo',
            ultimo_intento_at = :ahora
        WHERE id = :id
        """,
    )
    suspend fun marcarSubiendo(id: String, ahora: Instant)

    @Query(
        """
        UPDATE recaudacion_pendiente
        SET estado = 'enviada',
            subida_at = :ahora,
            recaudacion_id_remoto = :idRemoto,
            conflicto = :conflicto,
            ultimo_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun marcarEnviada(id: String, idRemoto: String, conflicto: Boolean, ahora: Instant)

    @Query(
        """
        UPDATE recaudacion_pendiente
        SET estado = 'error',
            intentos = intentos + 1,
            ultimo_error = :error,
            ultimo_intento_at = :ahora
        WHERE id = :id
        """,
    )
    suspend fun marcarError(id: String, error: String, ahora: Instant)

    /**
     * Marca una fila como TERMINAL ('fallida'): error no recuperable reintentando.
     * Sale del conjunto de drenado ([siguientePendiente]) para no bloquear la cola.
     */
    @Query(
        """
        UPDATE recaudacion_pendiente
        SET estado = 'fallida',
            intentos = intentos + 1,
            ultimo_error = :error,
            ultimo_intento_at = :ahora
        WHERE id = :id
        """,
    )
    suspend fun marcarFallida(id: String, error: String, ahora: Instant)

    /** Reintento manual desde el panel: vuelve a 'pendiente' y resetea el contador. */
    @Query(
        """
        UPDATE recaudacion_pendiente
        SET estado = 'pendiente',
            intentos = 0,
            ultimo_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun reencolar(id: String)

    /** Descarte manual desde el panel: elimina definitivamente la fila. */
    @Query("DELETE FROM recaudacion_pendiente WHERE id = :id")
    suspend fun descartar(id: String)

    /**
     * Recupera filas colgadas en 'subiendo' de una ejecución anterior abortada
     * (proceso muerto a mitad de subida): las devuelve a 'pendiente' para que el
     * worker las vuelva a drenar. Se llama al arrancar [doWork].
     */
    @Query(
        """
        UPDATE recaudacion_pendiente
        SET estado = 'pendiente'
        WHERE empresa_id = :empresaId AND estado = 'subiendo'
        """,
    )
    suspend fun rearmarColgadas(empresaId: String)

    /**
     * Detecta un doble-guardado: una recaudación aún no enviada con los MISMOS
     * contadores físicos para la misma instalación. Evita encolar duplicados
     * (que crearían dos recaudaciones / un conflicto en el servidor).
     */
    @Query(
        """
        SELECT * FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND instalacion_id = :instalacionId
          AND contador_entradas_actual = :entradas
          AND contador_salidas_actual = :salidas
          AND estado IN ('pendiente', 'subiendo', 'error', 'fallida')
        LIMIT 1
        """,
    )
    suspend fun buscarNoEnviadaConContadores(
        empresaId: String,
        instalacionId: String,
        entradas: Long,
        salidas: Long,
    ): RecaudacionPendienteEntity?

    /**
     * Igual que [buscarNoEnviadaConContadores] pero buscando una gemela YA enviada
     * (excluyendo la propia fila): detecta un doble-guardado cuyo gemelo ya subió,
     * para descartar el duplicado en vez de crear una segunda recaudación.
     */
    @Query(
        """
        SELECT * FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND instalacion_id = :instalacionId
          AND contador_entradas_actual = :entradas
          AND contador_salidas_actual = :salidas
          AND estado = 'enviada' AND id != :exceptoId
        LIMIT 1
        """,
    )
    suspend fun buscarEnviadaConContadores(
        empresaId: String,
        instalacionId: String,
        entradas: Long,
        salidas: Long,
        exceptoId: String,
    ): RecaudacionPendienteEntity?

    @Query(
        """
        SELECT COUNT(*) FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error', 'subiendo', 'fallida')
        """,
    )
    fun observarContadorPendientes(empresaId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('error', 'fallida')
        ORDER BY ultimo_intento_at DESC
        """,
    )
    fun observarBloqueadas(empresaId: String): Flow<List<RecaudacionPendienteEntity>>
}
