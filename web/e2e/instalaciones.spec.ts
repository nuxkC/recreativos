import { test, expect } from "@playwright/test";

import { InstalacionesPage } from "./pages/instalaciones.page";

/**
 * Flujo crítico: gestión de instalaciones (listado y acceso al alta).
 * Usa la sesión autenticada del proyecto "setup".
 */
test.describe("Instalaciones", () => {
  test("muestra el listado de instalaciones", async ({ page }) => {
    const instalaciones = new InstalacionesPage(page);
    await instalaciones.goto();
    await instalaciones.esperarListadoCargado();
  });

  test("permite abrir el formulario de alta de instalación", async ({ page }) => {
    const instalaciones = new InstalacionesPage(page);
    await instalaciones.goto();

    // El botón "Nueva instalación" solo aparece para roles de gestión.
    const puedeCrear = await instalaciones.nuevaButton.isVisible();
    test.skip(!puedeCrear, "El usuario de prueba no tiene rol de gestión para crear instalaciones");

    await instalaciones.irANueva();
    await expect(page.getByRole("heading", { name: "Nueva instalación" })).toBeVisible();

    // El formulario debe ofrecer los selectores de dominio o el aviso de
    // que no hay recursos disponibles (ambos son estados válidos).
    const formularioVisible = page.getByText("Datos de la instalación");
    await expect(formularioVisible).toBeVisible();
  });
});
