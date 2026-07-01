# Diseño — Higiene de entrada de datos: validadores compartidos + catálogo global fabricante/modelo

- **Fecha:** 2026-06-29
- **Estado:** aprobado (pendiente de plan de implementación)
- **Ámbito:** `web/`, `android/`, `supabase/`
- **Origen:** auditoría de campos de formularios (web + Android + BBDD). De 26 campos catalogados, este diseño ataca dos frentes: (A) campos de **formato** sin validación y (B) los campos **fabricante/modelo**, hoy texto libre y principal fuente de duplicados.

## 1. Problema

Al dar de alta/editar datos, varios campos se teclean a mano cuando su conjunto de valores es conocido o tiene un formato fijo. Consecuencias hoy:

1. **Fabricante y modelo de máquina** son texto libre en web **y** Android, y `text` sin constraint en BBDD → variantes del mismo valor ("Cirsa" / "CIRSA" / "Cirsa SA") que ensucian informes y búsquedas.
2. **CIF/NIF y teléfono no se validan en ningún sitio** (ni web ni Android); se puede guardar basura. No existe ningún validador de CIF/NIF/teléfono en todo el repo.
3. El **email se valida con reglas distintas** en web (Zod `z.string().email()`) y Android (`EMAIL_REGEX` propio) → un email aceptado en un cliente puede rechazarse en el otro. Además el validador de email está duplicado literal entre `web/src/lib/locales/schemas.ts` y `web/src/lib/ajustes/schemas.ts`.

> Nota verificada: el orden de `ESTADOS_MAQUINA` difiere entre web (`almacen,instalada,…`) y Android (`instalada,almacen,…`). **No es un bug funcional** (nadie indexa por posición), solo deuda de SSOT. Queda **fuera de alcance** de esta spec (ver §9).

## 2. Decisiones tomadas

- **Catálogo global, no por empresa.** Fabricante y modelo son los mismos para todas las empresas (como las comunidades autónomas). Serán las **primeras tablas del repo sin `empresa_id`** → se rompe el invariante de CLAUDE.md a conciencia.
- **Gobernanza:** cualquiera con rol de gestión puede **añadir** sobre la marcha; la **curación** (renombrar/fusionar, que afecta a todos) la hace un administrador de catálogo, identificado por una **bandera `es_admin_catalogo`** nueva en `public.usuario`, sembrada a la cuenta owner (micro-decisión 1, aprobada).
- **Validación dura** (bloquea guardar) pero **condicional a campo no vacío**, igual que el email hoy (micro-decisión 2).
- **Transición sin romper lecturas:** las columnas de texto `maquina.fabricante`/`maquina.modelo` se conservan y la RPC **denormaliza** el nombre del catálogo en ellas; así las lecturas web y el sync Room de Android no cambian en este ciclo. El borrado del texto viejo y el endurecimiento de la FK a obligatoria quedan para un PR de cierre posterior.

## 3. Realidad de "compartir código" (condiciona la Parte A)

Hay **tres runtimes que no se importan entre sí**: web (TS), Edge Functions (Deno TS) y Android (Kotlin).
- La web **no** importa de `supabase/functions/_shared`.
- **No hay codegen** TS→Kotlin; Android reimplementa a mano (ya tiene `EMAIL_REGEX`, `esEmailValido`, `ESTADOS_*`).

Implicación: un validador en `_shared` (Deno) **no es reutilizable de verdad**. La estrategia real:
- **Casa canónica TS:** `web/src/lib/shared/validators.ts`, importado por los schemas Zod (cubre formulario + Server Action a la vez).
- **Android:** puerto manual a Kotlin en `GestionShared.kt`.
- **Edge:** **no se crea ahora** (no hay ningún campo CIF en las Edge Functions; sería código muerto). Si algún día se cablea CIF en `registrar-empresa`, se **copia** (no se importa) a `_shared/validators.ts`.
- **Contrato de paridad:** una tabla de **vectores oro** (CIF/NIF/NIE/teléfonos válidos e inválidos) replicada como tests en Vitest (web) y JUnit (Android). Mismos inputs → mismos booleanos.

## 4. Troceado en PRs (orden por dependencias)

Regla del repo: una rama → un PR (<400 líneas, squash & merge), Conventional Commits con `T-XX`.

