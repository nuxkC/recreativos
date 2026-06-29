package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.CuadreRecuentoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CuadreRecuentoDao {

    /**
     * Observa el recuento físico de un técnico para una semana concreta.
     * Emite `null` mientras no exista la fila (semana sin recuento todavía).
     */
    @Query(
        """
        SELECT * FROM cuadre_recuento
        WHERE empresa_id = :empresaId AND tecnico_id = :tecnicoId
            AND semana_inicio = :semanaInicio
        """,
    )
    fun observar(
        empresaId: String,
        tecnicoId: String,
        semanaInicio: String,
    ): Flow<CuadreRecuentoEntity?>

    /** Inserta o reemplaza el recuento de la semana (PK compuesta). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: CuadreRecuentoEntity)
}
