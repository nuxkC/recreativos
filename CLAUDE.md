# CLAUDE.md

Guía para Claude Code (claude.ai/code) en este repo.

## Qué es esto

Recre: plataforma multi-tenant para empresas de máquinas recreativas. Monorepo, tres subproyectos independientes:

- `web/` — back-office Next.js 14 (App Router) + TS + Tailwind + shadcn/ui
- `android/` — app de técnicos Kotlin + Compose + Hilt + Room + WorkManager
- `supabase/` — Postgres (migraciones + RLS) + Edge Functions en Deno

**Normativos, léelos primero:** `.kiro/steering/architecture.md` (principios, capas, SSOT, estructura) y `.kiro/steering/conventions.md` (naming, git, dinero, fechas, errores, stack). La spec (requisitos, diseño, tareas `T-XX`) en `.kiro/specs/recre/`.

## Comandos

### web/ (desde `web/`)

```bash
npm run dev      # :3000 (necesita .env.local con Supabase)
npm run build    # producción
npm run lint     # ESLint
npm run format   # Prettier --write
npm run test     # Vitest una pasada
npm run test:watch
npx vitest run src/lib/utils/money.test.ts   # un solo test
npm run test:e2e # Playwright
```

### supabase/ (desde la raíz)

```bash
supabase start            # stack local (necesita Docker)
supabase db reset         # migraciones + seed.sql
supabase migration new <descripcion>
supabase functions serve  # edge functions con hot reload
supabase test db          # pgTAP de supabase/tests/sql/
deno test supabase/functions/
deno fmt supabase/functions/ && deno lint supabase/functions/
psql -h 127.0.0.1 -p 54322 -U postgres -d postgres -f supabase/tests/sql/01_semanas_iso_entre.sql   # un test SQL
```

### android/ (desde `android/`)

```bash
./gradlew assembleDebug    # APK debug (necesita local.properties con Supabase)
./gradlew test             # unitarios
./gradlew :app:testDebugUnitTest --tests "com.recre.app.core.util.*"   # un solo test
./gradlew connectedAndroidTest   # instrumentados (emulador/dispositivo)
./gradlew lint
```

## Arquitectura

**SSOT — el cálculo vive en el servidor.** Toda cifra económica se calcula y persiste server-side. La recaudación solo en `supabase/functions/_shared/calculo.ts` (+ SQL `obtener_baseline`, `semanas_iso_entre`). Web y Android llaman a la edge `calcular-recaudacion` y muestran el resultado; nunca recalculan local. Igual para denominaciones (`_shared/validators.ts`) y schemas Zod (`_shared/schemas.ts`).

**Multi-tenancy vía RLS.** Toda tabla lleva `empresa_id` con RLS (`migrations/…enable_rls_and_policies.sql` + helpers en `…_add_rls_helpers.sql`). Contexto empresa/rol del JWT en `_shared/auth.ts`. Tabla nueva → RLS desde su migración.

**Edge Functions.** Una carpeta kebab-case por endpoint en `supabase/functions/`, con `index.ts` (`Deno.serve`). Respuesta: éxito `{ data }`, error `{ error: { code, message, details? } }` con status y códigos de `_shared/errors.ts`. Entrada validada con Zod. CORS en `_shared/cors.ts`. Tests `*.test.ts` junto al código (Deno test + `@std/assert`).

**Capas por cliente.**
- **web**: Server Components por defecto; feature en `src/features/<feature>/` (components/hooks/api.ts/schemas.ts), queries Supabase vía react-query, genéricos en `components/common/`, shadcn en `components/ui/` (no editar a mano). Textos UI por next-intl (`src/i18n/messages/es.json`).
- **android**: Clean Architecture `data → domain ← ui` en `app/src/main/java/com/recre/app/`. ViewModels exponen `StateFlow<UiState>` (sealed); errores `DomainResult`/`DomainError` (`core/util/`). Features no se importan entre sí.

**Migraciones: aditivas e inmutables.** Una migración aplicada jamás se edita; otra con timestamp nuevo la rectifica. Formato `YYYYMMDDHHMMSS_descripcion.sql`. Funciones SQL críticas → tests pgTAP en `supabase/tests/sql/` (`BEGIN…ROLLBACK`, sin depender de `seed.sql`).

