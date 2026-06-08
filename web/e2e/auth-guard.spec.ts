import { expect, test } from "@playwright/test";

/**
 * T-80 — Guardas de autenticación del middleware.
 *
 * Sin sesión, cualquier ruta privada debe redirigir a `/login` conservando
 * el destino original en el query param `next`. Las rutas públicas (`/login`,
 * `/auth/*`) no redirigen.
 */

const PROTECTED_ROUTES = [
  "/",
  "/dashboard",
  "/licencias",
  "/maquinas",
  "/locales",
  "/instalaciones",
  "/recaudaciones",
  "/cambios-placa",
  "/conflictos",
  "/equipo",
  "/ajustes",
  "/sin-acceso",
  "/sin-permiso",
  "/seleccionar-empresa",
];

test.describe("Guardas de autenticación", () => {
  for (const route of PROTECTED_ROUTES) {
    test(`ruta privada ${route} redirige a /login sin sesión`, async ({ page }) => {
      await page.goto(route);

      await expect(page).toHaveURL(/\/login(\?|$)/);
      // El destino original se preserva en `next` (salvo la raíz, que es "/").
      const url = new URL(page.url());
      expect(url.pathname).toBe("/login");
      expect(url.searchParams.get("next")).toBe(route);
    });
  }

  test("la ruta /login es pública y no redirige", async ({ page }) => {
    const response = await page.goto("/login");
    expect(response?.ok()).toBeTruthy();
    await expect(page).toHaveURL(/\/login$/);
  });
});
