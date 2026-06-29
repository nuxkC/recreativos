# Cuadre semanal de caja del técnico — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar al técnico un apartado "Mi caja" en la app Android que muestra cuánto efectivo de la empresa debería llevar esa semana (total € y por denominación, derivado de sus recaudaciones vía una vista server-side) y lo compara con lo que cuenta físicamente.

**Architecture:** Una vista SQL `security_invoker` (`v_cuadre_semanal_tecnico`) agrega por (semana ISO en TZ de la empresa, denominación) el neto llevado `Σ(desglose_total − desglose_local)` de las recaudaciones firmes del técnico autenticado. Android lee la vista por PostgREST (esperado), guarda el recuento físico en Room (persistente), y un ViewModel combina ambos + el contador de recaudaciones pendientes de subir + realtime para producir el estado de pantalla.

**Tech Stack:** Postgres + pgTAP (supabase/), Kotlin + Compose + Hilt + Room + kotlinx.serialization + supabase-kt (android/).

## Global Constraints

- **Dinero**: nunca `Float`/`Double`/`number`. BBDD `numeric(10,2)`; transporte JSON como `String` con `@Serializable(with = NumericStringSerializer::class)`; en Kotlin `BigDecimal`. (CLAUDE.md "Reglas no negociables".)
- **SSOT**: no se recalcula dinero; la vista solo **agrega** importes ya persistidos por recaudación.
- **Semana ISO** en la **TZ de la empresa** (`public.empresa.zona_horaria`, default `Europe/Madrid`); lunes vía `date_trunc('week', fecha AT TIME ZONE zona_horaria)`.
- **Migraciones SQL**: aditivas e inmutables, formato `YYYYMMDDHHMMSS_descripcion.sql`. Funciones/vistas críticas → test pgTAP en `supabase/tests/sql/` con `BEGIN…ROLLBACK`, sin depender de `seed.sql`.
- **Vista** con `security_invoker = true` + `GRANT SELECT ... TO authenticated` (la RLS P2 de `recaudacion` aplica) y filtro explícito `tecnico_id = (SELECT auth.uid())`.
- **Idioma**: docs/comentarios/UI en español; identificadores en inglés salvo términos de dominio (`recaudacion`, `denominacion`, `cuadre`, `parteLocal`…). BBDD snake_case español.
- **Kotlin**: `strict`, sin `!!`. Tipos BBDD no se editan a mano.
- **Android Clean Architecture** `data → domain ← ui` en `app/src/main/java/com/recre/app/`; las features no se importan entre sí.
- **Room**: la versión sube de **8 → 9** con una `MIGRATION_8_9`.
- **Build/verify Android**: `cd android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:assembleDebug --console=plain` (los unit tests `./gradlew test` pueden fallar por locale no-UTF-8; validar dependencias con `assembleDebug`).
- **Conjunto de denominaciones** (validado en BBDD): `0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50`.
- **Commits**: Conventional Commits, scope `supabase` o `android`. Footer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

## File Structure

**Servidor**
- Create `supabase/migrations/20260629120000_v_cuadre_semanal_tecnico.sql` — la vista.
- Create `supabase/tests/sql/18_cuadre_semanal_tecnico.sql` — test pgTAP.

**Android — data**
- Create `android/app/src/main/java/com/recre/app/core/data/remote/dto/CuadreDtos.kt` — DTO de la vista.
- Create `android/app/src/main/java/com/recre/app/core/data/remote/CuadreRemoteDataSource.kt` — query PostgREST.
- Create `android/app/src/main/java/com/recre/app/core/data/local/entity/CuadreRecuentoEntity.kt` — recuento físico (Room).
- Create `android/app/src/main/java/com/recre/app/core/data/local/dao/CuadreRecuentoDao.kt` — DAO.
- Modify `android/app/src/main/java/com/recre/app/core/data/local/RecreDatabase.kt` — version 9 + entity + dao + `MIGRATION_8_9`.
- Modify el módulo Hilt que construye la base (la llamada `.addMigrations(...)`) — registrar `MIGRATION_8_9`.
- Modify `android/app/src/main/java/com/recre/app/core/data/local/dao/RecaudacionPendienteDao.kt` — `observarContadorFallidas`.

**Android — feature**
- Create `android/app/src/main/java/com/recre/app/feature/cuadre/domain/CuadreSemanal.kt` — modelos + cálculo de diferencia.
- Create `android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRepository.kt` — remote→dominio.
- Create `android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRecuentoStore.kt` — (de)serializa el recuento Room ↔ `Map<String,Int>`.
- Create `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreViewModel.kt` + `CuadreUiState.kt`.
- Create `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreScreen.kt`.
- Create `android/app/src/main/res/raw/cuadre_ok.json` — Lottie de "cuadra".
- Modify `android/app/src/main/java/com/recre/app/MainActivity.kt` — `Routes.CUADRE`, `composable`, `onCuadreClick` en `LocalesScreen`.
- Modify `android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt` — parámetro `onCuadreClick` + tarjeta de acceso.
- Modify `android/app/src/main/res/values/strings.xml` — textos.

**Tests**
- Create `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreDiferenciaTest.kt`
- Create `android/app/src/test/java/com/recre/app/core/data/remote/dto/CuadreDtosTest.kt`
- Create `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreRecuentoStoreTest.kt`
- Create `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreViewModelTest.kt`

---

## Task 1: Servidor — vista `v_cuadre_semanal_tecnico` + test pgTAP

**Files:**
- Create: `supabase/migrations/20260629120000_v_cuadre_semanal_tecnico.sql`
- Test: `supabase/tests/sql/18_cuadre_semanal_tecnico.sql`

**Interfaces:**
- Produces: vista `public.v_cuadre_semanal_tecnico(empresa_id uuid, tecnico_id uuid, semana_inicio date, denominacion numeric, cantidad_neta bigint, importe_neto numeric, num_recaudaciones bigint)`.

- [ ] **Step 1: Escribir el test pgTAP (falla: la vista no existe)**

Crea `supabase/tests/sql/18_cuadre_semanal_tecnico.sql`. Reutiliza la **cadena de setup** (empresa → usuario → local → maquina → licencia → instalacion) de `supabase/tests/sql/17_recaudacion_historica.sql` como plantilla; aquí van las partes específicas del cuadre. Usa empresa con `zona_horaria = 'UTC'` para que la frontera de semana sea predecible. Namespace UUID: `c0adre00-…`.

