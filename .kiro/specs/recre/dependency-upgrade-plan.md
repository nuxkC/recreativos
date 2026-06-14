# Plan de actualización de dependencias — Recre

> Estado: **propuesta para aprobación**. Objetivo: modernizar el catálogo sin romper la app. Versiones "latest"
> verificadas contra **registros reales** (npm / Maven Central / Google Maven) en jun-2026; **revalidar con
> `npm view` / `maven-metadata.xml` antes de cada bloque** (cambian). Prereleases (alpha/beta/rc) **excluidas**.

## 0. Principios

1. **Baseline verde antes de tocar nada.** Lockfiles commiteados; tests/lint/build en verde por subproyecto.
2. **Un bloque coherente = una rama = un PR.** Verificar build + tests **entre** bloques. Revertir un bloque entero
   si falla (sobre todo el atómico de Kotlin).
3. **SSOT compartido es sagrado.** `zod`, `date-fns`, `date-fns-tz`, `decimal.js` viven **a la vez** en `web/` y en
   `supabase/functions/deno.json`. Subir una *major* en un lado sin el otro **rompe el contrato** de validación/cálculo
   de recaudación. Mover **siempre en PR coordinada**.
4. **Reglas no negociables del repo**: dinero (`decimal.js`/`BigDecimal`, half-up) y semanas ISO (`Europe/Madrid`)
   no se tocan sin tests pgTAP + unitarios verdes antes y después.
5. **Separar "seguro ahora" de "requiere migración".** No mezclar saltos mayores con bumps menores.

## 1. Estado actual (lo que está escrito en el repo)

**Android** (`android/gradle/libs.versions.toml`, `app/build.gradle.kts`, wrapper):
Gradle **8.14.4** · AGP **8.7.3** · Kotlin **2.0.21** · KSP **2.0.21-1.0.27** · Compose BOM **2024.11.00** ·
minSdk **26** / target **35** / compile **35**. 50 librerías, 8 plugins. Stack: Hilt 2.52, Room 2.6.1,
WorkManager 2.9.1, coroutines 1.9.0, serialization-json 1.7.3, ktor 3.0.1, supabase-kt 3.0.2, lifecycle 2.8.7,
activity 1.9.3, navigation 2.8.4, CameraX 1.4.1, ML Kit text-recognition 16.0.1, firebase-bom 33.7.0, Coil 2.7.0.

**Web** (`web/package.json`): Next **14.2.0** · React/React-DOM **18.3.1** · Tailwind **3.4.16** ·
`@supabase/ssr` 0.5.2 · `@supabase/supabase-js` 2.108.1 · `@tanstack/react-query` 5.62.7 · `zod` 3.23.8 ·
`date-fns` 3.6.0 / `date-fns-tz` 3.2.0 · `decimal.js` 10.4.3 · `next-intl` 3.26.0 · `recharts` 2.13.3 ·
`sonner` 1.7.1 · ESLint 8.57.1 · TypeScript 5.7.2 · Vitest 4.1.8. 28 deps + 17 devDeps.

**Supabase** (`supabase/functions/deno.json`): `@supabase/supabase-js`, `zod`, `date-fns`/`date-fns-tz`, `decimal.js`
compartidos con web (SSOT del cálculo).

## 2. Las 5 cadenas de riesgo (entender antes de tocar)

1. **Android atómico**: Kotlin ⇄ KSP ⇄ compose-compiler ⇄ Compose BOM ⇄ Hilt ⇄ Room. Si se desincronizan, **falla la
   generación de código**. El **prefijo de KSP DEBE igualar la versión de Kotlin** (p.ej. Kotlin 2.1.21 → KSP
   `2.1.21-2.0.2`). Tratar como **un solo bloque**.
2. **Cascada web**: React 19 ← Next 15/16 ← ESLint 9/10 (flat config) ← `eslint-config-next` 16 ← `next-intl` 4.
   No se suben por separado. `@types/react` 19 es incompatible con runtime React 18.
3. **Tailwind v3→v4**: rompe la config (CSS-first `@theme`, nuevo PostCSS `@tailwindcss/postcss`), arrastra
   `shadcn/ui`, `tailwind-merge` 3, `prettier-plugin-tailwindcss` 0.7+, y reemplaza `tailwindcss-animate` por
   `tw-animate-css`. Riesgo **visual** además de build.
4. **SSOT del cálculo**: `zod` v4 y `date-fns` v4 son *major*; tocan los schemas y el cálculo compartidos
   web ⇄ supabase. PR coordinada con pgTAP + `deno test` verdes.
5. **supabase-kt ⇄ ktor**: supabase-kt 3.x fija la versión de ktor compatible; subir ktor aislado rompe el cliente.
   Mover los 5 módulos supabase-kt + ktor juntos según su matriz.

## 3. Plan ordenado por bloques

### Bloque 0 — Preparación
Rama por subproyecto. Baseline verde: web `npm run lint && npm run test && npx tsc --noEmit`; android
`JAVA_HOME=/snap/android-studio/current/jbr ./gradlew test`; supabase `deno test … --config`. **No tocar nada** hasta
tener baseline. Commitear lockfiles.

