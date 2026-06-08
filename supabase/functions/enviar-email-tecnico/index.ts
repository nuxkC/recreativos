/**
 * T-71 — Edge Function `enviar-email-tecnico`.
 *
 * Notifica por email al técnico cuando un administrador resuelve un
 * conflicto sobre una recaudación que él subió. La invoca
 * `resolver-conflicto` (T-26b) en modo fire-and-forget tras aplicar la
 * resolución; cualquier fallo aquí NO debe abortar la resolución del
 * conflicto.
 *
 * Implementación:
 *   1. Service-role client busca la recaudación + tecnico_id +
 *      datos de cabecera (local, máquina) para el cuerpo del email.
 *   2. `auth.admin.getUserById(tecnico_id)` resuelve el email del
 *      técnico (auth.users no es accesible vía PostgREST).
 *   3. POST a Resend (`RESEND_API_KEY` + `RESEND_FROM` env vars) con
 *      un body HTML mínimo en español.
 *
 * Modo dev / sin `RESEND_API_KEY`:
 *   - Se loguea la intención y se devuelve `skipped: true`. El sistema
 *     sigue funcional sin proveedor de email configurado, lo cual
 *     simplifica el desarrollo local con `supabase functions serve`.
 *
 * Errores conocidos (no abortan al caller):
 *   - `not_found`           — la recaudación no existe.
 *   - `tecnico_sin_email`   — el técnico no tiene email registrado.
 *   - `email_provider_failed` — Resend devolvió status no-2xx.
 *   - `email_skipped`       — `RESEND_API_KEY` no configurado.
 */

import { ZodError } from "zod";

import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { EnviarEmailTecnicoInputSchema } from "../_shared/schemas.ts";

interface RecaudacionEmailRow {
  id: string;
  empresa_id: string;
  tecnico_id: string;
  fecha: string;
  estado: string;
  resolucion: string | null;
  resolucion_notas: string | null;
  recaudacion_bruta: string | null;
  recaudacion_neta: string | null;
  parte_local: string | null;
  parte_empresa: string | null;
  instalacion: {
    maquina: { numero_serie: string; modelo: string | null } | null;
    local: { nombre: string | null } | null;
  } | null;
  empresa: { nombre: string | null } | null;
}

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
    input = EnviarEmailTecnicoInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const service = getServiceClient();

  const { data: rec, error: recError } = await service
    .from("recaudacion")
    .select(
      `id, empresa_id, tecnico_id, fecha, estado, resolucion,
       resolucion_notas, recaudacion_bruta, recaudacion_neta,
       parte_local, parte_empresa,
       instalacion:instalacion_id (
         maquina:maquina_id ( numero_serie, modelo ),
         local:local_id ( nombre )
       ),
       empresa:empresa_id ( nombre )`,
    )
    .eq("id", input.recaudacion_id)
    .maybeSingle<RecaudacionEmailRow>();

  if (recError) {
    throw makeError(
      "internal_error",
      "Error consultando recaudación",
      recError.message,
    );
  }
  if (!rec) {
    throw makeError("not_found", "Recaudación no encontrada");
  }

  // Resolución del email del técnico vía auth.admin (auth.users no es
  // visible para PostgREST).
  const { data: userData, error: userError } = await service.auth.admin
    .getUserById(rec.tecnico_id);
  if (userError || !userData?.user?.email) {
    return jsonResponse({
      ok: false,
      code: "tecnico_sin_email",
      message: "El técnico no tiene email registrado",
    });
  }

  const tecnicoEmail = userData.user.email;
  const apiKey = Deno.env.get("RESEND_API_KEY");
  const fromAddress = Deno.env.get("RESEND_FROM") ?? "Recre <noreply@recre.app>";

  if (!apiKey) {
    console.log(JSON.stringify({
      level: "info",
      msg: "email_skipped_no_resend_key",
      to: tecnicoEmail,
      recaudacion_id: rec.id,
      resolucion: rec.resolucion,
    }));
    return jsonResponse({ ok: true, skipped: true, code: "email_skipped" });
  }

  const subject = construirAsunto(rec);
  const html = construirHtml(rec);
  const text = construirTexto(rec);

  const resp = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: fromAddress,
      to: [tecnicoEmail],
      subject,
      html,
      text,
    }),
  });

  if (!resp.ok) {
    const detail = await resp.text();
    console.error(JSON.stringify({
      level: "error",
      msg: "email_provider_failed",
      status: resp.status,
      detail,
      recaudacion_id: rec.id,
    }));
    return jsonResponse({
      ok: false,
      code: "email_provider_failed",
      message: `Resend respondió ${resp.status}`,
    });
  }

  const result = await resp.json().catch(() => ({}));
  return jsonResponse({
    ok: true,
    sent_to: tecnicoEmail,
    provider_id: (result as { id?: string }).id,
  });
}));

