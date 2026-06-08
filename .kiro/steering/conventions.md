---
inclusion: always
---

# Recre — Convenciones

## Idioma

- Documentación, comentarios, mensajes de UI/error visibles, tickets → **español**.
- **Identificadores de código**: inglés EXCEPTO los del dominio del negocio (`recaudacion`, `instalacion`, `maquina`, `local`, `licencia`, `tasa`, `parteLocal`, `parteEmpresa`, `valorCredito`, `cambioPlaca`, etc.) que mantienen español por consistencia con BBDD y dominio.
- BBDD: `snake_case` español (`recaudacion_neta`, `parte_local`, etc.).

## Git

### Branching

- `main`: protegida, lista para producción.
- Ramas de trabajo: `<tipo>/<scope>-<descripcion-corta>`.
  - `feat/web-licencias-crud`
  - `feat/android-recaudacion-flow`
  - `fix/edge-calculo-redondeo`
  - `docs/spec-cambio-placa`
  - `chore/repo-monorepo-setup`
  - `refactor/shared-zod-schemas`
- Una tarea (`T-XX`) → una rama → un PR. Excepción: tareas pequeñas y muy relacionadas se pueden agrupar.

### Conventional Commits

Formato: `<tipo>(<scope>): <descripción> (T-XX)`

- **Tipos**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `build`, `ci`.
- **Scopes**: `web`, `android`, `supabase`, `spec`, `repo`, `ci`.
- Descripción en presente, minúscula, sin punto final, ≤ 70 caracteres.
- Cuerpo (opcional, recomendado): explica el **por qué**.
- Footer: `BREAKING CHANGE:`, `Refs: T-XX`, `Closes #N`.

Ejemplo:
```
feat(supabase): add calcular-recaudacion edge function (T-20)

Implements the server-side recaudacion calculation using
shared _shared/calculo.ts. Validates input with Zod and
returns {procede, bruto, neto, parte_local, parte_empresa}.

Refs: T-20
```

### Pull Requests

- Título = commit principal.
- Cuerpo:
  - **Resumen**: qué y por qué.
  - **Cambios**: lista breve.
  - **Cómo probar**: pasos verificables.
  - **Capturas** si UI.
  - **Notas**: limitaciones, follow-ups.
  - **Refs**: `T-XX`, `Closes #N`.
- Squash & merge a `main` (historial limpio).
- Sin merge sin review cuando hay humano disponible.
- Tamaño objetivo: < 400 líneas cambiadas. Si supera, dividir.

## Nombrado

| Cosa | Convención | Ejemplo |
|---|---|---|
| Tablas y columnas SQL | snake_case | `recaudacion`, `parte_local` |
| Funciones SQL | snake_case | `obtener_baseline` |
| Variables/funciones TS | camelCase | `parteLocal`, `obtenerBaseline()` |
| Tipos/interfaces TS | PascalCase | `Recaudacion`, `EmpresaUsuario` |
| Componentes React | PascalCase | `RecaudacionForm` |
| Constantes TS/JS | SCREAMING_SNAKE_CASE | `DENOMINACIONES_PERMITIDAS` |
| Archivos TS no-componente | kebab-case.ts | `calcular-recaudacion.ts` |
| Archivos componente | PascalCase.tsx | `RecaudacionForm.tsx` |
| Variables/funciones Kotlin | camelCase | `parteLocal` |
| Clases/objetos Kotlin | PascalCase | `RecaudacionViewModel` |
| Constantes Kotlin | SCREAMING_SNAKE_CASE | `const val MAX_DENOMINACIONES` |
| Archivos Kotlin | PascalCase.kt | `RecaudacionScreen.kt` |
| Migraciones SQL | timestamp_descripcion | `20260519120000_create_recaudacion.sql` |
| Edge Functions (carpeta) | kebab-case | `crear-recaudacion/` |
| Variables de entorno | SCREAMING_SNAKE_CASE | `SUPABASE_URL` |
| i18n keys | dot.case | `licencias.create.title` |

## Tipado

