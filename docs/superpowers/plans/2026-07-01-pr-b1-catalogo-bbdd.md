# PR-B1 — Catálogo global fabricante/modelo en BBDD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crear el catálogo **global** (sin `empresa_id`) `fabricante`/`modelo` con RLS de lectura abierta, escritura solo por RPC de alta idempotente, y guardarraíles pgTAP — la base sobre la que B2 engancha la máquina.

**Architecture:** Dos tablas globales con RLS `SELECT USING(true)` + `GRANT SELECT TO authenticated` (no anon) y escritura revocada; alta vía RPC `SECURITY DEFINER` idempotente (busca-o-crea por nombre normalizado); un helper de permiso `usuario_es_gestor_en_alguna_empresa()`. La **curación** (renombrar/fusionar) y la **bandera `es_admin_catalogo`** se difieren a B2, porque necesitan que `maquina` ya referencie el catálogo (para reflow del nombre denormalizado).

**Tech Stack:** PostgreSQL (Supabase), plpgsql, pgTAP. Sin cambios de cliente.

## Global Constraints

- **Migraciones aditivas e inmutables.** Formato `YYYYMMDDHHMMSS_descripcion.sql`, generado con `supabase migration new`. Una migración aplicada jamás se edita.
- **SSOT / escritura solo por función:** los clientes nunca escriben tablas directas; `authenticated` solo `SELECT`; toda escritura vía RPC `SECURITY DEFINER`. Directos revocados.
- **anon NO lee el catálogo:** `GRANT SELECT ... TO authenticated` (no `anon`). RLS habilitada con policy `SELECT USING (true)`.
- **Idioma:** comentarios en español; identificadores en inglés salvo términos de dominio (`fabricante`, `modelo`, `empresa_usuario`, `usuario`).
- **pgTAP** en `supabase/tests/sql/`: `BEGIN`…`ROLLBACK`, `SELECT plan(N)` con N exacto, sin depender de `seed.sql`. `auth.uid()` se usa sin schema.
- **Entorno de test:** requiere el stack local (Docker). Comandos: `supabase db reset` (aplica migraciones) + `supabase test db` (corre pgTAP). Si el CLI no está en PATH, usar `npx supabase …`. `auth.uid()`/rol se simulan con `SET LOCAL ROLE authenticated` + `SET LOCAL request.jwt.claims`.
- **Commits:** `<tipo>(supabase): <descripción> (T-268)`. Asigna `T-268` (siguiente libre tras T-267) y úsalo en todos los commits de esta rama; regístralo en `.kiro/specs/recre/tasks.md`.
- **Fuera de alcance (B2+):** FK en `maquina`, migración de datos, `crear/actualizar_maquina`, curación (`renombrar_*`/`fusionar_*`), bandera `es_admin_catalogo`, `supabase gen types typescript` (no hay consumidor cliente del catálogo todavía).

## File Structure

| Fichero | Responsabilidad | Acción |
|---------|-----------------|--------|
| `supabase/migrations/<ts>_catalogo_global_fabricante_modelo.sql` | Tablas + RLS + grants + helper + RPCs de alta | Crear (via `supabase migration new`) |
| `supabase/tests/sql/19_catalogo_global.sql` | Test funcional: alta idempotente, dedup normalizado, cascada, guard de permiso | Crear |
| `supabase/tests/sql/07_lockdown_escritura_global.sql` | Añadir `fabricante`/`modelo` a `_tablas_dominio`; `plan(140)`→`plan(154)` | Modificar |
| `supabase/tests/sql/08_lockdown_rpc_grants.sql` | Añadir firmas `crear_fabricante`/`crear_modelo`; `plan(56)`→`plan(60)` | Modificar |

---

### Task 1: Migración del catálogo + test funcional pgTAP (TDD)

**Files:**
- Create: `supabase/migrations/<ts>_catalogo_global_fabricante_modelo.sql`
- Test: `supabase/tests/sql/19_catalogo_global.sql`

