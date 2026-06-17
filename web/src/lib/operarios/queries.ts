import { isRol, type Rol } from "@/lib/auth/roles";
import { createClient } from "@/lib/supabase/server";

/** Miembro que puede llevar locales (rol operativo activo). */
export interface OperarioResumen {
  id: string;
  nombre: string;
  rol: Rol;
}

/** Filas crudas de empresa_usuario con el usuario embebido (to-one). */
interface OperarioRow {
  usuario_id: string;
  rol: string;
  activo: boolean;
  usuario: { id: string; nombre_completo: string | null } | null;
}

/**
 * Lista los operarios de una empresa: miembros ACTIVOS con rol operativo
 * (todos menos `contable`, que es solo lectura). Es la lista que puebla el
 * desplegable de "operario asignado" del local y la vista de rutas. Coincide
 * con lo que valida la RPC `actualizar_calendario_local` (operativo activo).
 */
export async function listarOperarios(empresaId: string): Promise<OperarioResumen[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("empresa_usuario")
    .select("usuario_id, rol, activo, usuario:usuario_id (id, nombre_completo)")
    .eq("empresa_id", empresaId)
    .eq("activo", true)
    .neq("rol", "contable")
    .returns<OperarioRow[]>();

  if (error) {
    throw new Error(`No se pudieron cargar los operarios: ${error.message}`);
  }

  return (data ?? [])
    .map<OperarioResumen | null>((row) => {
      if (!row.usuario || !isRol(row.rol) || row.rol === "contable") return null;
      return {
        id: row.usuario_id,
        nombre: row.usuario.nombre_completo ?? "—",
        rol: row.rol,
      };
    })
    .filter((o): o is OperarioResumen => o !== null)
    .sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
}

/** Local mínimo para la vista de rutas. */
export interface LocalDeRuta {
  id: string;
  nombre: string;
}

/**
 * Una "ruta": un operario con los locales que lleva. `operario: null` es el
 * grupo "Sin asignar" (locales sin operario, o asignados a alguien que ya no es
 * operativo/activo: en P1 se tratan como pendientes de asignar).
 */
export interface RutaOperario {
  operario: OperarioResumen | null;
  locales: LocalDeRuta[];
}

/**
 * Agrupa los locales de la empresa por operario asignado. Devuelve un grupo por
 * cada operario activo (aunque no lleve locales todavía) y, al final, el grupo
 * "Sin asignar". Versión P1: sin estado pendiente/atrasado (eso es P3).
 */
export async function listarRutas(empresaId: string): Promise<RutaOperario[]> {
  const operarios = await listarOperarios(empresaId);
  const activos = new Set(operarios.map((o) => o.id));

  const supabase = await createClient();
  const { data, error } = await supabase
    .from("local")
    .select("id, nombre, operario_id")
    .eq("empresa_id", empresaId)
    .order("nombre", { ascending: true })
    .returns<{ id: string; nombre: string; operario_id: string | null }[]>();

  if (error) {
    throw new Error(`No se pudieron cargar las rutas: ${error.message}`);
  }

  const SIN_ASIGNAR = "__sin_asignar__";
  const porOperario = new Map<string, LocalDeRuta[]>();
  for (const local of data ?? []) {
    // Si el operario asignado ya no es operativo/activo, cuenta como sin asignar.
    const key = local.operario_id && activos.has(local.operario_id) ? local.operario_id : SIN_ASIGNAR;
    const lista = porOperario.get(key) ?? [];
    lista.push({ id: local.id, nombre: local.nombre });
    porOperario.set(key, lista);
  }

  const rutas: RutaOperario[] = operarios.map((operario) => ({
    operario,
    locales: porOperario.get(operario.id) ?? [],
  }));
  rutas.push({ operario: null, locales: porOperario.get(SIN_ASIGNAR) ?? [] });
  return rutas;
}
