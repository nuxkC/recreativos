# Planificación de recaudación — P1: Datos + configuración (web) · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans para implementar tarea a tarea. Pasos con checkbox (`- [ ]`).

**Goal:** Permitir que un gestor configure, por local, **cada cuántas semanas** y **desde qué fecha** se recauda, y **qué operario** lo lleva — desde el formulario de instalación y la ficha de local. Sin cambiar todavía las lecturas (eso es P2) ni la agenda (P3).

**Architecture:** Migración aditiva en `local` (3 columnas), una RPC `actualizar_calendario_local` (ficha) y la extensión de `crear_instalacion` (fija el calendario del local al crear la 1ª instalación), ambas `SECURITY DEFINER` con validación de rol (patrón del repo). Web: el formulario de instalación gana cadencia + fecha de inicio (con aviso al añadir 2ª máquina), la ficha de local gana calendario + operario, y una vista de "operarios/rutas". Nada de esto cambia las policies de lectura (P1 solo añade).

**Tech Stack:** Supabase (Postgres + pgTAP + Deno), Next.js 14 (App Router, Server Actions, zod, react-hook-form), TypeScript strict.

**Spec:** `docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md` — §4 (datos + "¿toca?"), §6.1 (web), §7 (P1).

## Invariantes
- **Migración aditiva e inmutable.** Formato `YYYYMMDDHHMMSS_descripcion.sql`. No editar migraciones aplicadas.
- **Escrituras solo vía RPC `SECURITY DEFINER`** que valida rol (`usuario_es_gestor`) y tenant. No abrir escritura directa de columnas nuevas a `authenticated`.
- **No tocar el cálculo de dinero.** Cadencia/fecha solo planifican *cuándo*; la tasa sigue por semana ISO.
- **Tipos BBDD regenerados** con `supabase gen types`, nunca a mano.
- **Coherencia**: `cadencia_semanas` y `fecha_inicio_recaudacion` van juntas (ambas NULL o ambas no-NULL).

## Comandos
- Migración nueva: `supabase migration new <desc>` (desde la raíz; genera el timestamp).
- Aplicar + seed: `supabase db reset`.
- pgTAP: `supabase test db` (o `psql ... -f supabase/tests/sql/<f>.sql`).
- Tipos: `supabase gen types typescript --local > web/src/lib/database.types.ts` (ajustar a la ruta real del proyecto).
- Web: `npm --prefix web run lint && npm --prefix web run test && npm --prefix web run build`.

---

## File Structure

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `supabase/migrations/<ts>_local_calendario_recaudacion.sql` | 3 columnas en `local` + CHECK + índice | **Crear** |
| `supabase/migrations/<ts>_rpc_actualizar_calendario_local.sql` | RPC para fijar calendario + operario (ficha) | **Crear** |
| `supabase/migrations/<ts>_crear_instalacion_con_calendario.sql` | Extiende `crear_instalacion` para fijar el calendario del local | **Crear** |
| `supabase/tests/sql/<n>_calendario_local.sql` | pgTAP de las RPC + constraint | **Crear** |
| `web/src/lib/database.types.ts` (o ruta real) | Tipos regenerados | **Regenerar** |
| `web/src/lib/locales/schemas.ts` + `actions.ts` | Schema/acción `actualizarCalendarioLocal` | **Modificar/Crear** |
| `web/src/lib/instalaciones/schemas.ts` + `actions.ts` | Añadir cadencia + fecha al input/acción | **Modificar** |
| `web/src/components/instalaciones/instalacion-form.tsx` | Campos cadencia + fecha + aviso 2ª máquina | **Modificar** |
| `web/src/components/locales/local-form.tsx` (o ficha) | Editar calendario + desplegable operario | **Modificar** |
| `web/src/app/(dashboard)/operarios/page.tsx` | Vista "qué lleva cada operario" | **Crear** |

---

### Task 1: Migración — columnas de calendario + asignación en `local`

**Files:** Create `supabase/migrations/<ts>_local_calendario_recaudacion.sql`

- [ ] **Step 1: Genera la migración**

Run: `supabase migration new local_calendario_recaudacion`

- [ ] **Step 2: Escribe el SQL**

