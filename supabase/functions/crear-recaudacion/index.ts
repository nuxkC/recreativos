/**
 * T-21 — Edge Function `crear-recaudacion`.
 *
 * Persiste una recaudación. Recalcula TODAS las cifras server-side
 * (SSOT en `_shared/calculo.ts`), detecta conflictos comparando la
 * baseline enviada por el cliente contra la real al persistir, sube
 * firma y fotos, genera y archiva el PDF, e inserta la fila final.
 *
 * Idempotencia: si llega una recaudación con un `idempotency_key` ya
 * existente, devuelve la recaudación previa sin volver a procesar.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { calcularRecaudacion, importesIguales, sumarDesglose } from "../_shared/calculo.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { generarPdfTicket } from "../_shared/pdf.ts";
import { type CrearRecaudacionInput, CrearRecaudacionInputSchema } from "../_shared/schemas.ts";
import {
  type Bucket,
  createSignedUrl,
  decodeBase64Image,
  uploadToBucket,
} from "../_shared/storage.ts";
import type {
  CalculoRecaudacionResult,
  EmpresaContext,
  InstalacionContext,
} from "../_shared/types.ts";

const ROLES_OPERATIVO = ["owner", "admin", "gestor", "tecnico"] as const;

Deno.serve(withHandler(async (req: Request) => {
  if (req.method !== "POST") {
    throw makeError("validation_error", "Solo se admite POST");
  }

  const input = await parseInput(req);
  const { supabase, userId } = await requireUser(req);

  // Idempotency: si ya existe la recaudación, no la rehagamos.
  const existing = await findByIdempotencyKey(supabase, input.idempotency_key);
  if (existing) {
    return jsonResponse({ recaudacion: existing, reusada: true });
  }

  // Cargamos contexto completo (instalación + máquina + local + licencia + empresa).
  const ctx = await loadInstalacionContext(supabase, input.instalacion_id);
  await requireRolEnEmpresa(supabase, ctx.instalacion.empresa_id, ROLES_OPERATIVO);

  // Baseline real ahora mismo y semanas.
  const baselineActual = await fetchBaseline(supabase, input.instalacion_id, input.fecha);
  const semanas = await fetchSemanas(
    supabase,
    baselineActual.fecha_referencia,
    input.fecha,
    ctx.empresa.zona_horaria,
  );

  // Cálculo server-side.
  const resultado = calcularRecaudacion({
    baseline: baselineActual,
    contadorEntradasActual: input.contador_entradas_actual,
    contadorSalidasActual: input.contador_salidas_actual,
    valorCredito: ctx.instalacion.maquina.valor_credito,
    tasaSemanal: ctx.instalacion.tasa_semanal,
    porcentajeLocal: ctx.instalacion.porcentaje_local,
    semanas,
  });

  if (!resultado.procede) {
    throw makeError(
      "insufficient_funds",
      "El bruto es inferior a la tasa: no procede recaudar. Registra una lectura_no_recaudada.",
      {
        bruto: resultado.bruto,
        tasa_total: resultado.tasa_total,
      },
    );
  }

  validarDesglosesContraResultado(input, resultado);

  // Detectar conflicto: la baseline que vio el cliente vs la real ahora.
  const baselineIdRecibida = input.baseline_id;
  const baselineIdActual = baselineActual.origen === "instalacion_base"
    ? null
    : baselineActual.referencia_id;
  const conflicto = !mismaBaseline(
    {
      id: baselineIdRecibida,
      origen: input.baseline_origen,
      entradas: input.baseline_entradas,
      salidas: input.baseline_salidas,
    },
    {
      id: baselineIdActual,
      origen: baselineActual.origen,
      entradas: baselineActual.entradas,
      salidas: baselineActual.salidas,
    },
  );

  // Generamos el id ahora para construir paths antes de insertar.
  const recaudacionId = crypto.randomUUID();

  // Subimos firma y fotos opcionales.
  const firmaUrl = await subirFirma(supabase, ctx.empresa.id, recaudacionId, input.firma_base64);
  const fotoEntradasUrl = await subirFotoOpcional(
    supabase,
    "fotos-contadores",
    `${ctx.empresa.id}/${recaudacionId}/entradas`,
    input.foto_entradas_base64,
  );
  const fotoSalidasUrl = await subirFotoOpcional(
    supabase,
    "fotos-contadores",
    `${ctx.empresa.id}/${recaudacionId}/salidas`,
    input.foto_salidas_base64,
  );

  // Datos del técnico para el ticket.
  const { data: usuario } = await supabase
    .from("usuario")
    .select("nombre_completo")
    .eq("id", userId)
    .maybeSingle();

  // PDF de archivo.
  const pdfBytes = await generarPdfTicket({
    empresa: ctx.empresa,
    instalacion: ctx.instalacion,
    recaudacionId,
    tecnicoNombre: usuario?.nombre_completo ?? "(sin nombre)",
    fecha: input.fecha,
    contadoresAnterior: {
      entradas: Number(baselineActual.entradas),
      salidas: Number(baselineActual.salidas),
    },
    contadoresActual: {
      entradas: input.contador_entradas_actual,
      salidas: input.contador_salidas_actual,
    },
    resultado,
    desgloseTotal: input.desglose_total,
    desgloseLocal: input.desglose_local,
    firmaPng: await fetchPng(supabase, "firmas", firmaUrl),
    observaciones: input.observaciones,
    idempotencyKey: input.idempotency_key,
    dispositivoId: input.dispositivo_id,
  });

  const pdfPath = `${ctx.empresa.id}/${recaudacionId}.pdf`;
  // El bucket `tickets` solo permite INSERT desde service_role.
  const service = getServiceClient();
  await uploadToBucket(service, "tickets", pdfPath, pdfBytes, "application/pdf");

  // Inserción final: si hay conflicto, llenamos columnas *_recalculado.
  const insertPayload = construirInsertPayload({
    id: recaudacionId,
    input,
    ctx,
    resultado,
    baselineActual,
    tecnicoId: userId,
    firmaUrl,
    fotoEntradasUrl,
    fotoSalidasUrl,
    pdfPath,
    conflicto,
  });

  const { data: row, error: insertError } = await supabase
    .from("recaudacion")
    .insert(insertPayload)
    .select()
    .single();

  if (insertError) {
    throw makeError("internal_error", "No se pudo guardar la recaudación", insertError.message);
  }

  // Si hay conflicto, registramos una alerta para el admin.
  if (conflicto) {
    await service.from("alerta").insert({
      empresa_id: ctx.empresa.id,
      tipo: "recaudacion_conflicto",
      referencia_id: recaudacionId,
      mensaje: "Recaudación creada con baseline distinta a la actual del servidor",
    });
  }

  // Devolvemos signed URLs para que el cliente pueda mostrar el PDF.
  const pdfSignedUrl = await createSignedUrl(supabase, "tickets", pdfPath);

  return jsonResponse({ recaudacion: row, pdf_signed_url: pdfSignedUrl, conflicto }, 201);
}));

// ----------------------------------------------------------------------------- helpers

async function parseInput(req: Request): Promise<CrearRecaudacionInput> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw makeError("validation_error", "Body no es JSON válido");
  }
  try {
    return CrearRecaudacionInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }
}

async function findByIdempotencyKey(supabase: ReturnType<typeof getServiceClient>, key: string) {
  const { data, error } = await supabase
    .from("recaudacion")
    .select("*")
    .eq("idempotency_key", key)
    .maybeSingle();
  if (error) {
    throw makeError("internal_error", "Error comprobando idempotencia", error.message);
  }
  return data;
}

interface ContextoCargado {
  empresa: EmpresaContext;
  instalacion: InstalacionContext;
}

async function loadInstalacionContext(
  supabase: ReturnType<typeof getServiceClient>,
  instalacionId: string,
): Promise<ContextoCargado> {
  const { data, error } = await supabase
    .from("instalacion")
    .select(
      `id, empresa_id, maquina_id, licencia_id, local_id, fecha_inicio,
       tasa_semanal, porcentaje_local, contador_entradas_base, contador_salidas_base, estado,
       maquina:maquina_id(numero_serie, modelo, valor_credito),
       local:local_id(nombre, direccion, titular_nombre),
       licencia:licencia_id(numero),
       empresa:empresa_id(id, nombre, zona_horaria, cif, ticket_cabecera, ticket_pie, logo_url)`,
    )
    .eq("id", instalacionId)
    .maybeSingle();

  if (error) {
    throw makeError("internal_error", "Error consultando instalación", error.message);
  }
  if (!data) {
    throw makeError("not_found", "Instalación no encontrada o sin acceso");
  }
  if (data.estado !== "activa") {
    throw makeError("validation_error", "La instalación no está activa");
  }

  const empresa = data.empresa as unknown as EmpresaContext | null;
  if (!empresa) {
    throw makeError("internal_error", "Instalación sin empresa asociada");
  }

  return {
    empresa,
    instalacion: data as unknown as InstalacionContext,
  };
}

async function fetchBaseline(
  supabase: ReturnType<typeof getServiceClient>,
  instalacionId: string,
  fecha: string,
) {
  const { data, error } = await supabase
    .rpc("obtener_baseline", { p_instalacion_id: instalacionId, p_fecha: fecha })
    .single();
  const row = data as unknown as import("../_shared/types.ts").BaselineRpcRow | null;
  if (error || !row) {
    throw makeError("internal_error", "No se pudo obtener baseline", error?.message);
  }
  return {
    entradas: Number(row.entradas),
    salidas: Number(row.salidas),
    fecha_referencia: row.fecha_referencia,
    origen: row.origen,
    referencia_id: row.referencia_id,
  };
}

async function fetchSemanas(
  supabase: ReturnType<typeof getServiceClient>,
  desde: string,
  hasta: string,
  tz: string,
): Promise<number> {
  const { data, error } = await supabase.rpc("semanas_iso_entre", {
    p_desde: desde,
    p_hasta: hasta,
    p_tz: tz,
  });
  if (error || data === null) {
    throw makeError("internal_error", "No se pudieron calcular semanas", error?.message);
  }
  return Number(data);
}

function validarDesglosesContraResultado(
  input: CrearRecaudacionInput,
  resultado: CalculoRecaudacionResult,
): void {
  const sumaTotal = sumarDesglose(input.desglose_total);
  if (!importesIguales(sumaTotal, resultado.bruto)) {
    throw makeError(
      "validation_error",
      "El desglose total no coincide con la recaudación bruta",
      { suma_desglose: sumaTotal, bruto_calculado: resultado.bruto },
    );
  }
  const sumaLocal = sumarDesglose(input.desglose_local);
  if (!importesIguales(sumaLocal, resultado.parte_local)) {
    throw makeError(
      "validation_error",
      "El desglose de la parte local no coincide con la parte_local calculada",
      { suma_desglose: sumaLocal, parte_local_calculada: resultado.parte_local },
    );
  }
}

interface BaselineRecibida {
  id: string | null;
  origen: string;
  entradas: number;
  salidas: number;
}

function mismaBaseline(a: BaselineRecibida, b: BaselineRecibida): boolean {
  return a.origen === b.origen &&
    a.id === b.id &&
    a.entradas === b.entradas &&
    a.salidas === b.salidas;
}

async function subirFirma(
  supabase: ReturnType<typeof getServiceClient>,
  empresaId: string,
  recaudacionId: string,
  base64: string,
): Promise<string> {
  const { bytes, mime } = decodeBase64Image(base64);
  const ext = mime === "image/jpeg" ? "jpg" : "png";
  const path = `${empresaId}/${recaudacionId}.${ext}`;
  await uploadToBucket(supabase, "firmas", path, bytes, mime);
  return path;
}

async function subirFotoOpcional(
  supabase: ReturnType<typeof getServiceClient>,
  bucket: Bucket,
  basePath: string,
  base64: string | undefined,
): Promise<string | null> {
  if (!base64) return null;
  const { bytes, mime } = decodeBase64Image(base64);
  const ext = mime === "image/png" ? "png" : "jpg";
  const path = `${basePath}.${ext}`;
  await uploadToBucket(supabase, bucket, path, bytes, mime);
  return path;
}

async function fetchPng(
  supabase: ReturnType<typeof getServiceClient>,
  bucket: Bucket,
  path: string,
): Promise<Uint8Array | undefined> {
  try {
    const { data, error } = await supabase.storage.from(bucket).download(path);
    if (error || !data) return undefined;
    const buf = await data.arrayBuffer();
    return new Uint8Array(buf);
  } catch {
    return undefined;
  }
}

interface InsertParams {
  id: string;
  input: CrearRecaudacionInput;
  ctx: ContextoCargado;
  resultado: CalculoRecaudacionResult;
  baselineActual: { entradas: number; salidas: number; referencia_id: string; origen: string };
  tecnicoId: string;
  firmaUrl: string;
  fotoEntradasUrl: string | null;
  fotoSalidasUrl: string | null;
  pdfPath: string;
  conflicto: boolean;
}

function construirInsertPayload(p: InsertParams) {
  const base = {
    id: p.id,
    empresa_id: p.ctx.instalacion.empresa_id,
    instalacion_id: p.ctx.instalacion.id,
    tecnico_id: p.tecnicoId,
    fecha: p.input.fecha,
    contador_entradas_anterior: p.input.baseline_entradas,
    contador_salidas_anterior: p.input.baseline_salidas,
    contador_entradas_actual: p.input.contador_entradas_actual,
    contador_salidas_actual: p.input.contador_salidas_actual,
    valor_credito_aplicado: p.resultado.valor_credito,
    recaudacion_bruta: p.resultado.bruto,
    semanas_aplicadas: p.resultado.semanas,
    tasa_semanal_aplicada: p.resultado.tasa_semanal,
    tasa_total_aplicada: p.resultado.tasa_total,
    recaudacion_neta: p.resultado.neto,
    porcentaje_local_aplicado: p.resultado.porcentaje_local,
    parte_local: p.resultado.parte_local,
    parte_empresa: p.resultado.parte_empresa,
    desglose_total: p.input.desglose_total,
    desglose_local: p.input.desglose_local,
    firma_url: p.firmaUrl,
    foto_entradas_url: p.fotoEntradasUrl,
    foto_salidas_url: p.fotoSalidasUrl,
    ocr_entradas_valor: p.input.ocr_entradas_valor ?? null,
    ocr_salidas_valor: p.input.ocr_salidas_valor ?? null,
    pdf_url: p.pdfPath,
    observaciones: p.input.observaciones ?? null,
    dispositivo_id: p.input.dispositivo_id ?? null,
    idempotency_key: p.input.idempotency_key,
    baseline_origen: p.input.baseline_origen,
    baseline_id: p.input.baseline_id,
    estado: "firme" as const,
    conflicto: p.conflicto,
  };

  // Si hay conflicto, server-side recalculó con la baseline real -> rellenamos
  // las columnas paralelas con esos valores recalculados, conservando los del
  // cliente en las columnas oficiales para no alterar el reparto físico.
  if (p.conflicto) {
    return {
      ...base,
      bruto_recalculado: p.resultado.bruto,
      neto_recalculado: p.resultado.neto,
      parte_local_recalculada: p.resultado.parte_local,
      parte_empresa_recalculada: p.resultado.parte_empresa,
    };
  }
  return base;
}
