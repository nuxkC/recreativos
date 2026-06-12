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

    @Query(
        """
        SELECT COUNT(*) FROM averia_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error', 'subiendo')
        """,
    )
    fun observarContadorPendientes(empresaId: String): Flow<Int>
}
