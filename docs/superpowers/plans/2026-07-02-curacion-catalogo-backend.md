# Curación del catálogo — backend (supabase) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** RPCs para **renombrar** y **fusionar** fabricantes/modelos del catálogo global, restringidas a un admin (`usuario.es_admin_catalogo`), propagando el nombre denormalizado a las máquinas.

**Architecture:** Una migración aditiva añade el flag `usuario.es_admin_catalogo` + helper `usuario_es_admin_catalogo()` + 4 RPCs `SECURITY DEFINER` (renombrar/fusionar × fabricante/modelo). Renombrar reescribe el texto denormalizado `maquina.fabricante`/`maquina.modelo`; fusionar además repunta las FK `maquina.fabricante_id`/`modelo_id` y borra la entrada absorbida (sorteando `ON DELETE RESTRICT`). El `UNIQUE` de modelo es `(fabricante_id, nombre_normalizado)` → fusionar fabricantes puede colisionar modelos hijos, que se fusionan a su vez.

**Tech Stack:** Postgres (plpgsql, SECURITY DEFINER) · pgTAP.

## Global Constraints

- **Escrituras solo vía RPC** `SECURITY DEFINER`; guard = `usuario_es_admin_catalogo()`; grant `EXECUTE` a `authenticated`, revocado de `anon`/`PUBLIC`. El helper se revoca de todos (lo invocan las RPC).
- **Migración aditiva e inmutable**: fichero nuevo con timestamp posterior a `20260702120000`. No editar migraciones existentes.
- **Denormalización**: al renombrar/fusionar hay que reescribir SIEMPRE los textos `maquina.fabricante`/`maquina.modelo` de las máquinas afectadas (columnas que web/Android leen). Al fusionar, repuntar además `maquina.fabricante_id`/`modelo_id`.
- **Idempotencia normalizada**: `nombre_normalizado = lower(btrim(nombre))` (GENERATED). `fabricante` UNIQUE global; `modelo` UNIQUE `(fabricante_id, nombre_normalizado)`.
- **Guardarraíles**: `08_lockdown_rpc_grants.sql` enumera las RPC y sus grants → añadir las 4 nuevas y ajustar `plan(N)`. No cambiar firmas existentes.

---

## Task 1: Migración (flag + helper + 4 RPCs) y seed

**Files:**
- Create: `supabase/migrations/20260702140000_curacion_catalogo.sql`
- Modify: `supabase/seed.sql`

- [ ] **Step 1: Escribir la migración** — `supabase/migrations/20260702140000_curacion_catalogo.sql`

