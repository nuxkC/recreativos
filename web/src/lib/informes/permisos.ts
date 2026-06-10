import type { Rol } from "@/lib/auth/roles";

/**
 * Roles que pueden ver los informes: propietario, administración, gestión y
 * contabilidad. Los técnicos no acceden a la analítica agregada. La
 * autorización efectiva la aplica RLS sobre las vistas; esto es solo UX.
 */
export const ROLES_INFORMES = [
  "owner",
  "admin",
  "gestor",
  "contable",
] as const satisfies readonly Rol[];
