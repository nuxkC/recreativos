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
- **T-256** Bloque 10 — React 19 + Next 15→16. **Bloqueador real:** requiere resolver antes
  `next build` (roto por `@supabase/ssr`) y el test CRLF (deuda de T-246/B0) + **QA visual**.
- **T-257** Bloque 11 — Tailwind v4. **Bloqueador real:** reescritura CSS-first que debe
  **coordinarse con los tokens de T-227** + QA visual de regresión.
- **T-258** Bloque 12 — diferidos, cada uno su PR de migración: AGP 9/Gradle 9 (arrastra
  compileSdk 36; hoy se mantiene 35 a propósito), Kotlin 2.2/2.4, TypeScript 6, Coil 3,
  supabase-kt 3.6 + ktor, recharts 3, lucide 1.x.

### Verificación manual Android (deps hechas, falta probar en hardware)
- **T-253** OCR de contadores (T-100) + push (T-101) en **dispositivo real**.
- **T-254** `connectedAndroidTest` en **emulador/dispositivo**.

### Deuda técnica heredada (T-246/B0)
- `next build` roto por `@supabase/ssr` (preexistente) y un test acoplado a CRLF. **Hay que
  resolverlos antes de T-256.**

## Orden recomendado para lo pendiente
1. T-230 + T-241…T-245 (rediseño restante) — independientes de las deps mayores.
2. Resolver la deuda de T-246/B0 (`next build` + CRLF).
3. T-256 (React 19 + Next 16) → T-257 (Tailwind v4) → T-258 (diferidos), en ese orden.
4. Verificación en dispositivo de T-253/T-254 cuando haya hardware.
