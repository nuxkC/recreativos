package com.recre.app.core.session

import com.recre.app.core.auth.Rol

/**
 * Resumen mínimo de empresa para selección y header.
 * Para el detalle completo (CIF, dirección, etc.) ya hay otra capa en T-65.
 */
data class EmpresaResumen(
    val id: String,
    val nombre: String,
    val zonaHoraria: String,
)

/**
 * Pertenencia activa de un usuario a una empresa.
 *
 * Solo se construye desde filas con `activo = true`; las desactivadas
 * se filtran a nivel de query y nunca llegan al dominio para evitar
 * bugs por olvidarse del flag.
 */
data class Membresia(
    val empresa: EmpresaResumen,
    val rol: Rol,
)