```sql
-- Curación del catálogo global: renombrar y fusionar fabricantes/modelos,
-- restringido a admins de catálogo (usuario.es_admin_catalogo). Propaga el
-- nombre denormalizado a maquina.fabricante/maquina.modelo y, al fusionar,
-- repunta las FK y borra la entrada absorbida.

-- 1) Flag global de admin de catálogo (perfil 1:1 con auth.users).
ALTER TABLE public.usuario
    ADD COLUMN IF NOT EXISTS es_admin_catalogo boolean NOT NULL DEFAULT false;

-- Concede el flag al owner ya existente (no-op en un reset limpio: el usuario
-- aún no existe cuando corren las migraciones; el seed lo fija allí también).
UPDATE public.usuario SET es_admin_catalogo = true
    WHERE id = 'a0000000-0000-0000-0000-000000000001';

-- 2) Helper de permiso (revocado de todos; lo invocan las RPC SECURITY DEFINER).
CREATE OR REPLACE FUNCTION public.usuario_es_admin_catalogo()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_catalog
AS $$
    SELECT COALESCE(
        (SELECT es_admin_catalogo FROM public.usuario WHERE id = auth.uid()),
        false
    );
$$;
REVOKE ALL ON FUNCTION public.usuario_es_admin_catalogo() FROM PUBLIC, anon, authenticated;

-- 3) Renombrar fabricante.
CREATE OR REPLACE FUNCTION public.renombrar_fabricante(p_id uuid, p_nombre text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_nombre       text := btrim(p_nombre);
    v_norm         text := lower(btrim(p_nombre));
    v_norm_actual  text;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF v_nombre = '' THEN
        RAISE EXCEPTION 'el nombre no puede estar vacío' USING ERRCODE = '23514';
    END IF;

    SELECT nombre_normalizado INTO v_norm_actual FROM public.fabricante WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'fabricante no encontrado: %', p_id USING ERRCODE = 'no_data_found';
    END IF;

    -- Colisión con OTRO fabricante (mismo normalizado, distinto id) → fusionar.
    IF v_norm <> v_norm_actual AND EXISTS (
        SELECT 1 FROM public.fabricante WHERE nombre_normalizado = v_norm AND id <> p_id
    ) THEN
        RAISE EXCEPTION 'ya existe un fabricante «%»; usa fusionar', v_nombre
            USING ERRCODE = '23505';
    END IF;

    UPDATE public.fabricante SET nombre = v_nombre WHERE id = p_id;
    -- Reflow del texto denormalizado.
    UPDATE public.maquina SET fabricante = v_nombre WHERE fabricante_id = p_id;
END;
$$;

-- 4) Renombrar modelo (colisión acotada a su fabricante).
CREATE OR REPLACE FUNCTION public.renombrar_modelo(p_id uuid, p_nombre text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_nombre       text := btrim(p_nombre);
    v_norm         text := lower(btrim(p_nombre));
    v_norm_actual  text;
    v_fab          uuid;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF v_nombre = '' THEN
        RAISE EXCEPTION 'el nombre no puede estar vacío' USING ERRCODE = '23514';
    END IF;

    SELECT nombre_normalizado, fabricante_id INTO v_norm_actual, v_fab
        FROM public.modelo WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'modelo no encontrado: %', p_id USING ERRCODE = 'no_data_found';
    END IF;

    IF v_norm <> v_norm_actual AND EXISTS (
        SELECT 1 FROM public.modelo
        WHERE fabricante_id = v_fab AND nombre_normalizado = v_norm AND id <> p_id
    ) THEN
        RAISE EXCEPTION 'ya existe un modelo «%» en ese fabricante; usa fusionar', v_nombre
            USING ERRCODE = '23505';
    END IF;

    UPDATE public.modelo SET nombre = v_nombre WHERE id = p_id;
    UPDATE public.maquina SET modelo = v_nombre WHERE modelo_id = p_id;
END;
$$;

-- 5) Fusionar modelo (exige mismo fabricante para no romper la coherencia).
CREATE OR REPLACE FUNCTION public.fusionar_modelo(p_origen uuid, p_destino uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_fab_origen    uuid;
    v_fab_destino   uuid;
    v_nombre_destino text;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF p_origen = p_destino THEN
        RAISE EXCEPTION 'origen y destino no pueden ser iguales' USING ERRCODE = '22023';
    END IF;

    SELECT fabricante_id INTO v_fab_origen FROM public.modelo WHERE id = p_origen;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'modelo origen no encontrado: %', p_origen USING ERRCODE = 'no_data_found';
    END IF;
    SELECT fabricante_id, nombre INTO v_fab_destino, v_nombre_destino
        FROM public.modelo WHERE id = p_destino;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'modelo destino no encontrado: %', p_destino USING ERRCODE = 'no_data_found';
    END IF;
    IF v_fab_origen <> v_fab_destino THEN
        RAISE EXCEPTION 'solo se pueden fusionar modelos del mismo fabricante'
            USING ERRCODE = '22023';
    END IF;

    -- Repuntar máquinas del origen al destino (FK + texto) y borrar el absorbido.
    UPDATE public.maquina SET modelo_id = p_destino, modelo = v_nombre_destino
        WHERE modelo_id = p_origen;
    DELETE FROM public.modelo WHERE id = p_origen;
END;
$$;

-- 6) Fusionar fabricante (mueve modelos hijos, dedup por colisión, repunta máquinas).
CREATE OR REPLACE FUNCTION public.fusionar_fabricante(p_origen uuid, p_destino uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_nombre_destino text;
    m                record;
    v_dest_modelo    uuid;
    v_dest_mod_nombre text;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF p_origen = p_destino THEN
        RAISE EXCEPTION 'origen y destino no pueden ser iguales' USING ERRCODE = '22023';
    END IF;

    SELECT nombre INTO v_nombre_destino FROM public.fabricante WHERE id = p_destino;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'fabricante destino no encontrado: %', p_destino USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.fabricante WHERE id = p_origen) THEN
        RAISE EXCEPTION 'fabricante origen no encontrado: %', p_origen USING ERRCODE = 'no_data_found';
    END IF;

    -- Cada modelo del ORIGEN: si el DESTINO no tiene uno con ese nombre
    -- normalizado, se mueve; si colisiona, se fusiona (máquinas al del destino)
    -- y se borra el del origen.
    FOR m IN
        SELECT id, nombre_normalizado FROM public.modelo WHERE fabricante_id = p_origen
    LOOP
        SELECT id, nombre INTO v_dest_modelo, v_dest_mod_nombre
            FROM public.modelo
            WHERE fabricante_id = p_destino AND nombre_normalizado = m.nombre_normalizado;
        IF v_dest_modelo IS NULL THEN
            UPDATE public.modelo SET fabricante_id = p_destino WHERE id = m.id;
        ELSE
            UPDATE public.maquina SET modelo_id = v_dest_modelo, modelo = v_dest_mod_nombre
                WHERE modelo_id = m.id;
            DELETE FROM public.modelo WHERE id = m.id;
        END IF;
    END LOOP;

    -- Repuntar todas las máquinas del fabricante origen al destino (FK + texto).
    UPDATE public.maquina SET fabricante_id = p_destino, fabricante = v_nombre_destino
        WHERE fabricante_id = p_origen;

    -- Borrar el fabricante absorbido (ya sin modelos ni máquinas que lo referencien).
    DELETE FROM public.fabricante WHERE id = p_origen;
END;
$$;

-- 7) Grants: EXECUTE solo a authenticated (el guard interno exige admin).
REVOKE ALL ON FUNCTION public.renombrar_fabricante(uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.renombrar_modelo(uuid, text)     FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.fusionar_fabricante(uuid, uuid)  FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.fusionar_modelo(uuid, uuid)      FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.renombrar_fabricante(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.renombrar_modelo(uuid, text)     TO authenticated;
GRANT EXECUTE ON FUNCTION public.fusionar_fabricante(uuid, uuid)  TO authenticated;
GRANT EXECUTE ON FUNCTION public.fusionar_modelo(uuid, uuid)      TO authenticated;
```

