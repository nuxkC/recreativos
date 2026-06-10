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

    @Query(
        """
        SELECT COUNT(*) FROM recaudacion_pendiente
        WHERE empresa_id = :empresaId AND estado IN ('pendiente', 'error', 'subiendo')
        """,
    )
    fun observarContadorPendientes(empresaId: String): Flow<Int>
}