```sql
-- =============================================================================
-- Cuadre semanal del técnico — vista v_cuadre_semanal_tecnico.
-- Neto llevado = Σ(desglose_total − desglose_local) por (semana ISO, denominación)
-- del técnico autenticado, solo recaudaciones estado='firme'.
-- Setup como superusuario; aserciones bajan a rol authenticated + jwt (RLS).
-- BEGIN..ROLLBACK, sin seed.sql.
-- =============================================================================
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(6);

-- --- SETUP (superusuario): empresa(UTC), 2 técnicos, instalacion -------------
-- (Reutilizar el patrón de 17_recaudacion_historica.sql para auth.users,
--  empresa, usuario, local, maquina, licencia, instalacion. IDs base 'c0adre00-'.)
INSERT INTO auth.users (id) VALUES
    ('c0adre00-0000-0000-0000-0000000000a2'),   -- tecA
    ('c0adre00-0000-0000-0000-0000000000a3');    -- tecB
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('c0adre00-0000-0000-0000-000000000001', 'Test Cuadre', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('c0adre00-0000-0000-0000-0000000000a2', 'Tec A'),
    ('c0adre00-0000-0000-0000-0000000000a3', 'Tec B');
-- local + maquina + licencia + instalacion: copiar columnas de 17_recaudacion_historica.sql
-- usando instalacion id 'c0adre00-0000-0000-0000-0000000000f1' y empresa/tecnico de arriba.

-- Helper local para insertar una recaudación firme coherente con los constraints.
-- desglose_total/local deben cumplir: sumar_desglose(total)=recaudacion_bruta,
-- sumar_desglose(local)=parte_local, neta=bruta-tasa_total, partes suman neta.
-- Recaudación 1 (tecA, semana del 2026-06-22 lunes, UTC):
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen, estado
) VALUES (
    'c0adre00-0000-0000-0000-0000000000r1',
    'c0adre00-0000-0000-0000-000000000001',
    'c0adre00-0000-0000-0000-0000000000f1',
    'c0adre00-0000-0000-0000-0000000000a2',
    '2026-06-23 10:00:00+00',
    0, 0, 100, 0,
    1.00, 20.00, 0, 0, 0.00, 20.00,
    50.00, 10.00, 10.00,
    '[{"denominacion":2,"cantidad":10}]'::jsonb,
    '[{"denominacion":2,"cantidad":5}]'::jsonb,
    'c0adre-idem-1', 'instalacion_base', 'firme'
);
-- Recaudación 2 (tecA, MISMA semana): total 1×50€ + 0; local 0 -> neto carried 50€,1×50.
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen, estado
) VALUES (
    'c0adre00-0000-0000-0000-0000000000r2',
    'c0adre00-0000-0000-0000-000000000001',
    'c0adre00-0000-0000-0000-0000000000f1',
    'c0adre00-0000-0000-0000-0000000000a2',
    '2026-06-24 10:00:00+00',
    0, 0, 100, 0,
    1.00, 50.00, 0, 0, 0.00, 50.00,
    0.00, 0.00, 50.00,
    '[{"denominacion":50,"cantidad":1}]'::jsonb,
    '[]'::jsonb,
    'c0adre-idem-2', 'instalacion_base', 'firme'
);
-- Recaudación 3 (tecA, ANULADA, misma semana): no debe contar.
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen,
    estado, motivo_anulacion, anulada_por, anulada_en
) VALUES (
    'c0adre00-0000-0000-0000-0000000000r3',
    'c0adre00-0000-0000-0000-000000000001',
    'c0adre00-0000-0000-0000-0000000000f1',
    'c0adre00-0000-0000-0000-0000000000a2',
    '2026-06-25 10:00:00+00',
    0, 0, 100, 0,
    1.00, 20.00, 0, 0, 0.00, 20.00,
    50.00, 10.00, 10.00,
    '[{"denominacion":2,"cantidad":10}]'::jsonb,
    '[{"denominacion":2,"cantidad":5}]'::jsonb,
    'c0adre-idem-3', 'instalacion_base',
    'anulada', 'error de prueba', 'c0adre00-0000-0000-0000-0000000000a2', now()
);

-- --- ASERCIONES como rol authenticated + JWT de tecA -------------------------
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims TO '{"sub":"c0adre00-0000-0000-0000-0000000000a2","role":"authenticated"}';

-- 1) Neto de 2€ esa semana = 10(total r1) − 5(local r1) = 5
SELECT is(
    (SELECT cantidad_neta FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22' AND denominacion = 2),
    5::bigint, 'neto 2€ = 5 piezas');

-- 2) Neto de 50€ esa semana = 1
SELECT is(
    (SELECT cantidad_neta FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22' AND denominacion = 50),
    1::bigint, 'neto 50€ = 1 pieza');

-- 3) Importe total llevado esa semana = 5×2 + 1×50 = 60.00
SELECT is(
    (SELECT SUM(importe_neto) FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22'),
    60.00::numeric, 'total llevado = 60,00 €');

-- 4) num_recaudaciones = 2 (la anulada no cuenta)
SELECT is(
    (SELECT DISTINCT num_recaudaciones FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22'),
    2::bigint, 'num_recaudaciones = 2 (excluye anulada)');

-- 5) La recaudación anulada no añade denominaciones de más
SELECT is(
    (SELECT COUNT(*) FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22'),
    2::bigint, 'solo 2 denominaciones netas (2€ y 50€)');

-- 6) tecB no ve la caja de tecA (aislamiento)
SET LOCAL request.jwt.claims TO '{"sub":"c0adre00-0000-0000-0000-0000000000a3","role":"authenticated"}';
SELECT is(
    (SELECT COUNT(*) FROM public.v_cuadre_semanal_tecnico),
    0::bigint, 'tecB no ve la caja de tecA');

SELECT * FROM finish();
ROLLBACK;
```

- [ ] **Step 2: Ejecutar el test (debe FALLAR: la vista no existe)**

Run: `cd /home/a/Escritorio/recre-main && export PATH="$HOME/.deno/bin:$PATH" && npx supabase test db`
Expected: FAIL en `18_cuadre_semanal_tecnico.sql` con error tipo `relation "public.v_cuadre_semanal_tecnico" does not exist`.

- [ ] **Step 3: Crear la migración con la vista**

Crea `supabase/migrations/20260629120000_v_cuadre_semanal_tecnico.sql`:

```sql
-- =============================================================================
-- v_cuadre_semanal_tecnico — caja semanal del técnico.
--
-- Por (semana ISO en TZ de la empresa, denominación), el efectivo de la empresa
-- que el técnico se llevó = Σ(desglose_total) − Σ(desglose_local) de sus
-- recaudaciones FIRMES (estado='firme'). No recalcula dinero: agrega lo ya
-- persistido. security_invoker -> respeta la RLS estricta (P2); además filtra
-- tecnico_id = auth.uid() para que sea la caja PROPIA del técnico.
-- =============================================================================

CREATE OR REPLACE VIEW public.v_cuadre_semanal_tecnico
WITH (security_invoker = true) AS
WITH piezas AS (
    -- Total con signo +, local con signo − (mismo grano: pieza por denominación).
    SELECT
        r.empresa_id,
        r.tecnico_id,
        date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date AS semana_inicio,
        r.id AS recaudacion_id,
        (d.item->>'denominacion')::numeric(5, 2) AS denominacion,
        (d.item->>'cantidad')::bigint            AS cantidad
    FROM public.recaudacion r
    JOIN public.empresa e ON e.id = r.empresa_id
    CROSS JOIN LATERAL jsonb_array_elements(r.desglose_total) AS d(item)
    WHERE r.estado = 'firme' AND r.tecnico_id = (SELECT auth.uid())
    UNION ALL
    SELECT
        r.empresa_id,
        r.tecnico_id,
        date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date AS semana_inicio,
        r.id AS recaudacion_id,
        (d.item->>'denominacion')::numeric(5, 2)  AS denominacion,
        -((d.item->>'cantidad')::bigint)          AS cantidad
    FROM public.recaudacion r
    JOIN public.empresa e ON e.id = r.empresa_id
    CROSS JOIN LATERAL jsonb_array_elements(r.desglose_local) AS d(item)
    WHERE r.estado = 'firme' AND r.tecnico_id = (SELECT auth.uid())
),
neto AS (
    SELECT empresa_id, tecnico_id, semana_inicio, denominacion,
           SUM(cantidad)::bigint AS cantidad_neta
    FROM piezas
    GROUP BY empresa_id, tecnico_id, semana_inicio, denominacion
),
conteo AS (
    SELECT r.empresa_id, r.tecnico_id,
           date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date AS semana_inicio,
           COUNT(*)::bigint AS num_recaudaciones
    FROM public.recaudacion r
    JOIN public.empresa e ON e.id = r.empresa_id
    WHERE r.estado = 'firme' AND r.tecnico_id = (SELECT auth.uid())
    GROUP BY r.empresa_id, r.tecnico_id, date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date
)
SELECT
    n.empresa_id,
    n.tecnico_id,
    n.semana_inicio,
    n.denominacion,
    n.cantidad_neta,
    (n.denominacion * n.cantidad_neta)::numeric(10, 2) AS importe_neto,
    c.num_recaudaciones
FROM neto n
JOIN conteo c USING (empresa_id, tecnico_id, semana_inicio);

COMMENT ON VIEW public.v_cuadre_semanal_tecnico IS
    'Caja semanal del técnico: neto llevado Σ(desglose_total−desglose_local) por (semana ISO en TZ empresa, denominación) de sus recaudaciones firmes. security_invoker + tecnico_id=auth.uid().';

GRANT SELECT ON public.v_cuadre_semanal_tecnico TO authenticated;
```

