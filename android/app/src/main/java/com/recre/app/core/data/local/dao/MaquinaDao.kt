package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.MaquinaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaquinaDao {

    @Query("SELECT * FROM maquina WHERE empresa_id = :empresaId ORDER BY numero_serie")
    fun observarPorEmpresa(empresaId: String): Flow<List<MaquinaEntity>>

    @Query("SELECT * FROM maquina WHERE id = :id LIMIT 1")
    suspend fun obtener(id: String): MaquinaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<MaquinaEntity>)

    @Query("DELETE FROM maquina WHERE empresa_id = :empresaId")
    suspend fun borrarPorEmpresa(empresaId: String)

    @Query("SELECT COUNT(*) FROM maquina WHERE empresa_id = :empresaId")
    suspend fun contar(empresaId: String): Int
}
