import { cookies } from "next/headers";

import { isRol } from "@/lib/auth/roles";
import { createClient } from "@/lib/supabase/server";

import { EMPRESA_COOKIE_NAME } from "./cookie";
import type { Membresia, MembresiaActiva } from "./types";

/**
 * Forma que devuelve Supabase al hacer
 *   .from("empresa_usuario").select("rol, activo, empresa(id, nombre, zona_horaria)")
 *
 * Lo declaramos a mano hasta que el pipeline de tipos generados (`supabase
 * gen types typescript`) esté en su sitio.
 */
interface EmpresaUsuarioRow {
  rol: string;
  activo: boolean;
  empresa: {
    id: string;
    nombre: string;
    zona_horaria: string;
  } | null;
}

function mapMembresia(row: EmpresaUsuarioRow): Membresia | null {
  if (!row.empresa || !isRol(row.rol)) return null;
  return {
    rol: row.rol,
    activo: row.activo,
    empresa: {
      id: row.empresa.id,
      nombre: row.empresa.nombre,
      zonaHoraria: row.empresa.zona_horaria,
    },
  };
}

/**
 * Devuelve las membresías activas del usuario actual ordenadas por
 * nombre de empresa. Si el usuario no está autenticado lanza error
 * (las rutas protegidas ya lo evitan vía middleware).
 */
export async function listarMembresiasUsuarioActual(): Promise<Membresia[]> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) {
    throw new Error("listarMembresiasUsuarioActual: usuario no autenticado");
  }

  const { data, error } = await supabase
    .from("empresa_usuario")
    .select("rol, activo, empresa:empresa_id (id, nombre, zona_horaria)")
    .eq("usuario_id", user.id)
    .eq("activo", true)
    .returns<EmpresaUsuarioRow[]>();

  if (error) {
    throw new Error(`No se pudieron cargar las empresas: ${error.message}`);
  }

  const membresias = (data ?? []).map(mapMembresia).filter((m): m is Membresia => m !== null);

  membresias.sort((a, b) => a.empresa.nombre.localeCompare(b.empresa.nombre, "es"));
  return membresias;
}

/**
 * Resuelve la empresa activa a partir de la cookie persistida.
 *
 * - Si la cookie apunta a una membresía válida → la devuelve.
 * - Si no hay cookie y el usuario solo tiene 1 membresía → la usa por defecto.
 * - En cualquier otro caso devuelve `null` (la layout redirige a la pantalla
 *   de selección).
 *
 * Nunca confiamos en la cookie sin contrastarla contra la lista server-side.
 */
export async function obtenerMembresiaActiva(): Promise<{
  activa: MembresiaActiva | null;
  membresias: Membresia[];
}> {
  const membresias = await listarMembresiasUsuarioActual();
  const cookieStore = await cookies();
  const empresaIdCookie = cookieStore.get(EMPRESA_COOKIE_NAME)?.value ?? null;

  const fromCookie = empresaIdCookie
    ? membresias.find((m) => m.empresa.id === empresaIdCookie)
    : undefined;

  if (fromCookie) {
    return {
      activa: { empresa: fromCookie.empresa, rol: fromCookie.rol },
      membresias,
    };
  }

  const [unica] = membresias;
  if (!empresaIdCookie && membresias.length === 1 && unica) {
    return {
      activa: { empresa: unica.empresa, rol: unica.rol },
      membresias,
    };
  }

  return { activa: null, membresias };
}