**Interfaces:**
- Produces (SQL): tablas `public.fabricante(id, nombre, nombre_normalizado, created_at, created_by)` y `public.modelo(id, fabricante_id, nombre, nombre_normalizado, created_at, created_by)`; helper `public.usuario_es_gestor_en_alguna_empresa() → boolean`; RPCs `public.crear_fabricante(text) → uuid` y `public.crear_modelo(uuid, text) → uuid`.

- [ ] **Step 1: Crear rama**

Run (desde la raíz):
```bash
git checkout -b feat/supabase-catalogo-global
```

- [ ] **Step 2: Escribir el test funcional (fallará)**

Create `supabase/tests/sql/19_catalogo_global.sql`:
```sql
-- =============================================================================
-- T-268 — Catálogo GLOBAL fabricante/modelo: alta idempotente, dedup por nombre
-- normalizado, cascada modelo⊂fabricante, y guard de permiso (solo gestor).
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(9);

-- ---- fixtures: un gestor (owner) y un no-gestor (tecnico) en una empresa
INSERT INTO auth.users (id) VALUES
    ('f1000000-0000-0000-0000-0000000000a1'),   -- gestor
    ('f1000000-0000-0000-0000-0000000000a2');   -- tecnico (no gestor)
INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('f1000000-0000-0000-0000-0000000000a1', 'Gestor'),
    ('f1000000-0000-0000-0000-0000000000a2', 'Tecnico');
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('f1000000-0000-0000-0000-000000000001', 'Test Catalogo', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('f1000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-0000000000a1', 'owner',   true),
    ('f1000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-0000000000a2', 'tecnico', true);

-- ---- estructura
SELECT has_table('public', 'fabricante', 'existe la tabla fabricante');
SELECT has_table('public', 'modelo', 'existe la tabla modelo');

-- ---- actuar como cliente autenticado
SET LOCAL ROLE authenticated;

-- guard: un no-gestor NO puede dar de alta
SET LOCAL request.jwt.claims = '{"sub":"f1000000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT throws_ok(
    $$ SELECT public.crear_fabricante('Prohibido') $$,
    '42501',
    NULL,
    'un no-gestor no puede crear fabricante (42501)'
);
SELECT throws_ok(
    $$ SELECT public.crear_modelo('00000000-0000-0000-0000-000000000000'::uuid, 'X') $$,
    '42501',
    NULL,
    'un no-gestor no puede crear modelo (42501)'
);

-- gestor: alta idempotente + dedup por nombre normalizado
SET LOCAL request.jwt.claims = '{"sub":"f1000000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT is(
    public.crear_fabricante('Cirsa'),
    public.crear_fabricante('CIRSA'),
    'crear_fabricante es idempotente y deduplica por nombre normalizado'
);
SELECT is(
    public.crear_modelo(
        (SELECT id FROM public.fabricante WHERE nombre_normalizado = 'cirsa'),
        'Super Bar'
    ),
    public.crear_modelo(
        (SELECT id FROM public.fabricante WHERE nombre_normalizado = 'cirsa'),
        'super bar'
    ),
    'crear_modelo deduplica por (fabricante, nombre normalizado)'
);
SELECT throws_ok(
    $$ SELECT public.crear_modelo('00000000-0000-0000-0000-000000000000'::uuid, 'X') $$,
    '23503',
    NULL,
    'crear_modelo con fabricante inexistente falla (23503)'
);

-- anon NO puede leer el catálogo (permiso revocado)
RESET ROLE;
SET LOCAL ROLE anon;
SELECT throws_ok(
    $$ SELECT count(*) FROM public.fabricante $$,
    '42501',
    NULL,
    'anon NO puede leer el catalogo (permiso denegado)'
);
SELECT throws_ok(
    $$ SELECT count(*) FROM public.modelo $$,
    '42501',
    NULL,
    'anon NO puede leer modelo (permiso denegado)'
);

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
```

