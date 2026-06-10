import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ThemeToggle } from "./theme-toggle";

const setThemeMock = vi.fn();
let currentTheme = "system";

// next-intl: devolvemos la propia clave para no depender del catálogo real.
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => `tema.${key}`,
}));

// next-themes: controlamos el tema y espiamos setTheme.
vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: currentTheme, setTheme: setThemeMock }),
}));

afterEach(() => {
  setThemeMock.mockClear();
  currentTheme = "system";
});

describe("ThemeToggle", () => {
  it("renderiza un botón accesible con aria-label traducido", () => {
    render(<ThemeToggle />);

    const trigger = screen.getByRole("button", { name: "tema.toggleLabel" });
    expect(trigger).toBeInTheDocument();
  });

  it("expone el control como botón (foco por teclado) sin texto hardcodeado", () => {
    render(<ThemeToggle />);

    const trigger = screen.getByRole("button", { name: "tema.toggleLabel" });
    // El control debe ser enfocable por teclado para cumplir accesibilidad.
    trigger.focus();
    expect(trigger).toHaveFocus();
  });
});
