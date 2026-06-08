import type { Rol } from "@/lib/auth/roles";

/**
 * Datos mínimos de una empresa que necesita la UI del back-office.
 *
 * En cuanto tengamos `supabase gen types typescript` integrado al pipeline
 * estos tipos vendrán del esquema generado y este archivo se reducirá a
 * los `Resumen` que combinen filas de varias tablas.
 */
export interface EmpresaResumen {
  id: string;
  nombre: string;
  zonaHoraria: string;
}

export interface Membresia {
  empresa: EmpresaResumen;
  rol: Rol;
  activo: boolean;
}

/** Membresía activa actualmente cargada para el usuario. */
export interface MembresiaActiva {
  empresa: EmpresaResumen;
  rol: Rol;
}
