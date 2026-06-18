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

/** Estado de agenda de un local (derivado en v_agenda_operario, P3a). */
export type EstadoAgenda = "sin_planificar" | "al_dia" | "toca_hoy" | "atrasado";

/** Un local pendiente = toca hoy o atrasado. */
export function esPendiente(estado: EstadoAgenda): boolean {
  return estado === "toca_hoy" || estado === "atrasado";
}

/** Local con su estado de agenda para la vista de rutas. */
export interface LocalDeRuta {
  id: string;
  nombre: string;
  estado: EstadoAgenda;
}

/** Fila cruda de la vista de agenda. */
interface AgendaRow {
  local_id: string;
  nombre: string;
  operario_id: string | null;
  estado: EstadoAgenda;
}

/**
 * Una "ruta": un operario con los locales que lleva y cuántos tiene pendientes
 * (toca hoy / atrasado). `operario: null` es el grupo "Sin asignar" (locales sin
 * operario, o asignados a alguien que ya no es operativo/activo).
 */
export interface RutaOperario {
  operario: OperarioResumen | null;
  locales: LocalDeRuta[];
  pendientes: number;
}

/**
 * Agrupa los locales de la empresa por operario asignado, con su estado de
 * agenda (P3a, vista `v_agenda_operario`). Devuelve un grupo por cada operario
 * activo (aunque no lleve locales) y, al final, "Sin asignar". El gestor (ve-todo)
 * recibe todos los locales de su empresa; la vista respeta la RLS por seguridad.
 */
export async function listarRutas(empresaId: string): Promise<RutaOperario[]> {
  const operarios = await listarOperarios(empresaId);
  const activos = new Set(operarios.map((o) => o.id));

  const supabase = await createClient();
  const { data, error } = await supabase
    .from("v_agenda_operario")
    .select("local_id, nombre, operario_id, estado")
    .eq("empresa_id", empresaId)
    .order("nombre", { ascending: true })
    .returns<AgendaRow[]>();

  if (error) {
    throw new Error(`No se pudieron cargar las rutas: ${error.message}`);
  }

  const SIN_ASIGNAR = "__sin_asignar__";
  const porOperario = new Map<string, LocalDeRuta[]>();
  for (const fila of data ?? []) {
    // Si el operario asignado ya no es operativo/activo, cuenta como sin asignar.
    const key = fila.operario_id && activos.has(fila.operario_id) ? fila.operario_id : SIN_ASIGNAR;
    const lista = porOperario.get(key) ?? [];
    lista.push({ id: fila.local_id, nombre: fila.nombre, estado: fila.estado });
    porOperario.set(key, lista);
  }

  const contarPendientes = (locales: LocalDeRuta[]) =>
    locales.filter((l) => esPendiente(l.estado)).length;

  const rutas: RutaOperario[] = operarios.map((operario) => {
    const locales = porOperario.get(operario.id) ?? [];
    return { operario, locales, pendientes: contarPendientes(locales) };
  });
  const sinAsignar = porOperario.get(SIN_ASIGNAR) ?? [];
  rutas.push({ operario: null, locales: sinAsignar, pendientes: contarPendientes(sinAsignar) });
  return rutas;
}