| PR | Scope | Contenido | Depende de |
|----|-------|-----------|-----------|
| **A** | `web` + `android` | Validadores CIF/NIF + teléfono (+ unificar email) | — |
| **B1** | `supabase` | Tablas de catálogo + RLS + RPC alta/curación + helpers + bandera admin + pgTAP | — |
| **B2** | `supabase` (+ `web` mínimo) | FK en `maquina`, nuevas firmas RPC máquina, migración de datos, regen tipos | B1 |
| **B3** | `web` | UI: combobox con "crear" + cascada en formulario de máquina | B2 |
| **B4** | `android` | UI: autocomplete editable + cascada + repo de catálogo | B2 |

## 5. Parte A — Validadores de formato

**Qué se valida** (todo condicional a campo no vacío):
- **CIF/NIF/NIE español** con **dígito de control real**, no solo forma: NIF (8 díg + letra mod-23 `TRWAGMYFPDXBNJZSQVHLCKE`), NIE (X/Y/Z→0/1/2 + 7 díg + letra), CIF (letra inicial + 7 díg + dígito/letra de control). Normalizar a mayúsculas y sin espacios antes.
- **Teléfono España:** normalizar (quitar espacios y prefijo `+34`/`0034`) y validar `^[6-9]\d{8}$`.
- **Email:** ya hay motor en ambos clientes; **no se reescribe**, solo se **centraliza** en web (extraer el `emailOptional` duplicado al módulo compartido) y se deja `esEmailValido` en Android, alineando su regex a los vectores oro.

**Puntos de inyección**
- WEB (inyectar en el schema cubre form + Server Action):
  - **NUEVO** `web/src/lib/shared/validators.ts` — helpers puros (`esCifNif`, `esTelefonoEs` + normalizadores) y refinamientos Zod listos (`cifNifSchema`, `telefonoSchema`, `emailOptional`), con el patrón actual `trimmedString` + `.refine()`/`.pipe()`.
  - `web/src/lib/locales/schemas.ts` — `cifONif` (l.31) y `telefono` (l.35) pasan a usar los refinamientos compartidos; importar `emailOptional`.
  - `web/src/lib/ajustes/schemas.ts` — ídem `cif` (l.22) y `telefono` (l.24); borrar el `optionalEmail` duplicado.
  - `web/src/lib/maquinas/schemas.ts` — **no se toca** (no tiene estos campos).
  - **NUEVO** `web/src/lib/shared/validators.test.ts` (Vitest, vectores oro).
- ANDROID (validación dura que bloquea guardar; único formulario con estos campos = Locales):
  - `feature/gestion/GestionShared.kt` — añadir `esCifNif(raw): Boolean` y `esTelefono(raw): Boolean` (puerto Kotlin) junto a `esEmailValido`.
  - `feature/gestion/locales/LocalFormViewModel.kt` — en `guardar()` (junto a l.104): `if (cif.isNotEmpty() && !esCifNif(cif)) errores["cifONif"]="cif_invalido"` y equivalente para teléfono. Hoy esos campos solo se normalizan (l.114/116).
  - **NUEVO** test en el sourceSet `test` de Android con los **mismos** vectores oro.
- Mensajes de error: claves nuevas en `web/src/i18n/messages/es.json` y en los `strings` de Android.

## 6. Parte B — Catálogo global fabricante → modelo

### 6.1 Modelo de datos (PR-B1)
Migración nueva `<ts>_catalogo_global_fabricante_modelo.sql`:
- `public.fabricante`: `id uuid PK default gen_random_uuid()`, `nombre text NOT NULL`, `nombre_normalizado text GENERATED (lower(btrim(nombre)))`, `created_at`, `created_by uuid default auth.uid()`. `UNIQUE(nombre_normalizado)`.
- `public.modelo`: `id uuid PK`, `fabricante_id uuid NOT NULL REFERENCES public.fabricante(id) ON DELETE RESTRICT`, `nombre text NOT NULL`, `nombre_normalizado GENERATED`, `created_at`, `created_by`. `UNIQUE(fabricante_id, nombre_normalizado)`. **Cascada:** el modelo cuelga del fabricante.
- `nombre_normalizado = lower(btrim(nombre))` por defecto; `unaccent` **no** se usa (evita instalar extensión nueva).