> Nota preexistente (memoria del repo): `next build` está roto localmente por `@supabase/ssr`, y un test depende de
> CRLF. **Aislar/resolver** esto **antes** de la migración web mayor para no atribuir fallos al upgrade.

### Bloque 1 — Web, seguro ahora (patches/minors dentro de la major actual)
`@tanstack/react-query` 5.101.0 · `react-hook-form` 7.78.0 · `@hookform/resolvers` 3.10.0 (sigue en v3) ·
`decimal.js` 10.6.0 (**alinear con supabase**) · `zod` 3.25.76 (sigue en v3) · `next` 14.2.35 ·
`eslint-config-next` 14.2.35 · todos los `@radix-ui` a su último patch · `recharts` 2.15.4 · `sonner` 1.7.4 ·
`tailwind-merge` 2.6.1 · `tailwindcss` 3.4.19 · `prettier` 3.8.4 · `prettier-plugin-tailwindcss` 0.6.14 ·
`autoprefixer` 10.5.0 · `postcss` 8.5.15 · `@testing-library/*` últimos · `@vitejs/plugin-react` 4.7.0.
**Verificar**: lint + test + `tsc --noEmit`. Commit.

### Bloque 2 — Web, TypeScript dentro de 5.x
Subir TS al último **5.x** (no 6.0). Aislado (puede sacar nuevos errores de tipos). Verificar `tsc --noEmit` + test.

### Bloque 3 — Android, libs aisladas de bajo riesgo (sin tocar Kotlin)
`appcompat` 1.7.1 · `datastore-preferences` 1.1.7 · `material` 1.13.0 · `mockk`(+android) 1.14.11 · `turbine` 1.2.1 ·
`google-services` 4.4.4 · `core-ktx` 1.16.0 (sigue compatible con compileSdk 35).
**Verificar**: `assembleDebug` + `test` (con JAVA_HOME del snap). Commit.

### Bloque 4 — Android, AGP dentro de 8.x
AGP **8.7.3 → 8.13.2** (Gradle 8.14.4 actual ya lo soporta). **NO** subir a AGP 9 (solo alpha) ni Gradle 9 todavía.
Aísla el cambio de toolchain antes del bloque de Kotlin. Verificar build + lint. Commit.

### Bloque 5 — Android, ATÓMICO: Kotlin / KSP / Compose / Hilt / Room ⚛️
Mover **juntos y alineados**:
- Kotlin **2.0.21 → 2.1.21** en los 3 plugins (`kotlin.android`, `kotlin.compose`, `kotlin.serialization`).
- KSP → **2.1.21-2.0.2** (prefijo == Kotlin).
- Compose BOM → versión alineada con Kotlin 2.1 → **2025.10.00+** (esto es lo que **habilita Material 3 Expressive /
  `MotionScheme`** del plan de diseño; idealmente **2025.12.00**). Añadir `material-icons-core` **explícito** (desde
  material3 1.4.0 ya no es transitivo).
- `kotlinx-serialization-json` → ~1.8.x · coroutines core/android/test → **1.10.2** (los tres igual).
- Hilt → ~2.56.2 (KSP-compatible) · Room runtime/compiler/ktx → **2.7.2**.

**Verificar**: `assembleDebug` (genera código Hilt/Room/KSP) + `test`, **en especial tests de DAO/migraciones Room**.
**Si algo falla, revertir el bloque entero.** Commit.

### Bloque 6 — Android, lifecycle/activity/navigation + WorkManager
Tras Kotlin alineado: lifecycle runtime/viewmodel/viewmodel-compose → **2.9.x** (misma versión) ·
`activity-compose` 1.10.1 · `navigation-compose` 2.9.8 · `hilt-navigation-compose` 1.3.0 · `work-runtime-ktx` 2.10.x.
Verificar navegación y ciclos de vida. Commit.

### Bloque 7 — Android, CameraX / MLKit / Firebase (features con hardware/servicios)
CameraX `core/camera2/lifecycle/view` los 4 a **1.5.x juntos** → **probar OCR de contadores (T-100) en dispositivo**.
`firebase-bom` 34.14.1 (quitar versión explícita de `firebase-messaging`, la fija el BOM) → **probar push (T-101)**.
**Verificación MANUAL** además de build. Commit separado por feature.

### Bloque 8 — Android, tests instrumentados
`androidx-test-ext-junit` 1.3.0 + `espresso-core` 3.7.0 juntos. `connectedAndroidTest` si hay emulador; si no, al
menos compilar `androidTest`. Commit.

### Bloque 9 — REQUIERE MIGRACIÓN: SSOT (zod/date-fns) coordinado web ⇄ supabase
Antes de migrar `zod` 4 o `date-fns` 4 en web, migrar **en paralelo** `supabase/functions/deno.json`
(`zod`@3→@4, `date-fns`@3→@4; `decimal.js` ya en 10) porque schemas/cálculo son **SSOT compartido**. **Una sola PR
coordinada** con pgTAP + `deno test` verdes. No empezar hasta cerrar los bloques seguros.
> `@hookform/resolvers` pasa a 4.x/5.x **a la vez** que `zod` 4 (alinea la firma del resolver).

