# PR-B2 — Enganchar la máquina al catálogo (FK + resolución) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que `maquina` referencie el catálogo global (`fabricante_id`/`modelo_id`) sin romper ningún cliente: la RPC mantiene su firma de **texto** y resuelve internamente el nombre→id del catálogo (busca-o-crea), denormalizando el nombre canónico en las columnas de texto que web/Android ya leen.

**Architecture:** Enfoque **no-breaking** (mejora sobre el spec §6.4, que cambiaba la firma a ids y habría roto web+Android a la vez): `crear/actualizar_maquina` conservan su firma `(…, p_modelo text, p_fabricante text, …)` y, dentro, llaman a un helper interno `_resolver_catalogo(text, text)` que hace find-or-create en `fabricante`/`modelo` y devuelve ids + nombres canónicos. Así `maquina.fabricante_id`/`modelo_id` quedan poblados y `maquina.fabricante`/`modelo` (texto) siguen existiendo como copia denormalizada → **cero cambios en web/Android**. Datos existentes: un `DO`-block idempotente en la migración (cubre el cloud ya desplegado); el seed enlaza sus máquinas explícitamente.

**Tech Stack:** PostgreSQL (Supabase), plpgsql, pgTAP. Sin cambios de cliente, sin `gen types` (el repo no tiene tipos generados; los tipos web son a mano y no cambian).

## Global Constraints

- **Migraciones aditivas e inmutables.** Nueva migración vía `supabase migration new`; jamás editar una previa. Las columnas FK son **NULLABLE en transición**; las columnas de texto `modelo`/`fabricante` se **conservan** (su borrado y el endurecimiento de la FK a NOT NULL son un PR de cierre posterior).
- **No-breaking:** las firmas de `crear_maquina`/`actualizar_maquina` NO cambian (se usa `CREATE OR REPLACE` con la misma firma; los grants persisten). Ningún cambio en web/Android en este PR. Guardarraíles 07/08 **no cambian** (sin tablas nuevas; firmas RPC iguales; el helper interno va revocado de todos).
- **SSOT / seguridad:** el helper `_resolver_catalogo` es interno → `REVOKE ALL … FROM PUBLIC, anon, authenticated` (solo lo llaman las RPC `SECURITY DEFINER`, que corren como owner).
- **Entorno de test:** stack local (Docker). `npx supabase db reset` + `npx supabase test db` (el CLI no está en PATH). Long timeout.
- **Idioma:** comentarios español; identificadores inglés salvo dominio.
- **Commits:** `<tipo>(supabase): <descripción> (T-269)`. Asigna `T-269` y regístralo en `.kiro/specs/recre/tasks.md`.
- **Fuera de alcance (PR posterior):** curación (`renombrar_*`/`fusionar_*`) + bandera `es_admin_catalogo` (necesitan reflow del nombre denormalizado; se abordan cuando exista la pantalla de curación); borrado de las columnas de texto + FK NOT NULL.

## File Structure

| Fichero | Responsabilidad | Acción |
|---------|-----------------|--------|
| `supabase/migrations/<ts>_maquina_fk_catalogo.sql` | FK columns + helper `_resolver_catalogo` + `CREATE OR REPLACE` de las 2 RPC + backfill de datos existentes | Crear |
| `supabase/tests/sql/20_maquina_catalogo.sql` | Test funcional: la RPC pobla FK, denormaliza nombre canónico, cascada coherente, helper bloqueado | Crear |
| `supabase/seed.sql` | Insertar catálogo (4 fabricantes, 6 modelos) + enlazar las 6 máquinas seed | Modificar |

---

### Task 1: Migración FK + resolución + backfill (TDD)

**Files:**
- Create: `supabase/migrations/<ts>_maquina_fk_catalogo.sql`
- Test: `supabase/tests/sql/20_maquina_catalogo.sql`

**Interfaces:**
- Produces: columnas `maquina.fabricante_id`/`maquina.modelo_id` (uuid, FK); helper interno `public._resolver_catalogo(text, text) RETURNS TABLE(fab_id uuid, mod_id uuid, fab_nombre text, mod_nombre text)`; `crear_maquina`/`actualizar_maquina` con la MISMA firma pero poblando la FK + denormalizando.

