/**
 * T-20 — Edge Function `calcular-recaudacion`.
 *
 * Endpoint de PREVIEW: dado unos contadores propuestos, devuelve las cifras
 * que tendría la recaudación. NO persiste nada. Lo invoca la app móvil al
 * pulsar "Calcular" para mostrar al técnico el detalle antes del reparto.
 *
 * La verdad la calcula el servidor (SSOT) leyendo baseline y semanas desde
 * funciones SQL. El cliente solo aporta los contadores y la fecha.
 */

import { ZodError } from "zod";

import { requireUser } from "../_shared/auth.ts";
import { calcularRecaudacion } from "../_shared/calculo.ts";
import { makeError } from "../_shared/errors.ts";
import { jsonResponse } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { CalcularRecaudacionInputSchema } from "../_shared/schemas.ts";

Deno.serve(withHandler(async (req: Request) => {
  if (req.method !== "POST") {
    throw makeError("validation_error", "Solo se admite POST");
  }

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw makeError("validation_error", "Body no es JSON válido");
  }

  let input;
  try {
    input = CalcularRecaudacionInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase } = await requireUser(req);
  const fecha = input.fecha ?? new Date().toISOString();

  const { data: inst, error: instError } = await supabase
    .from("instalacion")
    .select(
      `id, empresa_id, tasa_semanal, porcentaje_local, estado,
       maquina:maquina_id(valor_credito),
       empresa:empresa_id(zona_horaria, redondeo_recaudacion)`,
    )
    .eq("id", input.instalacion_id)
    .maybeSingle();

  if (instError) {
    throw makeError("internal_error", "Error consultando instalación", instError.message);
  }
  if (!inst) {
    throw makeError("not_found", "Instalación no encontrada o sin acceso");
  }
  if (inst.estado !== "activa") {
    throw makeError("validation_error", "La instalación no está activa");
  }

  // Validación de monotonía de contadores (la baseline se compara después).
  if (
    input.contador_entradas_actual < 0 ||
    input.contador_salidas_actual < 0
  ) {
    throw makeError("validation_error", "Contadores negativos");
  }

  const { data: baseline, error: blError } = await supabase
    .rpc("obtener_baseline", {
      p_instalacion_id: input.instalacion_id,
      p_fecha: fecha,
    })
    .single();

  const baselineRow = baseline as unknown as import("../_shared/types.ts").BaselineRpcRow | null;

  if (blError || !baselineRow) {
    throw makeError("internal_error", "No se pudo obtener baseline", blError?.message);
  }

  // Si los contadores actuales son menores que la baseline -> error claro
  if (
    input.contador_entradas_actual < Number(baselineRow.entradas) ||
    input.contador_salidas_actual < Number(baselineRow.salidas)
  ) {
    throw makeError(
      "validation_error",
      "Los contadores actuales no pueden ser menores que la baseline",
      {
        baseline_entradas: baselineRow.entradas,
        baseline_salidas: baselineRow.salidas,
      },
    );
  }

  const empresa = (Array.isArray(inst.empresa) ? inst.empresa[0] : inst.empresa) as
    | { zona_horaria: string; redondeo_recaudacion: number }
    | null;
  const zonaHoraria = empresa?.zona_horaria ?? "Europe/Madrid";
  const redondeoUnidad = empresa?.redondeo_recaudacion ?? 0;

  const { data: semanas, error: semError } = await supabase.rpc("semanas_iso_entre", {
    p_desde: baselineRow.fecha_referencia,
    p_hasta: fecha,
    p_tz: zonaHoraria,
  });

  if (semError || semanas === null) {
    throw makeError("internal_error", "No se pudieron calcular semanas", semError?.message);
  }

  const maquina = (Array.isArray(inst.maquina) ? inst.maquina[0] : inst.maquina) as
    | { valor_credito: string | number }
    | null;
  if (!maquina) {
    throw makeError("internal_error", "La instalación no tiene máquina asociada");
  }

  // Merma de tolva pendiente: se recupera antes del reparto (§5.6). La vista es
  // security_invoker, así que la RLS limita al tenant del técnico.
  const { data: tolva } = await supabase
    .from("v_instalacion_tolva")
    .select("pendiente")
    .eq("instalacion_id", input.instalacion_id)
    .maybeSingle();
  const pendienteTolva = tolva ? String(tolva.pendiente) : "0";

  const result = calcularRecaudacion({
    baseline: {
      entradas: Number(baselineRow.entradas),
      salidas: Number(baselineRow.salidas),
      fecha_referencia: baselineRow.fecha_referencia,
      origen: baselineRow.origen,
      referencia_id: baselineRow.referencia_id,
    },
    contadorEntradasActual: input.contador_entradas_actual,
    contadorSalidasActual: input.contador_salidas_actual,
    valorCredito: String(maquina.valor_credito),
    tasaSemanal: String(inst.tasa_semanal),
    porcentajeLocal: String(inst.porcentaje_local),
    semanas: Number(semanas),
    redondeoUnidad,
    pendienteTolva,
  });

  return jsonResponse(result);
}));
