# Planificación de recaudación — cadencia, asignación y agenda · Design

**Fecha:** 2026-06-18
**Estado:** diseño aprobado en brainstorming; pendiente de revisión del usuario antes de planificar.
**Tipo:** feature nueva de producto (NO es parte del rediseño UI Fases 0–5, ni del "Histórico v2 a escala", que sigue pendiente aparte). Su fase P3 se apoya en el home de Locales ya rediseñado (rediseño Fase 2a).

---

## 1. Contexto y problema

Hoy el sistema **no sabe qué hay que recaudar, ni cuándo, ni quién**. Hallazgos verificados (ver memorias `recaudacion-modelo-derivado-sin-cadencia`, `roles-lecturas-no-restringidas`):

1. **No existe el estado "por recaudar".** No hay flag ni cadencia. El "importe a recaudar" se deriva en el momento de los contadores físicos vs la *baseline* (contadores de la última recaudación firme, vía `obtener_baseline`). Una máquina vuelve a ser recaudable **cuando la gente juega** (sube el contador), no cuando pasa el tiempo. El tiempo (`semanas_iso_entre`) solo escala la tasa fiscal.
2. **No hay frecuencia/cadencia pactada por local ni "próxima fecha de recaudación".** El tipo de alerta `local_sin_recaudar` existe en el esquema pero **nadie lo genera**.
3. **No hay asignación de locales a operarios** (ni tabla ni columna).
4. **Las lecturas no distinguen rol.** Las escrituras sí están gateadas por rol en tres capas (RLS + Edge + web actions), pero `local_select`/`recaudacion_select`/etc. usan solo `usuario_pertenece_a_empresa`. El "técnico solo ve las suyas" del histórico es un filtro **client-side** (eludible por API).

Consecuencia: en el rediseño UI (Fase 2a) el héroe "X por recaudar" y los filtros del home **se quedaron sin dato** y se difirieron. Esta feature lo resuelve de raíz.

## 2. Objetivo

Que el sistema sepa **qué recaudar, cuándo y quién**, de forma que:
- Los gestores definan, por local, **cada cuántas semanas** se recauda y **desde qué fecha**, y **a qué operario** se asigna.
- Cada operario vea, en su app, **su ruta**: qué locales le tocan hoy / esta semana / van atrasados — y solo los suyos.
- El "por recaudar" deje de estar cojo (alimenta el héroe y el orden del home ya rediseñado).

## 3. Decisiones tomadas (brainstorming)

1. **Granularidad: por LOCAL.** El calendario (cadencia + fecha de inicio) es propiedad del `local`. El operario visita el local y recauda todas sus máquinas. (Precedente del repo: `local.porcentaje_recuperacion` como override por-local.)
2. **Se fija al crear la instalación.** El formulario de instalación, que ya pide máquina/tasa/%, añade cadencia + fecha de inicio (que se escriben en el local). Al añadir una **segunda** máquina a un local que ya tiene calendario: el formulario lo muestra y, si se cambia, **avisa** ("cambia para todas las máquinas del local") y pide confirmación. También editable desde la ficha del local.
3. **El "¿toca?" es un calendario anclado a una fecha de inicio**, no se cuenta desde la última recaudación (eso fallaba: una visita sin recaudación no reiniciaría el reloj).
4. **El ciclo se cierra al VISITAR**, no solo al recaudar: cuenta una recaudación firme **o** una `lectura_no_recaudada` (visita en la que no procedía). Resuelve el problema del "volver la semana 3".
5. **Un operario por local** (1:N). Basta una columna FK; la cobertura por baja la resuelve el gestor reasignando.
6. **Lecturas estrictas por rol**: el técnico solo VE y recauda sus locales asignados, **forzado en la base de datos** (RLS). Gestor/admin/owner ven todo. El móvil del técnico sincroniza solo lo suyo.
7. **La agenda vive en el home de Locales** ya rediseñado (no pantalla nueva): sus locales, ordenados toca/atrasado primero, con héroe real y estado por local.
8. **Fuera de v1:** notificaciones push, "día de recaudación por defecto" a nivel empresa (el día sale de la fecha de inicio), y poda de la cola de subida (deuda técnica aparte).

