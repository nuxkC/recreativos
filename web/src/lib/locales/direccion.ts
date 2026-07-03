import type { Local } from "./types";

/** Nombres resueltos de provincia/municipio desde su código INE (opcionales). */
export interface NombresGeo {
  provincia?: string | null;
  municipio?: string | null;
}

/** ¿El local tiene algún dato de dirección estructurada (T-277)? */
export function tieneDireccionEstructurada(local: Local): boolean {
  return Boolean(
    local.calle ||
    local.codigoPostal ||
    local.municipioCodigo ||
    local.provinciaCodigo ||
    local.comunidadAutonoma,
  );
}

/**
 * Dirección de un local para mostrar en SOLO-LECTURA. Prioriza la dirección
 * estructurada cuando existe y cae al `direccion` de texto libre (transición)
 * si no. Degrada con elegancia: si se pasan los nombres resueltos de
 * provincia/municipio muestra "calle, CP municipio, provincia"; sin ellos usa la
 * comunidad autónoma (que ya es texto) → "calle, CP, comunidad". Devuelve null
 * si no hay nada que mostrar.
 */
export function formatearDireccion(local: Local, nombres?: NombresGeo): string | null {
  const partes: string[] = [];
  if (local.calle) partes.push(local.calle);

  const cpMunicipio = [local.codigoPostal, nombres?.municipio].filter(Boolean).join(" ").trim();
  if (cpMunicipio) partes.push(cpMunicipio);

  // Región: nombre de provincia si se resolvió, si no la CCAA (texto directo).
  // Se omite si coincide con el municipio para no repetir "Madrid, Madrid".
  const region = nombres?.provincia ?? local.comunidadAutonoma;
  if (region && region !== nombres?.municipio) partes.push(region);

  if (partes.length > 0) return partes.join(", ");
  return local.direccion ?? null;
}
