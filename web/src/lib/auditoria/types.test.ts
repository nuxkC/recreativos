import { describe, expect, it } from "vitest";

import {
  type EventoAuditoriaRow,
  isAccionAuditoria,
  isEntidadAuditoria,
  mapEventoAuditoriaRow,
} from "./types";

function row(overrides: Partial<EventoAuditoriaRow> = {}): EventoAuditoriaRow {
  return {
    id: "evt-1",
    empresa_id: "emp-1",
    actor_usuario_id: "usr-1",
    accion: "recaudacion_creada",
    entidad_tabla: "recaudacion",
    entidad_id: "rec-1",
    datos: { recaudacion_neta: "50.00", conflicto: false },
    created_at: "2026-05-10T08:00:00Z",
    ...overrides,
  };
}

describe("mapEventoAuditoriaRow", () => {
  it("convierte snake_case a camelCase y adjunta el nombre del actor", () => {
    const evento = mapEventoAuditoriaRow(row(), "Ana Gestora");
    expect(evento.empresaId).toBe("emp-1");
    expect(evento.actorUsuarioId).toBe("usr-1");
    expect(evento.actorNombre).toBe("Ana Gestora");
    expect(evento.accion).toBe("recaudacion_creada");
    expect(evento.entidadTabla).toBe("recaudacion");
    expect(evento.entidadId).toBe("rec-1");
    expect(evento.datos).toEqual({ recaudacion_neta: "50.00", conflicto: false });
  });

  it("normaliza datos null a objeto vacío", () => {
    const evento = mapEventoAuditoriaRow(row({ datos: null }), null);
    expect(evento.datos).toEqual({});
  });

  it("admite actor nulo (acción de sistema)", () => {
    const evento = mapEventoAuditoriaRow(row({ actor_usuario_id: null }), null);
    expect(evento.actorUsuarioId).toBeNull();
    expect(evento.actorNombre).toBeNull();
  });
});

describe("isAccionAuditoria", () => {
  it("reconoce acciones válidas", () => {
    expect(isAccionAuditoria("conflicto_resuelto")).toBe(true);
    expect(isAccionAuditoria("rol_cambiado")).toBe(true);
  });

  it("rechaza valores desconocidos", () => {
    expect(isAccionAuditoria("borrar_todo")).toBe(false);
    expect(isAccionAuditoria("")).toBe(false);
  });
});

describe("isEntidadAuditoria", () => {
  it("reconoce entidades válidas", () => {
    expect(isEntidadAuditoria("recaudacion")).toBe(true);
    expect(isEntidadAuditoria("empresa_usuario")).toBe(true);
  });

  it("rechaza entidades desconocidas", () => {
    expect(isEntidadAuditoria("usuario")).toBe(false);
  });
});
