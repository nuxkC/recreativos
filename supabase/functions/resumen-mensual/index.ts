/**
 * T-102 — Edge Function `resumen-mensual`.
 *
 * Envía un email al titular de cada local con el resumen de recaudación de
 * un mes. Pensada para ejecutarse por cron mensual (pg_cron + pg_net, ver
 * migración `..._resumen_mensual_view_and_cron.sql`) con body vacío `{}`,
 * en cuyo caso usa el MES ANTERIOR. Acepta overrides `{ mes, empresa_id }`.
 *
 * Flujo (service_role, bypassa RLS — es un job de servidor):
 *   1. Resuelve el mes objetivo en la zona horaria de empresa.
 *   2. Lee el view agregado `v_recaudaciones_por_local_maquina_mes` para ese
 *      mes (cálculo monetario centralizado en SQL con numeric).
 *   3. Enriquecе con datos de empresa, local (titular/email) y máquina.
 *   4. Por cada local con datos y email del titular, construye y envía el
 *      email vía `_shared/email.ts` (Resend).
 *
 * Criterios de borde:
 *   - Local sin titular/email → se omite con log (sin PII).
 *   - Mes sin datos → no se envía nada (resumen vacío no se manda).
 *   - Sin `RESEND_API_KEY` → se omite el envío (modo dev), como el resto
 *     del sistema; la función responde con el desglose de omitidos.
 *
 * Logging: JSON estructurado, SIN PII (no email, no nombre de titular).
 */

import { ZodError } from "zod";

import { requireServiceRole } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { emailConfigurado, enviarEmail } from "../_shared/email.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { ResumenMensualInputSchema } from "../_shared/schemas.ts";
import { ZONA_HORARIA_DEFAULT } from "../_shared/constants.ts";
import {
  construirAsuntoResumen,
  construirHtmlResumen,
  construirResumenLocal,
  construirTextoResumen,
  type MaquinaInfo,
  type MaquinaMesRow,
  resolverMes,
} from "./resumen.ts";

interface ViewRow {
  empresa_id: string;
  local_id: string;
  maquina_id: string;
  num_recaudaciones: number;
  parte_local_total: string;
  neto_total: string;
}

interface LocalRow {
  id: string;
  nombre: string;
  titular_nombre: string | null;
  email: string | null;
}

