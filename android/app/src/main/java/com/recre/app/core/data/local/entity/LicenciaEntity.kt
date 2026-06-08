package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Licencia de explotación (espejo de `public.licencia`).
 *
 * Las fechas `fecha_expedicion` y `fecha_caducidad` viajan como cadena ISO
 * (`YYYY-MM-DD`) sin convertir, igual que devuelve PostgREST para `date`.
 * El parseo a `LocalDate` se hace en el ViewModel cuando hace falta para
 * formatear o comparar con la fecha actual.
 */
@Entity(
    tableName = "licencia",
    indices = [
        Index(name = "idx_licencia_empresa", value = ["empresa_id"]),
        Index(name = "idx_licencia_numero", value = ["empresa_id", "numero"], unique = true),
    ],
)
data class LicenciaEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    val numero: String,
    val tipo: String?,
    @ColumnInfo(name = "fecha_expedicion")
    val fechaExpedicion: String?,
    @ColumnInfo(name = "fecha_caducidad")
    val fechaCaducidad: String?,
    @ColumnInfo(name = "comunidad_autonoma")
    val comunidadAutonoma: String?,
    val estado: String,
    val notas: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
