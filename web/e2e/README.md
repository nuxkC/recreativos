# Tests E2E (Playwright) — T-80

Cubren los flujos clave verificables **sin un backend Supabase real**:

- **`auth-guard.spec.ts`** — el middleware redirige cualquier ruta privada a
  `/login?next=<ruta>` cuando no hay sesión, y deja pasar las rutas públicas.
- **`login.spec.ts`** — render de la pantalla de login y validación en cliente
  (correo obligatorio/ inválido, contraseña obligatoria / longitud mínima).

## Ejecutar

```bash
# Desde web/
npm run test:e2e            # arranca la build de producción y corre los tests
npm run test:e2e:ui         # modo UI interactivo de Playwright
```

El `webServer` de `playwright.config.ts` hace `next build && next start` con
credenciales **placeholder** (`NEXT_PUBLIC_SUPABASE_URL` /
`NEXT_PUBLIC_SUPABASE_ANON_KEY`). Con esas credenciales el middleware resuelve
"sin usuario" y redirige a `/login`, que es justo lo que validan estos tests.

Puedes apuntar a un servidor ya levantado con:

```bash
E2E_BASE_URL=http://localhost:3000 npx playwright test
```

## Flujos autenticados (pendiente de entorno)

Los flujos que tocan datos (crear/anular recaudación, CRUDs de
licencias/máquinas/locales/instalaciones, resolución de conflictos, equipo)
requieren un **proyecto Supabase de pruebas** con:

- migraciones de `supabase/migrations/` aplicadas,
- un usuario semilla y una empresa con membresía,
- las variables `NEXT_PUBLIC_SUPABASE_URL` y `NEXT_PUBLIC_SUPABASE_ANON_KEY`
  reales en el entorno.

Cuando exista ese entorno, se añadirá un **proyecto Playwright `authenticated`**
con un `storageState` generado por un test de setup que haga login una vez y
reutilice la sesión. Esto evita repetir el login en cada test.
