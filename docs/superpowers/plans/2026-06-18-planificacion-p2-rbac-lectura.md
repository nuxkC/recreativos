# Planificación de recaudación — P2: RBAC de lectura estricto · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (o subagent-driven-development) para implementar tarea a tarea. Pasos con checkbox (`- [ ]`).

**Goal:** Que un **técnico** solo LEA los locales que tiene asignados (y todo lo que cuelga de ellos: instalaciones, recaudaciones, máquinas, licencias, averías, deudas); **owner/admin/gestor/contable** siguen viendo todo de su empresa.

**Architecture:** Migración aditiva que **reemplaza las policies SELECT** (DROP + CREATE) de las tablas operativas para añadir el filtro por asignación `local.operario_id = auth.uid()`. Dos helpers `SECURITY DEFINER STABLE` (`usuario_ve_todo_empresa`, `usuario_ve_local`, `usuario_ve_instalacion`) evitan repetir subconsultas y la recursión de RLS en subconsultas. Verificación **pgTAP exhaustiva antes de tocar el cliente**.

**Tech Stack:** Supabase (Postgres + RLS + pgTAP). Sin cambios de cálculo de dinero. Android/web: solo verificación (la RLS filtra sola).

**Spec:** `docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md` §5.

## Decisiones (confirmadas con el usuario, 2026-06-18)
- **Estricto total al reasignar:** si un local pasa de Juan a Pedro, Juan deja de ver TODO ese local, incluidas las recaudaciones que él hizo. No hay vía "conserva lo mío por `tecnico_id`". Regla única: "solo tus locales actuales".
- **Catálogo estricto:** el técnico ve una `maquina`/`licencia` solo si está instalada en uno de sus locales.
- **`contable` ve todo** (rol de solo-lectura financiera): el único rol restringido es `tecnico`. "Ve todo" = rol ∈ {owner, admin, gestor, contable}.

## Invariantes
- **Migración aditiva e inmutable.** `YYYYMMDDHHMMSS_descripcion.sql`. Las policies se reemplazan con DROP POLICY IF EXISTS + CREATE POLICY en una migración NUEVA; no se edita `20260519230500_enable_rls_and_policies.sql`.
- **No tocar escrituras.** Solo las policies `*_select` (y las `FOR ALL` que mezclan lectura: ver Task 3, nota de `local_modify`). Las INSERT/UPDATE/DELETE siguen igual.
- **`auth.uid()` = `usuario.id`** (el seed crea `usuario.id` = `auth.users.id`; `operario_id REFERENCES usuario(id)`). Las RLS son por `auth.uid()`.

---

## File Structure

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `supabase/migrations/<ts>_rbac_lectura_helpers.sql` | Helpers `usuario_ve_todo_empresa` / `usuario_ve_local` / `usuario_ve_instalacion` | **Crear** |
| `supabase/migrations/<ts>_rbac_lectura_policies.sql` | DROP+CREATE de las policies SELECT estrictas | **Crear** |
| `supabase/tests/sql/15_rbac_lectura_operario.sql` | pgTAP: técnico vs gestor vs reasignación vs catálogo vs avería | **Crear** |
| (verificación) `android/.../SyncRepository.kt` y queries web | Confirmar que ningún SELECT asume ver-todo | **Verificar, no editar salvo que rompa** |

---

### Task 1: Helpers de visibilidad

**Files:** Create `supabase/migrations/<ts>_rbac_lectura_helpers.sql`

- [ ] **Step 1: Escribe los helpers**

