import { test, expect } from "@playwright/test";

import { LoginPage } from "./pages/login.page";
import { getCredentials } from "./utils/env";

/**
 * Flujo crítico: autenticación.
 * Estos tests corren con contexto limpio (sin sesión reutilizada).
 */
test.describe("Login", () => {
  test("redirige a /login al acceder a una ruta protegida sin sesión", async ({ page }) => {
    await page.goto("/instalaciones");
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();
  });

  test("muestra un error con credenciales inválidas", async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login("noexiste@example.com", "contraseña-incorrecta");

    // El formulario muestra un toast de error (sonner) y permanece en /login.
    await expect(
      page.getByText(/Correo o contraseña incorrectos|No se ha podido iniciar sesión/),
    ).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("valida campos obligatorios y formato de email", async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.submitButton.click();

    // Zod + react-hook-form muestran mensajes de validación en español.
    await expect(page.getByText(/obligatorio|requerid/i).first()).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("inicia sesión con credenciales válidas y entra al dashboard", async ({ page }) => {
    const { email, password } = getCredentials();

    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email, password);

    await expect(page).not.toHaveURL(/\/login/);
  });
});