## 4. Modelo de datos

### 4.1 Campos nuevos en `local`

Migración aditiva (`ALTER TABLE public.local`):

- `cadencia_semanas smallint NULL` — cada cuántas semanas se recauda. `CHECK (cadencia_semanas IS NULL OR cadencia_semanas > 0)`. UI: número libre **+ atajos** 1 (semanal) / 2 (quincenal) / 4 (cada 4 semanas). **"Mensual" se modela como cada 4 semanas** (mantiene el día de la semana fijo y el cálculo por semanas limpio); el mes de calendario real queda fuera de v1.
- `fecha_inicio_recaudacion date NULL` — fecha ancla. El día de la semana de las recaudaciones sale de aquí (todas las fechas programadas caen en su mismo día, porque la cadencia es en semanas enteras).
- `operario_id uuid NULL REFERENCES public.usuario(id)` — responsable del local (uno). NULL = sin asignar.

**Coherencia:** un local está *planificado* solo si tiene cadencia **y** fecha de inicio. `CHECK ((cadencia_semanas IS NULL) = (fecha_inicio_recaudacion IS NULL))`. Los locales preexistentes quedan con ambos NULL = *sin planificar* (no entran en la lógica de agenda hasta configurarse).

**Validación del operario** (no expresable en CHECK): `operario_id`, si no es NULL, debe ser un `usuario` miembro **activo** de la misma empresa con rol operativo (`tecnico`+). Se valida en la RPC de escritura (no por trigger, siguiendo el patrón del repo de validar en `SECURITY DEFINER`).

### 4.2 La regla "¿toca?" (derivada, no se persiste)

Para un local *planificado* con fecha de inicio `F` y cadencia `C` (semanas), evaluado en el "hoy" de la zona horaria de la empresa (`empresa.zona_horaria`):

- **Fechas programadas:** `F`, `F + C·7 días`, `F + 2C·7 días`, … (todas el mismo día de la semana que `F`).
- **Fecha programada vigente** `S` = la mayor fecha programada `≤ hoy`. (Fórmula: `S = F + floor((hoy − F) / (7·C)) · 7·C días`.) Si `hoy < F`, el local aún no ha empezado → estado `al_dia` (programado, no pendiente).
- **Atendido este ciclo** = existe una recaudación con `estado='firme'` **o** una fila en `lectura_no_recaudada`, para **cualquier** instalación del local, con `fecha ∈ [S, hoy]`.
- **Estado del local:**
  - `sin_planificar` — sin calendario (NULL).
  - `al_dia` — `hoy < F`, **o** atendido este ciclo.
  - `toca_hoy` — `S = hoy` y no atendido este ciclo.
  - `atrasado` — `S < hoy` y no atendido este ciclo.

"Pendiente" = `toca_hoy ∪ atrasado`. Un local pendiente sigue apareciendo hasta que se visita (no se "salta" al siguiente ciclo sin atender). El héroe **"X por recaudar"** = nº de locales del operario con estado pendiente.

> Nota de definición (DECIDIDO): "atendido = **cualquier** máquina del local visitada en el ciclo". Razón: el operario hace el local entero en una sola visita —no es óptimo volver otro día al mismo bar—, así que se entiende que si fue, recaudó todas. Basta una recaudación firme o `lectura_no_recaudada` en el ciclo para dar el local por atendido. No se exige máquina por máquina.

Esta derivación se expone vía una **vista o RPC** (`v_agenda_operario` / `agenda_operario(p_empresa_id)`) que devuelve, por local visible para el usuario: `local_id`, nombre, `operario_id`, `cadencia_semanas`, `fecha_inicio_recaudacion`, `fecha_programada_vigente`, `estado` (sin_planificar/al_dia/toca_hoy/atrasado), y nº de máquinas. Respeta la RLS (un técnico solo ve sus locales). La función SQL crítica lleva tests pgTAP.

## 5. Visibilidad — RBAC de lectura estricto (P2)

