package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Deuda ABIERTA de un local cacheada en local (T-215).
 *
 * Se hidrata desde la vista `public.v_credito_local_saldo` filtrando solo las
 * deudas con `estado = 'abierto'`: son las únicas que necesita la app del
 * técnico para (1) el preview offline de recuperación durante una recaudación
 * (espejo de `_shared/recuperacion.ts`) y (2) la ficha de deudas del local.
 *
 * Las deudas saldadas/condonadas y el libro mayor de abonos NO se cachean: el
 * histórico se consulta en línea desde la ficha (operación de gestión, igual
 * que el resto del CRUD gestor).
 *
 * Decisiones:
 * - Importes (`principal`, `recuperado`, `saldo`) como `String` para preservar
 *   la precisión `numeric(10,2)`; se envuelven en `BigDecimal` antes de
 *   cualquier cálculo (igual que el resto del SSOT en local).
 * - `fecha` como `String` ISO (`YYYY-MM-DD`): solo se usa para el orden FIFO de
 *   imputación, nunca para aritmética.
 * - Sin `@ForeignKey` a `local`/`instalacion`: el sync reemplaza la tabla
 *   completa por empresa en una transacción y no necesita orden topológico.
 */
@Entity(
    tableName = "credito_local",
    indices = [
        Index(name = "idx_credito_local_empresa", value = ["empresa_id"]),
        Index(name = "idx_credito_local_local", value = ["local_id"]),
    ],
)
data class CreditoLocalEntity(
    @PrimaryKey
    @ColumnInfo(name = "credito_id")
    val creditoId: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    @ColumnInfo(name = "local_id")
    val localId: String,
    /** `tolva` | `prestamo`. La tolva se imputa antes que los préstamos. */
    val tipo: String,
    @ColumnInfo(name = "instalacion_id")
    val instalacionId: String?,
    val principal: String,
    @ColumnInfo(name = "tipo_interes")
    val tipoInteres: String,
    /** Fecha ISO `YYYY-MM-DD` de la deuda (orden FIFO de imputación). */
    val fecha: String,
    val estado: String,
    val notas: String?,
    /** Σ de abonos imputados a la deuda. */
    val recuperado: String,
    /** Saldo vivo = principal − recuperado. */
    val saldo: String,
)