### Bloque 10 — REQUIERE MIGRACIÓN: web mayor — React 19 + Next 15→16
Bloque grande aparte: `react`/`react-dom` 19 + `@types/react`/`@types/react-dom` 19 + `next` 15 (caching por defecto
cambia, `params`/`searchParams` pasan a **async**) → validar → `next` 16 + ESLint 9/10 (**flat config**
`eslint.config.js`) + `eslint-config-next` 16 + `next-intl` 4. **Migrar incremental** (14→15, estabilizar, luego 16).
Resolver antes el problema de `next build` con `@supabase/ssr` y evaluar `@supabase/ssr` 0.6.1 (handler de cookies
`getAll`/`setAll`). Verificar build + e2e Playwright en cada subpaso.
> **Habilita**: `useOptimistic` nativo y React **View Transitions** del plan de diseño.

### Bloque 11 — REQUIERE MIGRACIÓN: web mayor — Tailwind v4
Aparte de todo: `tailwindcss` 4 + `@tailwindcss/postcss` + `tailwind-merge` 3 + `prettier-plugin-tailwindcss` 0.8 +
sustituir `tailwindcss-animate` por `tw-animate-css` + **revalidar shadcn/ui** (`components/ui/`). Reconfigurar PostCSS
y theme CSS-first. **Validación visual** además de build.

### Bloque 12 — DIFERIDO: toolchains mayores (cada uno su PR)
Solo cuando lo anterior esté estable: Gradle 9.5.1 + AGP 9 (cuando salga de alpha) · Kotlin 2.2/2.4 ·
Compose BOM 2026.x · supabase-kt 3.6 + ktor alineado · Coil 3 (reescritura `io.coil-kt.coil3`) · TypeScript 6 ·
`recharts` 3 · `@vitejs/plugin-react` 6 · `jsdom`/`@types/node` mayores · `lucide-react` 1.x · `next-themes` (ya al día).

## 4. Registro de riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| R1 | SSOT (zod/date-fns/decimal) desalineado web↔supabase | PR coordinada (Bloque 9); pgTAP + deno test |
| R2 | Dinero / semanas ISO (decimal, date-fns-tz, Europe/Madrid) | Tests antes y después; no mezclar con otros bumps |
| R3 | Android Kotlin⇄KSP⇄compose⇄Hilt⇄Room desincronizado | Bloque 5 atómico; prefijo KSP == Kotlin; revertir entero |
| R4 | Cascada web React19/Next/ESLint/next-intl | Bloque 10; migración en cascada, no por piezas |
| R5 | Tailwind v3→v4 (config + shadcn + visual) | Bloque 11 aislado; validación visual |
| R6 | AGP 9 / Gradle 9 (JVM 17, config cache) | Diferidos (Bloque 12); AGP 8.13 basta con Gradle 8.14.4 |
| R7 | Hardware/servicios: CameraX 1.5 (OCR), firebase 34 (push) | Verificación manual en dispositivo (Bloque 7) |
| R8 | `next build` roto por `@supabase/ssr` (preexistente) + test CRLF | Resolver/aislar antes del Bloque 10 |
| R9 | Build android sin `JAVA_HOME` del snap | Exportar `JAVA_HOME=/snap/android-studio/current/jbr` en cada gate |
| R10 | supabase-kt fija ktor | Mover los 5 módulos + ktor juntos según matriz (Bloque 12) |

## 5. Comandos de verificación (gates entre bloques)

```bash
# web (desde la raíz, con --prefix)
npm --prefix web run lint
npm --prefix web run test
npx --prefix web tsc --noEmit
npm --prefix web run format:check
npm --prefix web run build
npm --prefix web run test:e2e
npm --prefix web outdated

# android (desde android/, JAVA_HOME obligatorio)
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew assembleDebug
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew test
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew lint
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew connectedAndroidTest   # con emulador

# supabase (desde la raíz)
deno test supabase/functions/ --config supabase/functions/deno.json
deno fmt supabase/functions/ && deno lint supabase/functions/
supabase test db   # pgTAP, con stack local arriba

# revalidar "latest" antes de cada bloque
npm view <pkg> version
curl -s https://repo1.maven.org/maven2/<grupo>/<artefacto>/maven-metadata.xml
```

## 6. Resumen ejecutivo

- **Seguro ya (bajo riesgo)**: Bloques 1–8 → ~40 bumps menores en web y android, incluido el **salto a Material 3
  Expressive** (Compose BOM 2025.12.00 + Kotlin 2.1.21) que **desbloquea el motion del plan de diseño**.
- **Requiere migración (planificar aparte)**: zod4/date-fns4 (SSOT coordinado), React 19 + Next 16, Tailwind v4.
- **Diferir**: Gradle 9 / AGP 9 (alpha), Coil 3, TS 6, supabase-kt 3.6 + ktor.
- **Crítico**: nunca subir Kotlin sin alinear KSP; nunca subir SSOT (zod/date-fns/decimal) en un solo lado; mantener
  los gates verdes con `JAVA_HOME` del snap.