Hoy las lecturas son por pertenencia a empresa. Se endurecen para que un **técnico** solo lea sus locales asignados; **gestor/admin/owner** siguen viendo todo.

Cambios de política (RLS) `USING (...)`, patrón común: `usuario_es_gestor(empresa_id) OR <pertenece a un local asignado a auth.uid()>`:

- `local`: `usuario_es_gestor(empresa_id) OR operario_id = auth.uid()`.
- `instalacion`, `recaudacion`, `cambio_placa`, `lectura_no_recaudada`, `averia`: vía la instalación → `EXISTS (local l WHERE l.id = <…>.local_id AND (usuario_es_gestor(l.empresa_id) OR l.operario_id = auth.uid()))`. (recaudacion/averia llegan al local a través de `instalacion`.)
- `maquina` / `licencia` (catálogo): visibles para el técnico solo si están instaladas en uno de sus locales (`EXISTS instalacion en mis locales`). Alternativa más simple si se considera no sensible: dejarlas por pertenencia a empresa. **Decisión:** estricto también (coherencia), salvo que la revisión diga lo contrario.

Helper SQL nuevo recomendado para no repetir el subquery: `usuario_ve_local(p_local_id uuid)` `SECURITY DEFINER STABLE` = gestor ∨ operario del local.

**Sincronización Android:** `SyncRepository` baja el inventario con SELECT PostgREST; con la RLS estricta, el técnico recibe **solo sus locales/instalaciones/recaudaciones automáticamente** (la RLS filtra; no hace falta cambiar el query, pero hay que **verificar** que ningún SELECT asume ver todo y rompe). Beneficio: menos datos en el móvil.

**Riesgo (la parte delicada):** tocar las policies de varias tablas a la vez. Mitigación: pgTAP exhaustivo (técnico ve solo lo asignado; gestor ve todo; local sin operario solo lo ve gestor; reasignar transfiere visibilidad) **antes** de tocar el cliente. Migración aditiva (DROP+CREATE de las policies SELECT en una migración nueva con timestamp).

## 6. Superficies

### 6.1 Web — gestión (rol gestor+)

- **Formulario de instalación** (`web/.../instalaciones`): añade *cadencia* + *fecha de inicio*, que se escriben en el `local`. Lógica:
  - Si el local **no** tiene calendario → los campos son obligatorios y lo fijan.
  - Si **ya** lo tiene → se muestran rellenos; editarlos dispara aviso de confirmación ("cambia para todas las máquinas del local").
  - La RPC `crear_instalacion` se amplía (o se acompaña de `configurar_calendario_local`) para fijar/actualizar el calendario del local de forma transaccional. (Forma exacta en el plan; valida `usuario_es_gestor`.)
- **Ficha del local** (`web/.../locales/[id]`): ve y edita `cadencia` + `fecha_inicio` + `operario_id` (desplegable de técnicos de la empresa). RPC nueva `actualizar_calendario_local(p_local_id, p_cadencia_semanas, p_fecha_inicio_recaudacion, p_operario_id)` (`SECURITY DEFINER`, valida gestor + que el operario sea técnico activo de la empresa).
- **Vista "operarios / rutas"**: lista de operarios con cuántos/qué locales lleva, para repartir y reasignar de un vistazo (parte de **P1**). En **P3**, cuando ya existe el cálculo de la agenda, esta vista se convierte en el **panel de control del gestor**: muestra además qué locales de la empresa están **pendientes/atrasados y quién los lleva** ("8 locales atrasados: 5 de Juan, 3 de Pedro"), para que el jefe vigile que nadie se queda atrás.

> **Operario asignable:** `operario_id` puede ser cualquier miembro **activo** de la empresa con rol operativo (`tecnico`+, incluidos gestores/admin que también hagan ruta). El desplegable de la ficha del local lista esos miembros.

### 6.2 Android — agenda del técnico (P3)

