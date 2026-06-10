import { type Locator, type Page, expect } from "@playwright/test";

/**
 * Page Object del listado de recaudaciones (`/recaudaciones`).
 */
export class RecaudacionesPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly tabla: Locator;
  readonly vacioMensaje: Locator;
  readonly filtroEstado: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole("heading", { name: "Recaudaciones" });
    this.tabla = page.getByRole("table");
    this.vacioMensaje = page.getByText("No hay recaudaciones que coincidan con los filtros.");
    this.filtroEstado = page.getByText("Estado", { exact: true }).first();
  }

  async goto(): Promise<void> {
    await this.page.goto("/recaudaciones");
    await expect(this.heading).toBeVisible();
  }

  /** El listado muestra o bien una tabla con filas o bien el estado vacío. */
  async esperarListadoCargado(): Promise<void> {
    await expect(this.tabla.or(this.vacioMensaje)).toBeVisible();
  }
}
