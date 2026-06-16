# Estado de integración — Rediseño UI/UX + Fase 5 (deps)

> Instantánea del **2026-06-15**. Resume qué quedó integrado en `main` y, sobre todo,
> **qué queda pendiente y por qué**. La verdad de cada tarea vive en `tasks.md`; esto
> es el mapa de navegación.

## Qué se integró a `main`

Dos programas, antes apilados en 11 PRs, integrados a `main` por merge commits (2026-06-15):

| PR | Tarea(s) | Contenido |
|----|----------|-----------|
| #21 | T-227/228/229 | Design system "Confianza Industrial": tokens, tipografía Geist, átomos |
| #22 | T-231 | Web: motion 12.x (animaciones) |
| #23 | T-232 | Android: keypad in-app + pantalla denominaciones |
| #24 | T-233 | Campos de fecha → DatePicker del sistema (**cierra F2**) |
| #25 | T-234/235/236 | Android: F3 IA (bottom-nav, ajustes con pestañas, centro de alertas) |
| #26 | T-237/238/239/240 | Web: F4 IA (Cmd+K, dashboard bento, deudas, descuadres inline) |
| #27 | T-246/247/248 | Deps web bajo riesgo (B0/B1/B2): minors + TypeScript 5.9.3 |
| #28 | T-249/250 | Deps Android bajo riesgo (B3/B4): libs + AGP 8.13.2 |
| #29 | T-251/252/253/254 | Deps Android (B5-B8): Kotlin 2.1.21 atómico + CameraX/Firebase |
| #30 | (deuda lint) | Android lint baseline a 0 errores |
| #31 | T-255 | zod 4 + date-fns 4 (B9), SSOT coordinado web ⇄ supabase |

**Validación del estado integrado** (rediseño + todas las deps juntas por primera vez):
web `tsc` 0 · Vitest 65/65 · lint limpio · supabase `deno check` 0 · `deno test` 66/66.
Android: catálogo de versiones coherente (KSP prefix == Kotlin), validado por bloque vía
`assembleDebug`/`testDebugUnitTest`; **sin** build full del estado integrado (ver pendientes).

> Nota PRs: #22/#23/#24 quedaron **cerrados** por GitHub al borrar la rama base de #21
> durante el merge; su contenido **sí está en `main`**. #25–#31 se cierran como *merged*.

## Correcciones post-integración

| PR | Origen | Contenido |
|----|--------|-----------|
| #50 | pre-existente | `/deudas`: embed PostgREST ambiguo en `v_local_saldo` → query de nombres aparte |
| #51 | **regresión T-255** | zod 4 `uuid()` pasó a RFC 4122 estricto y rechazaba los ids de **seed** (versión/variante `0`): **toda pantalla de detalle daba 404** (`notFound`) y las Edge rechazarían esos ids. Fix: `z.string().uuid()` → `z.string().guid()` (18 sitios web + `_shared/schemas.ts`). Verificado: detalle de las 6 secciones → 200; tsc/eslint 0; deno 44/44. En prod los uuid v4 reales no se veían afectados. |

## Qué queda PENDIENTE (no perder)

### Rediseño
- **T-229** (átomos base): caja marcada hecha; la capa Android está completa y los átomos
  web se consumen en F4. Si aparece algún átomo de los 44 sin materializar, reabrir aquí.
- ~~T-230 Android motion~~ → **Hecho** (capa propia `RecreMotion`). El API oficial
  (`MaterialExpressiveTheme`/`MotionScheme`) resultó ser `internal` en material3 1.4.0 (BOM 2025.12);
  se materializó el vocabulario §2.4 de forma estable. La migración al API oficial queda en **T-258**.
- **T-241** skeletons / pull-to-refresh / swipe actions.
- **T-242** wizards multipaso.
- **T-243** drawers y toasts consistentes.
- **T-244** transiciones de elemento compartido (se realza con T-256 / React 19).
- **T-245** barrido WCAG final (EAA).

### Fase 5 — deps mayores (van DESPUÉS del rediseño, ya en `main`)
- **T-256** Bloque 10 — React 19 + Next 15→16. Baseline B0 ya resuelto (`next build` verde + LF);
  queda **QA visual** de regresión tras el salto de React/Next.
- **T-257** Bloque 11 — Tailwind v4. **Bloqueador real:** reescritura CSS-first que debe
  **coordinarse con los tokens de T-227** + QA visual de regresión.
- **T-258** Bloque 12 — diferidos, cada uno su PR de migración: AGP 9/Gradle 9 (arrastra
  compileSdk 36; hoy se mantiene 35 a propósito), Kotlin 2.2/2.4, TypeScript 6, Coil 3,
  supabase-kt 3.6 + ktor, recharts 3, lucide 1.x.

### Verificación manual Android (deps hechas, falta probar en hardware)
- **T-253** OCR de contadores (T-100) + push (T-101) en **dispositivo real**.
- **T-254** `connectedAndroidTest` en **emulador/dispositivo**.

### Deuda técnica heredada (T-246/B0) — ✅ RESUELTA (2026-06-16)
- `next build`: fallaba por **2 imports sin usar** (`filter-chip`/`subtotal-separator`), no por
  `@supabase/ssr` (warning de Edge Runtime, no bloquea). Corregidos → build verde.
- "CRLF": era el fuente `registro/actions.ts` en CRLF (no un test; los CSV usan `\r\n` por
  RFC 4180) → normalizado a LF + prettier limpio. **Baseline listo para T-256.**

## Orden recomendado para lo pendiente
1. ~~Rediseño F0–F6 (T-227…T-245)~~ — ✅ hecho (swipe descartado; shared-element web pendiente de T-256).
2. ~~Deuda T-246/B0 (`next build` + CRLF)~~ — ✅ resuelta.
3. **Siguiente:** T-256 (React 19 + Next 16) → T-257 (Tailwind v4) → T-258 (diferidos), en ese orden.
4. Verificación en dispositivo de T-253/T-254 cuando haya hardware.
