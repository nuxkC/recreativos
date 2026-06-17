package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.AveriaPendienteEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Cola offline de averías reportadas por el técnico (T-222). Gemelo de
 * [RecaudacionPendienteDao], adaptado a la subida reanudable en dos fases
 * (avería + recambios) que evita duplicados al reintentar.
 */
@Dao
interface AveriaPendienteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: AveriaPendienteEntity)

    @Query("SELECT * FROM averia_pendiente WHERE id = :id LIMIT 1")
    suspend fun obtener(id: String): AveriaPendienteEntity?

    /** Reportes pendientes/erróneos de una máquina, para mostrarlos como «por subir». */
    @Query(
        """
        SELECT * FROM averia_pendiente
        WHERE maquina_id = :maquinaId AND estado <> 'enviada'
        ORDER BY created_at DESC
        """,
    )
    fun observarPorMaquina(maquinaId: String): Flow<List<AveriaPendienteEntity>>

    @Query(
        """
        SELECT * FROM averia_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error')
        ORDER BY created_at ASC
        LIMIT 1
        """,
    )
    suspend fun siguientePendiente(empresaId: String): AveriaPendienteEntity?

    @Query(
        """
        UPDATE averia_pendiente
        SET estado = 'subiendo', ultimo_intento_at = :ahora
        WHERE id = :id
        """,
    )
    suspend fun marcarSubiendo(id: String, ahora: Instant)

    /** Ancla la avería ya creada server-side: el reintento no la vuelve a crear. */
    @Query("UPDATE averia_pendiente SET averia_id_remoto = :idRemoto WHERE id = :id")
    suspend fun marcarAveriaCreada(id: String, idRemoto: String)

    /** Avanza el cursor de recambios subidos (subida reanudable, sin duplicar). */
    @Query("UPDATE averia_pendiente SET recambios_subidos = :n WHERE id = :id")
    suspend fun marcarRecambiosSubidos(id: String, n: Int)

    @Query(
        """
        UPDATE averia_pendiente
        SET estado = 'enviada', subida_at = :ahora, ultimo_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun marcarEnviada(id: String, ahora: Instant)

    @Query(
        """
        UPDATE averia_pendiente
        SET estado = 'error', intentos = intentos + 1,
            ultimo_error = :error, ultimo_intento_at = :ahora
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
        UPDATE averia_pendiente
        SET estado = 'fallida', intentos = intentos + 1,
            ultimo_error = :error, ultimo_intento_at = :ahora
        WHERE id = :id
        """,
    )
    suspend fun marcarFallida(id: String, error: String, ahora: Instant)

    /** Reintento manual desde el panel: vuelve a 'pendiente' y resetea el contador. */
    @Query(
        """
        UPDATE averia_pendiente
        SET estado = 'pendiente', intentos = 0, ultimo_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun reencolar(id: String)

    /** Descarte manual desde el panel: elimina definitivamente la fila. */
    @Query("DELETE FROM averia_pendiente WHERE id = :id")
    suspend fun descartar(id: String)

    /**
     * Recupera filas colgadas en 'subiendo' de una ejecución anterior abortada
     * (proceso muerto a mitad de subida): las devuelve a 'pendiente' para que el
     * worker las vuelva a drenar. Se llama al arrancar [doWork].
     */
    @Query(
        """
        UPDATE averia_pendiente
        SET estado = 'pendiente'
        WHERE empresa_id = :empresaId AND estado = 'subiendo'
        """,
    )
    suspend fun rearmarColgadas(empresaId: String)

    @Query(
        """
        SELECT COUNT(*) FROM averia_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error', 'subiendo', 'fallida')
        """,
    )
    fun observarContadorPendientes(empresaId: String): Flow<Int>

    /**
     * Averías BLOQUEADAS (estado IN 'error','fallida') con su `ultimoError`.
     * Alimenta el Centro de Incidencias (Reintentar/Descartar), gemelo de
     * [RecaudacionPendienteDao.observarBloqueadas].
     */
    @Query(
        """
        SELECT * FROM averia_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('error', 'fallida')
        ORDER BY ultimo_intento_at DESC
        """,
    )
    fun observarBloqueadas(empresaId: String): Flow<List<AveriaPendienteEntity>>
}