- [ ] **Step 4: Ejecutar el test (debe PASAR)**

Run: `cd /home/a/Escritorio/recre-main && export PATH="$HOME/.deno/bin:$PATH" && npx supabase db reset && npx supabase test db`
Expected: PASS — `18_cuadre_semanal_tecnico.sql ... ok` (6/6).

- [ ] **Step 5: Commit**

```bash
git -C /home/a/Escritorio/recre-main add supabase/migrations/20260629120000_v_cuadre_semanal_tecnico.sql supabase/tests/sql/18_cuadre_semanal_tecnico.sql
git -C /home/a/Escritorio/recre-main commit -m "feat(supabase): vista v_cuadre_semanal_tecnico para el cuadre semanal de caja"
```

---

## Task 2: Android — dominio + cálculo de diferencia (puro)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/cuadre/domain/CuadreSemanal.kt`
- Test: `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreDiferenciaTest.kt`

**Interfaces:**
- Produces:
  - `data class LineaCuadre(val denominacion: BigDecimal, val cantidadEsperada: Long, val cantidadContada: Long)` con `val delta: Long get() = cantidadContada - cantidadEsperada`.
  - `data class CuadreSemanal(val semanaInicio: LocalDate, val numRecaudaciones: Int, val totalEsperado: BigDecimal, val lineas: List<LineaCuadre>)`.
  - `enum class VeredictoCuadre { CUADRA, SOBRA, FALTA }`.
  - `data class DiferenciaCuadre(val totalEsperado: BigDecimal, val totalContado: BigDecimal, val diferencia: BigDecimal, val veredicto: VeredictoCuadre, val lineas: List<LineaCuadre>)`.
  - `fun calcularDiferencia(esperadoPorDenominacion: Map<BigDecimal, Long>, contadoPorDenominacion: Map<BigDecimal, Long>): DiferenciaCuadre` — itera el conjunto fijo de denominaciones.
  - `val DENOMINACIONES_CUADRE: List<BigDecimal>` = [50,20,10,5,2,1,0.50,0.20,0.10] (orden de mayor a menor).

- [ ] **Step 1: Escribir el test que falla**

Crea `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreDiferenciaTest.kt`:

```kotlin
package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.domain.VeredictoCuadre
import com.recre.app.feature.cuadre.domain.calcularDiferencia
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreDiferenciaTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `cuadra cuando contado iguala esperado`() {
        val esperado = mapOf(bd("50") to 1L, bd("2") to 5L)
        val contado = mapOf(bd("50") to 1L, bd("2") to 5L)
        val r = calcularDiferencia(esperado, contado)
        assertEquals(VeredictoCuadre.CUADRA, r.veredicto)
        assertEquals(0, r.diferencia.compareTo(BigDecimal.ZERO))
        assertEquals(0, r.totalEsperado.compareTo(bd("60.00")))
    }

    @Test
    fun `falta cuando contado es menor`() {
        // esperado 1×20 ; contado 0 -> faltan 20,00
        val r = calcularDiferencia(mapOf(bd("20") to 1L), emptyMap())
        assertEquals(VeredictoCuadre.FALTA, r.veredicto)
        assertEquals(0, r.diferencia.compareTo(bd("-20.00")))
        val linea20 = r.lineas.first { it.denominacion.compareTo(bd("20")) == 0 }
        assertEquals(-1L, linea20.delta)
    }

    @Test
    fun `sobra cuando contado es mayor`() {
        val r = calcularDiferencia(mapOf(bd("10") to 1L), mapOf(bd("10") to 3L))
        assertEquals(VeredictoCuadre.SOBRA, r.veredicto)
        assertEquals(0, r.diferencia.compareTo(bd("20.00")))
    }

    @Test
    fun `incluye todas las denominaciones aunque esten a cero`() {
        val r = calcularDiferencia(emptyMap(), emptyMap())
        assertEquals(9, r.lineas.size)
        assertEquals(VeredictoCuadre.CUADRA, r.veredicto)
    }
}
```

- [ ] **Step 2: Ejecutar el test (debe FALLAR a compilar/ejecutar)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.cuadre.CuadreDiferenciaTest"`
Expected: FAIL — `unresolved reference: calcularDiferencia`.

- [ ] **Step 3: Implementar el dominio**

Crea `android/app/src/main/java/com/recre/app/feature/cuadre/domain/CuadreSemanal.kt`:

```kotlin
package com.recre.app.feature.cuadre.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Denominaciones del cuadre, de mayor a menor (mismo conjunto validado en BBDD). */
val DENOMINACIONES_CUADRE: List<BigDecimal> = listOf(
    "50", "20", "10", "5", "2", "1", "0.50", "0.20", "0.10",
).map(::BigDecimal)

/** Una fila del cuadre: lo que deberías llevar vs lo que cuentas de una denominación. */
data class LineaCuadre(
    val denominacion: BigDecimal,
    val cantidadEsperada: Long,
    val cantidadContada: Long,
) {
    val delta: Long get() = cantidadContada - cantidadEsperada
    val importeEsperado: BigDecimal get() = denominacion.multiply(BigDecimal(cantidadEsperada))
}

/** Esperado de una semana (lado servidor). */
data class CuadreSemanal(
    val semanaInicio: LocalDate,
    val numRecaudaciones: Int,
    val totalEsperado: BigDecimal,
    val esperadoPorDenominacion: Map<BigDecimal, Long>,
)

enum class VeredictoCuadre { CUADRA, SOBRA, FALTA }

/** Resultado de comparar el recuento físico contra el esperado. */
data class DiferenciaCuadre(
    val totalEsperado: BigDecimal,
    val totalContado: BigDecimal,
    val diferencia: BigDecimal,
    val veredicto: VeredictoCuadre,
    val lineas: List<LineaCuadre>,
)

/**
 * Compara el esperado (servidor) con el contado (recuento físico) sobre el
 * conjunto fijo de denominaciones. Las denominaciones ausentes en un mapa
 * cuentan como 0. El total € es la cifra autoritativa del veredicto.
 */
fun calcularDiferencia(
    esperadoPorDenominacion: Map<BigDecimal, Long>,
    contadoPorDenominacion: Map<BigDecimal, Long>,
): DiferenciaCuadre {
    val lineas = DENOMINACIONES_CUADRE.map { den ->
        LineaCuadre(
            denominacion = den,
            cantidadEsperada = esperadoPorDenominacion.entries
                .firstOrNull { it.key.compareTo(den) == 0 }?.value ?: 0L,
            cantidadContada = contadoPorDenominacion.entries
                .firstOrNull { it.key.compareTo(den) == 0 }?.value ?: 0L,
        )
    }
    val totalEsperado = lineas.fold(BigDecimal.ZERO) { acc, l ->
        acc.add(l.denominacion.multiply(BigDecimal(l.cantidadEsperada)))
    }
    val totalContado = lineas.fold(BigDecimal.ZERO) { acc, l ->
        acc.add(l.denominacion.multiply(BigDecimal(l.cantidadContada)))
    }
    val diferencia = totalContado.subtract(totalEsperado)
    val veredicto = when (diferencia.signum()) {
        0 -> VeredictoCuadre.CUADRA
        1 -> VeredictoCuadre.SOBRA
        else -> VeredictoCuadre.FALTA
    }
    return DiferenciaCuadre(totalEsperado, totalContado, diferencia, veredicto, lineas)
}
```