- [ ] **Step 3: Ejecutar y verificar que falla**

Run (stack local levantado; usar `npx supabase` si el CLI no está en PATH):
```bash
supabase db reset
supabase test db
```
Expected: FAIL — el test `19_catalogo_global.sql` falla (`has_table('public','fabricante')` da falso / la función `crear_fabricante` no existe). El resto de tests siguen verdes.

- [ ] **Step 4: Crear el fichero de migración**

Run:
```bash
supabase migration new catalogo_global_fabricante_modelo
```
Esto crea `supabase/migrations/<ts>_catalogo_global_fabricante_modelo.sql` vacío.

- [ ] **Step 5: Escribir la migración**

Contenido completo de ese fichero:
```sql
-- =============================================================================
-- Catálogo GLOBAL de fabricante y modelo de máquina.
--
-- PRIMER catálogo del repo SIN empresa_id: fabricante/modelo son los mismos
-- para todas las empresas (como las comunidades autónomas). Rompe a conciencia
-- el invariante "toda tabla lleva empresa_id" de CLAUDE.md.
--
-- Lectura: abierta a cualquier usuario autenticado (RLS SELECT USING(true) +
-- GRANT SELECT TO authenticated; NO a anon).
-- Escritura: solo vía RPC SECURITY DEFINER (alta idempotente); INSERT/UPDATE/
-- DELETE directos revocados. La curación (renombrar/fusionar) llega en B2,
-- cuando la máquina ya referencia el catálogo.
-- =============================================================================

-- ---------------------------------------------------------------- tablas

CREATE TABLE IF NOT EXISTS public.fabricante (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre              text        NOT NULL CHECK (btrim(nombre) <> ''),
    nombre_normalizado  text        GENERATED ALWAYS AS (lower(btrim(nombre))) STORED,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid        DEFAULT auth.uid(),
    UNIQUE (nombre_normalizado)
);

COMMENT ON TABLE public.fabricante IS
    'Catálogo GLOBAL de fabricantes de máquina (sin empresa_id). Alta vía RPC crear_fabricante.';

CREATE TABLE IF NOT EXISTS public.modelo (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    fabricante_id       uuid        NOT NULL REFERENCES public.fabricante(id) ON DELETE RESTRICT,
    nombre              text        NOT NULL CHECK (btrim(nombre) <> ''),
    nombre_normalizado  text        GENERATED ALWAYS AS (lower(btrim(nombre))) STORED,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid        DEFAULT auth.uid(),
    UNIQUE (fabricante_id, nombre_normalizado)
);

COMMENT ON TABLE public.modelo IS
    'Catálogo GLOBAL de modelos, colgando de fabricante (cascada). Alta vía RPC crear_modelo.';

CREATE INDEX IF NOT EXISTS modelo_fabricante_id_idx ON public.modelo (fabricante_id);

-- ---------------------------------------------------------------- RLS: lectura global, escritura solo por RPC

ALTER TABLE public.fabricante ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.modelo     ENABLE ROW LEVEL SECURITY;

CREATE POLICY fabricante_select ON public.fabricante
    FOR SELECT TO authenticated USING (true);
CREATE POLICY modelo_select ON public.modelo
    FOR SELECT TO authenticated USING (true);

-- anon NO lee el catálogo: RLS acotada a authenticated + revocación explícita del
-- grant por defecto de Supabase (que concede a anon al crear la tabla).
GRANT SELECT ON public.fabricante, public.modelo TO authenticated;
REVOKE ALL ON public.fabricante FROM anon;
REVOKE ALL ON public.modelo     FROM anon;
REVOKE INSERT, UPDATE, DELETE ON public.fabricante FROM authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.modelo     FROM authenticated;

-- ---------------------------------------------------------------- helper: gestor en ALGUNA empresa

CREATE OR REPLACE FUNCTION public.usuario_es_gestor_en_alguna_empresa()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.empresa_usuario
         WHERE usuario_id = auth.uid()
           AND activo = true
           AND rol = ANY (ARRAY['owner', 'admin', 'gestor'])
    );
$$;

COMMENT ON FUNCTION public.usuario_es_gestor_en_alguna_empresa() IS
    'TRUE si el usuario autenticado es gestor (owner/admin/gestor) en alguna empresa. Guard del alta al catálogo global.';

-- Helper interno: solo lo llaman las RPCs SECURITY DEFINER (que corren como owner).
REVOKE ALL ON FUNCTION public.usuario_es_gestor_en_alguna_empresa() FROM PUBLIC, anon, authenticated;

-- ---------------------------------------------------------------- RPC de alta idempotente

CREATE OR REPLACE FUNCTION public.crear_fabricante(p_nombre text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor_en_alguna_empresa() THEN
        RAISE EXCEPTION 'sin permiso para dar de alta fabricantes'
            USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.fabricante (nombre)
    VALUES (btrim(p_nombre))
    ON CONFLICT (nombre_normalizado) DO UPDATE SET nombre = public.fabricante.nombre
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_fabricante(text) IS
    'Alta idempotente de fabricante (busca-o-crea por nombre normalizado). Valida rol gestor.';

CREATE OR REPLACE FUNCTION public.crear_modelo(p_fabricante_id uuid, p_nombre text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor_en_alguna_empresa() THEN
        RAISE EXCEPTION 'sin permiso para dar de alta modelos'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.fabricante WHERE id = p_fabricante_id) THEN
        RAISE EXCEPTION 'el fabricante indicado no existe'
            USING ERRCODE = '23503';
    END IF;

    INSERT INTO public.modelo (fabricante_id, nombre)
    VALUES (p_fabricante_id, btrim(p_nombre))
    ON CONFLICT (fabricante_id, nombre_normalizado) DO UPDATE SET nombre = public.modelo.nombre
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_modelo(uuid, text) IS
    'Alta idempotente de modelo bajo un fabricante (cascada; busca-o-crea por (fabricante_id, nombre normalizado)). Valida rol gestor y existencia del fabricante.';

REVOKE ALL ON FUNCTION public.crear_fabricante(text)       FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.crear_modelo(uuid, text)     FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_fabricante(text)    TO authenticated;
GRANT EXECUTE ON FUNCTION public.crear_modelo(uuid, text)  TO authenticated;
```

