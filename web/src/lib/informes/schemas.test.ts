import { describe, expect, it } from "vitest";

import { rangoPorDefecto, resolverFiltros } from "./schemas";

const AHORA = new Date("2026-06-15T10:00:00Z");
const UUID = "11111111-1111-1111-1111-111111111111";

describe("rangoPorDefecto", () => {
  it("abarca los últimos 12 meses hasta hoy", () => {
    expect(rangoPorDefecto(AHORA)).toEqual({
      desde: "2025-07-01",
      hasta: "2026-06-15",
    });
  });
});

describe("resolverFiltros", () => {
  it("aplica el rango por defecto cuando faltan fechas", () => {
    expect(resolverFiltros({}, AHORA)).toEqual({
      localId: null,
      desde: "2025-07-01",
      hasta: "2026-06-15",
    });
  });

  it("respeta fechas e id de local válidos", () => {
    expect(
      resolverFiltros({ local: UUID, desde: "2026-01-01", hasta: "2026-03-31" }, AHORA),
    ).toEqual({
      localId: UUID,
      desde: "2026-01-01",
      hasta: "2026-03-31",
    });
  });

  it("descarta valores manipulados y cae al valor por defecto", () => {
    const resultado = resolverFiltros(
      { local: "no-es-uuid", desde: "ayer", hasta: "2026-03-31" },
      AHORA,
    );
    expect(resultado.localId).toBeNull();
    expect(resultado.desde).toBe("2025-07-01");
    expect(resultado.hasta).toBe("2026-03-31");
  });
});