```sql
-- =============================================================================
-- Planificación P2 — helpers de visibilidad (RBAC de lectura estricto).
-- SECURITY DEFINER + STABLE: saltan RLS (evitan recursión cuando se usan dentro
-- de policies SELECT que filtran las mismas tablas que consultan).
-- =============================================================================

-- "Ve todo" = cualquier rol salvo el técnico (owner/admin/gestor/contable).
CREATE OR REPLACE FUNCTION public.usuario_ve_todo_empresa(p_empresa_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_catalog AS $$
    SELECT public.usuario_tiene_rol(p_empresa_id, ARRAY['owner', 'admin', 'gestor', 'contable']);
$$;
COMMENT ON FUNCTION public.usuario_ve_todo_empresa(uuid) IS
    'TRUE si el usuario ve TODO de la empresa (no es solo técnico). El técnico se restringe a sus locales asignados.';

-- Ve un local concreto: ve-todo de su empresa, o es su operario asignado.
CREATE OR REPLACE FUNCTION public.usuario_ve_local(p_local_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_catalog AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.local l
         WHERE l.id = p_local_id
           AND (public.usuario_ve_todo_empresa(l.empresa_id) OR l.operario_id = auth.uid())
    );
$$;
COMMENT ON FUNCTION public.usuario_ve_local(uuid) IS
    'TRUE si el usuario puede leer el local: ve-todo de su empresa, o es el operario asignado.';

-- Ve una instalación: a través de su local.
CREATE OR REPLACE FUNCTION public.usuario_ve_instalacion(p_instalacion_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_catalog AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.instalacion i
          JOIN public.local l ON l.id = i.local_id
         WHERE i.id = p_instalacion_id
           AND (public.usuario_ve_todo_empresa(l.empresa_id) OR l.operario_id = auth.uid())
    );
$$;
COMMENT ON FUNCTION public.usuario_ve_instalacion(uuid) IS
    'TRUE si el usuario puede leer la instalación: ve-todo de su empresa, o es operario del local donde está.';

REVOKE ALL ON FUNCTION public.usuario_ve_todo_empresa(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.usuario_ve_local(uuid)        FROM PUBLIC;
REVOKE ALL ON FUNCTION public.usuario_ve_instalacion(uuid)  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.usuario_ve_todo_empresa(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.usuario_ve_local(uuid)        TO authenticated;
GRANT EXECUTE ON FUNCTION public.usuario_ve_instalacion(uuid)  TO authenticated;
```

- [ ] **Step 2: `supabase db reset`** → aplica sin error.
- [ ] **Step 3: Commit** `git add supabase/migrations/*_rbac_lectura_helpers.sql && git commit -m "feat(supabase): helpers de visibilidad por operario (Planificación P2)"`

---

### Task 2: Policies SELECT estrictas — locales + cadena de instalación

**Files:** Create `supabase/migrations/<ts>_rbac_lectura_policies.sql`

> Patrón: cada policy nueva = `usuario_ve_todo_empresa(empresa_id) OR <camino al operario>`. Las tablas con `empresa_id` directo dejan la rama gestor barata; el técnico llega por el local. **No** se tocan las policies de INSERT/UPDATE/DELETE.

- [ ] **Step 1: Escribe la migración (parte A: local + cadena por instalación)**

```sql
-- =============================================================================
-- Planificación P2 — RBAC de lectura estricto. Reemplaza las policies SELECT de
-- las tablas operativas para que el técnico solo lea sus locales asignados.
-- Aditiva: DROP POLICY IF EXISTS + CREATE en migración nueva (no se edita la de
-- origen 20260519230500). Las escrituras NO cambian.
-- =============================================================================

-- local: ve-todo, o soy el operario.
DROP POLICY IF EXISTS local_select ON public.local;
CREATE POLICY local_select ON public.local
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR operario_id = auth.uid()
    );

-- instalacion: a través de su local.
DROP POLICY IF EXISTS instalacion_select ON public.instalacion;
CREATE POLICY instalacion_select ON public.instalacion
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_local(local_id)
    );

-- recaudacion: a través de su instalación.
DROP POLICY IF EXISTS recaudacion_select ON public.recaudacion;
CREATE POLICY recaudacion_select ON public.recaudacion
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_instalacion(instalacion_id)
    );

-- cambio_placa: a través de su instalación.
DROP POLICY IF EXISTS cambio_placa_select ON public.cambio_placa;
CREATE POLICY cambio_placa_select ON public.cambio_placa
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_instalacion(instalacion_id)
    );

-- lectura_no_recaudada: a través de su instalación.
DROP POLICY IF EXISTS lectura_no_recaudada_select ON public.lectura_no_recaudada;
CREATE POLICY lectura_no_recaudada_select ON public.lectura_no_recaudada
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_instalacion(instalacion_id)
    );

-- recaudacion_lock: a través de su instalación (sin empresa_id directo).
DROP POLICY IF EXISTS recaudacion_lock_select ON public.recaudacion_lock;
CREATE POLICY recaudacion_lock_select ON public.recaudacion_lock
    FOR SELECT USING (public.usuario_ve_instalacion(instalacion_id));
```

