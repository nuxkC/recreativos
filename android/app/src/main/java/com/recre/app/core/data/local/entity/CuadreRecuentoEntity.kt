package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.Instant

/**
 * Recuento físico del cuadre semanal de un técnico, persistido en local.
 *
 * Guarda el dinero que el técnico cuenta a mano (su recuento de efectivo) para
 * que sobreviva a cierres de la app y reinicios del dispositivo mientras prepara
 * el cuadre de la semana. Una sola fila por (empresa, técnico, semana): la PK
 * compuesta evita duplicados y `OnConflictStrategy.REPLACE` actualiza el
 * recuento en curso.
 *
 * Decisiones:
 * - `recuentoJson` como `String`: mapa `denominación -> cantidad` serializado
 *   por `CuadreRecuentoStore` con las denominaciones en `toPlainString()`, igual
 *   que los desgloses de `RecaudacionRepository` (precisión `numeric(10,2)`,
 *   nunca `Double`).
 * - `semanaInicio` como `String` ISO (`YYYY-MM-DD`, lunes de la semana ISO):
 *   solo identifica la semana, no se usa para aritmética.
 * - `updatedAt` como `Instant` (lo mapea `InstantConverter` a millis epoch).
 */
@Entity(
    tableName = "cuadre_recuento",
    primaryKeys = ["empresa_id", "tecnico_id", "semana_inicio"],
)
data class CuadreRecuentoEntity(
    @ColumnInfo(name = "empresa_id") val empresaId: String,
    @ColumnInfo(name = "tecnico_id") val tecnicoId: String,
    @ColumnInfo(name = "semana_inicio") val semanaInicio: String,
    @ColumnInfo(name = "recuento_json") val recuentoJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