- El **home de Locales** (ya rediseñado en Fase 2a) consume la agenda (`agenda_operario`): muestra **solo sus locales**, **ordenados** pendientes primero (atrasado → toca_hoy → al_dia → sin_planificar), con:
  - **Héroe "X por recaudar"** = nº de locales pendientes (dato real, cierra el diferido de Fase 2a).
  - **`StatusChip` por local**: `atrasado` (DANGER), `toca hoy` (WARNING/INFO), `al día` (SUCCESS/NEUTRAL), `sin planificar` (NEUTRAL).
  - Filtros (reusando `FilterChipRow`): pendientes / al día / todos. (Sustituye al filtro "por recaudar" que se difirió.)
- Sin pantalla nueva. La ficha de local puede mostrar su próxima fecha programada.

## 7. Fases (orden P1 → P2 → P3, cada una entregable y testeable)

- **P1 — Datos + configuración** (base de datos + web). Campos en `local`, RPCs de calendario/asignación, formulario de instalación y ficha de local, vista de operarios. **No cambia las lecturas** (solo añade): no rompe nada. Entregable: los gestores ya planifican y asignan.
- **P2 — Lecturas estrictas por operario** (base de datos + verificación sync Android). Policies RLS estrictas + helper `usuario_ve_local` + pgTAP. Entregable: cada quien ve lo suyo; cerrado el agujero. (Depende de la asignación de P1.)
- **P3 — La agenda** (vista/RPC `agenda_operario` + home Android + panel del gestor web). Cálculo del estado + home como agenda con héroe y estados + **panel de control del gestor** (web) que reusa el mismo cálculo (qué hay pendiente/atrasado en la empresa y quién lo lleva). Entregable: el técnico abre la app y ve su ruta, y el gestor vigila a todos. (Depende del calendario de P1 y, para filtrar a lo suyo, de la RLS de P2.)

Cada fase: su rama, sus PRs (<400 líneas, squash) y su mini-plan vía writing-plans. Tareas `T-XX` nuevas en `.kiro/specs/recre/tasks.md`.

## 8. Fuera de alcance

- Notificaciones push ("hoy te tocan 5 locales").
- "Día de recaudación por defecto" a nivel empresa (el día sale de la fecha de inicio; se puede añadir como ayuda de UI más tarde).
- Exigir "todas las máquinas del local" para dar el ciclo por atendido: **descartado** (v1 = cualquier visita en el ciclo, porque el operario hace el local entero en una visita).
- Mes de calendario real (v1 = "mensual" se trata como cada 4 semanas).
- Poda de la cola de subida local (`recaudacion_pendiente`/`averia_pendiente`) — deuda técnica separada (memoria `cola-subida-sin-poda`).
- El "Histórico v2 a escala" — feature separada, pendiente.

## 9. Riesgos y mitigación

- **RLS estricta rompe lecturas existentes** (P2). → pgTAP exhaustivo antes de tocar cliente; migración aditiva; verificar cada SELECT del sync Android y de la web.
- **Locales sin calendario / sin operario** (datos legacy). → NULL = sin_planificar / sin asignar; la agenda los trata explícitamente; el gestor los ve y configura.
- **Zona horaria**: "hoy" y los límites de ciclo se calculan con `empresa.zona_horaria` (ya disponible), nunca con la del dispositivo.
- **Coherencia cadencia↔tasa**: ninguna. La tasa sigue siendo por semana ISO real (`semanas_iso_entre`); la cadencia solo planifica *cuándo* visitar. No se tocan los cálculos de dinero (SSOT servidor intacto).
- **Reasignar un local a mitad de ciclo**: el nuevo operario lo ve, el anterior deja de verlo. Aceptable.

## 10. Criterios de "hecho"

- Un gestor puede fijar cadencia + fecha de inicio + operario de un local (web), y el aviso al añadir 2ª máquina funciona.
- Un técnico, en su app, ve **solo** sus locales (verificado a nivel base de datos, no solo UI), ordenados pendientes primero, con el héroe "X por recaudar" correcto.
- La función de agenda y las policies RLS llevan pgTAP en verde; `assembleDebug` y `next build` en verde.
- Los cálculos de dinero (recaudación, tasa) **no cambian**.