- [ ] **Step 2: (parte B en el MISMO fichero) catálogo + averías + deudas** — ver Task 3, que añade al mismo `<ts>_rbac_lectura_policies.sql`.
- [ ] **Step 3:** (commit junto con Task 3.)

---

### Task 3: Policies SELECT estrictas — catálogo, averías y deudas

**Files:** mismo `supabase/migrations/<ts>_rbac_lectura_policies.sql` (continúa)

- [ ] **Step 1: Catálogo (maquina, licencia) — visible si instalada en un local del operario**

```sql
-- maquina: el técnico la ve solo si está instalada en uno de sus locales.
DROP POLICY IF EXISTS maquina_select ON public.maquina;
CREATE POLICY maquina_select ON public.maquina
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR EXISTS (
            SELECT 1 FROM public.instalacion i
              JOIN public.local l ON l.id = i.local_id
             WHERE i.maquina_id = maquina.id AND l.operario_id = auth.uid()
        )
    );

-- licencia: idem, vía instalacion.licencia_id.
DROP POLICY IF EXISTS licencia_select ON public.licencia;
CREATE POLICY licencia_select ON public.licencia
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR EXISTS (
            SELECT 1 FROM public.instalacion i
              JOIN public.local l ON l.id = i.local_id
             WHERE i.licencia_id = licencia.id AND l.operario_id = auth.uid()
        )
    );
```

> Nota: el `EXISTS` interno consulta `instalacion`/`local`; como las policies SELECT de esas tablas también se evalúan en subconsultas, se hace inline (no helper) para que corra con el contexto del propietario de la policy de `maquina`. En la práctica el `EXISTS` directo sobre `instalacion`+`local` ve todas las filas (las policies no recursan dentro de un `EXISTS` correlacionado de otra policy en Postgres ≥ 15). **Verificar en pgTAP** (Task 4) que el técnico ve exactamente sus máquinas.

- [ ] **Step 2: averia — máquina-céntrica con `local_id` snapshot (puede ser NULL)**

```sql
-- averia: el historial sigue a la MÁQUINA; local_id es snapshot y puede ser NULL
-- (máquina en almacén). El técnico ve la avería solo si su snapshot apunta a un
-- local suyo; ve-todo la ve siempre (incluidas las de local_id NULL).
DROP POLICY IF EXISTS averia_select ON public.averia;
CREATE POLICY averia_select ON public.averia
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR (local_id IS NOT NULL AND public.usuario_ve_local(local_id))
    );

-- averia_recambio: cuelga de la avería; mismo criterio vía averia_id.
DROP POLICY IF EXISTS averia_recambio_select ON public.averia_recambio;
CREATE POLICY averia_recambio_select ON public.averia_recambio
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.averia a
             WHERE a.id = averia_recambio.averia_id
               AND (
                   public.usuario_ve_todo_empresa(a.empresa_id)
                   OR (a.local_id IS NOT NULL AND public.usuario_ve_local(a.local_id))
               )
        )
    );
```

- [ ] **Step 3: deudas (credito_local, recuperacion) — vía local**

