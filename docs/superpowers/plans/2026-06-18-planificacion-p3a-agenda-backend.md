# Planificación de recaudación — P3a: agenda (backend) · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Pasos con checkbox.

**Goal:** Exponer, por local visible, su **estado de agenda** (`sin_planificar` / `al_dia` / `toca_hoy` / `atrasado`) y su próxima fecha programada, derivado del calendario de P1, para que el home Android y el panel del gestor lo consuman.

**Architecture:** Una **VISTA** `public.v_agenda_operario` con `security_invoker = true` (PG15+): la RLS de P2 fluye automáticamente (el técnico solo ve sus locales; ve-todo ve todo). El "hoy" se calcula con `empresa.zona_horaria`. No se persiste nada; no cambia ninguna escritura ni cálculo de dinero.

**Tech Stack:** Supabase (Postgres + pgTAP). Solo lectura, aditivo.

**Spec:** `docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md` §4.2 (regla "¿toca?"), §6 (superficies que la consumen), §9 (zona horaria).

## Decisión de diseño (para revisar)
- **Vista (no RPC).** `v_agenda_operario` con `security_invoker=true`: se consulta como una tabla (PostgREST `from('v_agenda_operario')`), la RLS estricta de P2 la filtra sola, y la misma vista sirve al home Android (técnico → solo lo suyo) y al panel del gestor (ve-todo → agrupa por operario). Alternativa descartada: RPC `agenda_operario(p_empresa_id)` (más verboso de consumir; la vista es más natural para filtrar/ordenar/agrupar desde el cliente).

## Regla "¿toca?" (del spec §4.2)
Para un local *planificado* con fecha de inicio `F`, cadencia `C` (semanas), evaluado en `hoy` (TZ de la empresa):
- `S` (fecha programada vigente) = `F + floor((hoy − F)/(7C))·7C` días (la mayor fecha programada ≤ hoy). Si `hoy < F` → aún no empezó.
- **Atendido** = existe recaudación `firme` **o** `lectura_no_recaudada`, para CUALQUIER instalación del local, con `fecha::date` (en TZ empresa) ∈ `[S, hoy]`.
- **Estado:** `sin_planificar` (cadencia NULL) · `al_dia` (`hoy<F` **o** atendido) · `toca_hoy` (`S=hoy` y no atendido) · `atrasado` (`S<hoy` y no atendido).

---

## File Structure

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `supabase/migrations/<ts>_v_agenda_operario.sql` | Vista `v_agenda_operario` + GRANT SELECT | **Crear** |
| `supabase/tests/sql/16_agenda_operario.sql` | pgTAP del cálculo de estado + RLS | **Crear** |

---

### Task 1: Vista `v_agenda_operario`

**Files:** Create `supabase/migrations/<ts>_v_agenda_operario.sql`

- [ ] **Step 1: Escribe la vista**

```sql
-- =============================================================================
-- Planificación P3a — vista de agenda por local (estado "¿toca?").
-- security_invoker: la RLS de P2 filtra (técnico → solo sus locales; ve-todo →
-- todo). "hoy" en la zona horaria de la empresa. No persiste nada. Spec §4.2.
-- =============================================================================
CREATE OR REPLACE VIEW public.v_agenda_operario
WITH (security_invoker = true) AS
WITH base AS (
    SELECT
        l.id          AS local_id,
        l.empresa_id,
        l.nombre,
        l.operario_id,
        l.cadencia_semanas,
        l.fecha_inicio_recaudacion,
        e.zona_horaria                                   AS tz,
        (now() AT TIME ZONE e.zona_horaria)::date        AS hoy
    FROM public.local l
    JOIN public.empresa e ON e.id = l.empresa_id
),
calc AS (
    SELECT b.*,
        CASE
            WHEN b.cadencia_semanas IS NULL THEN NULL
            WHEN b.hoy < b.fecha_inicio_recaudacion THEN b.fecha_inicio_recaudacion
            ELSE b.fecha_inicio_recaudacion
                 + (((b.hoy - b.fecha_inicio_recaudacion) / (7 * b.cadencia_semanas))
                    * (7 * b.cadencia_semanas))
        END AS fecha_programada_vigente
    FROM base b
)
SELECT
    c.local_id,
    c.empresa_id,
    c.nombre,
    c.operario_id,
    c.cadencia_semanas,
    c.fecha_inicio_recaudacion,
    c.fecha_programada_vigente,
    CASE
        WHEN c.cadencia_semanas IS NULL THEN 'sin_planificar'
        WHEN c.hoy < c.fecha_inicio_recaudacion THEN 'al_dia'
        WHEN EXISTS (
            SELECT 1
              FROM public.instalacion i
             WHERE i.local_id = c.local_id
               AND (
                   EXISTS (
                       SELECT 1 FROM public.recaudacion r
                        WHERE r.instalacion_id = i.id
                          AND r.estado = 'firme'
                          AND (r.fecha AT TIME ZONE c.tz)::date
                              BETWEEN c.fecha_programada_vigente AND c.hoy
                   )
                   OR EXISTS (
                       SELECT 1 FROM public.lectura_no_recaudada lnr
                        WHERE lnr.instalacion_id = i.id
                          AND (lnr.fecha AT TIME ZONE c.tz)::date
                              BETWEEN c.fecha_programada_vigente AND c.hoy
                   )
               )
        ) THEN 'al_dia'
        WHEN c.fecha_programada_vigente = c.hoy THEN 'toca_hoy'
        ELSE 'atrasado'
    END AS estado,
    (SELECT count(*) FROM public.instalacion i2
      WHERE i2.local_id = c.local_id AND i2.estado = 'activa') AS n_maquinas
FROM calc c;

COMMENT ON VIEW public.v_agenda_operario IS
    'Agenda por local: estado "¿toca?" (sin_planificar/al_dia/toca_hoy/atrasado) + fecha programada vigente, derivado del calendario (P1). security_invoker → respeta la RLS estricta (P2). "hoy" en empresa.zona_horaria. Spec §4.2.';

GRANT SELECT ON public.v_agenda_operario TO authenticated;
```

