import { describe, expect, it } from "vitest";

import { AveriaInputSchema, RecambioInputSchema } from "./schemas";

describe("AveriaInputSchema", () => {
  it("acepta una avería válida y normaliza vacíos a null", () => {
    const parsed = AveriaInputSchema.parse({
      categoria: "atasco_billete",
      descripcion: "  ",
      poneMaquinaFueraServicio: "",
      notas: "  cambiar fuente  ",
    });
    expect(parsed.categoria).toBe("atasco_billete");
    expect(parsed.descripcion).toBeNull();
    expect(parsed.poneMaquinaFueraServicio).toBe(false);
    expect(parsed.notas).toBe("cambiar fuente");
  });

  it("coacciona el flag fuera de servicio (no-vacío → true)", () => {
    expect(AveriaInputSchema.parse({
      categoria: "otro",
      descripcion: "",
      poneMaquinaFueraServicio: "true",
      notas: "",
    }).poneMaquinaFueraServicio).toBe(true);
  });

  it("rechaza una categoría fuera del catálogo", () => {
    const result = AveriaInputSchema.safeParse({
      categoria: "explosion",
      descripcion: "",
      poneMaquinaFueraServicio: "",
      notas: "",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.categoria?.[0]).toBe("categoriaRequerida");
    }
  });
});

describe("RecambioInputSchema", () => {
  it("normaliza el coste (coma decimal, dos decimales) y deja vacío como null", () => {
    expect(RecambioInputSchema.parse({ pieza: "Aceptador", cantidad: "2", coste: "12,5", notas: "" }).coste).toBe(
      "12.50",
    );
    expect(RecambioInputSchema.parse({ pieza: "Tornillo", cantidad: "1", coste: "", notas: "" }).coste).toBeNull();
  });

  it("rechaza pieza vacía, cantidad no positiva y coste negativo o con >2 decimales", () => {
    expect(RecambioInputSchema.safeParse({ pieza: " ", cantidad: "1", coste: "", notas: "" }).success).toBe(false);
    expect(RecambioInputSchema.safeParse({ pieza: "X", cantidad: "0", coste: "", notas: "" }).success).toBe(false);
    expect(RecambioInputSchema.safeParse({ pieza: "X", cantidad: "1", coste: "-1", notas: "" }).success).toBe(false);
    expect(RecambioInputSchema.safeParse({ pieza: "X", cantidad: "1", coste: "1.234", notas: "" }).success).toBe(
      false,
    );
  });
});
