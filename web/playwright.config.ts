import { defineConfig, devices } from "@playwright/test";

/**
 * Configuración de las pruebas E2E (Playwright) de la web de Recre.
 *
 * Variables de entorno relevantes (ver `.env.example`):
 * - E2E_BASE_URL: URL donde corre la app (por defecto http://localhost:3000).
 * - E2E_USER_EMAIL / E2E_USER_PASSWORD: credenciales de un usuario de prueba
 *   con membresía activa. NUNCA se hardcodean credenciales reales.
 * - E2E_REUSE_SERVER: si es "false" Playwright levanta su propio servidor dev.
 */
const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const IS_CI = Boolean(process.env.CI);

// El proyecto "setup" autentica una vez y guarda el storageState reutilizable.
const STORAGE_STATE = "e2e/.auth/user.json";

export default defineConfig({
  testDir: "./e2e",
  // Solo los archivos *.spec.ts son tests; los *.setup.ts son proyectos de setup.
  fullyParallel: true,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI ? 1 : undefined,
  reporter: IS_CI ? [["html", { open: "never" }], ["list"]] : [["list"]],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: BASE_URL,
    locale: "es-ES",
    timezoneId: "Europe/Madrid",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "setup",
      testMatch: /.*\.setup\.ts/,
    },
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        storageState: STORAGE_STATE,
      },
      dependencies: ["setup"],
      // Los flujos de login no deben reutilizar la sesión autenticada.
      testIgnore: /login\.spec\.ts/,
    },
    {
      // Flujos de autenticación: contexto limpio, sin storageState.
      name: "auth-flows",
      use: { ...devices["Desktop Chrome"] },
      testMatch: /login\.spec\.ts/,
    },
  ],
  webServer: {
    command: "npm run dev",
    url: BASE_URL,
    reuseExistingServer: process.env.E2E_REUSE_SERVER !== "false",
    timeout: 120_000,
  },
});