- [ ] **Step 1: Crear rama**

Run (desde la raíz):
```bash
git checkout -b feat/supabase-maquina-fk
```

- [ ] **Step 2: Escribir el test (fallará)**

Create `supabase/tests/sql/20_maquina_catalogo.sql`:
```sql
-- =============================================================================
-- T-269 — La máquina referencia el catálogo global: crear_maquina resuelve el
-- texto a fabricante_id/modelo_id (busca-o-crea), denormaliza el nombre canónico
-- y mantiene la cascada coherente. El helper interno queda bloqueado a clientes.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(8);

-- fixtures: una empresa con un gestor
INSERT INTO auth.users (id) VALUES ('e1000000-0000-0000-0000-0000000000a1');
INSERT INTO public.usuario (id, nombre_completo) VALUES ('e1000000-0000-0000-0000-0000000000a1', 'Gestor');
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('e1000000-0000-0000-0000-000000000001', 'Test Maquina Catalogo', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('e1000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-0000000000a1', 'owner', true);

-- estructura + lockdown del helper interno
SELECT has_column('public', 'maquina', 'fabricante_id', 'maquina tiene fabricante_id');
SELECT has_column('public', 'maquina', 'modelo_id', 'maquina tiene modelo_id');
SELECT ok(
    NOT has_function_privilege('authenticated', 'public._resolver_catalogo(text, text)', 'EXECUTE'),
    'authenticated NO puede ejecutar _resolver_catalogo'
);
SELECT ok(
    NOT has_function_privilege('anon', 'public._resolver_catalogo(text, text)', 'EXECUTE'),
    'anon NO puede ejecutar _resolver_catalogo'
);

-- crear dos máquinas como gestor (mismo fabricante, distinta grafía; distinto modelo)
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"e1000000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT public.crear_maquina('e1000000-0000-0000-0000-000000000001', 'S1', 'Diamond', 'Cirsa', 0.20, 0, 0, 'almacen', NULL);
SELECT public.crear_maquina('e1000000-0000-0000-0000-000000000001', 'S2', 'Twister', 'CIRSA', 0.20, 0, 0, 'almacen', NULL);
RESET ROLE;

-- comportamiento
SELECT ok(
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S1') IS NOT NULL,
    'crear_maquina pobla fabricante_id'
);
SELECT is(
    (SELECT fabricante FROM public.maquina WHERE numero_serie = 'S1'),
    'Cirsa',
    'denormaliza el nombre canónico del fabricante'
);
SELECT is(
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S1'),
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S2'),
    'Cirsa/CIRSA normalizado -> mismo fabricante_id'
);
SELECT is(
    (SELECT mo.fabricante_id FROM public.modelo mo
       JOIN public.maquina m ON m.modelo_id = mo.id
      WHERE m.numero_serie = 'S1'),
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'S1'),
    'el modelo cuelga del fabricante correcto (cascada coherente)'
);

SELECT * FROM finish();
ROLLBACK;
```

- [ ] **Step 3: Ejecutar y verificar que falla**

Run (stack local; `npx supabase` porque el CLI no está en PATH; long timeout):
```bash
npx supabase db reset
npx supabase test db
```
Expected: FAIL — `20_maquina_catalogo.sql` falla (no existe `maquina.fabricante_id` ni `_resolver_catalogo`). El resto de la suite sigue verde.

- [ ] **Step 4: Crear la migración**

Run:
```bash
supabase migration new maquina_fk_catalogo
```

- [ ] **Step 5: Escribir la migración**