### 6.2 Seguridad (PR-B1)
- **Lectura abierta:** `ENABLE ROW LEVEL SECURITY`; policy `SELECT USING (true)`; `GRANT SELECT ... TO authenticated` (precedente: GRANT explícito en `20260612120000`).
- **Escritura bloqueada salvo RPC:** sin policies `*_modify`; `REVOKE INSERT,UPDATE,DELETE ON fabricante,modelo FROM authenticated,anon` (patrón `20260611150000`).
- **Helper nuevo** `usuario_es_gestor_en_alguna_empresa()` = `EXISTS(SELECT 1 FROM empresa_usuario WHERE usuario_id=auth.uid() AND rol IN ('owner','admin','gestor'))` (sql STABLE SECURITY DEFINER, `SET search_path`).
- **Bandera admin de catálogo:** `ALTER TABLE public.usuario ADD COLUMN es_admin_catalogo boolean NOT NULL DEFAULT false`. Helper `usuario_es_admin_catalogo()` = `SELECT es_admin_catalogo FROM usuario WHERE id=auth.uid()`. Se siembra `true` a la cuenta owner (`a@a.es`) en `seed.sql` (y vía service_role en cloud).

### 6.3 RPC (PR-B1) — molde `crear_maquina` (plpgsql, SECURITY DEFINER, `SET search_path=public,pg_catalog`, REVOKE PUBLIC/anon + GRANT EXECUTE authenticated, RETURNING id)
- **Alta (idempotente, "busca-o-crea"):**
  - `crear_fabricante(p_nombre text) RETURNS uuid` — guarda con `usuario_es_gestor_en_alguna_empresa()`; normaliza; si existe por `nombre_normalizado` devuelve su id, si no inserta. Evita duplicados por tecleo concurrente.
  - `crear_modelo(p_fabricante_id uuid, p_nombre text) RETURNS uuid` — valida que el fabricante existe; busca-o-crea dentro del fabricante.
- **Curación (guarda `usuario_es_admin_catalogo()`):**
  - `renombrar_fabricante(p_id, p_nombre)`, `renombrar_modelo(p_id, p_nombre)`.
  - `fusionar_fabricante(p_origen, p_destino)` — `UPDATE modelo SET fabricante_id=destino`; re-dedup; `DELETE` origen.
  - `fusionar_modelo(p_origen, p_destino)` — `UPDATE maquina SET modelo_id=destino` y re-denormaliza el nombre; `DELETE` origen.

### 6.4 Enganche de la máquina (PR-B2)
Migración nueva `<ts>_maquina_fk_catalogo.sql`:
- `ALTER TABLE public.maquina ADD COLUMN fabricante_id uuid REFERENCES fabricante(id) ON DELETE RESTRICT, ADD COLUMN modelo_id uuid REFERENCES modelo(id) ON DELETE RESTRICT` (ambas **NULLABLE** en transición). Se **conservan** `modelo`/`fabricante` text.
- Coherencia `modelo.fabricante_id = p_fabricante_id` validada **en la RPC** (es cross-table; no por CHECK).
- **Firmas de `crear_maquina`/`actualizar_maquina` cambian** → `DROP FUNCTION` + `CREATE` en **migración nueva** (no editar la inmutable `20260611140000`); sustituir `p_modelo`/`p_fabricante text` por `p_modelo_id`/`p_fabricante_id uuid`; resto igual (guard `usuario_es_gestor(p_empresa_id)`, INSERT, RETURNING id, re-GRANT EXECUTE authenticated). Validan FK + coherencia y **denormalizan** el nombre del catálogo en las columnas text.
- Web mínimo en este PR: `web/src/lib/maquinas/actions.ts` (`parseMaquinaForm` + params `p_*_id`), `schemas.ts` (`modelo`/`fabricante` string → `fabricanteId`/`modeloId` uuid nullable con **`guid()`**, no `uuid()` — gotcha zod4), `types.ts`. `queries.ts` sin cambios gracias a la denormalización.
- `supabase gen types typescript` tras migrar (no editar tipos a mano).