- [ ] **Step 4: Ejecutar el test (debe PASAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.cuadre.CuadreDiferenciaTest"`
Expected: PASS (4/4). Si el locale rompe los nombres con tildes, renombra los tests a ASCII; la lógica es lo que importa.

- [ ] **Step 5: Commit**

```bash
git -C /home/a/Escritorio/recre-main add android/app/src/main/java/com/recre/app/feature/cuadre/domain/CuadreSemanal.kt android/app/src/test/java/com/recre/app/feature/cuadre/CuadreDiferenciaTest.kt
git -C /home/a/Escritorio/recre-main commit -m "feat(android): dominio del cuadre semanal + calculo de diferencia"
```

---

## Task 3: Android — DTO + RemoteDataSource + Repository (lado esperado)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/core/data/remote/dto/CuadreDtos.kt`
- Create: `android/app/src/main/java/com/recre/app/core/data/remote/CuadreRemoteDataSource.kt`
- Create: `android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRepository.kt`
- Test: `android/app/src/test/java/com/recre/app/core/data/remote/dto/CuadreDtosTest.kt`

**Interfaces:**
- Consumes: `CuadreSemanal`, `LocalDate` (Task 2); `NumericStringSerializer` (`core/data/remote/dto/NumericStringSerializer.kt`); `DomainResult` (`core/util`); `SupabaseClient`, `SessionRepository`, `AuthRepository`, `EmpresaParamsDao`.
- Produces:
  - `data class CuadreSemanalRow(...)` (DTO de la vista).
  - `class CuadreRemoteDataSource { suspend fun obtener(): List<CuadreSemanalRow> }`.
  - `class CuadreRepository { suspend fun cargarSemana(semanaInicio: LocalDate): DomainResult<CuadreSemanal>; suspend fun zonaHoraria(): String }`.
  - `fun List<CuadreSemanalRow>.aCuadreSemanal(semanaInicio: LocalDate): CuadreSemanal` (mapeo puro, testeable).

- [ ] **Step 1: Escribir el test del DTO + mapeo (falla)**

Crea `android/app/src/test/java/com/recre/app/core/data/remote/dto/CuadreDtosTest.kt`:

```kotlin
package com.recre.app.core.data.remote.dto

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodifica filas de la vista`() {
        val payload = """
            [
              {"empresa_id":"e1","tecnico_id":"t1","semana_inicio":"2026-06-22",
               "denominacion":"50.00","cantidad_neta":1,"importe_neto":"50.00","num_recaudaciones":2},
              {"empresa_id":"e1","tecnico_id":"t1","semana_inicio":"2026-06-22",
               "denominacion":"2.00","cantidad_neta":5,"importe_neto":"10.00","num_recaudaciones":2}
            ]
        """.trimIndent()
        val filas = json.decodeFromString<List<CuadreSemanalRow>>(payload)
        assertEquals(2, filas.size)
        assertEquals("50.00", filas[0].denominacion)
        assertEquals(1L, filas[0].cantidadNeta)
    }

    @Test
    fun `mapea filas a CuadreSemanal con total agregado`() {
        val filas = listOf(
            CuadreSemanalRow("e1", "t1", "2026-06-22", "50.00", 1, "50.00", 2),
            CuadreSemanalRow("e1", "t1", "2026-06-22", "2.00", 5, "10.00", 2),
        )
        val cuadre = filas.aCuadreSemanal(LocalDate.of(2026, 6, 22))
        assertEquals(2, cuadre.numRecaudaciones)
        assertEquals(0, cuadre.totalEsperado.compareTo(BigDecimal("60.00")))
        assertEquals(1L, cuadre.esperadoPorDenominacion[BigDecimal("50.00")])
    }
}
```

- [ ] **Step 2: Ejecutar el test (debe FALLAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.core.data.remote.dto.CuadreDtosTest"`
Expected: FAIL — `unresolved reference: CuadreSemanalRow`.

- [ ] **Step 3: Crear el DTO + mapeo**

Crea `android/app/src/main/java/com/recre/app/core/data/remote/dto/CuadreDtos.kt`:

```kotlin
package com.recre.app.core.data.remote.dto

import com.recre.app.feature.cuadre.domain.CuadreSemanal
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una fila de `v_cuadre_semanal_tecnico`: (semana, denominación) → neto llevado. */
@Serializable
data class CuadreSemanalRow(
    @SerialName("empresa_id") val empresaId: String,
    @SerialName("tecnico_id") val tecnicoId: String,
    @SerialName("semana_inicio") val semanaInicio: String,
    @Serializable(with = NumericStringSerializer::class)
    val denominacion: String,
    @SerialName("cantidad_neta") val cantidadNeta: Long,
    @SerialName("importe_neto")
    @Serializable(with = NumericStringSerializer::class)
    val importeNeto: String,
    @SerialName("num_recaudaciones") val numRecaudaciones: Int,
)

/** Mapeo puro: filas de una semana → modelo de dominio (total = Σ importe_neto). */
fun List<CuadreSemanalRow>.aCuadreSemanal(semanaInicio: LocalDate): CuadreSemanal {
    val esperado = associate { BigDecimal(it.denominacion) to it.cantidadNeta }
    val total = fold(BigDecimal.ZERO) { acc, r -> acc.add(BigDecimal(r.importeNeto)) }
    val num = firstOrNull()?.numRecaudaciones ?: 0
    return CuadreSemanal(
        semanaInicio = semanaInicio,
        numRecaudaciones = num,
        totalEsperado = total,
        esperadoPorDenominacion = esperado,
    )
}
```