Contenido completo de `supabase/migrations/<ts>_maquina_fk_catalogo.sql`:
```sql
-- =============================================================================
-- La máquina referencia el catálogo global fabricante/modelo.
--
-- Enfoque NO-BREAKING: crear/actualizar_maquina conservan su firma de texto y
-- resuelven internamente el nombre->id del catálogo (busca-o-crea), poblando
-- las FK nuevas y denormalizando el nombre canónico en las columnas de texto
-- (que web/Android siguen leyendo). Las columnas de texto se conservan; su
-- borrado y la FK NOT NULL son un PR de cierre posterior.
-- =============================================================================

-- ---------------------------------------------------------------- FK aditivas (nullable en transición)

ALTER TABLE public.maquina
    ADD COLUMN IF NOT EXISTS fabricante_id uuid REFERENCES public.fabricante(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS modelo_id     uuid REFERENCES public.modelo(id)     ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS maquina_fabricante_id_idx ON public.maquina (fabricante_id);
CREATE INDEX IF NOT EXISTS maquina_modelo_id_idx     ON public.maquina (modelo_id);

-- ---------------------------------------------------------------- helper interno: resolver texto -> catálogo

CREATE OR REPLACE FUNCTION public._resolver_catalogo(p_fabricante text, p_modelo text)
RETURNS TABLE (fab_id uuid, mod_id uuid, fab_nombre text, mod_nombre text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_fab_id     uuid;
    v_mod_id     uuid;
    v_fab_nombre text;
    v_mod_nombre text;
BEGIN
    IF p_fabricante IS NOT NULL AND btrim(p_fabricante) <> '' THEN
        INSERT INTO public.fabricante (nombre) VALUES (btrim(p_fabricante))
        ON CONFLICT (nombre_normalizado) DO UPDATE SET nombre = public.fabricante.nombre
        RETURNING id, nombre INTO v_fab_id, v_fab_nombre;

        IF p_modelo IS NOT NULL AND btrim(p_modelo) <> '' THEN
            INSERT INTO public.modelo (fabricante_id, nombre) VALUES (v_fab_id, btrim(p_modelo))
            ON CONFLICT (fabricante_id, nombre_normalizado) DO UPDATE SET nombre = public.modelo.nombre
            RETURNING id, nombre INTO v_mod_id, v_mod_nombre;
        END IF;
    ELSE
        -- sin fabricante no se puede enlazar el modelo al catálogo: se conserva el texto crudo
        v_mod_nombre := NULLIF(btrim(p_modelo), '');
    END IF;

    RETURN QUERY SELECT v_fab_id, v_mod_id, v_fab_nombre, v_mod_nombre;
END;
$$;

COMMENT ON FUNCTION public._resolver_catalogo(text, text) IS
    'Interno: busca-o-crea fabricante/modelo en el catálogo global y devuelve ids + nombres canónicos. Solo lo llaman las RPC de máquina (SECURITY DEFINER).';

REVOKE ALL ON FUNCTION public._resolver_catalogo(text, text) FROM PUBLIC, anon, authenticated;

-- ---------------------------------------------------------------- RPC (misma firma; ahora poblan FK + denormalizan)

CREATE OR REPLACE FUNCTION public.crear_maquina(
    p_empresa_id                uuid,
    p_numero_serie              text,
    p_modelo                    text,
    p_fabricante                text,
    p_valor_credito             numeric,
    p_contador_entradas_inicial bigint,
    p_contador_salidas_inicial  bigint,
    p_estado                    text,
    p_notas                     text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id  uuid;
    v_cat record;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar maquinas'
            USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_cat FROM public._resolver_catalogo(p_fabricante, p_modelo);

    INSERT INTO public.maquina (
        empresa_id, numero_serie, modelo, fabricante, modelo_id, fabricante_id, valor_credito,
        contador_entradas_inicial, contador_salidas_inicial, estado, notas
    ) VALUES (
        p_empresa_id, p_numero_serie, v_cat.mod_nombre, v_cat.fab_nombre, v_cat.mod_id, v_cat.fab_id, p_valor_credito,
        p_contador_entradas_inicial, p_contador_salidas_inicial, p_estado, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) IS
    'Alta de maquina. Valida rol gestor + tenant. Resuelve fabricante/modelo al catálogo global (busca-o-crea) y denormaliza el nombre. Devuelve el id.';

CREATE OR REPLACE FUNCTION public.actualizar_maquina(
    p_id                        uuid,
    p_numero_serie              text,
    p_modelo                    text,
    p_fabricante                text,
    p_valor_credito             numeric,
    p_contador_entradas_inicial bigint,
    p_contador_salidas_inicial  bigint,
    p_estado                    text,
    p_notas                     text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
    v_cat        record;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.maquina WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'maquina no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar maquinas'
            USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_cat FROM public._resolver_catalogo(p_fabricante, p_modelo);

    UPDATE public.maquina SET
        numero_serie              = p_numero_serie,
        modelo                    = v_cat.mod_nombre,
        fabricante                = v_cat.fab_nombre,
        modelo_id                 = v_cat.mod_id,
        fabricante_id             = v_cat.fab_id,
        valor_credito             = p_valor_credito,
        contador_entradas_inicial = p_contador_entradas_inicial,
        contador_salidas_inicial  = p_contador_salidas_inicial,
        estado                    = p_estado,
        notas                     = p_notas
    WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) IS
    'Edición de maquina. Valida rol gestor + tenant. Resuelve fabricante/modelo al catálogo global y denormaliza el nombre.';

-- ---------------------------------------------------------------- backfill idempotente de datos existentes (cloud/dev)

DO $$
DECLARE
    m       RECORD;
    v_cat   record;
BEGIN
    FOR m IN
        SELECT id, fabricante, modelo FROM public.maquina
         WHERE fabricante_id IS NULL AND fabricante IS NOT NULL AND btrim(fabricante) <> ''
    LOOP
        SELECT * INTO v_cat FROM public._resolver_catalogo(m.fabricante, m.modelo);
        UPDATE public.maquina SET
            fabricante_id = v_cat.fab_id,
            modelo_id     = v_cat.mod_id,
            fabricante    = v_cat.fab_nombre,
            modelo        = v_cat.mod_nombre
        WHERE id = m.id;
    END LOOP;
END $$;
```

