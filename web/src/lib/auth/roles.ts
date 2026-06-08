/**
 * Roles de `empresa_usuario` y agrupaciones para autorización en la UI.
 *
 * El listado coincide con el `CHECK` SQL en
 * `supabase/migrations/20260519220000_create_core_tenant_tables.sql`.
 *
 * La lógica fina (qué endpoint puede invocar cada rol) la aplica
 * el backend (RLS + Edge Functions). En la web usamos esto solo para
 * mostrar/ocultar elementos: nunca para autorizar realmente.
 */

export const ROLES = ["owner", "admin", "gestor", "tecnico", "contable"] as const;
export type Rol = (typeof ROLES)[number];

/** Roles que pueden gestionar inventario (CRUD de licencias/máquinas/locales/instalaciones). */
export const ROLES_GESTION = ["owner", "admin", "gestor"] as const satisfies readonly Rol[];

/** Roles administrativos: gestionan equipo, ajustes y resuelven conflictos. */
export const ROLES_ADMIN = ["owner", "admin"] as const satisfies readonly Rol[];

/** Solo el dueño puede borrar la empresa o cambiar al owner. */
export const ROLES_OWNER = ["owner"] as const satisfies readonly Rol[];

export function isRol(value: string): value is Rol {
  return (ROLES as readonly string[]).includes(value);
}

export function tieneRol(rol: Rol, permitidos: readonly Rol[]): boolean {
  return permitidos.includes(rol);
}
