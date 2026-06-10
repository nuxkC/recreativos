package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Metadata de la última sincronización por empresa.
 *
 * Sirve a tres consumidores:
 * - HomeScreen muestra "Última sincronización: hace X" como feedback.
 * - T-59 bloquea recaudaciones cuando han pasado más de 48 h.
 * - T-65 lo expone en Ajustes con un botón "forzar sincronización".
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Instant,
    @ColumnInfo(name = "last_result")
    val lastResult: String,
)