- [ ] **Step 4: Ejecutar el test (debe PASAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.core.data.remote.dto.CuadreDtosTest"`
Expected: PASS (2/2).

- [ ] **Step 5: Crear el RemoteDataSource y el Repository (sin test propio; se verifican al compilar y vía el VM en Task 5)**

Crea `android/app/src/main/java/com/recre/app/core/data/remote/CuadreRemoteDataSource.kt`. Imita `AgendaRemoteDataSource` (consulta simple `.from(...).select{...}.decodeList()`):

```kotlin
package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.CuadreSemanalRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee `v_cuadre_semanal_tecnico`. La vista ya filtra por tecnico_id=auth.uid()
 * y la RLS por empresa, así que no hace falta pasar filtros: traemos todas las
 * semanas (acotadas por técnico) ordenadas y el VM agrupa/navega.
 */
@Singleton
class CuadreRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun obtener(): List<CuadreSemanalRow> =
        supabase
            .from("v_cuadre_semanal_tecnico")
            .select {
                order("semana_inicio", Order.DESCENDING)
            }
            .decodeList()
}
```

Crea `android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRepository.kt`:

```kotlin
package com.recre.app.feature.cuadre.data

import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.remote.CuadreRemoteDataSource
import com.recre.app.core.data.remote.dto.aCuadreSemanal
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import com.recre.app.feature.cuadre.domain.CuadreSemanal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Carga el cuadre (lado "esperado") de una semana concreta desde la vista.
 * Si la semana no tiene recaudaciones, devuelve un CuadreSemanal vacío (total 0).
 */
@Singleton
class CuadreRepository @Inject constructor(
    private val remote: CuadreRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val empresaParamsDao: EmpresaParamsDao,
) {
    suspend fun cargarSemana(semanaInicio: LocalDate): DomainResult<CuadreSemanal> =
        try {
            val filas = remote.obtener()
                .filter { it.semanaInicio == semanaInicio.toString() }
            DomainResult.Success(filas.aCuadreSemanal(semanaInicio))
        } catch (e: Exception) {
            Timber.e(e, "Fallo cargando el cuadre semanal")
            DomainResult.Failure(DomainError.Network)
        }

    /** TZ de la empresa para alinear el cálculo de la semana ISO con el servidor. */
    suspend fun zonaHoraria(): String {
        val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
            ?: return "Europe/Madrid"
        return empresaParamsDao.observe(empresaId).first()?.zonaHoraria ?: "Europe/Madrid"
    }
}
```

NOTA: confirma al implementar el nombre del símbolo de error de red en `core/util/DomainError` (p. ej. `DomainError.Network`/`DomainError.Conexion`) y el método de `EmpresaParamsDao` (`observe(empresaId)`), y ajusta. Si `CuadreSemanal` vacío necesita lista vacía, `filas` vacío ya produce `totalEsperado = 0` y `esperadoPorDenominacion = {}`.

- [ ] **Step 6: Verificar compilación**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:assembleDebug --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git -C /home/a/Escritorio/recre-main add android/app/src/main/java/com/recre/app/core/data/remote/dto/CuadreDtos.kt android/app/src/main/java/com/recre/app/core/data/remote/CuadreRemoteDataSource.kt android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRepository.kt android/app/src/test/java/com/recre/app/core/data/remote/dto/CuadreDtosTest.kt
git -C /home/a/Escritorio/recre-main commit -m "feat(android): lectura del cuadre semanal desde la vista server-side"
```

---

## Task 4: Android — recuento físico persistente (Room)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/core/data/local/entity/CuadreRecuentoEntity.kt`
- Create: `android/app/src/main/java/com/recre/app/core/data/local/dao/CuadreRecuentoDao.kt`
- Create: `android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRecuentoStore.kt`
- Modify: `android/app/src/main/java/com/recre/app/core/data/local/RecreDatabase.kt`
- Modify: el módulo Hilt con `.addMigrations(...)` (la llamada que construye `RecreDatabase`).
- Test: `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreRecuentoStoreTest.kt`

**Interfaces:**
- Consumes: `InstantConverter` (ya registrado), `RecreDatabase` (v8).
- Produces:
  - `CuadreRecuentoEntity(empresaId, tecnicoId, semanaInicio: String, recuentoJson: String, updatedAt: Instant)`.
  - `CuadreRecuentoDao { fun observar(e,t,s): Flow<CuadreRecuentoEntity?>; suspend fun upsert(value) }`.
  - `CuadreRecuentoStore { fun serializar(map: Map<BigDecimal,Long>): String; fun deserializar(json: String): Map<BigDecimal,Long> }`.
  - `RecreDatabase` v9 con `cuadreRecuentoDao()` y `MIGRATION_8_9`.

- [ ] **Step 1: Escribir el test del store de (de)serialización (falla)**

Crea `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreRecuentoStoreTest.kt`:

```kotlin
package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.data.CuadreRecuentoStore
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreRecuentoStoreTest {

    private val store = CuadreRecuentoStore()

    @Test
    fun `ida y vuelta conserva denominaciones y cantidades`() {
        val original = mapOf(BigDecimal("50") to 1L, BigDecimal("2") to 5L)
        val json = store.serializar(original)
        val vuelta = store.deserializar(json)
        assertEquals(1L, vuelta[BigDecimal("50")])
        assertEquals(5L, vuelta[BigDecimal("2")])
    }

    @Test
    fun `deserializar vacio o invalido devuelve mapa vacio`() {
        assertEquals(emptyMap<BigDecimal, Long>(), store.deserializar(""))
        assertEquals(emptyMap<BigDecimal, Long>(), store.deserializar("no-json"))
    }
}
```

- [ ] **Step 2: Ejecutar el test (debe FALLAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.cuadre.CuadreRecuentoStoreTest"`
Expected: FAIL — `unresolved reference: CuadreRecuentoStore`.

- [ ] **Step 3: Crear el store**

Crea `android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRecuentoStore.kt`:

```kotlin
package com.recre.app.feature.cuadre.data

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * (De)serializa el recuento físico `denominación → cantidad` a/desde JSON para
 * guardarlo como String en Room (mismo patrón que los desgloses en
 * RecaudacionRepository). Las claves se guardan como texto (toPlainString).
 */
@Singleton
class CuadreRecuentoStore @Inject constructor() {
    private val json = Json
    private val serializer = MapSerializer(String.serializer(), Long.serializer())

    fun serializar(map: Map<BigDecimal, Long>): String =
        json.encodeToString(serializer, map.mapKeys { it.key.toPlainString() })

    fun deserializar(texto: String): Map<BigDecimal, Long> =
        runCatching {
            json.decodeFromString(serializer, texto).mapKeys { BigDecimal(it.key) }
        }.getOrDefault(emptyMap())
}
```

- [ ] **Step 4: Ejecutar el test (debe PASAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.cuadre.CuadreRecuentoStoreTest"`
Expected: PASS (2/2).

- [ ] **Step 5: Crear la entidad y el DAO**

Crea `android/app/src/main/java/com/recre/app/core/data/local/entity/CuadreRecuentoEntity.kt`:

```kotlin
package com.recre.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.Instant

/**
 * Recuento físico que el técnico introduce al cuadrar su caja de una semana.
 * Persistente: sobrevive a salir/volver. PK por (empresa, técnico, semana).
 */
@Entity(
    tableName = "cuadre_recuento",
    primaryKeys = ["empresa_id", "tecnico_id", "semana_inicio"],
)
data class CuadreRecuentoEntity(
    @ColumnInfo(name = "empresa_id") val empresaId: String,
    @ColumnInfo(name = "tecnico_id") val tecnicoId: String,
    @ColumnInfo(name = "semana_inicio") val semanaInicio: String,
    @ColumnInfo(name = "recuento_json") val recuentoJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
```

Crea `android/app/src/main/java/com/recre/app/core/data/local/dao/CuadreRecuentoDao.kt`:

```kotlin
package com.recre.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recre.app.core.data.local.entity.CuadreRecuentoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CuadreRecuentoDao {

    @Query(
        """
        SELECT * FROM cuadre_recuento
        WHERE empresa_id = :empresaId AND tecnico_id = :tecnicoId
          AND semana_inicio = :semanaInicio
        LIMIT 1
        """,
    )
    fun observar(empresaId: String, tecnicoId: String, semanaInicio: String): Flow<CuadreRecuentoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: CuadreRecuentoEntity)
}
```

- [ ] **Step 6: Subir Room a v9 + registrar entidad, DAO y migración**

En `android/app/src/main/java/com/recre/app/core/data/local/RecreDatabase.kt`:

1. Añade `CuadreRecuentoEntity::class` a la lista `entities`.
2. Cambia `version = 8` → `version = 9`.
3. Añade el accessor abstracto: `abstract fun cuadreRecuentoDao(): CuadreRecuentoDao`.
4. Añade en el `companion object`, junto a las demás migraciones:

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cuadre_recuento` (" +
                "`empresa_id` TEXT NOT NULL, " +
                "`tecnico_id` TEXT NOT NULL, " +
                "`semana_inicio` TEXT NOT NULL, " +
                "`recuento_json` TEXT NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`empresa_id`, `tecnico_id`, `semana_inicio`))",
        )
    }
}
```

5. Asegura los imports `androidx.room.migration.Migration` y `androidx.sqlite.db.SupportSQLiteDatabase` (ya presentes por las migraciones existentes), y `import com.recre.app.core.data.local.entity.CuadreRecuentoEntity` + `import com.recre.app.core.data.local.dao.CuadreRecuentoDao`.

En el módulo Hilt que construye `RecreDatabase` (busca `.addMigrations(`), añade `RecreDatabase.MIGRATION_8_9` a la lista de migraciones.

- [ ] **Step 7: Verificar compilación (Room genera el DAO y valida el esquema v9)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:assembleDebug --console=plain 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`. (Si Room exige un schema JSON exportado, se generará en `app/schemas/…/9.json`; añádelo al commit.)

- [ ] **Step 8: Commit**

```bash
git -C /home/a/Escritorio/recre-main add android/app/src/main/java/com/recre/app/core/data/local/entity/CuadreRecuentoEntity.kt android/app/src/main/java/com/recre/app/core/data/local/dao/CuadreRecuentoDao.kt android/app/src/main/java/com/recre/app/feature/cuadre/data/CuadreRecuentoStore.kt android/app/src/main/java/com/recre/app/core/data/local/RecreDatabase.kt android/app/src/test/java/com/recre/app/feature/cuadre/CuadreRecuentoStoreTest.kt
# añade también el módulo Hilt modificado y app/schemas/ si Room exporta el v9
git -C /home/a/Escritorio/recre-main commit -m "feat(android): recuento fisico del cuadre persistido en Room (v9)"
```

---

## Task 5: Android — ViewModel + estado de pantalla

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreUiState.kt`
- Create: `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreViewModel.kt`
- Modify: `android/app/src/main/java/com/recre/app/core/data/local/dao/RecaudacionPendienteDao.kt` (añade `observarContadorFallidas`).
- Test: `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreViewModelTest.kt`

**Interfaces:**
- Consumes: `CuadreRepository`, `CuadreRecuentoStore`, `CuadreRecuentoDao`, `RecaudacionPendienteDao`, `RealtimeManager`, `SessionRepository`, `AuthRepository`, `calcularDiferencia`, `DiferenciaCuadre`.
- Produces:
  - `sealed interface CuadreUiState { Cargando; data class BloqueadoPorPendientes(reintentables, fallidas); data class Listo(...); object Vacio... }` (ver código).
  - `CuadreViewModel { val state: StateFlow<CuadreUiState>; fun onContarChange(denominacion, cantidad); fun onCambiarSemana(delta: Long); fun onSubirPendientes() }`.

- [ ] **Step 1: Añadir el contador de fallidas al DAO de pendientes**

En `android/app/src/main/java/com/recre/app/core/data/local/dao/RecaudacionPendienteDao.kt` añade:

```kotlin
@Query(
    """
    SELECT COUNT(*) FROM recaudacion_pendiente
    WHERE empresa_id = :empresaId AND estado = 'fallida'
    """,
)
fun observarContadorFallidas(empresaId: String): Flow<Int>
```

- [ ] **Step 2: Escribir el test del VM (falla)**

Crea `android/app/src/test/java/com/recre/app/feature/cuadre/CuadreViewModelTest.kt`. Usa fakes en memoria para las dependencias. Verifica las tres transiciones clave: bloqueado por pendientes, vacío, listo con diferencia.

```kotlin
package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.domain.VeredictoCuadre
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuadreViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // NOTA: este test asume una factoría/constructor del VM que acepta colaboradores
    // ya como interfaces/fakes. Si el VM concreto depende de tipos finales, extrae
    // las 5 colaboraciones (cargarSemana, observarRecuento, guardarRecuento,
    // pendientes, fallidas) tras una pequeña interfaz `CuadreDeps` e inyéctala.
    // Aquí se valida la LÓGICA de estado, que es lo de valor.

    @Test
    fun `bloquea cuando hay recaudaciones pendientes`() = runTest(dispatcher) {
        val pendientes = MutableStateFlow(3)
        val fallidas = MutableStateFlow(0)
        val vm = nuevoVm(pendientes = pendientes, fallidas = fallidas)
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is CuadreUiState.BloqueadoPorPendientes)
        assertEquals(3, (s as CuadreUiState.BloqueadoPorPendientes).reintentables)
    }

    @Test
    fun `listo muestra diferencia al contar`() = runTest(dispatcher) {
        val vm = nuevoVm(
            esperado = mapOf(BigDecimal("20") to 1L),
            pendientes = MutableStateFlow(0),
        )
        advanceUntilIdle()
        vm.onContarChange(BigDecimal("20"), 0)
        advanceUntilIdle()
        val s = vm.state.value as CuadreUiState.Listo
        assertEquals(VeredictoCuadre.FALTA, s.diferencia.veredicto)
    }
}
```

> Si extraer fakes resulta costoso, sustituye este test por uno que ejerza directamente la composición de estado (una función pura `construirEstado(cuadre, recuento, pendientes, fallidas)` que el VM use internamente). En ese caso, mueve la lógica de decisión a esa función pura y testéala. **El objetivo es testear la máquina de estados, no el framework.**

- [ ] **Step 3: Ejecutar el test (debe FALLAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.cuadre.CuadreViewModelTest"`
Expected: FAIL — `unresolved reference: CuadreUiState`.

- [ ] **Step 4: Crear el estado y el ViewModel**

Crea `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreUiState.kt`:

```kotlin
package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.domain.DiferenciaCuadre
import java.time.LocalDate

sealed interface CuadreUiState {
    data object Cargando : CuadreUiState
    data class BloqueadoPorPendientes(val reintentables: Int, val fallidas: Int) : CuadreUiState
    data class Vacio(val semanaInicio: LocalDate) : CuadreUiState
    data class Listo(
        val semanaInicio: LocalDate,
        val numRecaudaciones: Int,
        val diferencia: DiferenciaCuadre,
    ) : CuadreUiState
    data object SinConexion : CuadreUiState
}
```

Crea `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreViewModel.kt`. Combina esperado (servidor) + recuento (Room) + pendientes/fallidas + realtime. Patrón `revision.drop(1).collect { recargar() }` como `HistoricoViewModel`.

```kotlin
package com.recre.app.feature.cuadre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.CuadreRecuentoDao
import com.recre.app.core.data.local.dao.RecaudacionPendienteDao
import com.recre.app.core.data.local.entity.CuadreRecuentoEntity
import com.recre.app.core.session.AuthRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.RealtimeManager
import com.recre.app.core.util.DomainResult
import com.recre.app.feature.cuadre.data.CuadreRecuentoStore
import com.recre.app.feature.cuadre.data.CuadreRepository
import com.recre.app.feature.cuadre.domain.CuadreSemanal
import com.recre.app.feature.cuadre.domain.calcularDiferencia
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CuadreViewModel @Inject constructor(
    private val repository: CuadreRepository,
    private val recuentoDao: CuadreRecuentoDao,
    private val recuentoStore: CuadreRecuentoStore,
    private val pendientesDao: RecaudacionPendienteDao,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val realtimeManager: RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow<CuadreUiState>(CuadreUiState.Cargando)
    val state: StateFlow<CuadreUiState> = _state.asStateFlow()

    private lateinit var empresaId: String
    private lateinit var tecnicoId: String
    private var semanaInicio: LocalDate = LocalDate.now()
    private var cuadre: CuadreSemanal? = null
    private var contado: Map<BigDecimal, Long> = emptyMap()

    init {
        viewModelScope.launch {
            empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id ?: return@launch
            tecnicoId = authRepository.currentUserId() ?: return@launch
            val tz = ZoneId.of(repository.zonaHoraria())
            semanaInicio = LocalDate.now(tz).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            cargar()
            // Realtime: cualquier cambio server-side (p. ej. una recaudación que
            // sincroniza o se anula) recalcula el esperado.
            viewModelScope.launch { realtimeManager.revision.drop(1).collect { cargar() } }
        }
    }

    fun onCambiarSemana(delta: Long) {
        semanaInicio = semanaInicio.plusWeeks(delta)
        viewModelScope.launch { cargar() }
    }

    fun onContarChange(denominacion: BigDecimal, cantidad: Long) {
        contado = contado.toMutableMap().apply { this[denominacion] = cantidad.coerceAtLeast(0) }
        viewModelScope.launch {
            recuentoDao.upsert(
                CuadreRecuentoEntity(
                    empresaId = empresaId,
                    tecnicoId = tecnicoId,
                    semanaInicio = semanaInicio.toString(),
                    recuentoJson = recuentoStore.serializar(contado),
                    updatedAt = Instant.now(),
                ),
            )
            recomputar()
        }
    }

    fun onSubirPendientes() {
        // Reutiliza el mecanismo de subida existente (el worker drena al haber red).
        // Inyecta el manager de subida si procede; mínimo: marcar para que el
        // RecaudacionUploadManager encole. (Confirmar API existente al implementar.)
    }

    private suspend fun cargar() {
        val pendientes = pendientesDao.observarContadorPendientes(empresaId).first()
        val fallidas = pendientesDao.observarContadorFallidas(empresaId).first()
        if (pendientes > 0) {
            _state.update { CuadreUiState.BloqueadoPorPendientes(pendientes - fallidas, fallidas) }
            return
        }
        when (val res = repository.cargarSemana(semanaInicio)) {
            is DomainResult.Success -> {
                cuadre = res.value
                contado = recuentoDao.observar(empresaId, tecnicoId, semanaInicio.toString())
                    .first()?.let { recuentoStore.deserializar(it.recuentoJson) } ?: emptyMap()
                recomputar()
            }
            is DomainResult.Failure -> _state.update { CuadreUiState.SinConexion }
        }
    }

    private fun recomputar() {
        val c = cuadre ?: return
        if (c.numRecaudaciones == 0) {
            _state.update { CuadreUiState.Vacio(semanaInicio) }
            return
        }
        val diferencia = calcularDiferencia(c.esperadoPorDenominacion, contado)
        _state.update {
            CuadreUiState.Listo(
                semanaInicio = semanaInicio,
                numRecaudaciones = c.numRecaudaciones,
                diferencia = diferencia,
            )
        }
    }
}
```

NOTA al implementar: confirma las rutas/símbolos exactos de `AuthRepository.currentUserId()`, `SessionState.Active.empresa.id`, `RecaudacionPendienteDao.observarContadorPendientes`, y `RealtimeManager.revision`; ajusta imports. Para `onSubirPendientes`, inyecta el manager de subida ya existente (`RecaudacionUploadManager` o equivalente) y llama a su método de encolado.

- [ ] **Step 5: Ejecutar el test (debe PASAR)**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.cuadre.CuadreViewModelTest"`
Expected: PASS. (Si los fakes son inviables por tipos finales, aplica la nota del Step 2: extrae `construirEstado(...)` pura y testéala; deja el VM delegando en ella.)

- [ ] **Step 6: Verificar compilación**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:assembleDebug --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git -C /home/a/Escritorio/recre-main add android/app/src/main/java/com/recre/app/feature/cuadre/CuadreUiState.kt android/app/src/main/java/com/recre/app/feature/cuadre/CuadreViewModel.kt android/app/src/main/java/com/recre/app/core/data/local/dao/RecaudacionPendienteDao.kt android/app/src/test/java/com/recre/app/feature/cuadre/CuadreViewModelTest.kt
git -C /home/a/Escritorio/recre-main commit -m "feat(android): ViewModel del cuadre (esperado + recuento + pendientes + realtime)"
```

---

## Task 6: Android — pantalla, navegación y textos

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreScreen.kt`
- Create: `android/app/src/main/res/raw/cuadre_ok.json` (Lottie check verde).
- Modify: `android/app/src/main/java/com/recre/app/MainActivity.kt` (`Routes.CUADRE`, `composable`, `onCuadreClick`).
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt` (parámetro `onCuadreClick` + tarjeta de acceso).
- Modify: `android/app/src/main/res/values/strings.xml`.

**Interfaces:**
- Consumes: `CuadreViewModel`, `CuadreUiState`, `LineaCuadre`, `VeredictoCuadre`; componentes `RecreDetailTopBar`, `AppCard`, `CountUpText`, `StatusChip`, `LottieIllustration`, `formatearImporteEs`.
- Produces: `@Composable fun CuadreScreen(viewModel: CuadreViewModel = hiltViewModel(), onBack: () -> Unit)`.

- [ ] **Step 1: Textos en `strings.xml`**

Añade a `android/app/src/main/res/values/strings.xml`:

```xml
<string name="cuadre_titulo">Mi caja</string>
<string name="cuadre_acceso">Cuadrar mi caja</string>
<string name="cuadre_acceso_sub">Comprueba el efectivo de la semana</string>
<string name="cuadre_deberias_llevar">Deberías llevar</string>
<string name="cuadre_de_n_recaudaciones">de %1$d recaudaciones</string>
<string name="cuadre_llevas">Llevas %1$s</string>
<string name="cuadre_cuadra">Cuadra</string>
<string name="cuadre_faltan">Faltan %1$s</string>
<string name="cuadre_sobran">Sobran %1$s</string>
<string name="cuadre_col_denominacion">Denominación</string>
<string name="cuadre_col_deberias">Deberías</string>
<string name="cuadre_col_cuentas">Tú cuentas</string>
<string name="cuadre_vacio">Aún no has recaudado esta semana</string>
<string name="cuadre_bloqueado_titulo">Tienes recaudaciones sin subir</string>
<string name="cuadre_bloqueado_reintentables">Quedan %1$d por subir. El cuadre necesita que estén todas.</string>
<string name="cuadre_bloqueado_fallidas">%1$d no se pudieron subir. Revisa el panel de subidas.</string>
<string name="cuadre_subir_ahora">Subir ahora</string>
<string name="cuadre_sin_conexion">Conéctate para ver el cuadre de esta semana</string>
<string name="cuadre_semana_anterior">Semana anterior</string>
<string name="cuadre_semana_siguiente">Semana siguiente</string>
```

- [ ] **Step 2: Lottie de éxito**

Crea `android/app/src/main/res/raw/cuadre_ok.json` reutilizando el check verde existente: copia el contenido de `android/app/src/main/res/raw/sync_ok.json` (mismo disco + trim-path del check, color verde de marca). Verifica con `JSON.parse` (o `python -c "import json,sys;json.load(open(sys.argv[1]))" android/app/src/main/res/raw/cuadre_ok.json`).

- [ ] **Step 3: Crear `CuadreScreen`**

Crea `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreScreen.kt`. Usa los componentes del sistema de diseño; tabla inline de denominaciones con input de conteo.

```kotlin
package com.recre.app.feature.cuadre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.cuadre.domain.LineaCuadre
import com.recre.app.feature.cuadre.domain.VeredictoCuadre
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.LottieIllustration
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.formatearImporteEs
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal

@Composable
fun CuadreScreen(
    onBack: () -> Unit,
    viewModel: CuadreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.cuadre_titulo),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val s = state) {
                is CuadreUiState.Cargando -> Text("…")
                is CuadreUiState.SinConexion ->
                    Text(stringResource(R.string.cuadre_sin_conexion))
                is CuadreUiState.Vacio ->
                    Text(stringResource(R.string.cuadre_vacio))
                is CuadreUiState.BloqueadoPorPendientes -> BloqueadoCard(
                    reintentables = s.reintentables,
                    fallidas = s.fallidas,
                    onSubir = viewModel::onSubirPendientes,
                )
                is CuadreUiState.Listo -> ListoContenido(
                    estado = s,
                    onContar = viewModel::onContarChange,
                )
            }
        }
    }
}

