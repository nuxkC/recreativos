# Import operador (fase 1): export de la app antigua → CSVs de mapeo + dataset limpio

**Fecha:** 2026-07-04 · **Estado:** aprobado (diseño validado con el usuario)

## Contexto y objetivo

En la raíz del repo hay un export de Firebase RTDB (`operador-373d1-default-rtdb-export.json`, 1,9 MB) de una app anterior más sencilla: bares (locales), máquinas, asociación local↔máquina y recaudaciones. Cuando se cambiaba una máquina, se desasociaba del bar y se asociaba otra; el export solo conserva la asociación **actual** (solo 38 de 2.914 recaudaciones traen `nombreLocal`; el resto no dice dónde se hizo).

Objetivo: portar esos datos a la estructura Recre como **una empresa nueva**. Los nombres de locales, números de serie y nombres/modelos de máquina del export son **inventados**: antes de importar hay que sustituirlos por los reales mediante CSVs de mapeo que rellena el usuario. Además, la oficina **sí tiene registro en papel de qué máquina estuvo en qué local y cuándo**: esos tramos entran también por CSV y permiten reconstruir el histórico fielmente.

**Fase 1 (esta spec):** generar los CSVs desde el export + script que, con los CSVs rellenos, produce un dataset limpio y validado en términos Recre. **No toca Supabase.**
**Fase 2 (spec aparte, futura):** importador que carga `dataset.json` en Supabase (local primero, luego cloud) vía service_role, como el seed.

## Fuente: qué hay en el export

| Colección | Volumen | Contenido relevante |
|---|---|---|
| `empresasOperadoras` | 2 | A001 "Automaticos EMI" (la real: 28 locales, 33 máquinas). A002 "Recreativos Santutxu": vestigial, sin locales → se descarta (listado en informe) |
| `locales` | 28 | nombre, dirección texto libre, contacto, `empresaResponsable`, máquina actual (por nº serie), deudas: `prestamos.prestamosPendientes`, `recuperacionTolva.tolvaPendiente` |
| `maquinas` | 35 | nº serie (clave), nombre comercial, contadores actuales (`entradas`/`salidas`, strings), `enUso` (26), tolva, `fechaUltimaRec` |
| `historialRecaudaciones` | 2.914 | por máquina y fecha (`dd-mm-yyyy` como clave): contadores `entradas`/`salidas`, denominaciones `moneticaTotal`/`moneticaLocalTotal`, `tasa`, `total`, `totalEmpresa`, `totalLocal`, a veces `nombreLocal` |
| `licencias` | 2 | una por empresa (`BH-1234`, `BH-5678`); en Recre la licencia va por máquina |

Distribución por año: 2002: 1 · 2020: 1 · 2022: 239 · 2023: 718 · 2024: 791 · 2025: 731 · 2026: 431. Las primeras recaudaciones válidas empiezan en **2020**.

Suciedad detectada: máquina " prueba" (con clave-fecha `00-00-000`), clave `98-6` en P1b1P, 1 registro `02-02-2002` (dedazo probable, contadores plausibles), 17 registros fechados 30/31-12-2026 (futuro) en 16 máquinas distintas con importes reales — patrón de apunte automático de la app vieja; decide el usuario. Variantes de campos: `Empresa`/`empresa`, `ID`/`id`, `moneticaLocal`/`moneticaLocalTotal`.

## Decisiones tomadas

1. **Histórico completo**, con el local de cada recaudación resuelto por los **tramos de instalación** que la oficina rellena en CSV (no por la asociación actual).
2. **Importes literales** del export: los totales/reparto históricos se conservan tal cual (el modelo de reparto viejo —tasa fija, `totalLocal` puede ser negativo— no es el de Recre). El modelo de Recre solo aplica a recaudaciones nuevas.
3. **Mapeo de todo lo identificativo**: nombres, series, modelos, fabricantes, direcciones, contactos, licencias. Columna `*_real` vacía = se importa el valor del export tal cual.
4. **Nada se descarta en silencio**: todo registro excluido queda listado en el informe o en `fechas_dudosas.csv` con decisión manual.

## Estructura

```
tools/import-operador/
  extraer_csvs.ts    # lee el export → genera csv/ con columnas *_real vacías + pistas
  aplicar_mapeo.ts   # lee export + csv/ rellenos → out/dataset.json + out/informe.md
  comun.ts           # parseo/normalización compartida (fechas, dinero-string, campos variantes)
  *.test.ts          # deno test junto al código (convención del repo)
  csv/               # los 6 CSV (versionados: son el trabajo manual del usuario)
  out/               # dataset + informe (gitignored: derivables)
```

Deno/TypeScript estricto, sin `any`, dinero siempre como string decimal (nunca float), `deno fmt`/`deno lint` — convenciones del repo.

## Los 6 CSVs