- [ ] **Step 2: `supabase db reset`** → aplica sin error.
- [ ] **Step 3: Commit** `git add supabase/migrations/*_v_agenda_operario.sql && git commit -m "feat(supabase): vista v_agenda_operario (estado de agenda por local) (Planificación P3a)"`

---

### Task 2: pgTAP del cálculo de estado

**Files:** Create `supabase/tests/sql/16_agenda_operario.sql`

> Como en P2: setup como superusuario, luego `SET LOCAL ROLE authenticated` + `request.jwt.claims` para verificar la RLS. Empresa con `zona_horaria='UTC'` para que `hoy = (now() AT TIME ZONE 'UTC')::date` sea determinista. Las fechas de inicio se fijan relativas a ese "hoy".

- [ ] **Step 1: Escribe el test** (namespace `c1808…`):
  - Empresa E1 `zona_horaria='UTC'`. Usuarios: owner (ve-todo), tecA (técnico).
  - Locales de tecA (todos con operario = tecA), cada uno con su máquina/licencia/instalación:
    - `L_hoy`:      `F = hoy`,        `C=1` → `S=hoy`, sin visita → **toca_hoy**.
    - `L_atras`:    `F = hoy − 10`,   `C=1` → `S=hoy−3`, sin visita → **atrasado**.
    - `L_futuro`:   `F = hoy + 7`,    `C=1` → `hoy<F` → **al_dia**.
    - `L_atendido`: `F = hoy`,        `C=1`, con **recaudación firme** hoy → **al_dia**.
    - `L_lectura`:  `F = hoy`,        `C=1`, con **lectura_no_recaudada** hoy → **al_dia**.
    - `L_sinplan`:  cadencia NULL → **sin_planificar**.
  - Un local de OTRO operario (`L_otro`, operario = owner) para la prueba de RLS.

```sql
-- Ejemplos de aserciones (estado por local, como tecA):
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"<tecA>","role":"authenticated"}';
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id='<L_hoy>'),      'toca_hoy',       'L_hoy: toca hoy');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id='<L_atras>'),    'atrasado',       'L_atras: atrasado');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id='<L_futuro>'),   'al_dia',         'L_futuro: al dia (aun no empieza)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id='<L_atendido>'), 'al_dia',         'L_atendido: al dia (recaudado en ciclo)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id='<L_lectura>'),  'al_dia',         'L_lectura: al dia (lectura en ciclo)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id='<L_sinplan>'),  'sin_planificar', 'L_sinplan: sin planificar');
-- fecha programada vigente:
SELECT is((SELECT fecha_programada_vigente FROM public.v_agenda_operario WHERE local_id='<L_atras>'),
          (now() AT TIME ZONE 'UTC')::date - 3, 'L_atras: S = hoy-3');
-- RLS: tecA NO ve el local de otro operario, y solo ve sus filas.
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE local_id='<L_otro>'), 0::bigint, 'tecA no ve L_otro en la agenda');
SELECT is((SELECT count(*) FROM public.v_agenda_operario), 6::bigint, 'tecA ve sus 6 locales en la agenda');
-- owner (ve-todo) ve los 7.
SET LOCAL request.jwt.claims = '{"sub":"<owner>","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.v_agenda_operario), 7::bigint, 'owner ve los 7 locales');
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE estado='toca_hoy'),  1::bigint, 'owner: 1 toca_hoy');
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE estado='atrasado'),  1::bigint, 'owner: 1 atrasado');
RESET ROLE;
```

  Los INSERT de recaudación/lectura siguen el patrón de `15_rbac_lectura_operario.sql` (desglose válido que suma al importe; `recuperado_total=0`). Ajusta `plan(N)` al nº real de asserts.

- [ ] **Step 2: `supabase test db`** → verde (este test + los 15 previos).
- [ ] **Step 3: Commit** `git add supabase/tests/sql/16_agenda_operario.sql && git commit -m "test(supabase): pgTAP de v_agenda_operario (estado de agenda) (Planificación P3a)"`

---

## Cierre de P3a
- [ ] `supabase db reset` + `supabase test db` → verde.
- [ ] PR a `main` (<400 líneas; squash). T-266 (parte backend) anotado en tasks.md.
- [ ] No se han tocado escrituras ni cálculo de dinero.

**Salida:** la base `v_agenda_operario` lista para que **P3b** (panel del gestor web) y **P3c** (home/agenda Android) la consuman. La misma vista da, por local, el estado y la fecha programada, respetando quién ve qué.