### TypeScript
- `strict: true` en `tsconfig`.
- Sin `any`. Usa `unknown` y estrecha.
- Sin `as` salvo en boundaries justificados (con comentario del por qué).
- Tipos de BBDD generados, jamás editados a mano.
- Validación runtime con **Zod** en cada frontera (Edge inputs, formularios web).

### Kotlin
- Listas inmutables (`List`) por defecto.
- Sin `!!`. Si lo necesitas, repiensa el tipo o usa `requireNotNull` con mensaje.
- Sealed classes/interfaces para estados (`UiState.Loading | Success | Error`).
- Data classes para modelos.

## Manejo de dinero

**Crítico**. Nunca `number`/`Float`/`Double` para almacenar u operar euros.

- BBDD: `numeric(10,2)` (o el tamaño aplicable).
- TS: `decimal.js` (`new Decimal(...)`). Persistir/transmitir como string.
- Kotlin: `java.math.BigDecimal` con `MathContext.DECIMAL64` y `RoundingMode.HALF_UP`.
- Redondeo: **half-up** al céntimo. La empresa absorbe la diferencia (`parte_empresa = neto - parte_local`).
- Display centralizado: `formatEuros(value)` por plataforma. Formato es-ES → `1.234,56 €`.

## Manejo de fechas y semanas

- BBDD: `timestamptz` siempre.
- Zona horaria: `empresa.zona_horaria`, default `Europe/Madrid`.
- Semanas ISO: `semanas_iso_entre()` (SQL) o helper único compartido. NUNCA recalcular ad hoc.
- TS: `date-fns` + `date-fns-tz` (`getISOWeek`, `getISOWeekYear`).
- Kotlin: `java.time` + `IsoFields.WEEK_OF_WEEK_BASED_YEAR`.

## Manejo de errores

### Edge Functions
- Éxito: `{ data: ... }`.
- Error: `{ error: { code, message, details? } }` con HTTP status apropiado (400/401/403/404/409/500).
- Códigos en `_shared/errors.ts` (const string union o enum).
- Logging estructurado: `console.log(JSON.stringify({ level, msg, ...ctx }))`. **Sin PII** (sin email, sin contadores de cliente, sin firma).

### Web
- `ErrorBoundary` raíz.
- `Toast` (sonner) para errores accionables por usuario.
- react-query `onError` global para errores de red.
- Mensajes traducidos por código de error.

### Android
- `sealed class DomainResult<out T>` con `Success`/`Failure(error: DomainError)`.
- `DomainError` sealed: `Network`, `Validation`, `Auth`, `Conflict`, `Unknown`.
- ViewModels exponen errores en su `UiState`.
- `Snackbar` para errores recuperables.

## Logging y observabilidad

- Edge: JSON estructurado, sin PII.
- Web: `console.error` solo para inesperados.
- Android: `Timber` con tag por feature.
- Eventos clave (recaudación creada, conflicto detectado, anulación) → log con id + actor + timestamp.

## Seguridad

- Secretos en `.env*` (gitignored). `*.env.example` versionados.
- RLS en TODAS las tablas con `empresa_id`. Test de acceso cruzado en CI.
- Validación server-side de TODO input cliente. No confiar nunca en cliente.
- Storage privado, signed URLs (10 min).
- Tokens fuera de logs y URLs.
- Android: Room cifrado (SQLCipher); `EncryptedSharedPreferences` para tokens.
- Sin claves hardcodeadas. Sin `console.log` de tokens/firmas/PII.

## Testing

Pirámide: muchas unitarias, algunas integración, pocas e2e.

- **Unit**: lógica pura (cálculo, validación, mappers, formatters). Objetivo 80%+ en código de dominio.
- **Integración**: Edge Functions contra Supabase local. Verificar RLS.
- **E2E web**: Playwright en flujos críticos (login, alta instalación, listar recaudaciones).
- **Instrumentado Android**: Compose UI Test + Espresso para el flujo de recaudación completo.

Ubicación junto al código:
- `archivo.ts` → `archivo.test.ts`
- `Clase.kt` → `ClaseTest.kt` en `src/test/`

