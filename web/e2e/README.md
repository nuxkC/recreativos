# Pruebas E2E (Playwright)

Pruebas end-to-end de los flujos críticos de la web: **login**, **listado/alta de
instalaciones** y **listado de recaudaciones** (ver `conventions.md` → Testing).

## Estructura

```
e2e/
  auth.setup.ts            # Inicia sesión una vez y guarda la sesión (storageState)
  login.spec.ts            # Flujo de autenticación (contexto limpio)
  instalaciones.spec.ts    # Listado y acceso al alta de instalaciones
  recaudaciones.spec.ts    # Listado y filtrado de recaudaciones
  pages/                   # Page Objects (selectores reutilizables)
  utils/env.ts             # Lectura tipada de variables de entorno
  .auth/                   # Sesión persistida (gitignored)
```

## Requisitos previos

1. Instalar dependencias y navegadores:

   ```bash
   npm install
   npx playwright install --with-deps
   ```

2. Configurar variables de entorno (ver `web/.env.example`):

   - `E2E_BASE_URL` — URL de la app (por defecto `http://localhost:3000`).
   - `E2E_USER_EMAIL` / `E2E_USER_PASSWORD` — usuario de prueba con membresía
     activa. **No** uses credenciales reales de producción.
   - `E2E_REUSE_SERVER=false` — para que Playwright levante su propio `npm run dev`.

   La app necesita además `NEXT_PUBLIC_SUPABASE_URL` y `NEXT_PUBLIC_SUPABASE_ANON_KEY`
   apuntando a una instancia de Supabase (local o de staging) con datos de prueba.

## Ejecución

```bash
# Listar los tests detectados (no requiere navegadores ni Supabase)
npx playwright test --list

# Ejecutar todos los tests
npm run test:e2e

# Modo interactivo (UI)
npm run test:e2e:ui

# Ver el último informe HTML
npm run test:e2e:report
```

## Notas de diseño

- La autenticación se hace una sola vez en `auth.setup.ts` y se reutiliza vía
  `storageState`. El flujo de login se prueba aparte con contexto limpio.
- Los selectores viven en los Page Objects (`pages/`) y se basan en roles y
  textos accesibles (es-ES) en lugar de clases CSS frágiles.
- Las pruebas no asumen datos concretos: aceptan tanto listados con filas como
  el estado vacío, y omiten acciones de gestión si el rol del usuario no las
  permite (`test.skip`).
