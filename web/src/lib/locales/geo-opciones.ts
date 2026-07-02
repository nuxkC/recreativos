import type { ComboboxOption } from "@/components/common/combobox";

/**
 * Opciones de la cascada de dirección estructurada CCAA→provincia→municipio.
 *
 * A diferencia del catálogo de fabricante/modelo (listas abiertas, value =
 * nombre), provincia y municipio son listas CERRADAS oficiales (INE): el
 * `value` del combobox es el CÓDIGO INE (lo que persiste `local`) y el `label`
 * el nombre visible. La CCAA sí es texto (la "lista de oro" de 19).
 */

/** Provincia de referencia; `comunidadAutonoma` en forma de lista de oro. */
export interface ProvinciaOpcion {
  codigo: string;
  nombre: string;
  comunidadAutonoma: string;
}

/** Municipio de referencia; `provinciaCodigo` = FK a su provincia. */
export interface MunicipioOpcion {
  codigo: string;
  nombre: string;
  provinciaCodigo: string;
}

/**
 * Provincias de una comunidad autónoma, como opciones de combobox
 * (value = código INE, label = nombre). Lista vacía si no hay CCAA elegida.
 */
export function opcionesProvincia(
  provincias: ProvinciaOpcion[],
  comunidadAutonoma: string | null,
): ComboboxOption[] {
  if (!comunidadAutonoma) return [];
  return provincias
    .filter((p) => p.comunidadAutonoma === comunidadAutonoma)
    .map((p) => ({ value: p.codigo, label: p.nombre }));
}

/**
 * Municipios de una provincia, como opciones de combobox
 * (value = código INE, label = nombre). Lista vacía si no hay provincia elegida.
 */
export function opcionesMunicipio(
  municipios: MunicipioOpcion[],
  provinciaCodigo: string | null,
): ComboboxOption[] {
  if (!provinciaCodigo) return [];
  return municipios
    .filter((m) => m.provinciaCodigo === provinciaCodigo)
    .map((m) => ({ value: m.codigo, label: m.nombre }));
}
