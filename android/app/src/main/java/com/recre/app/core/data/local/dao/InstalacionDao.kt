package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.InstalacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalacionDao {

    @Query(
        """
        SELECT * FROM instalacion
        WHERE empresa_id = :empresaId AND estado = 'activa'
        ORDER BY local_id
        """,
    )
    fun observarActivasPorEmpresa(empresaId: String): Flow<List<InstalacionEntity>>

    @Query(
        """
        SELECT * FROM instalacion
        WHERE empresa_id = :empresaId AND local_id = :localId AND estado = 'activa'
        ORDER BY maquina_id
        """,
    )
    fun observarActivasPorLocal(empresaId: String, localId: String): Flow<List<InstalacionEntity>>

    @Query("SELECT * FROM instalacion WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<InstalacionEntity?>

    @Query("SELECT * FROM instalacion WHERE id = :id LIMIT 1")
    suspend fun obtener(id: String): InstalacionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<InstalacionEntity>)

    @Query("DELETE FROM instalacion WHERE empresa_id = :empresaId")
    suspend fun borrarPorEmpresa(empresaId: String)

    @Query("SELECT COUNT(*) FROM instalacion WHERE empresa_id = :empresaId AND estado = 'activa'")
    suspend fun contarActivas(empresaId: String): Int
}
