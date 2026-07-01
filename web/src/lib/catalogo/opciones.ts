import type { ComboboxOption } from "@/components/common/combobox";

/**
 * Fila del catálogo global de fabricantes (tabla `public.fabricante`, sin
 * `empresa_id`). Los ids se usan solo en cliente para cablear la cascada
 * modelo⊂fabricante; nunca se envían al servidor (la RPC resuelve por nombre).
 */
export interface FabricanteOpcion {
  id: string;
  nombre: string;
}

/** Fila del catálogo global de modelos; `fabricanteId` = FK a su fabricante. */
export interface ModeloOpcion {
  id: string;
  nombre: string;
  fabricanteId: string;
}

/** Fabricantes como opciones de combobox: value = label = nombre canónico. */
export function opcionesFabricante(fabricantes: FabricanteOpcion[]): ComboboxOption[] {
  return fabricantes.map((f) => ({ value: f.nombre, label: f.nombre }));
}

/**
 * id del fabricante cuyo nombre coincide (comparación laxa: trim + minúsculas),
 * o `null` si el nombre está vacío o no existe en el catálogo (fabricante nuevo
 * tecleado por el usuario). Sirve para acotar la lista de modelos.
 */
export function idFabricantePorNombre(
  fabricantes: FabricanteOpcion[],
  nombre: string | null,
): string | null {
  if (!nombre) return null;
  const objetivo = nombre.trim().toLowerCase();
  if (objetivo.length === 0) return null;
  const encontrado = fabricantes.find((f) => f.nombre.trim().toLowerCase() === objetivo);
  return encontrado?.id ?? null;
}

/**
 * Modelos del fabricante seleccionado, como opciones de combobox. Si el
 * fabricante no está catalogado (es nuevo) o no hay ninguno, devuelve `[]`: el
 * usuario podrá teclear un modelo nuevo, que la RPC creará bajo ese fabricante.
 */
export function opcionesModelo(
  modelos: ModeloOpcion[],
  fabricantes: FabricanteOpcion[],
  fabricanteNombre: string | null,
): ComboboxOption[] {
  const fabId = idFabricantePorNombre(fabricantes, fabricanteNombre);
  if (!fabId) return [];
  return modelos
    .filter((m) => m.fabricanteId === fabId)
    .map((m) => ({ value: m.nombre, label: m.nombre }));
}
