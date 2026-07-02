import { createClient } from "@/lib/supabase/server";

import type { FabricanteOpcion, ModeloOpcion } from "./opciones";

/**
 * Catálogo GLOBAL de fabricantes (sin filtro de empresa). Lectura permitida a
 * `authenticated` por el grant de PR-B1; la página ya exige rol de gestión
 * antes de llamar aquí. El cliente Supabase no está tipado → `.returns<...>()`.
 */
export async function listarFabricantes(): Promise<FabricanteOpcion[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("fabricante")
    .select("id, nombre")
    .order("nombre", { ascending: true })
    .returns<Array<{ id: string; nombre: string }>>();

  if (error) {
    throw new Error(`No se pudieron cargar los fabricantes: ${error.message}`);
  }

  return (data ?? []).map((row) => ({ id: row.id, nombre: row.nombre }));
}

/** Catálogo GLOBAL de modelos; incluye `fabricante_id` para la cascada. */
export async function listarModelos(): Promise<ModeloOpcion[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("modelo")
    .select("id, nombre, fabricante_id")
    .order("nombre", { ascending: true })
    .returns<Array<{ id: string; nombre: string; fabricante_id: string }>>();

  if (error) {
    throw new Error(`No se pudieron cargar los modelos: ${error.message}`);
  }

  return (data ?? []).map((row) => ({
    id: row.id,
    nombre: row.nombre,
    fabricanteId: row.fabricante_id,
  }));
}

/**
 * ¿El usuario actual es administrador GLOBAL del catálogo? Self-read de la
 * propia fila de `usuario` (permitido por la RLS `usuario_select_self`). Se
 * usa solo para mostrar/ocultar UI (p. ej. el enlace en ajustes); la
 * autorización real la aplican las RPCs de curación.
 */
export async function esAdminCatalogo(): Promise<boolean> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return false;

  const { data } = await supabase
    .from("usuario")
    .select("es_admin_catalogo")
    .eq("id", user.id)
    .limit(1)
    .returns<Array<{ es_admin_catalogo: boolean | null }>>();

  return Boolean(data?.[0]?.es_admin_catalogo);
}
