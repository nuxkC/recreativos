import { expect, test } from "@playwright/test";

/**
 * T-80 — Pantalla de login.
 *
 * Verifica el render y la validación en cliente (zod + react-hook-form), que
 * no necesita backend. El envío real contra Supabase se cubrirá en los tests
 * autenticados cuando exista un entorno de pruebas (ver e2e/README.md).
 */

test.describe("Login", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
  });

  test("renderiza título, descripción y campos", async ({ page }) => {
    await expect(page.getByText("Recre", { exact: true })).toBeVisible();
    await expect(
      page.getByText("Accede con tu cuenta para gestionar locales, máquinas y recaudaciones."),
    ).toBeVisible();

    await expect(page.getByLabel("Correo electrónico")).toBeVisible();
    await expect(page.getByLabel("Contraseña")).toBeVisible();
    await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();
  });

  test("muestra errores de validación al enviar el formulario vacío", async ({ page }) => {
    await page.getByRole("button", { name: "Entrar" }).click();

    await expect(page.getByText("El correo es obligatorio")).toBeVisible();
    await expect(page.getByText("La contraseña es obligatoria")).toBeVisible();
    // No debe navegar fuera de /login.
    await expect(page).toHaveURL(/\/login$/);
  });

  test("rechaza un correo con formato inválido (validación nativa del navegador)", async ({
    page,
  }) => {
    const email = page.getByLabel("Correo electrónico");
    await email.fill("no-es-un-email");
    await page.getByLabel("Contraseña").fill("123456");
    await page.getByRole("button", { name: "Entrar" }).click();

    // El input type="email" hace que el navegador bloquee el envío: la
    // restricción de validez falla y la página no navega fuera de /login.
    const valid = await email.evaluate((el) => (el as HTMLInputElement).validity.valid);
    expect(valid).toBe(false);
    await expect(page).toHaveURL(/\/login$/);
  });

  test("valida longitud mínima de contraseña", async ({ page }) => {
    await page.getByLabel("Correo electrónico").fill("tecnico@empresa.com");
    await page.getByLabel("Contraseña").fill("123");
    await page.getByRole("button", { name: "Entrar" }).click();

    await expect(page.getByText("La contraseña debe tener al menos 6 caracteres")).toBeVisible();
  });
});