```sql
-- Calendario de recaudación por local (Planificación P1).
-- Aditivo: NULL = local sin planificar / sin operario. No toca lecturas (P2).
alter table public.local
  add column cadencia_semanas smallint
    check (cadencia_semanas is null or cadencia_semanas > 0),
  add column fecha_inicio_recaudacion date,
  add column operario_id uuid references public.usuario(id),
  add constraint local_calendario_coherente
    check ((cadencia_semanas is null) = (fecha_inicio_recaudacion is null));

comment on column public.local.cadencia_semanas is
  'Cada cuántas semanas se recauda el local (NULL = sin planificar). "Mensual" = 4.';
comment on column public.local.fecha_inicio_recaudacion is
  'Fecha ancla del calendario; el día de la semana de recaudación sale de aquí.';
comment on column public.local.operario_id is
  'Operario (usuario) responsable del local; NULL = sin asignar.';

-- Resuelve rápido "locales de un operario" (agenda P3, RLS P2).
create index idx_local_operario on public.local (operario_id)
  where operario_id is not null;
```

- [ ] **Step 3: Aplica** → `supabase db reset` (BUILD OK, sin errores).
- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/*_local_calendario_recaudacion.sql
git commit -m "feat(supabase): calendario de recaudación por local (cadencia/fecha/operario) (Planificación P1)"
```

---

### Task 2: RPC `actualizar_calendario_local` (ficha de local) + pgTAP

**Files:**
- Create `supabase/migrations/<ts>_rpc_actualizar_calendario_local.sql`
- Create/extend `supabase/tests/sql/<n>_calendario_local.sql`

- [ ] **Step 1: Escribe la migración de la RPC**

```sql
create or replace function public.actualizar_calendario_local(
  p_local_id uuid,
  p_cadencia_semanas smallint,
  p_fecha_inicio_recaudacion date,
  p_operario_id uuid
) returns void
language plpgsql security definer set search_path = public as $$
declare v_empresa_id uuid;
begin
  select empresa_id into v_empresa_id from local where id = p_local_id;
  if v_empresa_id is null then
    raise exception 'Local no encontrado' using errcode = '42704';
  end if;
  if not usuario_es_gestor(v_empresa_id) then
    raise exception 'No autorizado' using errcode = '42501';
  end if;

  -- Coherencia: cadencia y fecha van juntas; cadencia > 0.
  if (p_cadencia_semanas is null) <> (p_fecha_inicio_recaudacion is null) then
    raise exception 'cadencia y fecha de inicio deben ir juntas' using errcode = '22023';
  end if;
  if p_cadencia_semanas is not null and p_cadencia_semanas <= 0 then
    raise exception 'la cadencia debe ser mayor que 0' using errcode = '22023';
  end if;

  -- El operario debe ser miembro OPERATIVO ACTIVO de la misma empresa.
  if p_operario_id is not null and not exists (
    select 1 from empresa_usuario
    where empresa_id = v_empresa_id and usuario_id = p_operario_id
      and activo = true and rol in ('owner','admin','gestor','tecnico')
  ) then
    raise exception 'El operario no es un miembro operativo activo de la empresa'
      using errcode = '42501';
  end if;

  update local set
    cadencia_semanas = p_cadencia_semanas,
    fecha_inicio_recaudacion = p_fecha_inicio_recaudacion,
    operario_id = p_operario_id,
    updated_at = now()
  where id = p_local_id;
end $$;

revoke all on function public.actualizar_calendario_local(uuid, smallint, date, uuid) from public, anon;
grant execute on function public.actualizar_calendario_local(uuid, smallint, date, uuid) to authenticated;
```

- [ ] **Step 2: pgTAP** (`BEGIN…ROLLBACK`, claims de un gestor y de un técnico de prueba; sin depender de `seed.sql`). Cubre: gestor fija calendario+operario OK; técnico → 42501; cadencia sin fecha → 22023; operario de otra empresa → 42501; cadencia 0 → 22023. Patrón de los tests SQL existentes (`set local role authenticated; set local request.jwt.claims ...`).

- [ ] **Step 3: Ejecuta** → `supabase test db` → todo verde.
- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/*_rpc_actualizar_calendario_local.sql supabase/tests/sql/*_calendario_local.sql
git commit -m "feat(supabase): RPC actualizar_calendario_local + pgTAP (Planificación P1)"
```

---

### Task 3: Extender `crear_instalacion` para fijar el calendario del local

**Files:** Create `supabase/migrations/<ts>_crear_instalacion_con_calendario.sql`

> Recrea `crear_instalacion` (firma vigente en `20260612120000_tolva_prestamos_creditos.sql:396`) añadiendo **al final** dos params opcionales de calendario. Si vienen, fija el calendario del local en la misma transacción (NO toca operario). Mantiene la validación de gestor y el aislamiento de tenant existentes — **copia el cuerpo vigente y añade el bloque**; no inventes lógica nueva.

- [ ] **Step 1: Lee** el cuerpo vigente de `crear_instalacion` (`20260612120000_tolva_prestamos_creditos.sql:396-...`).

- [ ] **Step 2: Recréala** con `create or replace function public.crear_instalacion(<params vigentes>, p_cadencia_semanas smallint default null, p_fecha_inicio_recaudacion date default null) returns uuid`. Al final del cuerpo, **antes del `return`**, añade:

```sql
  -- Calendario del local (Planificación P1): si el formulario lo aporta, lo fija.
  if p_cadencia_semanas is not null or p_fecha_inicio_recaudacion is not null then
    if (p_cadencia_semanas is null) <> (p_fecha_inicio_recaudacion is null) then
      raise exception 'cadencia y fecha de inicio deben ir juntas' using errcode = '22023';
    end if;
    if p_cadencia_semanas <= 0 then
      raise exception 'la cadencia debe ser mayor que 0' using errcode = '22023';
    end if;
    update public.local
      set cadencia_semanas = p_cadencia_semanas,
          fecha_inicio_recaudacion = p_fecha_inicio_recaudacion,
          updated_at = now()
      where id = p_local_id;  -- p_local_id ya validado como de la empresa arriba
  end if;
```

`revoke/grant` igual que la versión vigente. (La firma antigua deja de usarse; los nuevos params son opcionales, así que los llamadores actuales no rompen — pero la web pasará a la firma nueva en Task 5.)

- [ ] **Step 3: pgTAP** (añade casos al fichero de Task 2): crear instalación con cadencia+fecha fija el calendario del local; con solo uno → 22023.

- [ ] **Step 4: `supabase db reset` + `supabase test db`** → verde.
- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/*_crear_instalacion_con_calendario.sql supabase/tests/sql/*_calendario_local.sql
git commit -m "feat(supabase): crear_instalacion fija el calendario del local (Planificación P1)"
```

---

### Task 4: Regenerar tipos + schemas web

**Files:** Regenera tipos; `web/src/lib/instalaciones/schemas.ts`, `web/src/lib/locales/schemas.ts`

- [ ] **Step 1: Regenera** los tipos con `supabase gen types` a la ruta real del proyecto (confírmala; no editar a mano).
- [ ] **Step 2: `InstalacionInputSchema`** (`web/src/lib/instalaciones/schemas.ts:70`) — añade:

```ts
cadenciaSemanas: z.coerce.number().int().positive().nullable(),
fechaInicioRecaudacion: z.string().date().nullable(),
// regla: ambas o ninguna
```
con un `.refine(d => (d.cadenciaSemanas == null) === (d.fechaInicioRecaudacion == null), 'La cadencia y la fecha de inicio van juntas')`.

- [ ] **Step 3: `CalendarioLocalInputSchema`** (nuevo, en `web/src/lib/locales/schemas.ts`): `localId`, `cadenciaSemanas` (nullable positive int), `fechaInicioRecaudacion` (nullable date), `operarioId` (nullable uuid), con el mismo `.refine` de coherencia.

- [ ] **Step 4: `npm --prefix web run lint`** → OK. **Commit.**

```bash
git add web/src/lib/database.types.ts web/src/lib/instalaciones/schemas.ts web/src/lib/locales/schemas.ts
git commit -m "feat(web): tipos + schemas de calendario de recaudación (Planificación P1)"
```

---

### Task 5: Formulario de instalación — cadencia + fecha + aviso 2ª máquina

**Files:** `web/src/lib/instalaciones/actions.ts`, `web/src/components/instalaciones/instalacion-form.tsx`

- [ ] **Step 1: Acción** `crearInstalacion` (`actions.ts:88`) — pasa los nuevos params a la RPC: `p_cadencia_semanas`, `p_fecha_inicio_recaudacion` (junto a los existentes en la llamada `supabase.rpc("crear_instalacion", {...})`, `actions.ts:161`). Mantiene `requireRol(ROLES_GESTION)`.

- [ ] **Step 2: Form** (`instalacion-form.tsx`) — tras elegir `localId`, **carga el calendario actual del local** (consulta `local.cadencia_semanas, fecha_inicio_recaudacion`):
  - Si el local **no** tiene calendario → muestra los campos *Cadencia* (select con atajos 1/2/4 + opción "otro" para número libre) y *Fecha de inicio* (date), **obligatorios**.
  - Si **ya** tiene → precarga los valores; si el usuario los edita, muestra el aviso (componente de confirmación existente / `AlertDialog`): *"Este local ya se recauda {cada N semanas, desde DD/MM}. Si lo cambias, cambia para todas sus máquinas."* y exige confirmar antes de enviar.

```tsx
// Atajos de cadencia (valor libre permitido via "otro")
const CADENCIAS = [
  { label: "Semanal", semanas: 1 },
  { label: "Quincenal", semanas: 2 },
  { label: "Cada 4 semanas (mensual)", semanas: 4 },
];
```

- [ ] **Step 3: `npm --prefix web run build`** → OK. Prueba manual: crear 1ª instalación fija el calendario; crear 2ª con cambio dispara el aviso.

- [ ] **Step 4: Commit**

```bash
git add web/src/lib/instalaciones/actions.ts web/src/components/instalaciones/instalacion-form.tsx
git commit -m "feat(web): formulario de instalación con cadencia/fecha y aviso de 2ª máquina (Planificación P1)"
```

---

### Task 6: Ficha de local — editar calendario + operario

**Files:** `web/src/lib/locales/actions.ts`, el form/ficha de local (`web/src/components/locales/local-form.tsx` o la página `locales/[id]`)

- [ ] **Step 1: Acción** `actualizarCalendarioLocal` (nueva, en `web/src/lib/locales/actions.ts`): valida con `CalendarioLocalInputSchema` + `requireRol(ROLES_GESTION)`, llama `supabase.rpc("actualizar_calendario_local", {...})`, `revalidatePath` de la ficha.

- [ ] **Step 2: UI** en la ficha del local: sección "Recaudación" con *Cadencia* + *Fecha de inicio* (mismos controles que Task 5) y un desplegable **Operario** poblado con los miembros operativos activos de la empresa (`empresa_usuario` rol ∈ operativos, `activo`). Un valor "Sin asignar" (NULL).

- [ ] **Step 3: `npm --prefix web run build`** → OK. Prueba: editar calendario y asignar operario desde la ficha.

- [ ] **Step 4: Commit**

```bash
git add web/src/lib/locales/actions.ts web/src/components/locales/
git commit -m "feat(web): ficha de local edita calendario y operario asignado (Planificación P1)"
```

---

### Task 7: Vista "operarios / rutas"

**Files:** `web/src/app/(dashboard)/operarios/page.tsx` (+ entrada en el menú lateral)

> Versión P1: lista de operarios con los locales que llevan (sin estado pendiente/atrasado todavía — eso llega en P3, cuando exista el cálculo de la agenda).

- [ ] **Step 1: Página** (Server Component, `requireRol(ROLES_GESTION)`): por cada miembro operativo activo, lista sus locales (`local WHERE operario_id = miembro`), con un contador. Locales `operario_id IS NULL` agrupados en "Sin asignar". Tabla/cards reusando los componentes UI existentes del back-office.

- [ ] **Step 2: Enlace** en el sidebar (solo visible para `ROLES_GESTION`, con `rolCumple`).

- [ ] **Step 3: `npm --prefix web run lint && build`** → OK. **Commit.**

```bash
git add web/src/app/\(dashboard\)/operarios/ web/src/components/  # ajustar a los ficheros tocados
git commit -m "feat(web): vista operarios/rutas (asignación de locales) (Planificación P1)"
```

---

## Cierre de P1

- [ ] `supabase test db` → todo verde (constraints + RPC + crear_instalacion).
- [ ] `npm --prefix web run lint && test && build` → verde.
- [ ] PR(s) a `main` (agrupar backend y web en PRs <400 líneas; squash). Tareas `T-XX` nuevas anotadas en `.kiro/specs/recre/tasks.md`.
- [ ] **No** se han tocado las policies de lectura ni el cálculo de dinero (se verifica en review).

**Salida:** los gestores ya pueden definir, por local, cadencia + fecha de inicio (desde la instalación o la ficha) y asignar un operario, y ver qué lleva cada uno. Queda lista la base para **P2** (lecturas estrictas por operario) y **P3** (la agenda + el "por recaudar").

**Diferido a P2/P3:** ninguna restricción de lectura por operario (P2); el cálculo "¿toca? / atrasado", el héroe del home, la agenda Android y el panel de control del gestor (P3).
