package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmpresaParamsDao {

    @Query("SELECT * FROM empresa_params WHERE empresa_id = :empresaId LIMIT 1")
    fun observe(empresaId: String): Flow<EmpresaParamsEntity?>

    @Query("SELECT * FROM empresa_params WHERE empresa_id = :empresaId LIMIT 1")
    suspend fun obtener(empresaId: String): EmpresaParamsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: EmpresaParamsEntity)

    @Query("DELETE FROM empresa_params WHERE empresa_id = :empresaId")
    suspend fun borrarPorEmpresa(empresaId: String)
}
