import { type Locator, type Page, expect } from "@playwright/test";

/**
 * Page Object del listado y alta de instalaciones (`/instalaciones`).
 */
export class InstalacionesPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly nuevaButton: Locator;
  readonly tabla: Locator;
  readonly vacioMensaje: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole("heading", { name: "Instalaciones" });
    this.nuevaButton = page.getByRole("link", { name: "Nueva instalación" });
    this.tabla = page.getByRole("table");
    this.vacioMensaje = page.getByText("No hay instalaciones que coincidan con los filtros.");
  }

  async goto(): Promise<void> {
    await this.page.goto("/instalaciones");
    await expect(this.heading).toBeVisible();
  }

  async irANueva(): Promise<void> {
    await this.nuevaButton.click();
    await this.page.waitForURL("**/instalaciones/nueva");
  }

  /** El listado muestra o bien una tabla con filas o bien el estado vacío. */
  async esperarListadoCargado(): Promise<void> {
    await expect(this.tabla.or(this.vacioMensaje)).toBeVisible();
  }
}
