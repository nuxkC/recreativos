package com.recre.app.core.auth

/**
 * Roles de un miembro dentro de una empresa.
 *
 * Espejo de la columna `empresa_usuario.rol` (CHECK + texto en SQL para
 * mantener flexibilidad). El orden del enum **coincide con la jerarquía**
 * y se usa para gating de UI:
 *
 * `OWNER > ADMIN > GESTOR > TECNICO > CONTABLE`
 *
 * La autorización real la siguen aplicando RLS y las Edge Functions; este
 * tipo es solo para mostrar/ocultar acciones en la UI.
 */
enum class Rol(val raw: String) {
    OWNER("owner"),
    ADMIN("admin"),
    GESTOR("gestor"),
    TECNICO("tecnico"),
    CONTABLE("contable"),
    ;

    companion object {
        fun fromRaw(value: String?): Rol? =
            value?.let { raw -> entries.firstOrNull { it.raw == raw } }
    }
}

/**
 * Conjuntos de roles reutilizables. Espejan los `ROLES_*` de la web
 * (`web/src/lib/auth/roles.ts`).
 */
val ROLES_OWNER: Set<Rol> = setOf(Rol.OWNER)
val ROLES_ADMIN: Set<Rol> = setOf(Rol.OWNER, Rol.ADMIN)
val ROLES_GESTION: Set<Rol> = setOf(Rol.OWNER, Rol.ADMIN, Rol.GESTOR)
val ROLES_OPERATIVOS: Set<Rol> = setOf(Rol.OWNER, Rol.ADMIN, Rol.GESTOR, Rol.TECNICO)

/** True si [rol] está en [permitidos]. Útil para Composables. */
fun rolCumple(rol: Rol?, permitidos: Set<Rol>): Boolean =
    rol != null && rol in permitidos
