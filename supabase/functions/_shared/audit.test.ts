/**
 * T-202 — Tests del helper PURO de auditoría (`audit.ts`).
 *
 * Ejecutar con:
 *   deno test supabase/functions/_shared/audit.test.ts
 */

import { assertEquals } from "@std/assert";

import { CLAVES_PII_PROHIBIDAS, construirRegistroAuditoria, sanearDatos } from "./audit.ts";

Deno.test("construye la fila con campos snake_case y datos saneados", () => {
  const row = construirRegistroAuditoria({
    empresaId: "emp-1",
    actorUsuarioId: "usr-1",
    accion: "usuario_invitado",
    entidadTabla: "empresa_usuario",
    entidadId: "usr-2",
    datos: { rol: "gestor", invitado_nuevo: true },
  });
  assertEquals(row.empresa_id, "emp-1");
  assertEquals(row.actor_usuario_id, "usr-1");
  assertEquals(row.accion, "usuario_invitado");
  assertEquals(row.entidad_tabla, "empresa_usuario");
  assertEquals(row.entidad_id, "usr-2");
  assertEquals(row.datos, { rol: "gestor", invitado_nuevo: true });
});

Deno.test("actor_usuario_id puede ser null (acción de sistema)", () => {
  const row = construirRegistroAuditoria({
    empresaId: "emp-1",
    actorUsuarioId: null,
    accion: "rol_cambiado",
    entidadTabla: "empresa_usuario",
    entidadId: null,
    datos: { rol_nuevo: "admin" },
  });
  assertEquals(row.actor_usuario_id, null);
  assertEquals(row.entidad_id, null);
});

Deno.test("sanearDatos elimina claves con PII", () => {
  const limpio = sanearDatos({
    rol: "tecnico",
    email: "fulano@ejemplo.com",
    telefono: "600000000",
    titular_nombre: "Pepe",
    observaciones: "texto libre con datos del cliente",
  });
  assertEquals(limpio, { rol: "tecnico" });
});

Deno.test("sanearDatos filtra PII independientemente de mayúsculas", () => {
  const limpio = sanearDatos({
    Rol: "admin",
    EMAIL: "x@y.com",
    Telefono: "600",
  });
  assertEquals(limpio, { Rol: "admin" });
});

Deno.test("sanearDatos descarta valores undefined", () => {
  const limpio = sanearDatos({ rol: "gestor", rol_anterior: undefined });
  assertEquals(limpio, { rol: "gestor" });
});

Deno.test("sanearDatos con undefined devuelve objeto vacío", () => {
  assertEquals(sanearDatos(undefined), {});
});

Deno.test("construirRegistroAuditoria nunca propaga PII a datos", () => {
  const row = construirRegistroAuditoria({
    empresaId: "emp-1",
    actorUsuarioId: "usr-1",
    accion: "usuario_invitado",
    entidadTabla: "empresa_usuario",
    entidadId: "usr-2",
    datos: { rol: "contable", email: "secreto@ejemplo.com" },
  });
  for (const clave of CLAVES_PII_PROHIBIDAS) {
    assertEquals(clave in row.datos, false);
  }
  assertEquals(row.datos, { rol: "contable" });
});
