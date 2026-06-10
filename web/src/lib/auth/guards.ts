import { redirect } from "next/navigation";

import { obtenerMembresiaActiva } from "@/lib/empresas/queries";
import type { MembresiaActiva } from "@/lib/empresas/types";

import type { Rol } from "./roles";

/**
 * Asegura que hay una empresa activa. Lo usan las páginas protegidas
 * más allá de la layout (`app/(dashboard)/layout.tsx` ya protege la
 * mayoría, pero los Server Components pueden necesitar la empresa
 * activa para hacer queries).
 *
 * Si no hay empresa válida → redirect a `/seleccionar-empresa`.
 */
export async function requireMembresiaActiva(): Promise<MembresiaActiva> {
  const { activa } = await obtenerMembresiaActiva();
  if (!activa) {
    redirect("/seleccionar-empresa");
  }
  return activa;
}

/**
 * Asegura que el rol del usuario en la empresa activa está dentro de la
 * lista de permitidos. RLS y las Edge Functions hacen la autorización
 * efectiva; este guard está para mejorar la UX (404/redirect inmediato
 * en lugar de cargar la página y fallar al disparar la mutación).
 *
 * Si el rol no está permitido → redirect a `/sin-permiso`.
 */
export async function requireRol(permitidos: readonly Rol[]): Promise<MembresiaActiva> {
  const activa = await requireMembresiaActiva();
  if (!permitidos.includes(activa.rol)) {
    redirect("/sin-permiso");
  }
  return activa;
}

/**
 * Variante no-redirect: devuelve si el rol cumple. Útil en componentes
 * que sólo quieren mostrar/ocultar acciones (ej. el botón "Eliminar").
 */
export function rolCumple(rol: Rol, permitidos: readonly Rol[]): boolean {
  return permitidos.includes(rol);
}
