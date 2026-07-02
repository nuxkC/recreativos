import { createClient } from "@/lib/supabase/server";

import type { MunicipioOpcion, ProvinciaOpcion } from "./geo-opciones";

/**
 * Lecturas de las tablas de referencia geográfica GLOBAL (INE). Son de solo
 * lectura y comunes a todas las empresas; RLS permite SELECT a authenticated.
 *
 * Se precargan enteras server-side (52 provincias, ~8100 municipios) y la
 * cascada filtra en cliente con `opcionesProvincia`/`opcionesMunicipio`, igual
 * que el catálogo de máquinas. El volumen de municipios es asumible para un
 * formulario de back-office; si creciera el coste, migrar a carga on-demand
 * por provincia (Server Action) sin cambiar los helpers puros.
 */
export async function listarProvincias(): Promise<ProvinciaOpcion[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("provincia")
    .select("codigo, nombre, comunidad_autonoma")
    .order("nombre", { ascending: true })
    .returns<Array<{ codigo: string; nombre: string; comunidad_autonoma: string }>>();

  if (error) {
    throw new Error(`No se pudieron cargar las provincias: ${error.message}`);
  }

  return (data ?? []).map((row) => ({
    codigo: row.codigo,
    nombre: row.nombre,
    comunidadAutonoma: row.comunidad_autonoma,
  }));
}

export async function listarMunicipios(): Promise<MunicipioOpcion[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("municipio")
    .select("codigo, nombre, provincia_codigo")
    .order("nombre", { ascending: true })
    .returns<Array<{ codigo: string; nombre: string; provincia_codigo: string }>>();

  if (error) {
    throw new Error(`No se pudieron cargar los municipios: ${error.message}`);
  }

  return (data ?? []).map((row) => ({
    codigo: row.codigo,
    nombre: row.nombre,
    provinciaCodigo: row.provincia_codigo,
  }));
}