### 6.5 Migración de datos existentes (PR-B2)
Datos reales en `seed.sql` (l.118-123): 4 fabricantes (Cirsa, Unidesa, R. Franco, MGA) y 6 modelos.
- **Gotcha de orden:** en `db reset`, `seed.sql` corre **después** de las migraciones → un backfill dentro de la migración no ve filas locales. Estrategia doble:
  - (a) **Bloque `DO` idempotente** en la migración: `INSERT fabricante SELECT DISTINCT btrim(fabricante) ... ON CONFLICT DO NOTHING`; ídem modelo por par; `UPDATE maquina SET fabricante_id/modelo_id` por match normalizado where id IS NULL → migra el **cloud ya desplegado** (`ztwcvxvrqndmhxvuahsc`), no-op en local fresco.
  - (b) **`seed.sql` actualizado:** insert directo de fabricante/modelo (superuser, sin RPC) + máquinas con `modelo_id`/`fabricante_id`, preservando los 6 pares y el texto por back-compat.

### 6.6 UI Web (PR-B3) — patrón Server Component → props (sin react-query/api.ts)
- `web/src/components/common/combobox.tsx` (ya es cmdk `Command` + `Popover`, filtra por label, **sin uso** hoy): añadir prop `onCreate?(text)` y un `CommandItem` "Crear «{query}»" en `CommandEmpty`/al pie.
- `web/src/components/maquinas/maquina-form.tsx`: los 2 `<Input>` de texto (modelo l.216-228, fabricante l.229-241) → 2 Combobox en **cascada** (al cambiar fabricante, reset modelo). Actualizar `buildFormData`.
- `web/src/app/(dashboard)/maquinas/nueva/page.tsx` y `.../[id]/page.tsx`: hoy renderizan `<MaquinaForm>` sin props; añadir `Promise.all` de `listarFabricantes()`/`listarModelos()` (patrón de `instalaciones/nueva/page.tsx`) y pasarlos como props.
- **NUEVO** `web/src/lib/catalogo/queries.ts` (`listarFabricantes`/`listarModelos`, globales sin `empresaId`) y **NUEVO** `web/src/lib/catalogo/actions.ts` (`'use server'`: `crearFabricante`/`crearModelo` → `requireRol(ROLES_GESTION)` → `supabase.rpc(...)` → `revalidatePath`; molde `maquinas/actions.ts`). Opcionalmente `renombrar_*`/`fusionar_*` para la pantalla de curación (puede ir en PR aparte).
- `web/src/i18n/messages/es.json`: label "Crear «...»" + errores cif/teléfono.

### 6.7 UI Android (PR-B4) — no hay combo editable hoy; se construye
- **NUEVO** combo editable: `ExposedDropdownMenuBox` con `MenuAnchorType.PrimaryEditable` (`Field.kt` l.350 hoy es `PrimaryNotEditable`) + filtro tipo `SearchField` sobre `List<FkOption>` + item "Crear nuevo «texto»". Envolver en `GestionAutocomplete` en `feature/gestion/components/FormFields.kt` (junto a `GestionDropdown`).
- `feature/gestion/maquinas/MaquinaFormScreen.kt` (l.191-200): los 2 `GestionTextField` (fabricante/modelo) → 2 `GestionAutocomplete` en cascada.
- `MaquinaFormViewModel`: exponer `fabricantesDisponibles`/`modelosDisponibles: List<FkOption>` (promover `FkOption` desde `InstalacionFormViewModel.kt` a común), cascada por `fabricanteId`; `onCrearFabricante`/`onCrearModelo` → repositorio → RPC.
- **Datos del catálogo:** el catálogo es global → **no** entra en `TABLAS_SYNC` (que filtra por `empresa_id`). Fetch **on-demand** al abrir el formulario (catálogo pequeño). Realtime de catálogo queda fuera (añadiría complejidad a `RealtimeManager`).
- **NUEVO** repositorio de catálogo que llama `rpc crear_fabricante/crear_modelo` (escritura solo vía RPC).

## 7. Pruebas
- **pgTAP (obligatorio, si no fallan los guardarraíles existentes):**
  - `supabase/tests/sql/07_lockdown_escritura_global.sql` — añadir `fabricante,modelo` a `_tablas_dominio` (assert SELECT sí / write directo no).
  - `supabase/tests/sql/08_lockdown_rpc_grants.sql` — firmas nuevas: `crear_fabricante`/`crear_modelo`/`renombrar_*`/`fusionar_*` y las nuevas firmas de `crear_maquina`/`actualizar_maquina` con `p_*_id`.
  - **NUEVO** `supabase/tests/sql/NN_catalogo_global.sql` — cascada, alta idempotente con de-dup, `UNIQUE` normalizado, fusionar reapunta máquinas.
