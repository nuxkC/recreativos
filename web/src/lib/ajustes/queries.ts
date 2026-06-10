import { createClient } from "@/lib/supabase/server";

import { type EmpresaAjustes, type EmpresaAjustesRow, mapEmpresaAjustesRow } from "./types";

export async function obtenerAjustesEmpresa(empresaId: string): Promise<EmpresaAjustes | null> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("empresa")
    .select("*")
    .eq("id", empresaId)
    .returns<EmpresaAjustesRow[]>()
    .maybeSingle();
  if (error) {
    throw new Error(`No se pudieron cargar los ajustes: ${error.message}`);
  }
  return data ? mapEmpresaAjustesRow(data) : null;
}
