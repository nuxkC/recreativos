package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.LicenciaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenciaDao {

    @Query("SELECT * FROM licencia WHERE empresa_id = :empresaId ORDER BY numero")
    fun observarPorEmpresa(empresaId: String): Flow<List<LicenciaEntity>>

    @Query("SELECT * FROM licencia WHERE id = :id LIMIT 1")
    suspend fun obtener(id: String): LicenciaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<LicenciaEntity>)

    @Query("DELETE FROM licencia WHERE empresa_id = :empresaId")
    suspend fun borrarPorEmpresa(empresaId: String)

    @Query("SELECT COUNT(*) FROM licencia WHERE empresa_id = :empresaId")
    suspend fun contar(empresaId: String): Int
}