- [ ] **Step 6: Ejecutar y verificar que pasa**

Run:
```bash
supabase db reset
supabase test db
```
Expected: PASS — `19_catalogo_global.sql` en verde (7/7, incluye el assert anti-lectura-anon) y el resto de la suite sigue verde (07/08 aún no tocados, siguen cuadrando).

- [ ] **Step 7: Commit**

```bash
git add supabase/migrations/*_catalogo_global_fabricante_modelo.sql supabase/tests/sql/19_catalogo_global.sql
git commit -m "feat(supabase): catálogo global fabricante/modelo con alta vía RPC (T-268)"
```

---

### Task 2: Extender los guardarraíles pgTAP (07 y 08)

**Files:**
- Modify: `supabase/tests/sql/07_lockdown_escritura_global.sql`
- Modify: `supabase/tests/sql/08_lockdown_rpc_grants.sql`

**Interfaces:**
- Consumes: las tablas `fabricante`/`modelo` y las RPCs `crear_fabricante`/`crear_modelo` de Task 1.

- [ ] **Step 1: Añadir las tablas al guardarraíl 07**

En `supabase/tests/sql/07_lockdown_escritura_global.sql`, dentro del `INSERT INTO _tablas_dominio(t) VALUES (...)`, añadir dos filas manteniendo el orden alfabético (`fabricante` tras `('empresa_usuario'),`; `modelo` tras `('maquina'),`):
```sql
    ('empresa_usuario'),
    ('fabricante'),
    ('instalacion'),
```
```sql
    ('maquina'),
    ('modelo'),
    ('recaudacion'),
```
Y actualizar el `plan` y su comentario (20 → 22 tablas; 22 × 7 = 154):
```sql
-- 22 tablas × (1 SELECT authenticated + 3 no-write authenticated + 3 no-write anon) = 154
SELECT plan(154);
```
(No se tocan los `SELECT ok(...) FROM _tablas_dominio`: iteran solos sobre la lista.)

