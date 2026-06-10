import path from "node:path";

import { test as setup, expect } from "@playwright/test";

import { LoginPage } from "./pages/login.page";
import { getCredentials } from "./utils/env";

const STORAGE_STATE = path.join(__dirname, ".auth", "user.json");

/**
 * Setup de autenticación: inicia sesión una sola vez vía UI y persiste el
 * estado (cookies de sesión + empresa activa) para reutilizarlo en el resto
 * de proyectos. Evita repetir el login en cada test.
 */
setup("autenticar y guardar sesión", async ({ page }) => {
  const { email, password } = getCredentials();

  const loginPage = new LoginPage(page);
  await loginPage.goto();
  await loginPage.login(email, password);

  // Tras el login el middleware redirige fuera de /login.
  await expect(page).not.toHaveURL(/\/login/);

  // Si el usuario pertenece a varias empresas, elegir la primera para fijar
  // la empresa activa en la cookie (necesaria para las rutas del dashboard).
  if (page.url().includes("/seleccionar-empresa")) {
    await page.getByRole("button").first().click();
    await page.waitForURL((url) => !url.pathname.includes("/seleccionar-empresa"));
  }

  // Confirmar que tenemos una sesión operativa (no estamos en login ni bloqueados).
  await expect(page).not.toHaveURL(/\/(login|sin-acceso)/);

  await page.context().storageState({ path: STORAGE_STATE });
});