- [ ] **Step 2: Sembrar el flag en el owner** — `supabase/seed.sql`

En el INSERT de `public.usuario` del owner `a0000000-0000-0000-0000-000000000001` (o con un `UPDATE` inmediatamente después del bloque de usuarios), fija `es_admin_catalogo = true` para ese id. Si el INSERT de usuario no lista la columna, añade tras el bloque:
```sql
update public.usuario set es_admin_catalogo = true
    where id = 'a0000000-0000-0000-0000-000000000001';
```

- [ ] **Step 3: `supabase db reset`** — aplica migración + seed sin error.

Run: `npx supabase db reset`
Expected: sin errores; el owner queda con `es_admin_catalogo = true`.

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/20260702140000_curacion_catalogo.sql supabase/seed.sql
git commit -m "feat(supabase): RPCs de curación del catálogo (renombrar/fusionar) + es_admin_catalogo (T-275)"
```

---

## Task 2: pgTAP de curación + guardarraíl 08

**Files:**
- Create: `supabase/tests/sql/22_curacion_catalogo.sql`
- Modify: `supabase/tests/sql/08_lockdown_rpc_grants.sql`

- [ ] **Step 1: Escribir el pgTAP** — `supabase/tests/sql/22_curacion_catalogo.sql`

Sigue el patrón de `19_catalogo_global.sql` / `20_maquina_catalogo.sql` (`BEGIN … SELECT plan(N) … SELECT * FROM finished(); ROLLBACK;`; fixtures `auth.users` + `public.usuario` + `public.empresa` + `public.empresa_usuario`; actuar como cliente con `SET LOCAL ROLE authenticated` + `SET LOCAL request.jwt.claims`). Crea DOS usuarios: uno con `es_admin_catalogo = true` (admin) y otro sin (gestor normal). Fixtures de catálogo: 2 fabricantes (A, B), modelos bajo cada uno (incluyendo un nombre que colisione al fusionar A→B), y ≥1 `maquina` con `fabricante_id`/`modelo_id` + textos denormalizados poblados.

Asserts que DEBE cubrir (usa el `plan(N)` correcto):
1. **Guard**: un usuario NO admin (`es_admin_catalogo=false`) recibe `42501` al llamar `renombrar_fabricante` (y comprueba al menos otra RPC, p.ej. `fusionar_fabricante`).
2. **renombrar_fabricante** (como admin): cambia el nombre del fabricante Y reescribe `maquina.fabricante` de las máquinas de ese fabricante (verifica el texto denormalizado con `results_eq`/`is`).
3. **renombrar_fabricante colisión**: renombrar A al nombre normalizado de B → `throws_ok(..., '23505', ...)`.
4. **renombrar_modelo**: reescribe `maquina.modelo`; colisión dentro del mismo fabricante → `23505`.
5. **fusionar_modelo mismo fabricante**: máquinas del modelo origen quedan con `modelo_id` = destino y `modelo` = nombre destino; el modelo origen deja de existir (`is_empty`). Cross-fabricante → `throws_ok(..., '22023', ...)`.
6. **fusionar_fabricante**: las máquinas del origen quedan con `fabricante_id` = destino y `fabricante` = nombre destino; los modelos del origen sin colisión pasan a colgar del destino; los que colisionan se fusionan (máquinas repuntadas al modelo del destino) y desaparecen; el fabricante origen deja de existir.

- [ ] **Step 2: Ampliar el guardarraíl 08** — `supabase/tests/sql/08_lockdown_rpc_grants.sql`

Añade a la lista de RPC verificadas (con su firma exacta) las 4 nuevas: `renombrar_fabricante(uuid, text)`, `renombrar_modelo(uuid, text)`, `fusionar_fabricante(uuid, uuid)`, `fusionar_modelo(uuid, uuid)` — cada una debe tener `EXECUTE` para `authenticated` y NO para `anon`. Ajusta el `plan(N)` según cuántos asserts por RPC haga el fichero (mira cómo se añadieron `crear_fabricante`/`crear_modelo`). El helper `usuario_es_admin_catalogo()` NO debe estar concedido a `authenticated` (si el fichero verifica helpers revocados, inclúyelo; si no, no lo añadas).

- [ ] **Step 3: Suite pgTAP completa**

Run: `npx supabase test db`
Expected: TODA la suite verde, incluido `22_` y `08_` ampliado; sin romper `07_`.

- [ ] **Step 4: Commit**

```bash
git add supabase/tests/sql/22_curacion_catalogo.sql supabase/tests/sql/08_lockdown_rpc_grants.sql
git commit -m "test(supabase): pgTAP de curación del catálogo + guardarraíl 08 (T-275)"
```

## Notas de diseño

- **Colisión al renombrar** = error `23505` con mensaje "usa fusionar" (renombrar no fusiona silenciosamente). Re-ortografía (mismo normalizado) SÍ se permite.
- **Fusionar modelo** exige mismo fabricante: un modelo pertenece a un fabricante; fusionar entre fabricantes dejaría `maquina.fabricante_id` incoherente con `modelo_id`.
- **Fusionar fabricante** resuelve el `UNIQUE(fabricante_id, nombre_normalizado)` moviendo modelos no colisionantes y fusionando (repuntar+borrar) los que colisionan, antes de repuntar máquinas y borrar el fabricante (respeta `ON DELETE RESTRICT`).
- **Reflow**: renombrar reescribe solo texto; fusionar reescribe texto + FK. Nunca se deja `maquina` apuntando a una entrada borrada.

## Self-review

- Cobertura: renombrar/fusionar × fabricante/modelo ✓; guard admin ✓; reflow denormalizado ✓; colisiones ✓; grants + guardarraíl ✓. Web (panel) = PR aparte.
- Sin placeholders; SQL literal. Timestamps de migración posteriores al último.
