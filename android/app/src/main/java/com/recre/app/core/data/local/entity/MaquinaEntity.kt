package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Máquina del inventario (espejo de `public.maquina`).
 *
 * Decisión clave: `valor_credito` se almacena como `String` (no `Double`)
 * para mantener la precisión de `numeric(4,2)` y permitir construir un
 * `BigDecimal` antes de cualquier cálculo monetario. Misma regla que sigue
 * la web (`web/src/lib/maquinas/types.ts`).
 *
 * Los contadores `bigint` se serializan a `Long`: en este dominio nunca
 * se acercan a `Long.MAX_VALUE` (los reales están en el orden de millones).
 */
@Entity(
    tableName = "maquina",
    indices = [
        Index(name = "idx_maquina_empresa", value = ["empresa_id"]),
        Index(name = "idx_maquina_numero_serie", value = ["empresa_id", "numero_serie"], unique = true),
    ],
)
data class MaquinaEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    @ColumnInfo(name = "numero_serie")
    val numeroSerie: String,
    val modelo: String?,
    val fabricante: String?,
    @ColumnInfo(name = "valor_credito")
    val valorCredito: String,
    @ColumnInfo(name = "contador_entradas_inicial")
    val contadorEntradasInicial: Long,
    @ColumnInfo(name = "contador_salidas_inicial")
    val contadorSalidasInicial: Long,
    val estado: String,
    val notas: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
