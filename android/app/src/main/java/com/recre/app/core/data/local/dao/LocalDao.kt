package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.LocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDao {

    @Query("SELECT * FROM `local` WHERE empresa_id = :empresaId ORDER BY nombre COLLATE NOCASE")
    fun observarPorEmpresa(empresaId: String): Flow<List<LocalEntity>>

    @Query("SELECT * FROM `local` WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<LocalEntity?>

    @Query("SELECT * FROM `local` WHERE id = :id LIMIT 1")
    suspend fun obtener(id: String): LocalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<LocalEntity>)

    @Query("DELETE FROM `local` WHERE empresa_id = :empresaId")
    suspend fun borrarPorEmpresa(empresaId: String)

    @Query("SELECT COUNT(*) FROM `local` WHERE empresa_id = :empresaId")
    suspend fun contar(empresaId: String): Int
}
