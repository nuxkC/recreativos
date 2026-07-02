package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Local del inventario (espejo de `public.local`).
 *
 * Se sincroniza completo en T-51 para listar locales y mostrar el detalle
 * sin red. Mantiene `empresa_id` aunque RLS ya filtre en backend, por dos
 * razones: (1) permite cachear datos de varias empresas si el técnico
 * cambia frecuentemente, (2) las queries locales filtran explícitamente
 * por `empresa_id` para evitar mezclas accidentales.
 */
@Entity(
    tableName = "local",
    indices = [Index(name = "idx_local_empresa", value = ["empresa_id"])],
)
data class LocalEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    val nombre: String,
    val direccion: String?,
    @ColumnInfo(name = "cif_o_nif")
    val cifONif: String?,
    @ColumnInfo(name = "titular_nombre")
    val titularNombre: String?,
    val telefono: String?,
    val email: String?,
    val notas: String?,
    // Dirección estructurada (T-277); espejo de las columnas text nullable de
    // `public.local`. Provincia/municipio son códigos INE; CCAA la lista de oro.
    @ColumnInfo(name = "comunidad_autonoma")
    val comunidadAutonoma: String?,
    @ColumnInfo(name = "provincia_codigo")
    val provinciaCodigo: String?,
    @ColumnInfo(name = "municipio_codigo")
    val municipioCodigo: String?,
    val calle: String?,
    @ColumnInfo(name = "codigo_postal")
    val codigoPostal: String?,
    /**
     * Override del % de recuperación de deuda del local (T-215). `null` =
     * hereda el de la empresa ([EmpresaParamsEntity.porcentajeRecuperacion]).
     * Espejo de `local.porcentaje_recuperacion` (smallint nullable).
     */
    @ColumnInfo(name = "porcentaje_recuperacion")
    val porcentajeRecuperacion: Int?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