- [ ] **Step 2: Añadir las firmas RPC al guardarraíl 08**

En `supabase/tests/sql/08_lockdown_rpc_grants.sql`, dentro del `INSERT INTO _fns(sig) VALUES (...)`, añadir un bloque temático nuevo (antes del `;` final; la última fila actual `('saldar_tolva_pendiente(uuid, text)')` pasa a llevar coma):
```sql
    ('saldar_tolva_pendiente(uuid, text)'),
    -- catálogo global (T-268)
    ('crear_fabricante(text)'),
    ('crear_modelo(uuid, text)');
```
Y actualizar el `plan` y su comentario (28 → 30 funciones; 30 × 2 = 60):
```sql
-- 30 funciones × (authenticated EXECUTE + anon NO EXECUTE) = 60
SELECT plan(60);
```

- [ ] **Step 3: Ejecutar la suite completa**

Run:
```bash
supabase db reset
supabase test db
```
Expected: PASS — toda la suite en verde, incluidos 07 (154 asserts, valida que `fabricante`/`modelo` son legibles por `authenticated` pero no escribibles por `authenticated`/`anon`) y 08 (60 asserts, valida que `crear_fabricante`/`crear_modelo` son ejecutables por `authenticated` y no por `anon`).

- [ ] **Step 4: Commit**

```bash
git add supabase/tests/sql/07_lockdown_escritura_global.sql supabase/tests/sql/08_lockdown_rpc_grants.sql
git commit -m "test(supabase): guardarraíles cubren fabricante/modelo y sus RPCs (T-268)"
```

---

## Self-Review

- **Cobertura de la spec (§6.1/6.2/6.3, alcance B1):** tablas globales sin `empresa_id` (T1 ✓); `nombre_normalizado GENERATED lower(btrim())` + `UNIQUE` (T1 ✓); cascada `modelo.fabricante_id → fabricante` `ON DELETE RESTRICT` + índice (T1 ✓); RLS `SELECT USING(true)` + `GRANT SELECT` solo a `authenticated` + `REVOKE` escritura (T1 ✓); helper `usuario_es_gestor_en_alguna_empresa()` (T1 ✓); RPCs de alta idempotente con guard de gestor (T1 ✓); pgTAP funcional (T1 ✓) + guardarraíles 07/08 (T2 ✓). **Diferido a B2 a conciencia:** FK en `maquina`, migración de datos, curación (`renombrar_*`/`fusionar_*`), bandera `es_admin_catalogo`, `gen types` — documentado en Global Constraints.
- **Placeholders:** `<ts>` lo genera `supabase migration new` (no es un hueco de contenido). `T-268` es el id de tarea a registrar en `tasks.md`. Sin TODO/TBD.
- **Consistencia de tipos/firmas:** las firmas usadas en el guardarraíl 08 (`crear_fabricante(text)`, `crear_modelo(uuid, text)`) coinciden exactamente con las `CREATE FUNCTION` de T1. Los `RAISE EXCEPTION` usan `42501` (permiso) y `23503` (FK inexistente), que son los ERRCODE que asertan los `throws_ok` del test 19. El `ON CONFLICT DO UPDATE SET nombre = public.<t>.nombre` preserva el nombre ya existente (idempotencia sin pisar la primera grafía) y devuelve el `id` — verificado por los `is(...)` del test.