Deno.serve(withHandler(async (req: Request) => {
  if (req.method !== "POST") {
    throw makeError("validation_error", "Solo se admite POST");
  }

  // Solo el cron (service_role) puede disparar el envío de resúmenes. Sin esto,
  // cualquiera con el anon key público podría regenerar y reenviar la
  // liquidación mensual de cualquier empresa/mes.
  requireServiceRole(req);

  let raw: unknown = {};
  try {
    const text = await req.text();
    raw = text.trim() ? JSON.parse(text) : {};
  } catch {
    throw makeError("validation_error", "Body no es JSON válido");
  }

  let input;
  try {
    input = ResumenMensualInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const service = getServiceClient();
  const ahora = new Date();

  const resumen = {
    locales_con_datos: 0,
    enviados: 0,
    omitidos_sin_email: 0,
    omitidos_sin_proveedor: 0,
    omitidos_ya_enviados: 0,
    fallidos: 0,
  };

  // El mes objetivo se resuelve POR EMPRESA en su propia zona horaria (la vista
  // ya agrupa cada recaudación por el mes local de su empresa). Agrupamos las
  // empresas por el mes resuelto para minimizar consultas (normalmente un grupo).
  const empresas = await cargarEmpresasConTz(service, input.empresa_id);
  if (empresas.length === 0) {
    console.log(JSON.stringify({ level: "info", msg: "resumen_mensual_sin_empresas" }));
    return jsonResponse(resumen);
  }

  const grupos = new Map<string, Grupo>();
  for (const emp of empresas) {
    const { mes, mesLocalStart, etiqueta } = resolverMes(
      ahora,
      emp.zona_horaria ?? ZONA_HORARIA_DEFAULT,
      input.mes,
    );
    const g = grupos.get(mesLocalStart) ?? { mes, etiqueta, mesLocalStart, empresaIds: [] };
    g.empresaIds.push(emp.id);
    grupos.set(mesLocalStart, g);
  }

  const hayProveedor = emailConfigurado();
  for (const grupo of grupos.values()) {
    await procesarGrupo(service, grupo, resumen);
  }

  console.log(JSON.stringify({
    level: "info",
    msg: "resumen_mensual_completado",
    proveedor_email: hayProveedor,
    ...resumen,
  }));

  return jsonResponse(resumen);
}));

interface Grupo {
  mes: string;
  etiqueta: string;
  mesLocalStart: string;
  empresaIds: string[];
}

/** Procesa un grupo de empresas que comparten el mismo mes objetivo. */
async function procesarGrupo(
  service: ReturnType<typeof getServiceClient>,
  grupo: Grupo,
  resumen: {
    locales_con_datos: number;
    enviados: number;
    omitidos_sin_email: number;
    omitidos_sin_proveedor: number;
    omitidos_ya_enviados: number;
    fallidos: number;
  },
): Promise<void> {
  const { data: filas, error: filasError } = await service
    .from("v_recaudaciones_por_local_maquina_mes")
    .select(
      "empresa_id, local_id, maquina_id, num_recaudaciones, parte_local_total, neto_total",
    )
    .eq("mes_local", grupo.mesLocalStart)
    .in("empresa_id", grupo.empresaIds)
    .returns<ViewRow[]>();
  if (filasError) {
    throw makeError("internal_error", "Error consultando el resumen", filasError.message);
  }
  if (!filas || filas.length === 0) {
    console.log(JSON.stringify({ level: "info", msg: "resumen_mensual_sin_datos", mes: grupo.mes }));
    return;
  }

  // Agrupamos filas por local y recopilamos ids para los lookups.
  const filasPorLocal = new Map<string, ViewRow[]>();
  const empresaIds = new Set<string>();
  const localIds = new Set<string>();
  const maquinaIds = new Set<string>();
  for (const fila of filas) {
    const lista = filasPorLocal.get(fila.local_id) ?? [];
    lista.push(fila);
    filasPorLocal.set(fila.local_id, lista);
    empresaIds.add(fila.empresa_id);
    localIds.add(fila.local_id);
    maquinaIds.add(fila.maquina_id);
  }
  resumen.locales_con_datos += filasPorLocal.size;

  const [empresaPorId, localPorId, maquinaPorId] = await Promise.all([
    cargarEmpresas(service, [...empresaIds]),
    cargarLocales(service, [...localIds]),
    cargarMaquinas(service, [...maquinaIds]),
  ]);

  for (const [localId, filasLocal] of filasPorLocal) {
    const local = localPorId[localId];
    const empresaId = filasLocal[0]?.empresa_id ?? "";

    if (!local?.email) {
      resumen.omitidos_sin_email += 1;
      console.log(JSON.stringify({
        level: "info",
        msg: "resumen_mensual_local_sin_email",
        mes: grupo.mes,
        empresa_id: empresaId,
        local_id: localId,
      }));
      continue;
    }

    // Claim idempotente: marca (local, mes) ANTES de enviar. Si ya estaba, no se
    // reenvía (evita liquidaciones duplicadas si el cron corre dos veces).
    const reservado = await reservarEnvio(service, empresaId, localId, grupo.mes);
    if (!reservado) {
      resumen.omitidos_ya_enviados += 1;
      continue;
    }

    const resumenLocal = construirResumenLocal({
      empresaNombre: empresaPorId[empresaId] ?? "Recre",
      localNombre: local.nombre,
      titularNombre: local.titular_nombre,
      mesEtiqueta: grupo.etiqueta,
      filas: filasLocal as MaquinaMesRow[],
      maquinaInfoPorId: maquinaPorId,
    });

    const resultado = await enviarEmail({
      to: local.email,
      subject: construirAsuntoResumen(resumenLocal),
      html: construirHtmlResumen(resumenLocal),
      text: construirTextoResumen(resumenLocal),
    });

    if (resultado.status === "sent") {
      resumen.enviados += 1;
    } else if (resultado.status === "skipped") {
      // Sin proveedor de email: no salió de verdad → liberamos el claim para no
      // dejar marcado como enviado algo que no se envió.
      resumen.omitidos_sin_proveedor += 1;
      await liberarEnvio(service, localId, grupo.mes);
    } else {
      // Fallo de envío: liberamos el claim para reintentar en la próxima pasada.
      resumen.fallidos += 1;
      await liberarEnvio(service, localId, grupo.mes);
      console.error(JSON.stringify({
        level: "error",
        msg: "resumen_mensual_email_fallido",
        mes: grupo.mes,
        empresa_id: empresaId,
        local_id: localId,
        http_status: resultado.httpStatus,
        detail: resultado.detail,
      }));
    }
  }
}

/** Empresas a procesar con su zona horaria (todas, o solo la indicada). */
async function cargarEmpresasConTz(
  service: ReturnType<typeof getServiceClient>,
  empresaId?: string,
): Promise<{ id: string; zona_horaria: string | null }[]> {
  let q = service.from("empresa").select("id, zona_horaria");
  if (empresaId) q = q.eq("id", empresaId);
  const { data, error } = await q.returns<{ id: string; zona_horaria: string | null }[]>();
  if (error) {
    throw makeError("internal_error", "Error cargando empresas", error.message);
  }
  return data ?? [];
}

/**
 * Reserva atómicamente el envío (local, mes). Devuelve true si lo consiguió
 * (no estaba enviado), false si ya existía. INSERT ON CONFLICT DO NOTHING: el
 * RETURNING viene vacío cuando la fila ya estaba.
 */
async function reservarEnvio(
  service: ReturnType<typeof getServiceClient>,
  empresaId: string,
  localId: string,
  mes: string,
): Promise<boolean> {
  const { data, error } = await service
    .from("resumen_mensual_envio")
    .upsert(
      { empresa_id: empresaId, local_id: localId, mes },
      { onConflict: "local_id,mes", ignoreDuplicates: true },
    )
    .select("id");
  if (error) {
    throw makeError("internal_error", "Error reservando el envío del resumen", error.message);
  }
  return (data?.length ?? 0) > 0;
}

/** Libera el claim de envío (local, mes) cuando el email no llegó a salir. */
async function liberarEnvio(
  service: ReturnType<typeof getServiceClient>,
  localId: string,
  mes: string,
): Promise<void> {
  await service
    .from("resumen_mensual_envio")
    .delete()
    .eq("local_id", localId)
    .eq("mes", mes);
}

async function cargarEmpresas(
  service: ReturnType<typeof getServiceClient>,
  ids: string[],
): Promise<Record<string, string>> {
  if (ids.length === 0) return {};
  const { data, error } = await service
    .from("empresa")
    .select("id, nombre")
    .in("id", ids)
    .returns<{ id: string; nombre: string }[]>();
  if (error) {
    throw makeError("internal_error", "Error cargando empresas", error.message);
  }
  return Object.fromEntries((data ?? []).map((e) => [e.id, e.nombre]));
}

async function cargarLocales(
  service: ReturnType<typeof getServiceClient>,
  ids: string[],
): Promise<Record<string, LocalRow>> {
  if (ids.length === 0) return {};
  const { data, error } = await service
    .from("local")
    .select("id, nombre, titular_nombre, email")
    .in("id", ids)
    .returns<LocalRow[]>();
  if (error) {
    throw makeError("internal_error", "Error cargando locales", error.message);
  }
  return Object.fromEntries((data ?? []).map((l) => [l.id, l]));
}

async function cargarMaquinas(
  service: ReturnType<typeof getServiceClient>,
  ids: string[],
): Promise<Record<string, MaquinaInfo>> {
  if (ids.length === 0) return {};
  const { data, error } = await service
    .from("maquina")
    .select("id, numero_serie, modelo")
    .in("id", ids)
    .returns<{ id: string; numero_serie: string; modelo: string | null }[]>();
  if (error) {
    throw makeError("internal_error", "Error cargando máquinas", error.message);
  }
  return Object.fromEntries(
    (data ?? []).map((m) => [m.id, { numero_serie: m.numero_serie, modelo: m.modelo }]),
  );
}