## Reglas no negociables

- **Dinero**: nunca `number`/`Float`/`Double`. BBDD `numeric(10,2)`; TS `decimal.js` (transmitir string); Kotlin `BigDecimal`. Half-up al céntimo; `parte_empresa = neto - parte_local`.
- **Semanas ISO**: solo `semanas_iso_entre()` (SQL) o el helper compartido, nunca ad hoc. `timestamptz` siempre; TZ de la empresa (default `Europe/Madrid`).
- **Idioma**: docs/comentarios/UI en español. Identificadores en inglés EXCEPTO términos de dominio (`recaudacion`, `instalacion`, `maquina`, `licencia`, `parteLocal`…). BBDD snake_case español.
- **Tipos BBDD** con `supabase gen types typescript`; no editar a mano.
- TS `strict`, sin `any` (usa `unknown`). Kotlin sin `!!`.
- Sin PII en logs (firmas, contadores, emails). Edge: logging JSON estructurado.
- Comentarios explican el **porqué**, no el qué.

## Git

- Conventional Commits: `<tipo>(<scope>): <descripción> (T-XX)` — scopes: `web`, `android`, `supabase`, `spec`, `repo`, `ci`. Presente, minúscula, ≤70 car.
- Ramas: `<tipo>/<scope>-<descripcion>` (ej. `feat/web-licencias-crud`). Una `T-XX` → una rama → un PR (<400 líneas; squash & merge).
- Plan de tareas `T-XX` en `.kiro/specs/recre/tasks.md`; márcalo al completar.

## graphify

Knowledge graph en `graphify-out/` (god nodes, comunidades, relaciones cross-file).

- Preguntas sobre el código: primero `graphify query "<pregunta>"` (subgrafo acotado, mucho menor que grep). `graphify path "<A>" "<B>"` para relaciones; `graphify explain "<concepto>"` para un concepto.
- `graphify-out/wiki/index.md` para navegación amplia; `GRAPH_REPORT.md` solo para revisión arquitectónica.
- Tras modificar código: `graphify update .` (AST-only, sin coste API).

## Ahorro de contexto

Fuga nº1 medida = releer los mismos ficheros (`Read` ~3,2M chars en 29 sesiones, los mismos ~12 ficheros 9-25×). Aplica a Claude **y a subagentes**.

- **Símbolo > fichero (Serena).** TS (`web/`, `supabase/functions/`) y Kotlin (`android/`): `find_symbol`/`get_symbols_overview`/`find_referencing_symbols`, no `Read` del fichero entero. Flujo: graphify orienta → Serena trae el símbolo → `Read` solo para editar. SQL no lo cubre Serena (fallback: `Read` de rango). **No borres `supabase/functions/tsconfig.json`**: solo existe para el LSP de tsserver; sin él las referencias en Edge Functions fallan. Hooks `PreToolUse` te lo recuerdan al usar `Read` y `cat`/`sed` sobre código.
- **RTK comprime Bash solo** (~75% medido: silencia progreso, poda con `tail`, filtra `grep`/`ls`/`find`). No lo repliques a mano. Tu responsabilidad es la *disciplina de llamada*: no leas código por Bash (`cat`/`head`/`sed` sobre `.ts/.kt/.sql` esquiva Serena), manda build/test al sandbox `ctx_execute` o acota `… 2>&1 | tail -40` (devuelve el fallo, no el log de éxito), y agrupa comandos (`a && b`) en vez de ping-pong.
- **Migraciones SQL inmutables → no se releen.** El esquema sale de graphify o de los steering files.
- **context-mode**: web con `ctx_fetch_and_index`+`ctx_search` (no `WebFetch`); procesar/filtrar/contar/parsear salidas grandes con `ctx_execute`/`ctx_batch_execute` (solo el `console.log` entra), no `Read`+razonar.
- **Rutas absolutas, no `cd`** (480 `cd` medidos, disparan permisos y ruido): `git -C <dir>`, `npm --prefix web …`, `./gradlew` desde `android/`, `supabase`/`psql` desde la raíz.
