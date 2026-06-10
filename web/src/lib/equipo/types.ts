import type { Rol } from "@/lib/auth/roles";

/**
 * Una membresía vista desde la pantalla de Equipo: incluye los datos
 * básicos del usuario asociado.
 */
export interface MiembroEquipo {
  empresaId: string;
  usuarioId: string;
  rol: Rol;
  activo: boolean;
  /** ¿Coincide con el usuario logueado? Útil para deshabilitar acciones
   *  de auto-modificación. */
  esYo: boolean;
  usuario: {
    id: string;
    nombreCompleto: string | null;
    telefono: string | null;
    email: string | null;
  };
}

export interface MiembroRow {
  empresa_id: string;
  usuario_id: string;
  rol: string;
  activo: boolean;
  usuario: {
    id: string;
    nombre_completo: string | null;
    telefono: string | null;
  } | null;
}