```sql
-- credito_local: tolva/préstamos del local → por el local directo.
DROP POLICY IF EXISTS credito_local_select ON public.credito_local;
CREATE POLICY credito_local_select ON public.credito_local
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id) OR public.usuario_ve_local(local_id)
    );

-- recuperacion: abono contra un credito_local → por su credito.
-- VERIFICAR la columna FK (se asume credito_local_id) antes de aplicar:
--   grep -n "recuperacion" en 20260612120000_tolva_prestamos_creditos.sql
DROP POLICY IF EXISTS recuperacion_select ON public.recuperacion;
CREATE POLICY recuperacion_select ON public.recuperacion
    FOR SELECT USING (
        public.usuario_ve_todo_empresa(empresa_id)
        OR EXISTS (
            SELECT 1 FROM public.credito_local c
             WHERE c.id = recuperacion.credito_local_id
               AND public.usuario_ve_local(c.local_id)
        )
    );
```

- [ ] **Step 4: `supabase db reset`** → aplica sin error.
- [ ] **Step 5: Commit** `git add supabase/migrations/*_rbac_lectura_policies.sql && git commit -m "feat(supabase): RBAC de lectura estricto por operario (Planificación P2)"`

> **Nota `local_modify`:** es `FOR ALL` (incluye USING para UPDATE/DELETE) con `usuario_es_gestor`. NO se toca: el operario no edita el local, solo lo lee. La lectura va por `local_select`. Igual para el resto de `*_modify`.

---

### Task 4: pgTAP exhaustivo de visibilidad

**Files:** Create `supabase/tests/sql/15_rbac_lectura_operario.sql`

- [ ] **Step 1: Monta el escenario** (BEGIN…ROLLBACK, namespace `c1807…`):
  - Empresa E1. Usuarios: `owner` (ve-todo), `tecA` (técnico), `tecB` (técnico), `cont` (contable).
  - Locales: `L1` (operario = tecA), `L2` (operario = tecB), `L3` (operario = NULL).
  - Por cada local: 1 máquina + 1 licencia + 1 instalación; 1 recaudación firme; 1 lectura_no_recaudada; 1 cambio_placa; 1 credito_local (tolva) + 1 recuperacion; 1 averia (snapshot local_id = ese local).
  - Una avería extra `AVx` con `local_id = NULL` (máquina en almacén).

- [ ] **Step 2: Asserts por rol** (cambiando `SET LOCAL request.jwt.claims` al `sub` de cada uno). Usa `is((SELECT count(*) FROM <tabla>), N)` — RLS filtra el count.

```sql
-- tecA (técnico de L1): ve SOLO lo de L1.
SET LOCAL request.jwt.claims = '{"sub":"<tecA>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local),        1::bigint, 'tecA ve 1 local (L1)');
SELECT is((SELECT count(*) FROM public.instalacion),  1::bigint, 'tecA ve 1 instalacion');
SELECT is((SELECT count(*) FROM public.recaudacion),  1::bigint, 'tecA ve 1 recaudacion');
SELECT is((SELECT count(*) FROM public.maquina),      1::bigint, 'tecA ve 1 maquina (la de L1)');
SELECT is((SELECT count(*) FROM public.licencia),     1::bigint, 'tecA ve 1 licencia');
SELECT is((SELECT count(*) FROM public.cambio_placa), 1::bigint, 'tecA ve 1 cambio_placa');
SELECT is((SELECT count(*) FROM public.lectura_no_recaudada), 1::bigint, 'tecA ve 1 lectura');
SELECT is((SELECT count(*) FROM public.credito_local),1::bigint, 'tecA ve 1 credito');
SELECT is((SELECT count(*) FROM public.recuperacion), 1::bigint, 'tecA ve 1 recuperacion');
SELECT is((SELECT count(*) FROM public.averia),       1::bigint, 'tecA ve 1 averia (snapshot L1)');
SELECT is((SELECT id FROM public.local), '<L1>'::uuid, 'tecA: el local que ve es L1');

-- tecA NO ve L2/L3 ni lo suyo: 0 filas de la máquina de L2.
SELECT is((SELECT count(*) FROM public.maquina WHERE id = '<maqL2>'), 0::bigint, 'tecA no ve la maquina de L2');

-- owner (ve-todo): ve los 3 locales y todo el resto.
SET LOCAL request.jwt.claims = '{"sub":"<owner>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local),  3::bigint, 'owner ve los 3 locales');
SELECT is((SELECT count(*) FROM public.averia), 4::bigint, 'owner ve las 4 averias (incl. local_id NULL)');

-- contable (ve-todo financiero): ve todas las recaudaciones y deudas.
SET LOCAL request.jwt.claims = '{"sub":"<cont>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.recaudacion),  3::bigint, 'contable ve las 3 recaudaciones');
SELECT is((SELECT count(*) FROM public.credito_local),3::bigint, 'contable ve las 3 deudas');

-- L3 (sin operario): ningún técnico lo ve; solo ve-todo.
SET LOCAL request.jwt.claims = '{"sub":"<tecB>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local WHERE id = '<L3>'), 0::bigint, 'L3 sin operario: tecB no lo ve');
```

