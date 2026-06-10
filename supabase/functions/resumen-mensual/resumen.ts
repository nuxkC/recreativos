/**
 * Lógica pura del resumen mensual (T-102).
 *
 * Sin acceso a red ni BBDD: resolución del mes objetivo, agregación
 * monetaria con `decimal.js` (NUNCA float) y construcción del email en
 * español (HTML + texto). Aislada en este módulo para poder testearla
 * (`resumen.test.ts`).
 */

import { Decimal } from "decimal.js";

/** Fila agregada por (empresa, local, máquina, mes) del view SQL. */
export interface MaquinaMesRow {
  empresa_id: string;
  local_id: string;
  maquina_id: string;
  num_recaudaciones: number;
  parte_local_total: string;
  neto_total: string;
}

/** Datos de máquina para enriquecer el resumen. */
export interface MaquinaInfo {
  numero_serie: string;
  modelo: string | null;
}

/** Línea por máquina dentro del resumen de un local. */
export interface MaquinaResumen {
  numeroSerie: string;
  modelo: string | null;
  numRecaudaciones: number;
  parteLocal: string;
}

/** Resumen completo de un local para un mes. */
export interface ResumenLocal {
  empresaNombre: string;
  localNombre: string;
  titularNombre: string | null;
  mesEtiqueta: string;
  numRecaudaciones: number;
  parteLocalTotal: string;
  netoTotal: string;
  maquinas: MaquinaResumen[];
}

const NOMBRES_MES = [
  "enero",
  "febrero",
  "marzo",
  "abril",
  "mayo",
  "junio",
  "julio",
  "agosto",
  "septiembre",
  "octubre",
  "noviembre",
  "diciembre",
] as const;

/**
 * Resuelve el mes objetivo.
 *
 * - Si `mesInput` viene (formato `YYYY-MM`), se usa tal cual.
 * - Si no, se calcula el MES ANTERIOR a `now` en la zona horaria `tz`.
 *
 * Devuelve:
 *   - `mes`: `YYYY-MM`.
 *   - `mesLocalStart`: `YYYY-MM-01 00:00:00`, valor con el que filtrar la
 *     columna `mes_local` (timestamp local) del view.
 *   - `etiqueta`: "abril de 2026".
 */
export function resolverMes(
  now: Date,
  tz: string,
  mesInput?: string,
): { mes: string; mesLocalStart: string; etiqueta: string } {
  let year: number;
  let month: number; // 1..12

  if (mesInput) {
    const [y, m] = mesInput.split("-");
    year = Number(y);
    month = Number(m);
  } else {
    const partes = obtenerAnioMesEnTz(now, tz);
    year = partes.year;
    month = partes.month - 1; // mes anterior
    if (month === 0) {
      month = 12;
      year -= 1;
    }
  }

  const mm = String(month).padStart(2, "0");
  return {
    mes: `${year}-${mm}`,
    mesLocalStart: `${year}-${mm}-01 00:00:00`,
    etiqueta: `${NOMBRES_MES[month - 1]} de ${year}`,
  };
}

/** Extrae año y mes (1..12) de una fecha en una zona horaria concreta. */
function obtenerAnioMesEnTz(date: Date, tz: string): { year: number; month: number } {
  const fmt = new Intl.DateTimeFormat("en-CA", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
  });
  const partes = fmt.formatToParts(date);
  const year = Number(partes.find((p) => p.type === "year")?.value);
  const month = Number(partes.find((p) => p.type === "month")?.value);
  return { year, month };
}

/**
 * Agrupa las filas por máquina en un resumen de local, sumando importes con
 * `decimal.js`. Las filas ya vienen filtradas para un único (empresa, local,
 * mes). `maquinaInfoPorId` aporta nº de serie y modelo.
 */
