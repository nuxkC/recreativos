package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Parámetros de la empresa activa cacheados en local.
 *
 * Se sincroniza durante T-51 y se usa por:
 * - Cabecera/pie del ticket impreso (T-62).
 * - Zona horaria para mostrar fechas y para el cálculo de semanas ISO
 *   cuando la app calcula offline.
 * - Nombre y datos en la cabecera del ticket.
 *
 * Fila única por empresa: la PK es el `empresa_id` para que cuando el
 * técnico cambie de empresa se reemplace por completo.
 */
@Entity(tableName = "empresa_params")
data class EmpresaParamsEntity(
    @PrimaryKey
    @ColumnInfo(name = "empresa_id")
    val empresaId: String,
    val nombre: String,
    val cif: String?,
    val direccion: String?,
    val telefono: String?,
    val email: String?,
    @ColumnInfo(name = "logo_url")
    val logoUrl: String?,
    @ColumnInfo(name = "zona_horaria")
    val zonaHoraria: String,
    @ColumnInfo(name = "ticket_cabecera")
    val ticketCabecera: String?,
    @ColumnInfo(name = "ticket_pie")
    val ticketPie: String?,
    /** Unidad de redondeo del bruto (0 = sin redondeo). Espejo de empresa.redondeo_recaudacion. */
    @ColumnInfo(name = "redondeo_recaudacion")
    val redondeoRecaudacion: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
