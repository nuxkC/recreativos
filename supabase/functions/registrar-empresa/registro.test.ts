/**
 * Tests de la lógica pura del registro self-service (T-200) y del schema Zod.
 *
 * Ejecutar con:
 *   deno test supabase/functions/registrar-empresa/registro.test.ts
 */

import { assertEquals } from "@std/assert";

import { RegistrarEmpresaInputSchema } from "../_shared/schemas.ts";
import { esEmailDuplicado, requiereCredenciales } from "./registro.ts";

Deno.test("esEmailDuplicado detecta los mensajes habituales de Auth", () => {
  assertEquals(esEmailDuplicado("User already registered"), true);
  assertEquals(esEmailDuplicado("Email address already exists"), true);
  assertEquals(esEmailDuplicado("duplicate key value"), true);
  assertEquals(esEmailDuplicado("A user with this email has been registered"), true);
});

Deno.test("esEmailDuplicado es falso para otros errores o vacío", () => {
  assertEquals(esEmailDuplicado("Password is too weak"), false);
  assertEquals(esEmailDuplicado(""), false);
  assertEquals(esEmailDuplicado(undefined), false);
  assertEquals(esEmailDuplicado(null), false);
});

Deno.test("requiereCredenciales: con sesión nunca hacen falta", () => {
  assertEquals(requiereCredenciales(true, undefined, undefined), false);
  assertEquals(requiereCredenciales(true, "a@b.com", "secreto"), false);
});

Deno.test("requiereCredenciales: sin sesión exige email y password", () => {
  assertEquals(requiereCredenciales(false, undefined, undefined), true);
  assertEquals(requiereCredenciales(false, "a@b.com", undefined), true);
  assertEquals(requiereCredenciales(false, undefined, "secreto"), true);
  assertEquals(requiereCredenciales(false, "a@b.com", "secreto"), false);
});

Deno.test("schema acepta un alta válida y normaliza espacios", () => {
  const parsed = RegistrarEmpresaInputSchema.parse({
    nombre_empresa: "  Recreativos Pepe  ",
    nombre_completo: "  Pepe López  ",
    email: "pepe@empresa.com",
    password: "secreto123",
  });
  assertEquals(parsed.nombre_empresa, "Recreativos Pepe");
  assertEquals(parsed.nombre_completo, "Pepe López");
});

Deno.test("schema rechaza nombre de empresa vacío", () => {
  const res = RegistrarEmpresaInputSchema.safeParse({
    nombre_empresa: "   ",
    nombre_completo: "Pepe",
    email: "pepe@empresa.com",
    password: "secreto123",
  });
  assertEquals(res.success, false);
});

Deno.test("schema rechaza password corta", () => {
  const res = RegistrarEmpresaInputSchema.safeParse({
    nombre_empresa: "Recre",
    nombre_completo: "Pepe",
    email: "pepe@empresa.com",
    password: "123",
  });
  assertEquals(res.success, false);
});

Deno.test("schema permite omitir email/password (alta con sesión previa)", () => {
  const res = RegistrarEmpresaInputSchema.safeParse({
    nombre_empresa: "Recre",
    nombre_completo: "Pepe",
  });
  assertEquals(res.success, true);
});
