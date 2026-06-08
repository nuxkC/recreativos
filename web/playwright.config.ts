import { defineConfig, devices } from "@playwright/test";

/**
 * Configuración de Playwright para los E2E de la web (T-80).
 *
 * Los tests de este directorio cubren flujos que NO requieren un backend
 * Supabase real: guardas de autenticación del middleware, render del login y
 * validación de formularios en cliente. Por eso el `webServer` arranca la
 * build de producción con credenciales placeholder: el middleware resuelve
 * "sin usuario" y redirige a `/login`, que es justo lo que verificamos.
 *
 * Para flujos autenticados (crear recaudación, CRUDs, etc.) se necesita un
 * proyecto Supabase de pruebas; esos tests se añadirán como un proyecto
 * Playwright aparte cuando exista el entorno (ver e2e/README.md).
 */
const PORT = Number(process.env.E2E_PORT ?? 3100);
const BASE_URL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: `npm run build && npm run start -- --port ${PORT}`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    env: {
      NEXT_PUBLIC_SUPABASE_URL:
        process.env.NEXT_PUBLIC_SUPABASE_URL ?? "https://placeholder.supabase.co",
      NEXT_PUBLIC_SUPABASE_ANON_KEY:
        process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "placeholder-anon-key",
    },
  },
});