- [ ] **Step 6: Ejecutar y verificar que pasa**

Run:
```bash
npx supabase db reset
npx supabase test db
```
Expected: PASS — `20_maquina_catalogo.sql` en verde (8/8) y el resto de la suite verde (07/08 sin cambios, siguen cuadrando; las firmas de las RPC no cambiaron).

- [ ] **Step 7: Commit**

```bash
git add supabase/migrations/*_maquina_fk_catalogo.sql supabase/tests/sql/20_maquina_catalogo.sql
git commit -m "feat(supabase): maquina referencia el catálogo (FK + resolución) (T-269)"
```

---

### Task 2: Enlazar las máquinas del seed al catálogo

**Files:**
- Modify: `supabase/seed.sql`

**Interfaces:**
- Consumes: las tablas `fabricante`/`modelo` y las columnas `maquina.fabricante_id`/`modelo_id` de Task 1.

**Contexto:** en `db reset`, `seed.sql` corre DESPUÉS de las migraciones, así que el `DO`-block de la migración ve `maquina` vacía y no enlaza nada local. El seed debe insertar el catálogo (UUIDs fijos, al estilo del resto del seed) y enlazar sus 6 máquinas, para que el entorno de desarrollo tenga fabricantes/modelos que mostrar en los autocompletes de B3/B4.

- [ ] **Step 1: Insertar el catálogo en el seed (antes del bloque de máquinas)**

En `supabase/seed.sql`, **justo antes** del `insert into public.maquina (` (≈línea 114, tras su comentario de cabecera), añadir:
```sql
-- Catálogo global fabricante/modelo (UUIDs fijos para re-ejecutar con ON CONFLICT).
insert into public.fabricante (id, nombre) values
    ('d0000000-0000-0000-0000-000000000001', 'Cirsa'),
    ('d0000000-0000-0000-0000-000000000002', 'Unidesa'),
    ('d0000000-0000-0000-0000-000000000003', 'R. Franco'),
    ('d0000000-0000-0000-0000-000000000004', 'MGA')
on conflict (nombre_normalizado) do nothing;

insert into public.modelo (id, fabricante_id, nombre) values
    ('d1000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'Super Cherry'),
    ('d1000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001', 'Diamond'),
    ('d1000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002', 'Gallo'),
    ('d1000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000003', 'Bingo Plus'),
    ('d1000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000004', 'Fruit Mania'),
    ('d1000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000001', 'Twister')
on conflict (fabricante_id, nombre_normalizado) do nothing;
```

- [ ] **Step 2: Enlazar las máquinas del seed (añadir columnas FK al INSERT existente)**

