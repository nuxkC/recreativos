import { describe, expect, it } from "vitest";

import { LocalInputSchema } from "./schemas";

const base = {
  nombre: "Bar Pepe",
  // Dirección estructurada (T-277): el formulario siempre envía los 5, vacíos → null.
  comunidadAutonoma: "",
  provinciaCodigo: "",
  municipioCodigo: "",
  calle: "",
  codigoPostal: "",
  cifONif: "",
  titularNombre: "",
  telefono: "",
  email: "",
  notas: "",
};

describe("LocalInputSchema (validación de formato)", () => {
  it("acepta campos opcionales vacíos como null", () => {
    const p = LocalInputSchema.parse(base);
    expect(p.cifONif).toBeNull();
    expect(p.telefono).toBeNull();
    expect(p.email).toBeNull();
  });

  it("rechaza un CIF/NIF inválido", () => {
    const r = LocalInputSchema.safeParse({ ...base, cifONif: "12345678A" });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.flatten().fieldErrors.cifONif?.[0]).toBe("cifInvalido");
  });

  it("rechaza un teléfono inválido", () => {
    const r = LocalInputSchema.safeParse({ ...base, telefono: "512345678" });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.flatten().fieldErrors.telefono?.[0]).toBe("telefonoInvalido");
  });

  it("acepta CIF y teléfono válidos", () => {
    const p = LocalInputSchema.parse({ ...base, cifONif: "12345678Z", telefono: "612345678" });
    expect(p.cifONif).toBe("12345678Z");
    expect(p.telefono).toBe("612345678");
  });
});