- [ ] **Step 3: Reasignación transfiere visibilidad** (en el mismo test, como owner):

```sql
SET LOCAL request.jwt.claims = '{"sub":"<owner>","role":"authenticated"}';
SELECT public.actualizar_calendario_local('<L1>', NULL, NULL, '<tecB>');  -- L1 pasa a tecB
-- Ahora tecA NO ve L1 y tecB SÍ.
SET LOCAL request.jwt.claims = '{"sub":"<tecA>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local), 0::bigint, 'tras reasignar, tecA no ve L1');
SET LOCAL request.jwt.claims = '{"sub":"<tecB>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.recaudacion WHERE instalacion_id = '<instL1>'), 1::bigint, 'tras reasignar, tecB ve la recaudacion de L1');
```

- [ ] **Step 4: `supabase test db`** → todo verde (este test + los 14 previos). Ajusta `plan(N)` al número real de asserts.
- [ ] **Step 5: Commit** `git add supabase/tests/sql/15_rbac_lectura_operario.sql && git commit -m "test(supabase): pgTAP de visibilidad estricta por operario (Planificación P2)"`

---

### Task 5: Verificación del cliente (Android + web) — sin romper

> La RLS filtra sola: con la policy estricta, el técnico recibe menos filas. Hay que CONFIRMAR que ningún SELECT del cliente asume "veo todo" y peta con 0 filas, y que el gestor/web no sufre regresión.

- [ ] **Step 1: Android — `SyncRepository`/DAOs.** Verifica que `sync` baja inventario y recaudaciones con SELECT PostgREST sin filtros que dupliquen la RLS, y que un técnico con 0 locales asignados no provoca crash (lista vacía, no error). `JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew assembleDebug` (desde `android/`).
- [ ] **Step 2: Web (gestor).** `npm --prefix web run build && test`. El back-office lo usan roles ve-todo → no debería cambiar nada. La vista `/operarios` y la ficha de local siguen funcionando.
- [ ] **Step 3:** Documenta en el PR: "verificado que el técnico solo recibe sus locales; gestor/web sin regresión". Si algún SELECT del cliente rompe, **ese** arreglo va en su propia tarea (no ampliar el alcance de P2 aquí).

---

## Cierre de P2
- [ ] `supabase db reset` + `supabase test db` → verde (incluye `15_rbac_lectura_operario`).
- [ ] PR(s) a `main` (<400 líneas; squash). T-265 (parte P2) anotado en `.kiro/specs/recre/tasks.md`.
- [ ] **No** se han tocado escrituras ni cálculo de dinero (se verifica en review).

**Salida:** un técnico que abra la app o el back-office solo ve los locales que tiene asignados y todo lo que cuelga de ellos; gestor/admin/owner/contable siguen viendo todo. Queda lista la base para **P3** (la agenda "¿toca hoy?", el héroe del home y el panel de control del gestor), que ya puede asumir visibilidad por operario.

**Diferido a P3:** el cálculo "¿toca? / atrasado", `v_agenda_operario`/`agenda_operario`, el héroe del home Android y el panel del gestor.
