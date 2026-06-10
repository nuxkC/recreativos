import { test, expect } from "@playwright/test";

import { RecaudacionesPage } from "./pages/recaudaciones.page";

/**
 * Flujo crítico: listado de recaudaciones.
 * Usa la sesión autenticada del proyecto "setup".
 */
test.describe("Recaudaciones", () => {
  test("muestra el listado de recaudaciones", async ({ page }) => {
    const recaudaciones = new RecaudacionesPage(page);
    await recaudaciones.goto();
    await recaudaciones.esperarListadoCargado();
  });

  test("filtra por estado anulada manteniéndose en la página", async ({ page }) => {
    const recaudaciones = new RecaudacionesPage(page);
    await recaudaciones.goto();

    // Navegación por query param: el filtro de estado se refleja en la URL
    // y la página sigue mostrando el listado (con o sin resultados).
    await page.goto("/recaudaciones?estado=anulada");
    await expect(recaudaciones.heading).toBeVisible();
    await recaudaciones.esperarListadoCargado();
  });
});
