import { describe, expect, it } from "vitest";

import { calcularInfoTrial } from "./trial";

const AHORA = new Date("2026-05-23T10:00:00Z");

function enDias(dias: number): Date {
  return new Date(AHORA.getTime() + dias * 24 * 60 * 60 * 1000);
}

describe("calcularInfoTrial", () => {
  it("clasifica como vigente cuando quedan muchos días", () => {
    const info = calcularInfoTrial(enDias(14), AHORA);
    expect(info.estado).toBe("vigente");
    expect(info.diasRestantes).toBe(14);
  });

  it("clasifica como porExpirar dentro del umbral", () => {
    const info = calcularInfoTrial(enDias(2), AHORA);
    expect(info.estado).toBe("porExpirar");
    expect(info.diasRestantes).toBe(2);
  });

  it("clasifica como porExpirar justo en el umbral (3 días)", () => {
    const info = calcularInfoTrial(enDias(3), AHORA);
    expect(info.estado).toBe("porExpirar");
    expect(info.diasRestantes).toBe(3);
  });

  it("redondea hacia arriba las fracciones de día", () => {
    const info = calcularInfoTrial(new Date(AHORA.getTime() + 0.2 * 24 * 60 * 60 * 1000), AHORA);
    expect(info.estado).toBe("porExpirar");
    expect(info.diasRestantes).toBe(1);
  });

  it("marca como expirado cuando la fecha ya pasó", () => {
    const info = calcularInfoTrial(enDias(-1), AHORA);
    expect(info.estado).toBe("expirado");
    expect(info.diasRestantes).toBe(0);
  });

  it("marca como expirado exactamente en el momento de fin", () => {
    const info = calcularInfoTrial(AHORA, AHORA);
    expect(info.estado).toBe("expirado");
    expect(info.diasRestantes).toBe(0);
  });

  it("respeta un umbral personalizado", () => {
    const info = calcularInfoTrial(enDias(5), AHORA, 7);
    expect(info.estado).toBe("porExpirar");
    expect(info.diasRestantes).toBe(5);
  });

  it("acepta una fecha ISO en string", () => {
    const info = calcularInfoTrial("2026-06-06T10:00:00Z", AHORA);
    expect(info.estado).toBe("vigente");
    expect(info.diasRestantes).toBe(14);
  });

  it("trata una fecha inválida como expirado", () => {
    const info = calcularInfoTrial("no-es-fecha", AHORA);
    expect(info.estado).toBe("expirado");
    expect(info.diasRestantes).toBe(0);
  });
});