- **Web:** Vitest sobre `validators.test.ts` (vectores oro).
- **Android:** JUnit con los mismos vectores oro; nombres de test en ASCII o `-Dfile.encoding=UTF-8` (gotcha de locale). Validar deps con `assembleDebug` y `JAVA_HOME=/snap/android-studio/current/jbr`.

## 8. Riesgos asumidos
- **Primera tabla sin `empresa_id`** del repo → rompe el invariante documentado, a conciencia.
- **Visibilidad cross-tenant:** con `SELECT USING(true)`, toda empresa ve los nombres de fabricante/modelo de las demás. Mitigación: solo nombres, nada sensible.
- **Polución por alta libre** (typos, near-dups). Mitigación: `nombre_normalizado` + alta idempotente; las variantes semánticas se arreglan con **fusionar** (curación).
- **Tres copias del validador** sin import común → control único = vectores oro en ambos test suites.
- **Denormalización del nombre en `maquina.text`:** renombrar/fusionar **debe** re-denormalizar (`UPDATE maquina`) o el nombre se desincroniza.

## 9. Fuera de alcance (siguientes tandas, mismo patrón)
- **Comunidad autónoma** y **tipo de licencia** → desplegables cerrados sobre constante global (el ejemplo original del usuario).
- **Dirección estructurada** (CCAA → provincia → municipio → CP) → proyecto aparte; hoy `direccion` es un único campo libre.
- **Centralizar enums ya cerrados** (`ESTADOS_*`, `CATEGORIAS_AVERIA`…) en un único sitio y arreglar la divergencia de orden web/Android.
- **PR de cierre del catálogo:** hacer la FK obligatoria + borrar columnas text `maquina.modelo/fabricante` (tras backfill completo).
- **Pantalla de curación** (renombrar/fusionar) en web, si no se incluye en PR-B3.

## 10. Ficheros a tocar (resumen)

> La asignación PR la manda la tabla de §4. En "Parte B (web)", `maquinas/{schemas,actions,types}.ts` son el lado web de **PR-B2** (enganche FK); el resto del bloque es **PR-B3** (UI).
**Parte A:** `web/src/lib/shared/validators.ts` (NUEVO), `web/src/lib/shared/validators.test.ts` (NUEVO), `web/src/lib/locales/schemas.ts`, `web/src/lib/ajustes/schemas.ts`, `android/.../feature/gestion/GestionShared.kt`, `android/.../feature/gestion/locales/LocalFormViewModel.kt`, test Android (NUEVO), `web/src/i18n/messages/es.json`.

**Parte B (BBDD):** `supabase/migrations/<ts>_catalogo_global_fabricante_modelo.sql` (NUEVO), `supabase/migrations/<ts>_maquina_fk_catalogo.sql` (NUEVO), `supabase/seed.sql`, `supabase/tests/sql/07_lockdown_escritura_global.sql`, `supabase/tests/sql/08_lockdown_rpc_grants.sql`, `supabase/tests/sql/NN_catalogo_global.sql` (NUEVO).

**Parte B (web):** `web/src/components/common/combobox.tsx`, `web/src/components/maquinas/maquina-form.tsx`, `web/src/app/(dashboard)/maquinas/nueva/page.tsx`, `web/src/app/(dashboard)/maquinas/[id]/page.tsx`, `web/src/lib/catalogo/queries.ts` (NUEVO), `web/src/lib/catalogo/actions.ts` (NUEVO), `web/src/lib/maquinas/{schemas,actions,types,queries}.ts`.

**Parte B (Android):** `android/.../ui/components/Field.kt`, `android/.../feature/gestion/components/FormFields.kt`, `android/.../feature/gestion/maquinas/MaquinaFormScreen.kt`, `android/.../feature/gestion/maquinas/MaquinaFormViewModel.kt`, `android/.../feature/gestion/instalaciones/InstalacionFormViewModel.kt` (promover `FkOption`), repositorio de catálogo (NUEVO).