Separador `;` (Excel es-ES), UTF-8, cabecera en la primera fila. Columnas `*_real` vacías ⇒ se conserva el valor del export.

1. **`empresa.csv`** (1 fila, A001): `id_origen; nombre_inventado; direccion_inventada; nombre_real; direccion_real`
2. **`locales.csv`** (28): `id_origen; nombre_inventado; direccion_inventada; contacto_inventado; nombre_real; contacto_real; municipio_real; via_real; numero_real; cp_real` — dirección estructurada porque desde T-277 Recre no tiene dirección de texto libre; CCAA y provincia se derivan del municipio (INE).
3. **`maquinas.csv`** (35): `num_serie_origen; nombre_inventado; num_serie_real; fabricante_real; modelo_real; licencia_real; descartar` — `descartar` pre-marcado para " prueba". Fabricante/modelo alimentan el catálogo global.
4. **`instalaciones.csv`** (tramos de oficina): `num_serie_origen; local_origen; fecha_inicio; fecha_fin; pistas` — `fecha_fin` vacía = vigente. Pre-rellenado con una fila por máquina (local actual + rango de fechas de sus recaudaciones). La columna `pistas` es solo informativa (la ignora `aplicar_mapeo.ts`): trae el nº de recaudaciones de la máquina y las anclas conocidas (los registros con `nombreLocal`, p. ej. `2024-03-05→Gipuzkoa`). El usuario parte/añade filas según los registros de oficina.
5. **`deudas.csv`** (solo saldos ≠ 0 del export): `local_origen; tipo (tolva|prestamo); importe_export; importe_real`
6. **`fechas_dudosas.csv`** (~20 filas): `num_serie_origen; clave_original; entradas; salidas; total; fecha_real; descartar` — el `02-02-2002`, los 17 de 30/31-12-2026 y las 2 claves basura. `fecha_real` rellena ⇒ se conserva con esa fecha; `descartar` ⇒ fuera (constará en el informe).

## Reglas de limpieza y normalización (en `comun.ts`, con tests)

- **Ventana válida: [2020-01-01, hoy].** Lo de fuera no se pierde: va a `fechas_dudosas.csv`.
- Máquina " prueba" descartada entera (listada en informe).
- Unificación de variantes: `Empresa`→`empresa`, `ID`→`id`, `moneticaLocal`→`moneticaLocalTotal`.
- Denominaciones: claves del export → céntimos: `010`→10, `020`→20, `050`→50, `1`→100, `2`→200, `5`→500, `10`→1000, `20`→2000, `50`→5000.
- Dinero: strings decimales normalizadas a 2 decimales (half-up); contadores como enteros.
- Fechas `dd-mm-yyyy` → ISO `yyyy-mm-dd`.

## Validaciones (salen en `out/informe.md`)

- **Cobertura de tramos**: cada recaudación cae en exactamente un tramo de `instalaciones.csv`. Huecos, solapes y huérfanas listadas con máquina y fecha para corregir el CSV.
- Contadores no monótonos por máquina (advertencia, no bloqueo: pudo haber reset de contador).
- Totales por máquina, por local y por año, para cuadrar contra la app vieja.
- Recuento de descartes con motivo (nada silencioso).
- Coherencia de mapeos: series reales duplicadas, locales de `instalaciones.csv` que no existen en `locales.csv`, municipios no INE (cuando estén rellenos).

## Salida: `out/dataset.json`

Forma canónica en términos Recre, con importes literales y todos los campos originales conservados (los que Recre no use quedan bajo `origen`): `empresa`, `locales[]` (dirección estructurada), `catalogo[]` (fabricante/modelo), `maquinas[]` (serie real, contadores actuales), `instalaciones[]` (tramos con fechas), `recaudaciones[]` (instalación resuelta, contadores, denominaciones en céntimos, totales literales, `tasa` conservada como dato de origen), `deudas[]`. El mapeo fino a columnas/RPCs de Supabase se decide en la spec de fase 2.

## Criterios de éxito de la fase 1

1. `extraer_csvs.ts` genera los 6 CSVs con las pistas descritas sin editar el export.
2. Con los CSVs rellenos, `aplicar_mapeo.ts` produce `dataset.json` + `informe.md`; con CSVs sin rellenar también funciona (valores del export) para poder probar el pipeline ya.
3. El informe demuestra: 0 recaudaciones perdidas sin decisión explícita (suma = 2.914 − descartes documentados).
4. Tests Deno de: parseo de fechas/dinero, mapeo de denominaciones, resolución de tramos (hueco/solape/huérfana), unificación de variantes.

## Fuera de alcance (fase 2)

Importador a Supabase (empresa nueva, RPCs/service_role, orden de inserción, idempotencia, verificación post-carga contra el informe), elección local vs cloud, y el mapeo de `tasa` al modelo de reparto de Recre si algún día se quiere recalcular.
