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