@Composable
private fun BloqueadoCard(reintentables: Int, fallidas: Int, onSubir: () -> Unit) {
    AppCard {
        Column {
            Text(stringResource(R.string.cuadre_bloqueado_titulo))
            if (reintentables > 0) {
                Text(stringResource(R.string.cuadre_bloqueado_reintentables, reintentables))
                androidx.compose.material3.TextButton(onClick = onSubir) {
                    Text(stringResource(R.string.cuadre_subir_ahora))
                }
            }
            if (fallidas > 0) {
                Text(stringResource(R.string.cuadre_bloqueado_fallidas, fallidas))
            }
        }
    }
}

@Composable
private fun ListoContenido(
    estado: CuadreUiState.Listo,
    onContar: (BigDecimal, Long) -> Unit,
) {
    val dif = estado.diferencia
    AppCard {
        Column {
            Text(stringResource(R.string.cuadre_deberias_llevar))
            CountUpText(importe = dif.totalEsperado.toPlainString())
            Text(stringResource(R.string.cuadre_de_n_recaudaciones, estado.numRecaudaciones))
            val veredicto = when (dif.veredicto) {
                VeredictoCuadre.CUADRA -> stringResource(R.string.cuadre_cuadra)
                VeredictoCuadre.FALTA ->
                    stringResource(R.string.cuadre_faltan, formatearImporteEs(dif.diferencia.abs().toPlainString()))
                VeredictoCuadre.SOBRA ->
                    stringResource(R.string.cuadre_sobran, formatearImporteEs(dif.diferencia.abs().toPlainString()))
            }
            Text(stringResource(R.string.cuadre_llevas, formatearImporteEs(dif.totalContado.toPlainString())) + " · " + veredicto)
            if (dif.veredicto == VeredictoCuadre.CUADRA) {
                LottieIllustration(rawRes = R.raw.cuadre_ok)
            }
        }
    }
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(dif.lineas) { linea -> FilaDenominacion(linea, onContar) }
    }
}

