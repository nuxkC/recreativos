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
import { type CreditoAbierto, planificarRecuperacion } from "../_shared/recuperacion.ts";
import { detectarSolapeContador, type TramoFirme } from "../_shared/solape.ts";
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

  // La fecha de la recaudación no puede ser futura (margen para el desfase de
  // reloj del dispositivo). Una fecha futura solaparía intervalos y duplicaría
  // importes al recalcular semanas/baseline.
  const margenRelojMs = 5 * 60 * 1000;
  if (new Date(input.fecha).getTime() > Date.now() + margenRelojMs) {
    throw makeError("validation_error", "La fecha de la recaudación no puede ser futura");
  }

  // Baseline real ahora mismo y semanas.
  const baselineActual = await fetchBaseline(supabase, input.instalacion_id, input.fecha);

  // Monotonía de contadores: un contador físico no retrocede. Si el actual es
  // menor que la baseline, el bruto saldría negativo/inflado. Sin este guard
  // (que calcular-recaudacion sí aplica) se persistía una recaudación corrupta.
  if (
    input.contador_entradas_actual < baselineActual.entradas ||
    input.contador_salidas_actual < baselineActual.salidas
  ) {
    throw makeError(
      "validation_error",
      "Los contadores actuales no pueden ser inferiores a la baseline",
      {
        baseline: { entradas: baselineActual.entradas, salidas: baselineActual.salidas },
        actual: {
          entradas: input.contador_entradas_actual,
          salidas: input.contador_salidas_actual,
        },
      },
    );
  }

  const semanas = await fetchSemanas(
    supabase,
    baselineActual.fecha_referencia,
    input.fecha,
    ctx.empresa.zona_horaria,
  );

  // Merma de tolva pendiente: se recupera ANTES del reparto (§5.6). Es estado del
  // servidor (ledger tolva_movimiento); el mismo valor alimenta ambos cálculos
  // (servidor y, si hay conflicto, el del cliente).
  const pendienteTolva = await fetchPendienteTolva(supabase, input.instalacion_id);

  // Cálculo server-side con la baseline REAL (la del servidor ahora mismo).
  const resultadoServidor = calcularRecaudacion({
    baseline: baselineActual,
    contadorEntradasActual: input.contador_entradas_actual,
    contadorSalidasActual: input.contador_salidas_actual,
    valorCredito: ctx.instalacion.maquina.valor_credito,
    tasaSemanal: ctx.instalacion.tasa_semanal,
    porcentajeLocal: ctx.instalacion.porcentaje_local,
    semanas,
    redondeoUnidad: ctx.empresa.redondeo_recaudacion,
    pendienteTolva,
  });

  // Detectar conflicto ANTES de validar: la baseline que vio el cliente vs la
  // real ahora. Si difieren, otra recaudación/cambio de placa entró mientras el
  // técnico trabajaba (normalmente offline).
  const baselineIdActual = baselineActual.origen === "instalacion_base"
    ? null
    : baselineActual.referencia_id;
  const conflicto = !mismaBaseline(
    {
      id: input.baseline_id,
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

  // Caso 8 (T-262): ¿el tramo de contador de esta recaudación pisa el de otra
  // recaudación firme? Pasa con subidas DESORDENADAS de la cola offline: una con
  // fecha posterior se persiste antes y `obtener_baseline` (que elige por fecha)
  // le da una baseline que solapa el tramo de otra → el segmento común se contaría
  // dos veces. No se rechaza —el dinero de la caja no se pierde—: se marca como
  // conflicto para que el gestor lo resuelva (anular/sustituir) en web.
  const tramosFirmes = await fetchTramosFirmes(supabase, input.instalacion_id, input.fecha);
  const solapeIds = detectarSolapeContador(
    {
      entradasAnterior: input.baseline_entradas,
      entradasActual: input.contador_entradas_actual,
    },
    tramosFirmes,
  );
  const hayConflicto = conflicto || solapeIds.length > 0;

  // `resultado` es el reparto OFICIAL: lo que se persiste en las columnas
  // principales y se imprime en el ticket que firma el titular.
  //  · Sin conflicto: coincide con el del servidor; si no procede, no se recauda.
  //  · Con conflicto: el desglose físico de monedas que trae el técnico cuadra
  //    con SU baseline (la que vio offline). Recalculamos con ella —ese es el
  //    reparto físico real— y dejamos el cálculo del servidor en las columnas
  //    *_recalculado para que un admin resuelva. No se rechaza la subida: el
  //    dinero de la caja no se pierde.
  let resultado = resultadoServidor;
  if (conflicto) {
    const fechaRefCliente = await fetchFechaReferenciaCliente(
      supabase,
      input.instalacion_id,
      input.baseline_origen,
      input.baseline_id,
    );
    const semanasCliente = await fetchSemanas(
      supabase,
      fechaRefCliente,
      input.fecha,
      ctx.empresa.zona_horaria,
    );
    const resultadoCliente = calcularRecaudacion({
      baseline: {
        entradas: input.baseline_entradas,
        salidas: input.baseline_salidas,
        fecha_referencia: fechaRefCliente,
        origen: input.baseline_origen,
        referencia_id: input.baseline_id ?? input.instalacion_id,
      },
      contadorEntradasActual: input.contador_entradas_actual,
      contadorSalidasActual: input.contador_salidas_actual,
      valorCredito: ctx.instalacion.maquina.valor_credito,
      tasaSemanal: ctx.instalacion.tasa_semanal,
      porcentajeLocal: ctx.instalacion.porcentaje_local,
      semanas: semanasCliente,
      redondeoUnidad: ctx.empresa.redondeo_recaudacion,
      pendienteTolva,
    });
    if (!resultadoCliente.procede) {
      throw makeError(
        "validation_error",
        "La recaudación no procede ni con la baseline del propio dispositivo (bruto < tasa).",
        { bruto: resultadoCliente.bruto, tasa_total: resultadoCliente.tasa_total },
      );
    }
    resultado = resultadoCliente;
  } else if (!resultadoServidor.procede) {
    throw makeError(
      "insufficient_funds",
      "El bruto es inferior a la tasa: no procede recaudar. Registra una lectura_no_recaudada.",
      {
        bruto: resultadoServidor.bruto,
        tasa_total: resultadoServidor.tasa_total,
      },
    );
  }

  // Recuperación de deuda (T-214): se retiene un % de la parte_local del local
  // para amortizar sus deudas (tolva/préstamo). pct = override del local o, si no,
  // el de la empresa. No cambia parte_empresa; solo reparte la parte_local.
  const pctRecuperacion = ctx.instalacion.local.porcentaje_recuperacion ??
    ctx.empresa.porcentaje_recuperacion;
  const creditosAbiertos = await fetchCreditosAbiertos(supabase, ctx.instalacion.local_id);
  const planRecuperacion = planificarRecuperacion({
    parteLocal: resultado.parte_local,
    porcentajeRecuperacion: pctRecuperacion,
    creditos: creditosAbiertos,
    orden: input.orden_recuperacion,
  });

  // El desglose físico que se entrega al local debe cuadrar con pagado_local
  // (parte_local − recuperado). Con pct=0 o sin deuda, pagado_local = parte_local
  // y el comportamiento es idéntico al histórico.
  validarDesglosesContraResultado(input, resultado, planRecuperacion.pagado_local);

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
      // Salidas ajustadas: en el ticket la lectura debe cuadrar con el bruto
      // oficial (redondeado). Sin redondeo coincide con la leída.
      salidas: resultado.contador_salidas_ajustado,
    },
    resultado,
    recuperado: planRecuperacion.recuperado_total,
    pagadoLocal: planRecuperacion.pagado_local,
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
    resultadoServidor,
    baselineActual,
    tecnicoId: userId,
    firmaUrl,
    fotoEntradasUrl,
    fotoSalidasUrl,
    pdfPath,
    conflicto: hayConflicto,
    recuperadoTotal: planRecuperacion.recuperado_total,
  });

  // La inserción va con service_role vía RPC atómica `persistir_recaudacion`:
  // inserta la recaudación + las recuperaciones (amortización de deuda) + salda
  // los créditos que llegan a 0, en una sola transacción. Así nunca queda dinero
  // retenido sin reflejar en la deuda. El rol `authenticated` no tiene GRANT de
  // escritura; el acceso al tenant ya se validó arriba (RLS + requireRolEnEmpresa).
  const { data: row, error: insertError } = await service
    .rpc("persistir_recaudacion", {
      p_recaudacion: insertPayload,
      p_recuperaciones: planRecuperacion.asignaciones,
      p_usuario_id: userId,
    })
    .single();

  if (insertError) {
    throw makeError("internal_error", "No se pudo guardar la recaudación", insertError.message);
  }

  // Si hay conflicto (baseline distinta y/o tramo de contador solapado),
  // registramos una alerta para que el gestor lo revise y resuelva en web.
  if (hayConflicto) {
    await service.from("alerta").insert({
      empresa_id: ctx.empresa.id,
      tipo: "recaudacion_conflicto",
      referencia_id: recaudacionId,
      mensaje: mensajeConflicto(conflicto, solapeIds),
    });
  }

  // Devolvemos signed URLs para que el cliente pueda mostrar el PDF.
  const pdfSignedUrl = await createSignedUrl(supabase, "tickets", pdfPath);

  return jsonResponse(
    { recaudacion: row, pdf_signed_url: pdfSignedUrl, conflicto: hayConflicto },
    201,
  );
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
       local:local_id(nombre, direccion, titular_nombre, porcentaje_recuperacion),
       licencia:licencia_id(numero),
       empresa:empresa_id(id, nombre, zona_horaria, cif, ticket_cabecera, ticket_pie, logo_url, redondeo_recaudacion, porcentaje_recuperacion)`,
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

/**
 * Tramos de contador de las recaudaciones FIRMES de una instalación en el epoch
 * de contador actual: las posteriores al último cambio de placa <= `fecha` (un
 * reset de placa reinicia el contador, así que comparar entre epochs daría falsos
 * solapes). Alimenta la detección del caso 8 (T-262).
 */
async function fetchTramosFirmes(
  supabase: ReturnType<typeof getServiceClient>,
  instalacionId: string,
  fecha: string,
): Promise<TramoFirme[]> {
  const { data: cambioPlaca } = await supabase
    .from("cambio_placa")
    .select("fecha")
    .eq("instalacion_id", instalacionId)
    .lte("fecha", fecha)
    .order("fecha", { ascending: false })
    .limit(1)
    .maybeSingle();

  let query = supabase
    .from("recaudacion")
    .select("id, contador_entradas_anterior, contador_entradas_actual")
    .eq("instalacion_id", instalacionId)
    .eq("estado", "firme");
  if (cambioPlaca?.fecha) {
    query = query.gte("fecha", cambioPlaca.fecha);
  }

  const { data, error } = await query;
  if (error) {
    throw makeError("internal_error", "No se pudieron leer los tramos firmes", error.message);
  }
  return (data ?? []).map((r) => ({
    id: r.id as string,
    entradasAnterior: Number(r.contador_entradas_anterior),
    entradasActual: Number(r.contador_entradas_actual),
  }));
}

/** Mensaje de la alerta de conflicto, según su causa (baseline y/o solape). */
function mensajeConflicto(baselineDistinta: boolean, solapeIds: string[]): string {
  if (solapeIds.length > 0) {
    const solapeMsg =
      `Tramo de contador solapado con otra recaudación firme (${solapeIds.join(", ")}); ` +
      "posible doble conteo, revisar.";
    return baselineDistinta
      ? `${solapeMsg} Además, la baseline difiere de la actual del servidor.`
      : solapeMsg;
  }
  return "Recaudación creada con baseline distinta a la actual del servidor";
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

/**
 * Fecha de referencia (ISO) del evento baseline que vio el cliente. Necesaria
 * en conflictos para recalcular las semanas con la baseline del dispositivo.
 * La TZ de `instalacion_base` la resuelve la propia función SQL.
 */
async function fetchFechaReferenciaCliente(
  supabase: ReturnType<typeof getServiceClient>,
  instalacionId: string,
  origen: string,
  baselineId: string | null,
): Promise<string> {
  const { data, error } = await supabase.rpc("fecha_referencia_baseline", {
    p_instalacion_id: instalacionId,
    p_origen: origen,
    p_baseline_id: baselineId,
  });
  if (error || data === null) {
    throw makeError(
      "internal_error",
      "No se pudo determinar la baseline del dispositivo",
      error?.message,
    );
  }
  return data as string;
}

/**
 * Deudas abiertas del local (tolva/préstamo) con su saldo vivo, para calcular la
 * recuperación. Lee la vista security_invoker con el cliente del usuario: la RLS
 * deja ver las deudas de la empresa a la que pertenece el técnico.
 */
async function fetchCreditosAbiertos(
  supabase: ReturnType<typeof getServiceClient>,
  localId: string,
): Promise<CreditoAbierto[]> {
  const { data, error } = await supabase
    .from("v_credito_local_saldo")
    .select("credito_id, tipo, saldo, fecha, estado")
    .eq("local_id", localId)
    .eq("estado", "abierto");
  if (error) {
    throw makeError("internal_error", "No se pudieron cargar las deudas del local", error.message);
  }
  return (data ?? []).map((r) => ({
    id: r.credito_id as string,
    tipo: r.tipo as "tolva" | "prestamo",
    saldo: String(r.saldo),
    fecha: r.fecha as string,
  }));
}

/**
 * Merma de tolva pendiente de reponer en la instalación (de v_instalacion_tolva).
 * Se recupera antes del reparto (§5.6). 0 si no hay merma. La vista es
 * security_invoker; la RLS limita al tenant del técnico.
 */
async function fetchPendienteTolva(
  supabase: ReturnType<typeof getServiceClient>,
  instalacionId: string,
): Promise<string> {
  const { data, error } = await supabase
    .from("v_instalacion_tolva")
    .select("pendiente")
    .eq("instalacion_id", instalacionId)
    .maybeSingle();
  if (error) {
    throw makeError("internal_error", "No se pudo cargar el pendiente de tolva", error.message);
  }
  return data ? String(data.pendiente) : "0";
}

function validarDesglosesContraResultado(
  input: CrearRecaudacionInput,
  resultado: CalculoRecaudacionResult,
  pagadoLocal: string,
): void {
  const sumaTotal = sumarDesglose(input.desglose_total);
  if (!importesIguales(sumaTotal, resultado.bruto)) {
    throw makeError(
      "validation_error",
      "El desglose total no coincide con la recaudación bruta",
      { suma_desglose: sumaTotal, bruto_calculado: resultado.bruto },
    );
  }
  // El desglose de la parte local debe cuadrar con lo que se ENTREGA al local
  // (pagado_local = parte_local − recuperado). Sin recuperación coincide con
  // parte_local.
  const sumaLocal = sumarDesglose(input.desglose_local);
  if (!importesIguales(sumaLocal, pagadoLocal)) {
    throw makeError(
      "validation_error",
      "El desglose de la parte local no coincide con lo que se entrega al local",
      { suma_desglose: sumaLocal, pagado_local_calculado: pagadoLocal },
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
  resultadoServidor: CalculoRecaudacionResult;
  baselineActual: { entradas: number; salidas: number; referencia_id: string; origen: string };
  tecnicoId: string;
  firmaUrl: string;
  fotoEntradasUrl: string | null;
  fotoSalidasUrl: string | null;
  pdfPath: string;
  conflicto: boolean;
  recuperadoTotal: string;
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
    // Salidas ajustadas por el redondeo: se guardan como el contador real para
    // que la baseline de la próxima recaudación arrastre la diferencia. La
    // lectura original queda en `contador_salidas_leido` (solo auditoría).
    contador_salidas_actual: p.resultado.contador_salidas_ajustado,
    contador_salidas_leido: p.input.contador_salidas_actual,
    recaudacion_bruta_real: p.resultado.recaudacion_bruta_real,
    redondeo_aplicado: p.resultado.redondeo_aplicado,
    valor_credito_aplicado: p.resultado.valor_credito,
    recaudacion_bruta: p.resultado.bruto,
    semanas_aplicadas: p.resultado.semanas,
    tasa_semanal_aplicada: p.resultado.tasa_semanal,
    tasa_total_aplicada: p.resultado.tasa_total,
    recaudacion_neta: p.resultado.neto,
    porcentaje_local_aplicado: p.resultado.porcentaje_local,
    parte_local: p.resultado.parte_local,
    parte_empresa: p.resultado.parte_empresa,
    reposicion_tolva: p.resultado.reposicion_tolva,
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
    recuperado_total: p.recuperadoTotal,
  };

  // Si hay conflicto, las columnas oficiales (en `base`) llevan el reparto del
  // cliente —el físico, que cuadra con el desglose de monedas— y las columnas
  // paralelas llevan el recálculo del servidor con la baseline real, para que un
  // admin compare y resuelva. (Antes ambas recibían el valor del servidor, lo
  // que dejaba *_recalculado == oficial y hacía inútil resolver-conflicto.)
  if (p.conflicto) {
    return {
      ...base,
      bruto_recalculado: p.resultadoServidor.bruto,
      neto_recalculado: p.resultadoServidor.neto,
      parte_local_recalculada: p.resultadoServidor.parte_local,
      parte_empresa_recalculada: p.resultadoServidor.parte_empresa,
    };
  }
  return base;
}