## Internacionalización

Aunque solo soportemos español, todo texto de UI por i18n:
- Web: `next-intl`, `web/src/i18n/<locale>.json`.
- Android: `strings.xml` en `res/values/` (default español).
- Claves en `dot.case`, agrupadas por feature.

## Accesibilidad

- shadcn/ui ya es accesible; respetar atributos.
- Iconos con label (`aria-label` web, `contentDescription` Android).
- Contraste AA mínimo.
- Navegación por teclado en web; foco visible.
- Soporte de Dynamic Type en Android.

## Performance

- Web: Server Components por defecto, lazy loading por ruta, `next/dynamic` para componentes pesados, `next/image`.
- React: `useMemo`/`useCallback` solo cuando hay problema medible.
- Compose: parámetros estables (`@Stable`/`@Immutable`), `key` en `LazyColumn`.
- DB: índices en columnas de filtros frecuentes; `EXPLAIN` en queries lentas.
- React Query: `staleTime` 60 s por defecto, ajustar por tipo de dato.

## Documentación

- KDoc/JSDoc en funciones públicas y APIs no triviales.
- Cada feature compleja: `README.md` en su carpeta.
- ADRs en `.kiro/adr/NNNN-titulo.md` para decisiones arquitectónicas.
- Mantener `.kiro/specs/recre/` actualizada cuando cambien decisiones cerradas.

## Linting y formato

- **Web**: ESLint (next + typescript-eslint) + Prettier. Reglas estrictas: `no-unused-vars: error`, `no-explicit-any: error`, `import/order`, `react-hooks/*`.
- **Android**: ktlint + detekt. Configs en raíz del módulo.
- **Edge Functions**: `deno fmt` + `deno lint`.
- **SQL**: sqlfluff (config en raíz).
- Pre-commit con lefthook (web) → format + lint en archivos staged.
- CI: lint completo + tests obligatorios para mergear.

## Stack y librerías

### web/
- Next.js 14 (App Router), TypeScript strict
- Tailwind CSS + shadcn/ui (Radix bajo el capó)
- @supabase/supabase-js + @supabase/ssr
- @tanstack/react-query (server state)
- react-hook-form + @hookform/resolvers/zod (forms)
- zod (validación, idéntica a edge)
- decimal.js (dinero), date-fns + date-fns-tz (fechas)
- sonner (toasts), next-intl (i18n)
- vitest + @testing-library/react (unit)
- playwright (e2e)
- eslint + prettier + lefthook

### android/
- Kotlin 2.x, Jetpack Compose + Material 3
- Hilt (DI)
- Room + KTX + Paging 3 (local DB)
- WorkManager (sync background)
- Coroutines + Flow
- Supabase Kotlin SDK (auth, postgrest, storage, realtime opcional)
- Kotlinx Serialization
- BigDecimal (java.math) para dinero
- ML Kit Text Recognition (fase 2 OCR)
- Bluetooth/ESC-POS para AGPTEK PT210 (librería ESC/POS Kotlin)
- Coil (imágenes)
- Timber (logging)
- JUnit 5 + Turbine + MockK + Compose UI Test (testing)
- ktlint + detekt

### supabase/
- Deno (Edge Functions)
- @supabase/supabase-js (server-side)
- zod (validación, compartida con web)
- pdf-lib (PDFs)
- date-fns + date-fns-tz, decimal.js
- pgTAP (SQL tests)

## Antipatrones (resumen)

- ❌ Recalcular recaudación en cliente.
- ❌ Editar tipos de BBDD generados.
- ❌ `number` para dinero.
- ❌ Importar entre features.
- ❌ Lógica de negocio en componentes UI.
- ❌ `console.log` con datos sensibles.
- ❌ Comentarios de QUÉ hace el código (debe leerse solo). Comenta el PORQUÉ.
- ❌ Abstracciones para 1 caso de uso.
- ❌ PRs gigantes mezclando features.
- ❌ Modificar migraciones aplicadas (siempre crea una nueva).