/* ----------------------------------------------------------------- */
/* Construcción del email — HTML simple, paralelo con copy del back. */
/* ----------------------------------------------------------------- */

function construirAsunto(rec: RecaudacionEmailRow): string {
  const empresa = rec.empresa?.nombre ?? "Recre";
  const local = rec.instalacion?.local?.nombre ?? "una recaudación";
  return `[${empresa}] Resuelto el conflicto de ${local}`;
}

function construirHtml(rec: RecaudacionEmailRow): string {
  const local = rec.instalacion?.local?.nombre ?? "—";
  const maquina = rec.instalacion?.maquina?.numero_serie ?? "—";
  const fecha = formatearFecha(rec.fecha);
  const resolucion = formatearResolucion(rec.resolucion);
  const notas = (rec.resolucion_notas ?? "").trim();
  const cifras = formatearCifrasHtml(rec);

  return [
    `<p>Hola,</p>`,
    `<p>Se ha resuelto el conflicto de la recaudación que registraste el ` +
      `<strong>${escape(fecha)}</strong> en <strong>${escape(local)}</strong> ` +
      `(máquina ${escape(maquina)}).</p>`,
    `<p><strong>Resolución:</strong> ${escape(resolucion)}</p>`,
    cifras,
    notas
      ? `<p><strong>Notas del administrador:</strong><br>${escape(notas)}</p>`
      : "",
    `<p>Si tienes dudas con esta resolución, contacta con tu administrador.</p>`,
    `<p>— Recre</p>`,
  ].filter(Boolean).join("\n");
}

function construirTexto(rec: RecaudacionEmailRow): string {
  const local = rec.instalacion?.local?.nombre ?? "—";
  const maquina = rec.instalacion?.maquina?.numero_serie ?? "—";
  const fecha = formatearFecha(rec.fecha);
  const resolucion = formatearResolucion(rec.resolucion);
  const lineas = [
    `Hola,`,
    ``,
    `Se ha resuelto el conflicto de la recaudación que registraste el ${fecha} ` +
      `en ${local} (máquina ${maquina}).`,
    ``,
    `Resolución: ${resolucion}`,
  ];
  if (rec.resolucion_notas) {
    lineas.push(``, `Notas del administrador:`, rec.resolucion_notas);
  }
  lineas.push(``, `— Recre`);
  return lineas.join("\n");
}

function formatearResolucion(resolucion: string | null): string {
  switch (resolucion) {
    case "aceptada":
      return "Importes oficiales aceptados (los del cliente)";
    case "sustituida":
      return "Sustituidos por los importes recalculados por el servidor";
    case "anulada":
      return "Recaudación anulada";
    default:
      return "Resolución desconocida";
  }
}

function formatearCifrasHtml(rec: RecaudacionEmailRow): string {
  if (rec.estado === "anulada") {
    return `<p>La recaudación se ha marcado como <strong>anulada</strong> y no ` +
      `aporta importes a la liquidación.</p>`;
  }
  const filas = [
    ["Bruto", rec.recaudacion_bruta],
    ["Neto", rec.recaudacion_neta],
    ["Parte local", rec.parte_local],
    ["Parte empresa", rec.parte_empresa],
  ]
    .filter(([_, v]) => v !== null)
    .map(([k, v]) => `<tr><td>${escape(k as string)}</td><td>${escape(String(v))}</td></tr>`)
    .join("");
  if (!filas) return "";
  return `<table><tbody>${filas}</tbody></table>`;
}

function formatearFecha(iso: string): string {
  // El cliente de email puede ajustar zona horaria; mostramos la fecha tal
  // y como llega del backend (UTC) para evitar discrepancias.
  return iso.slice(0, 10);
}

/** Escapado mínimo para HTML — evita inyección al pintar campos del usuario. */
function escape(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
