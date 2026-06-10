# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es esto

Recre: plataforma multi-tenant para empresas de máquinas recreativas. Monorepo con tres subproyectos independientes:

- `web/` — back-office en Next.js 14 (App Router) + TypeScript + Tailwind + shadcn/ui
- `android/` — app para técnicos en Kotlin + Jetpack Compose + Hilt + Room + WorkManager
- `supabase/` — Postgres (migraciones + RLS) y Edge Functions en Deno

**Lee primero los steering files** — son normativos para todo el repo:
- `.kiro/steering/architecture.md` — principios, capas, SSOT, estructura por subproyecto
- `.kiro/steering/conventions.md` — naming, git, dinero, fechas, errores, stack completo

La spec (requisitos, diseño técnico, plan de tareas `T-XX`) vive en `.kiro/specs/recre/`.

## Comandos

### web/ (ejecutar desde `web/`)

```bash
npm run dev               # dev server en :3000 (necesita .env.local con Supabase)
npm run build             # build de producción
npm run lint              # ESLint
npm run format            # Prettier --write
npm run test              # Vitest una pasada
npm run test:watch        # Vitest watch
npx vitest run src/lib/utils/money.test.ts   # un solo test
npm run test:e2e          # Playwright
```

### supabase/ (ejecutar desde la raíz del repo)

```bash
supabase start            # stack local (necesita Docker)
supabase db reset         # aplica migraciones + seed.sql
supabase migration new <descripcion>   # nueva migración
supabase functions serve  # edge functions en local con hot reload
supabase test db          # tests pgTAP de supabase/tests/sql/
deno test supabase/functions/          # tests unitarios de edge functions
deno fmt supabase/functions/ && deno lint supabase/functions/
# Un solo test SQL:
psql -h 127.0.0.1 -p 54322 -U postgres -d postgres -f supabase/tests/sql/01_semanas_iso_entre.sql
```

### android/ (ejecutar desde `android/`)

```bash
./gradlew assembleDebug          # APK debug (necesita local.properties con Supabase)
./gradlew test                   # tests unitarios
./gradlew :app:testDebugUnitTest --tests "com.recre.app.core.util.*"   # un solo test
./gradlew connectedAndroidTest   # instrumentados (emulador/dispositivo)
./gradlew lint
```

## Arquitectura — lo que hay que entender

### SSOT: el cálculo vive en el servidor

La regla central del proyecto: **toda cifra económica se calcula y persiste server-side**. El cálculo de recaudación vive solo en `supabase/functions/_shared/calculo.ts` (y funciones SQL como `obtener_baseline`, `semanas_iso_entre`). Web y Android llaman a la edge function `calcular-recaudacion` y muestran el resultado; **nunca lo recalculan localmente**. Lo mismo para validación de denominaciones (`_shared/validators.ts`) y schemas Zod (`_shared/schemas.ts`).

### Multi-tenancy vía RLS

Todas las tablas llevan `empresa_id` con políticas RLS (ver `migrations/20260519230500_enable_rls_and_policies.sql` y los helpers en `..._add_rls_helpers.sql`). El contexto de empresa/rol se extrae del JWT en `_shared/auth.ts`. Cualquier tabla nueva necesita RLS desde su migración.

### Edge Functions

Una carpeta kebab-case por endpoint en `supabase/functions/`, cada una con `index.ts` (`Deno.serve`). Contrato de respuesta: éxito `{ data: ... }`, error `{ error: { code, message, details? } }` con status HTTP apropiado y códigos definidos en `_shared/errors.ts`. Validación de entrada con Zod en cada función. CORS via `_shared/cors.ts`. Tests unitarios `*.test.ts` junto al código (Deno test + `@std/assert`).

### Capas por cliente

- **web**: Server Components por defecto; lógica de feature en `src/features/<feature>/` (components/hooks/api.ts/schemas.ts) cuando se cree, queries Supabase via react-query, componentes genéricos en `components/common/`, shadcn en `components/ui/` (no editar a mano). Textos de UI siempre por next-intl (`src/i18n/messages/es.json`).
- **android**: Clean Architecture `data → domain ← ui` bajo `app/src/main/java/com/recre/app/`. ViewModels exponen `StateFlow<UiState>` (sealed); errores como `DomainResult`/`DomainError` (en `core/util/`). Features no se importan entre sí.

### Migraciones

Aditivas e inmutables: una migración aplicada **jamás se edita**; se crea otra con timestamp nuevo que rectifique. Formato `YYYYMMDDHHMMSS_descripcion.sql`. Funciones SQL críticas llevan tests pgTAP en `supabase/tests/sql/` (envueltos en `BEGIN...ROLLBACK`, sin depender de `seed.sql`).

## Reglas no negociables

- **Dinero**: jamás `number`/`Float`/`Double`. BBDD `numeric(10,2)`; TS `decimal.js` (transmitir como string); Kotlin `BigDecimal`. Redondeo half-up al céntimo; `parte_empresa = neto - parte_local`.
- **Semanas ISO**: solo via `semanas_iso_entre()` (SQL) o el helper compartido. Nunca recalcular ad hoc. `timestamptz` siempre; zona horaria de la empresa (default `Europe/Madrid`).
- **Idioma**: docs, comentarios y textos de UI en español. Identificadores de código en inglés EXCEPTO términos del dominio (`recaudacion`, `instalacion`, `maquina`, `licencia`, `parteLocal`, etc.), que se mantienen en español. BBDD en snake_case español.
- **Tipos de BBDD** generados con `supabase gen types typescript`; no se editan a mano.
- TS `strict`, sin `any` (usa `unknown`). Kotlin sin `!!`.
- Sin PII en logs (firmas, contadores del titular, emails). Edge: logging JSON estructurado.
- Comentarios explican el **porqué**, no el qué.

## Git

- Conventional Commits: `<tipo>(<scope>): <descripción> (T-XX)` — scopes: `web`, `android`, `supabase`, `spec`, `repo`, `ci`. Descripción en presente, minúscula, ≤ 70 caracteres.
- Ramas: `<tipo>/<scope>-<descripcion>` (ej. `feat/web-licencias-crud`). Una tarea `T-XX` → una rama → un PR (< 400 líneas; squash & merge).
- El plan de tareas con los `T-XX` está en `.kiro/specs/recre/tasks.md`; márcalo al completar tareas.
