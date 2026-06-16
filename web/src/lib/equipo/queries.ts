import { isRol } from "@/lib/auth/roles";
import { createClient } from "@/lib/supabase/server";

import type { MiembroEquipo, MiembroRow } from "./types";

/**
 * Lista los miembros (empresa_usuario) de una empresa con los datos
 * básicos del usuario joinados. RLS solo permite SELECT a miembros, así
 * que la query siempre devuelve a la persona que la ejecuta y al resto
 * de compañeros.
 *
 * `auth.users.email` no es accesible vía PostgREST — lo resolvemos
 * llamando a `supabase.auth.admin.listUsers` desde una función futura
 * (T-71). Mientras, mostramos el email solo del usuario actual.
 */
export async function listarMiembros(
  empresaId: string,
  yoUsuarioId: string,
): Promise<MiembroEquipo[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("empresa_usuario")
    .select(
      `empresa_id,
       usuario_id,
       rol,
       activo,
       usuario:usuario_id (id, nombre_completo, telefono)`,
    )
    .eq("empresa_id", empresaId)
    .order("activo", { ascending: false })
    .returns<MiembroRow[]>();

  if (error) {
    throw new Error(`No se pudieron cargar los miembros: ${error.message}`);
  }

  // El email del usuario actual lo conocemos desde supabase.auth.getUser.
  const { data: { user } = { user: null } } = await supabase.auth.getUser();
  const yoEmail = user?.email ?? null;

  const filas = data ?? [];
  return filas
    .map<MiembroEquipo | null>((row) => {
      if (!row.usuario || !isRol(row.rol)) return null;
      const esYo = row.usuario_id === yoUsuarioId;
      return {
        empresaId: row.empresa_id,
        usuarioId: row.usuario_id,
        rol: row.rol,
        activo: row.activo,
        esYo,
        usuario: {
          id: row.usuario.id,
          nombreCompleto: row.usuario.nombre_completo,
          telefono: row.usuario.telefono,
          email: esYo ? yoEmail : null,
        },
      };
    })
    .filter((m): m is MiembroEquipo => m !== null)
    .sort((a, b) => {
      // Activos primero, luego por nombre.
      if (a.activo !== b.activo) return a.activo ? -1 : 1;
      const an = a.usuario.nombreCompleto ?? "";
      const bn = b.usuario.nombreCompleto ?? "";
      return an.localeCompare(bn, "es");
    });
}
