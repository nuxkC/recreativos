---
inclusion: always
---

# Recre — Arquitectura y principios

Aplica a todo el proyecto (web, android, supabase). Léelo antes de proponer estructura, crear archivos o introducir librerías.

## Principios fundamentales

1. **Single Source of Truth (SSOT)**. La lógica crítica vive en UN solo sitio:
   - Cálculo de recaudación → `supabase/functions/_shared/calculo.ts` y/o función SQL `calcular_recaudacion`. Web y Android **consumen** el resultado, nunca lo recalculan.
   - Validación de denominaciones → `supabase/functions/_shared/validators.ts`.
   - Schemas Zod → `supabase/functions/_shared/schemas.ts`. Reutilizables por web (cliente y server) y por Edge.
   - Tipos de BBDD → generados con `supabase gen types typescript`. **No se editan a mano.**
2. **DRY pero no prematuro**. 1 uso → en línea. 2 → evalúa. 3 → extrae a un módulo común.
3. **Capas y separación de responsabilidades**. UI no habla con la BBDD directamente. Lógica de negocio fuera de componentes UI.
4. **Tipado fuerte end-to-end**. `strict` en TypeScript. Kotlin sin `!!`. Sin `any`/`Any`.
5. **Inmutabilidad por defecto**. `const`/`val`. Estado se reemplaza, no se muta.
6. **Falla rápido y claro**. Validación en frontera (entrada Edge Function, formulario). Errores explícitos.
7. **Sin acoplamiento entre features**. Una feature no importa de otra. Lo común sube a `core`/`shared`.
8. **Server-side wins**. Cualquier cifra económica final se valida y persiste server-side. El cliente solo propone y muestra.

## Estructura por subproyecto

### web/

```
web/
  src/
    app/                          # Next.js App Router
      (auth)/login/page.tsx
      (dashboard)/
        licencias/, maquinas/, locales/, instalaciones/,
        recaudaciones/, conflictos/, equipo/, ajustes/
      layout.tsx
    components/
      ui/                         # shadcn/ui (no editar manualmente)
      common/                     # genéricos: DataTable, EmptyState, ConfirmDialog
    features/                     # 1 carpeta por feature
      <feature>/
        components/
        hooks/
        api.ts                    # queries Supabase
        schemas.ts                # zod (re-export desde shared cuando aplique)
        types.ts
    lib/
      supabase/{client,server,types}.ts
      utils/{money,date,format}.ts
      auth/, query/
    hooks/                        # hooks reusables genéricos
    i18n/                         # es.json
```

Reglas:
- Cálculo de recaudación / tasa / reparto → `calcular-recaudacion` (Edge), nunca local.
- Queries Supabase → `features/<feature>/api.ts`. Componentes consumen via hooks de react-query.
- Componentes "tontos" en `components/`. Lógica de feature en `features/`.
- Server Components por defecto. Cliente solo cuando hace falta interacción/estado.

### android/

```
android/app/src/main/java/com/recre/
  core/
    data/{remote,local,repository,sync}/
    domain/{model,repository,usecase}/
    ui/{theme,components}/        # design system propio
    util/{money,date,format}/
  feature/
    auth/, locales/, recaudacion/, gestion/, ajustes/
      ui/                         # composables + screens
      vm/                         # ViewModels
  di/                              # módulos Hilt
```

Reglas:
- **Clean Architecture**: `data → domain ← ui`. Features no se importan entre sí.
- ViewModels exponen `StateFlow<UiState>` (sealed). Composables son tontos.
- Repositorios como interfaces en `domain`, implementación en `data`.
- Mappers explícitos: DTO ↔ entidad domain ↔ UI state.
- Recaudación se calcula vía Edge; nunca localmente fuera de eso.
- Single-module en fase 1; preparado para multi-module Gradle cuando crezca.

### supabase/

```
supabase/
  migrations/
    20260519120000_<descripcion>.sql
  functions/
    _shared/
      calculo.ts        # SSOT del cálculo de recaudación
      schemas.ts        # zod
      validators.ts     # denominaciones, contadores, etc.
      auth.ts           # extraer user/empresa/role del JWT
      storage.ts        # uploads + signed URLs
      pdf.ts            # generación tickets
      errors.ts         # códigos y helpers HTTP
      types.ts          # tipos de dominio
      constants.ts      # DENOMINACIONES_PERMITIDAS, etc.
    <kebab-case>/index.ts
  tests/{sql,functions}/
  seed.sql
```

Reglas:
- Cada Edge Function = endpoint pequeño y enfocado. Reusable a `_shared/`.
- **Migraciones aditivas**: una vez aplicadas no se editan; se crea otra que rectifique.
- RLS en cada tabla con `empresa_id`. Cada policy se revisa en code review.
- Funciones SQL críticas (baseline, semanas) tienen tests pgTAP.

## Tamaños y límites (suaves, alertan a refactor)

- TS: archivo ≤ 300 líneas (máx 500). Función ≤ 50 líneas. Componente ≤ 200.
- Kotlin: archivo ≤ 400 (máx 600). Función ≤ 50. Composable ≤ 150.
- SQL: función ≤ 30 líneas o lleva tests y comentarios.

## Compartir código entre subproyectos

- **TypeScript** (web ↔ edge): por ahora, copiar schemas/utilidades cortas y mantenerlas idénticas. Cuando el solapamiento crezca, montar **pnpm workspaces** con `packages/shared/` (tipos, zod, money, date). ADR previo a hacerlo.
- **Kotlin** ↔ TypeScript: no hay compartición directa. Los contratos viven en los schemas Zod del backend; en Kotlin se replican como data classes y se validan al deserializar.

## Escalabilidad

- **Modularización Android** cuando >5 features grandes (fase 2): pasar a módulos Gradle.
- **Monorepo** con pnpm workspaces si justificado.
- **ADRs** en `.kiro/adr/NNNN-titulo.md` para decisiones arquitectónicas (cambio de stack, librería core, refactor mayor). Plantilla: contexto / decisión / alternativas / consecuencias.
- **Feature flags** server-side cuando aparezca la primera feature en beta.

## Antipatrones a evitar (también listados en `conventions.md`)

- Recalcular recaudación en cliente.
- Editar tipos de BBDD generados.
- `number`/`Float`/`Double` para dinero.
- Importar entre features (`feature/a` desde `feature/b`).
- Lógica de negocio en componentes UI.
- Crear abstracciones para 1 caso de uso.
- Modificar migraciones aplicadas.
- PRs gigantes mezclando features.