export function construirResumenLocal(params: {
  empresaNombre: string;
  localNombre: string;
  titularNombre: string | null;
  mesEtiqueta: string;
  filas: readonly MaquinaMesRow[];
  maquinaInfoPorId: Readonly<Record<string, MaquinaInfo>>;
}): ResumenLocal {
  const maquinas: MaquinaResumen[] = params.filas
    .map((fila) => {
      const info = params.maquinaInfoPorId[fila.maquina_id];
      return {
        numeroSerie: info?.numero_serie ?? fila.maquina_id,
        modelo: info?.modelo ?? null,
        numRecaudaciones: fila.num_recaudaciones,
        parteLocal: new Decimal(fila.parte_local_total).toFixed(2),
      };
    })
    .sort((a, b) => a.numeroSerie.localeCompare(b.numeroSerie, "es"));

  const parteLocalTotal = params.filas.reduce(
    (acc, f) => acc.plus(new Decimal(f.parte_local_total)),
    new Decimal(0),
  );
  const netoTotal = params.filas.reduce(
    (acc, f) => acc.plus(new Decimal(f.neto_total)),
    new Decimal(0),
  );
  const numRecaudaciones = params.filas.reduce((acc, f) => acc + f.num_recaudaciones, 0);

  return {
    empresaNombre: params.empresaNombre,
    localNombre: params.localNombre,
    titularNombre: params.titularNombre,
    mesEtiqueta: params.mesEtiqueta,
    numRecaudaciones,
    parteLocalTotal: parteLocalTotal.toFixed(2),
    netoTotal: netoTotal.toFixed(2),
    maquinas,
  };
}

/**
 * Formatea un importe (string decimal) a formato es-ES: `1.234,56 €`.
 * Trabaja sobre el string decimal para NO pasar por float.
 */
export function formatEuros(value: string): string {
  const fixed = new Decimal(value).toFixed(2);
  const negativo = fixed.startsWith("-");
  const abs = negativo ? fixed.slice(1) : fixed;
  const punto = abs.indexOf(".");
  const entero = abs.slice(0, punto);
  const decimales = abs.slice(punto + 1);
  const enteroAgrupado = entero.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${negativo ? "-" : ""}${enteroAgrupado},${decimales} €`;
}

/** Asunto del email. */
export function construirAsuntoResumen(resumen: ResumenLocal): string {
  return `[${resumen.empresaNombre}] Resumen de ${resumen.mesEtiqueta} — ${resumen.localNombre}`;
}

/** Cuerpo HTML del email. */
export function construirHtmlResumen(resumen: ResumenLocal): string {
  const saludo = resumen.titularNombre ? `Hola ${escapeHtml(resumen.titularNombre)},` : "Hola,";

  const filasMaquina = resumen.maquinas
    .map((m) => {
      const nombre = m.modelo
        ? `${escapeHtml(m.numeroSerie)} (${escapeHtml(m.modelo)})`
        : escapeHtml(m.numeroSerie);
      return `<tr><td>${nombre}</td><td style="text-align:right">${m.numRecaudaciones}` +
        `</td><td style="text-align:right">${escapeHtml(formatEuros(m.parteLocal))}</td></tr>`;
    })
    .join("");

  return [
    `<p>${saludo}</p>`,
    `<p>Este es el resumen de recaudación de <strong>${escapeHtml(resumen.localNombre)}` +
    `</strong> correspondiente a <strong>${escapeHtml(resumen.mesEtiqueta)}</strong>.</p>`,
    `<p><strong>Total a liquidar (parte del local):</strong> ` +
    `${escapeHtml(formatEuros(resumen.parteLocalTotal))}<br>` +
    `<strong>Nº de recaudaciones:</strong> ${resumen.numRecaudaciones}</p>`,
    `<table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse">` +
    `<thead><tr><th>Máquina</th><th>Recaudaciones</th><th>Parte local</th></tr></thead>` +
    `<tbody>${filasMaquina}</tbody></table>`,
    `<p>Importes calculados por el sistema. Ante cualquier duda, contacta con ` +
    `${escapeHtml(resumen.empresaNombre)}.</p>`,
    `<p>— ${escapeHtml(resumen.empresaNombre)}</p>`,
  ].join("\n");
}

/** Cuerpo en texto plano del email. */
export function construirTextoResumen(resumen: ResumenLocal): string {
  const lineas: string[] = [
    resumen.titularNombre ? `Hola ${resumen.titularNombre},` : "Hola,",
    "",
    `Resumen de recaudación de ${resumen.localNombre} — ${resumen.mesEtiqueta}.`,
    "",
    `Total a liquidar (parte del local): ${formatEuros(resumen.parteLocalTotal)}`,
    `Nº de recaudaciones: ${resumen.numRecaudaciones}`,
    "",
    "Detalle por máquina:",
  ];
  for (const m of resumen.maquinas) {
    const nombre = m.modelo ? `${m.numeroSerie} (${m.modelo})` : m.numeroSerie;
    lineas.push(
      `  - ${nombre}: ${m.numRecaudaciones} recaudaciones, ${formatEuros(m.parteLocal)}`,
    );
  }
  lineas.push("", `— ${resumen.empresaNombre}`);
  return lineas.join("\n");
}

/** Escapado mínimo de HTML para campos provenientes de datos del usuario. */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