Sustituir el bloque `insert into public.maquina (...) values ... on conflict (id) do nothing;` (≈líneas 114-124) por (añade `fabricante_id, modelo_id` a la lista de columnas y sus valores mapeados):
```sql
insert into public.maquina (
    id, empresa_id, numero_serie, modelo, fabricante, fabricante_id, modelo_id, valor_credito,
    contador_entradas_inicial, contador_salidas_inicial, estado
) values
    ('c0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'SC-1001', 'Super Cherry', 'Cirsa',     'd0000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001', 0.20, 120000,  90000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', 'DM-1002', 'Diamond',      'Cirsa',     'd0000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000002', 0.20,  80000,  55000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000001', 'GA-1003', 'Gallo',        'Unidesa',   'd0000000-0000-0000-0000-000000000002', 'd1000000-0000-0000-0000-000000000003', 0.20, 200000, 160000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000001', 'BG-1004', 'Bingo Plus',   'R. Franco', 'd0000000-0000-0000-0000-000000000003', 'd1000000-0000-0000-0000-000000000004', 0.20,  50000,  38000, 'instalada'),
    ('c0000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000001', 'FR-1005', 'Fruit Mania',  'MGA',       'd0000000-0000-0000-0000-000000000004', 'd1000000-0000-0000-0000-000000000005', 0.20,      0,      0, 'almacen'),
    ('c0000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000001', 'TW-1006', 'Twister',      'Cirsa',     'd0000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000006', 0.20,  30000,  21000, 'instalada')
on conflict (id) do nothing;
```

- [ ] **Step 3: Verificar seed + suite**

Run:
```bash
npx supabase db reset
npx supabase test db
```
Then verify the 6 seed machines are all linked (should print `0`):
```bash
npx supabase db reset >/dev/null 2>&1; docker exec supabase_db_recre psql -U postgres -d postgres -tAc "SELECT count(*) FROM public.maquina WHERE fabricante_id IS NULL;"
```
Expected: `db reset` aplica migraciones + seed sin error (FK satisfechas: el catálogo se inserta antes que las máquinas); `test db` verde; el count de máquinas sin `fabricante_id` = **0** (las 6 del seed enlazadas).

- [ ] **Step 4: Commit**

```bash
git add supabase/seed.sql
git commit -m "feat(supabase): el seed enlaza sus máquinas al catálogo (T-269)"
```

---

## Self-Review

- **Cobertura (alcance B2, spec §6.4 adaptado):** FK aditivas nullable + índices (T1 ✓); columnas de texto conservadas + denormalización del nombre canónico (T1 ✓); RPC pueblan la FK **sin cambiar su firma** → sin romper web/Android (T1 ✓, mejora consciente sobre el "cambiar a ids" del spec, que rompía ambos clientes a la vez); coherencia cascada validada en el test (T1 ✓); migración de datos existentes vía `DO`-block idempotente para el cloud (T1 ✓) + enlace explícito del seed (T2 ✓). **Diferido a conciencia:** curación, bandera `es_admin_catalogo`, borrado de texto + FK NOT NULL, `gen types` (no hay pipeline de tipos).
- **No hay cambios de guardarraíl** porque las firmas RPC no cambian y el helper interno va revocado de todos (no es cliente-ejecutable, no entra en 08). El test 20 asserta explícitamente ese lockdown.
- **Placeholders:** `<ts>` lo genera `supabase migration new`. `T-269` a registrar en `tasks.md`. Sin TODO/TBD.
- **Consistencia:** `_resolver_catalogo` devuelve `(fab_id, mod_id, fab_nombre, mod_nombre)` y ambas RPC lo consumen con `SELECT * INTO v_cat`; el `INSERT`/`UPDATE` de máquina usan `v_cat.fab_id/mod_id` (FK) y `v_cat.fab_nombre/mod_nombre` (texto denormalizado). El backfill reusa el mismo helper. Los UUIDs fijos del seed (`d0…` fabricantes, `d1…` modelos) mapean cada máquina a su par correcto (Cirsa: Super Cherry/Diamond/Twister; Unidesa: Gallo; R. Franco: Bingo Plus; MGA: Fruit Mania), coherentes con el `fabricante_id` de cada modelo.
