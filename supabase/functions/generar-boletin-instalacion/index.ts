/**
 * T-203 — Edge Function `generar-boletin-instalacion`.
 *
 * Genera (o reutiliza) el boletín digital de instalación de una instalación:
 *   1. valida el input (Zod),
 *   2. comprueba acceso (membresía + rol de gestión en la empresa),
 *   3. reúne los datos de la instalación (máquina, local, licencia, empresa),
 *   4. genera el PDF, lo sube al bucket privado `boletines` y
 *   5. persiste la referencia en `instalacion` y devuelve una signed URL.
 *
 * Idempotente: si la instalación ya tiene `boletin_url` y no se pide
 * `forzar`, devuelve la URL del boletín existente sin regenerarlo.
 *
 * Acceso: gestor, admin u owner de la empresa de la instalación.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { type BoletinInstalacionContext, generarPdfBoletin } from "../_shared/boletin-pdf.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { GenerarBoletinInstalacionInputSchema } from "../_shared/schemas.ts";
import { type Bucket, createSignedUrl, uploadToBucket } from "../_shared/storage.ts";

const ROLES_GESTION = ["owner", "admin", "gestor"] as const;
const BUCKET: Bucket = "boletines";

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
    input = GenerarBoletinInstalacionInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase } = await requireUser(req);

  // Cargamos el contexto completo respetando RLS (el caller solo ve sus
  // empresas). Si la instalación no existe o no es suya -> not_found.
  const ctx = await loadContext(supabase, input.instalacion_id);
  await requireRolEnEmpresa(supabase, ctx.empresaId, ROLES_GESTION);

  const path = `${ctx.empresaId}/${ctx.boletin.instalacion.id}.pdf`;

  // Idempotencia: si ya existe y no se fuerza, devolvemos el existente.
  if (ctx.boletinUrl && !input.forzar) {
    const url = await createSignedUrl(supabase, BUCKET, ctx.boletinUrl);
    console.log(JSON.stringify({
      level: "info",
      msg: "boletin_reusado",
      instalacion_id: ctx.boletin.instalacion.id,
      empresa_id: ctx.empresaId,
    }));
    return jsonResponse({ boletin_signed_url: url, regenerado: false });
  }

  // Logo de empresa (opcional) para la cabecera.
  const service = getServiceClient();
  const logoPng = ctx.logoUrl ? await fetchPng(service, "logos", ctx.logoUrl) : undefined;

  const pdfBytes = await generarPdfBoletin({
    ctx: ctx.boletin,
    emitidoEn: new Date().toISOString(),
    logoPng,
  });

  // El bucket `boletines` solo permite INSERT desde service_role.
  await uploadToBucket(service, BUCKET, path, pdfBytes, "application/pdf");

  const { error: updError } = await service
    .from("instalacion")
    .update({ boletin_url: path, boletin_generado_at: new Date().toISOString() })
    .eq("id", ctx.boletin.instalacion.id);

  if (updError) {
    throw makeError(
      "internal_error",
      "No se pudo registrar el boletín en la instalación",
      updError.message,
    );
  }

  const url = await createSignedUrl(supabase, BUCKET, path);

  console.log(JSON.stringify({
    level: "info",
    msg: "boletin_generado",
    instalacion_id: ctx.boletin.instalacion.id,
    empresa_id: ctx.empresaId,
    forzado: input.forzar,
  }));

  return jsonResponse({ boletin_signed_url: url, regenerado: true }, 201);
}));

// ----------------------------------------------------------------------------- helpers

interface ContextoBoletin {
  empresaId: string;
  boletinUrl: string | null;
  logoUrl: string | null;
  boletin: BoletinInstalacionContext;
}

async function loadContext(
  supabase: ReturnType<typeof getServiceClient>,
  instalacionId: string,
): Promise<ContextoBoletin> {
  const { data, error } = await supabase
    .from("instalacion")
    .select(
      `id, empresa_id, fecha_inicio, tasa_semanal, porcentaje_local,
       contador_entradas_base, contador_salidas_base, estado,
       boletin_url,
       maquina:maquina_id(numero_serie, modelo, fabricante, valor_credito),
       local:local_id(nombre, direccion, titular_nombre, cif_o_nif),
       licencia:licencia_id(numero),
       empresa:empresa_id(nombre, cif, direccion, zona_horaria, logo_url)`,
    )
    .eq("id", instalacionId)
    .maybeSingle();

  if (error) {
    throw makeError("internal_error", "Error consultando instalación", error.message);
  }
  if (!data) {
    throw makeError("not_found", "Instalación no encontrada o sin acceso");
  }

  const row = data as unknown as InstalacionJoinRow;
  if (!row.empresa) {
    throw makeError("internal_error", "Instalación sin empresa asociada");
  }
  if (!row.maquina || !row.local || !row.licencia) {
    throw makeError("internal_error", "Instalación con datos relacionados incompletos");
  }

  return {
    empresaId: row.empresa_id,
    boletinUrl: row.boletin_url,
    logoUrl: row.empresa.logo_url,
    boletin: {
      instalacion: {
        id: row.id,
        fecha_inicio: row.fecha_inicio,
        tasa_semanal: row.tasa_semanal,
        porcentaje_local: row.porcentaje_local,
        contador_entradas_base: Number(row.contador_entradas_base),
        contador_salidas_base: Number(row.contador_salidas_base),
        estado: row.estado,
      },
      empresa: {
        nombre: row.empresa.nombre,
        cif: row.empresa.cif,
        direccion: row.empresa.direccion,
        zona_horaria: row.empresa.zona_horaria,
      },
      local: {
        nombre: row.local.nombre,
        direccion: row.local.direccion,
        titular_nombre: row.local.titular_nombre,
        cif_o_nif: row.local.cif_o_nif,
      },
      maquina: {
        numero_serie: row.maquina.numero_serie,
        modelo: row.maquina.modelo,
        fabricante: row.maquina.fabricante,
        valor_credito: String(row.maquina.valor_credito),
      },
      licencia: {
        numero: row.licencia.numero,
      },
    },
  };
}

interface InstalacionJoinRow {
  id: string;
  empresa_id: string;
  fecha_inicio: string;
  tasa_semanal: string;
  porcentaje_local: string;
  contador_entradas_base: number | string;
  contador_salidas_base: number | string;
  estado: string;
  boletin_url: string | null;
  maquina: {
    numero_serie: string;
    modelo: string | null;
    fabricante: string | null;
    valor_credito: number | string;
  } | null;
  local: {
    nombre: string;
    direccion: string | null;
    titular_nombre: string | null;
    cif_o_nif: string | null;
  } | null;
  licencia: { numero: string } | null;
  empresa: {
    nombre: string;
    cif: string | null;
    direccion: string | null;
    zona_horaria: string;
    logo_url: string | null;
  } | null;
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
