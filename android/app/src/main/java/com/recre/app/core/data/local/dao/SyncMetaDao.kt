package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {

    @Query("SELECT * FROM sync_meta WHERE empresa_id = :empresaId LIMIT 1")
    fun observe(empresaId: String): Flow<SyncMetaEntity?>

    @Query("SELECT * FROM sync_meta WHERE empresa_id = :empresaId LIMIT 1")
    suspend fun obtener(empresaId: String): SyncMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: SyncMetaEntity)
}