@Composable
private fun FilaDenominacion(linea: LineaCuadre, onContar: (BigDecimal, Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatearImporteEs(linea.denominacion.toPlainString()), Modifier.weight(1f))
        Text(linea.cantidadEsperada.toString(), Modifier.weight(1f))
        OutlinedTextField(
            value = if (linea.cantidadContada == 0L) "" else linea.cantidadContada.toString(),
            onValueChange = { txt ->
                onContar(linea.denominacion, txt.filter { it.isDigit() }.toLongOrNull() ?: 0L)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1.2f),
        )
        Text(if (linea.delta == 0L) "·" else linea.delta.toString(), Modifier.weight(0.6f))
    }
}
```

NOTA al implementar: `OutlinedTextField`/`TextButton` de Material disparan el guardarraíl `SinMaterialPeladoTest` en `feature/**`. Sustitúyelos por los equivalentes del sistema (`Field*`/`RecreTextButton` — revisa `ui/components`) antes de dar por cerrada la tarea, o el test de guardarraíl fallará. La estructura lógica se mantiene.

- [ ] **Step 4: Cablear navegación**

En `android/app/src/main/java/com/recre/app/MainActivity.kt`:
1. En `object Routes` añade: `const val CUADRE = "cuadre"`.
2. En el `NavHost` añade:

```kotlin
composable(Routes.CUADRE) {
    CuadreScreen(onBack = { navController.popBackStack() })
}
```
(import `com.recre.app.feature.cuadre.CuadreScreen`).
3. En el `composable(Routes.LOCALES)` pasa el callback nuevo:

```kotlin
onCuadreClick = { navController.navigate(Routes.CUADRE) },
```

En `android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt`:
1. Añade a la firma: `onCuadreClick: () -> Unit,`.
2. En la cabecera/acciones del cuerpo (junto a donde se usan `onAlertasClick`/`onIncidenciasClick`), añade una tarjeta de acceso:

```kotlin
AppCard(onClick = onCuadreClick, modifier = Modifier.fillMaxWidth()) {
    Column {
        Text(stringResource(R.string.cuadre_acceso))
        Text(stringResource(R.string.cuadre_acceso_sub))
    }
}
```

- [ ] **Step 5: Verificar compilación + guardarraíl**

Run: `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:assembleDebug --console=plain 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`.

Run (guardarraíl): `cd /home/a/Escritorio/recre-main/android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "*SinMaterialPelado*"`
Expected: PASS (tras sustituir los componentes Material por los del sistema en `CuadreScreen`).

- [ ] **Step 6: Commit**

```bash
git -C /home/a/Escritorio/recre-main add android/app/src/main/java/com/recre/app/feature/cuadre/CuadreScreen.kt android/app/src/main/res/raw/cuadre_ok.json android/app/src/main/java/com/recre/app/MainActivity.kt android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt android/app/src/main/res/values/strings.xml
git -C /home/a/Escritorio/recre-main commit -m "feat(android): pantalla Mi caja (cuadre semanal) + acceso desde el home"
```

---

## Self-Review

**1. Spec coverage**
- Alcance técnico-only Android → Tasks 2–6, sin tabla/web de oficina. ✅
- Fórmula Σ(total − local) total y por denominación → vista (Task 1) + dominio (Task 2). ✅
- Semana ISO en TZ empresa → `date_trunc(... AT TIME ZONE zona_horaria)` (Task 1) + cálculo de lunes con TZ en el VM (Task 5). ✅
- Origen servidor autoritativo (vista, no recálculo) → Task 1/3. ✅
- Exigir subir pendientes → `BloqueadoPorPendientes` con reintentables/fallidas (Tasks 5 y 6). ✅
- Recuento persistido en Room → Task 4 + uso en Task 5. ✅
- Semanas pasadas en lectura → `onCambiarSemana` (Task 5) + ‹ › (UX, Task 6 — añadir botones de navegación de semana al TopBar si se desea; el `onCambiarSemana` ya existe). ⚠️ Ver nota.
- Excluir anuladas → `WHERE estado='firme'` + test (Task 1). ✅
- Realtime → `revision.drop(1)` (Task 5). ✅
- Testing pgTAP + unit → Tasks 1–5. ✅

**2. Placeholder scan**: Hay NOTAs de "confirmar símbolo exacto" (DomainError, AuthRepository.currentUserId, API de subida, equivalentes del sistema de diseño) — son verificaciones de integración con código existente, no huecos de lógica; cada una indica qué confirmar y dónde. El control ‹ › de semana en el TopBar (botones que llaman `onCambiarSemana(±1)`) debe añadirse en Task 6 Step 3 dentro de `RecreDetailTopBar(actions = { ... })`; el ViewModel ya lo soporta.

**3. Type consistency**: `calcularDiferencia(Map<BigDecimal,Long>, Map<BigDecimal,Long>): DiferenciaCuadre` usado igual en Tasks 2 y 5. `CuadreSemanal.esperadoPorDenominacion: Map<BigDecimal,Long>` consumido en VM. `CuadreSemanalRow` campos = columnas de la vista (Task 1). `MIGRATION_8_9`, `version=9`, entidad `cuadre_recuento` coherentes (Task 4). ✅

**Correcciones aplicadas inline**: añadida nota de los botones ‹ › de semana (el VM ya expone `onCambiarSemana`); marcada la sustitución de componentes Material por los del sistema para no romper el guardarraíl.

---

## Riesgos y notas de integración (confirmar al implementar)

- Cadena de setup del test pgTAP: reutilizar columnas de `17_recaudacion_historica.sql` para empresa/usuario/local/maquina/licencia/instalacion.
- Símbolos exactos: `DomainError.*`, `AuthRepository.currentUserId()`, `SessionState.Active.empresa.id`, `EmpresaParamsDao.observe()/zonaHoraria`, API del manager de subida para `onSubirPendientes`.
- Guardarraíl `SinMaterialPeladoTest`: `CuadreScreen` debe usar `Field*`/`RecreTextButton`/`AppCard`, no `OutlinedTextField`/`TextButton` de Material.
- Room v9: si el proyecto exporta esquema, commitear `app/schemas/.../9.json`; registrar `MIGRATION_8_9` en el módulo Hilt de la base.
