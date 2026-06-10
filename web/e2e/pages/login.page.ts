import { type Locator, type Page, expect } from "@playwright/test";

/**
 * Page Object de la pantalla de login (`/login`).
 * Centraliza los selectores para que los tests no dependan del DOM concreto.
 */
export class LoginPage {
  readonly page: Page;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    // Inputs accesibles por su label (next-intl: "Correo electrónico" / "Contraseña").
    this.emailInput = page.getByLabel("Correo electrónico");
    this.passwordInput = page.getByLabel("Contraseña");
    this.submitButton = page.getByRole("button", { name: "Entrar" });
  }

  async goto(): Promise<void> {
    await this.page.goto("/login");
    await expect(this.submitButton).toBeVisible();
  }

  async login(email: string, password: string): Promise<void> {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }
}
