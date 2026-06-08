# Recre — Web

Back-office en **Next.js 14** (App Router) + **TypeScript** + **Tailwind** + **shadcn/ui** + **Supabase**.

## Requisitos

- Node.js 20+ (recomendado 22)
- npm (también funciona con pnpm o yarn)
- Una instancia de Supabase activa (local con `supabase start` o cloud)

## Configuración

```bash
cp .env.example .env.local
# Edita .env.local con la URL y la anon key de tu proyecto Supabase
```

## Scripts

```bash
npm run dev         # arranca el servidor de desarrollo en :3000
npm run build       # compila para producción
npm run start       # arranca el build de producción
npm run lint        # ESLint
npm run format      # Prettier --write
npm run format:check
npm run test        # Vitest una sola pasada
npm run test:watch  # Vitest watch
```

## Estructura

```
src/
  app/
    (auth)/login/         # pantalla de login
    (dashboard)/          # rutas protegidas (sidebar + header)
      dashboard/
      licencias/
      maquinas/
      locales/
      instalaciones/
      recaudaciones/
      cambios-placa/
      conflictos/
      equipo/
      ajustes/
      layout.tsx          # sidebar + topbar + guard de empresa activa
    seleccionar-empresa/  # selector cuando el usuario tiene >1 membresía
    sin-acceso/           # mensaje cuando no hay membresía activa
    auth/                 # callbacks y signout
    layout.tsx            # providers globales
    page.tsx              # redirige al dashboard
  components/
    common/               # genéricos (PlaceholderPage, ...)
    layout/               # sidebar, topbar, empresa-switcher, user-menu
    ui/                   # shadcn/ui (no editar manualmente)
  i18n/
    request.ts            # config next-intl
    messages/es.json      # traducciones
  lib/
    auth/roles.ts         # roles + helpers de autorización UI
    empresas/             # queries, server actions y cookie de empresa activa
    env.ts                # acceso tipado a env vars
    query/                # provider de TanStack Query
    supabase/             # cliente browser, server y middleware
  middleware.ts           # protege rutas y refresca cookie de sesión
```

## Convenciones

Antes de tocar código lee:

- `.kiro/steering/architecture.md` — principios y estructura por feature
- `.kiro/steering/conventions.md` — naming, libs, antipatrones

En particular:

- Toda la UI vía claves i18n (`next-intl`).
- Sin cálculo de recaudación en cliente: se llama a la Edge Function `calcular-recaudacion`.
- Dinero con `decimal.js` (`Decimal`), nunca `number`.
- Fechas con `date-fns` + `date-fns-tz`.

## Estado

Inicializado en T-04. El layout protegido (sidebar, topbar, selector de empresa
con cookie persistente) llegó en T-30. Las pantallas reales del back-office
(licencias, máquinas, locales, instalaciones, recaudaciones, conflictos,
equipo, ajustes) se construyen en T-31..T-40.
