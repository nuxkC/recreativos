package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.CreditoLocalEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO de las deudas abiertas cacheadas (T-215). Ver [CreditoLocalEntity].
 *
 * Orden de listado: tolva antes que préstamo, y dentro de cada tipo FIFO
 * (fecha asc, desempate por id). Coincide con el orden por defecto de
 * imputación del SSOT (`_shared/recuperacion.ts` / `Recuperacion.kt`), de modo
 * que el preview offline puede consumir las filas tal cual sin reordenar.
 */
@Dao
interface CreditoLocalDao {

    @Query(
        """
        SELECT * FROM `credito_local`
        WHERE local_id = :localId
        ORDER BY CASE tipo WHEN 'tolva' THEN 0 ELSE 1 END, fecha ASC, credito_id ASC
        """,
    )
    fun observarPorLocal(localId: String): Flow<List<CreditoLocalEntity>>

    /** Todas las deudas abiertas cacheadas de la empresa (para el índice de Deudas). */
    @Query("SELECT * FROM `credito_local` WHERE empresa_id = :empresaId")
    fun observarPorEmpresa(empresaId: String): Flow<List<CreditoLocalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<CreditoLocalEntity>)

    @Query("DELETE FROM `credito_local` WHERE empresa_id = :empresaId")
    suspend fun borrarPorEmpresa(empresaId: String)
}
