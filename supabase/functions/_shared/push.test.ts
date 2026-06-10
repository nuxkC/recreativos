/**
 * Tests de la lógica pura de construcción del payload FCM (T-101).
 *
 * Ejecutar con:
 *   deno test supabase/functions/_shared/push.test.ts
 *
 * No requieren red ni credenciales: `construirMensajeFcm` es pura.
 */

import { assert, assertEquals } from "@std/assert";

import { ANDROID_CHANNEL_CONFLICTOS, construirMensajeFcm } from "./push.ts";

Deno.test("construye el payload mínimo con notificación y canal por defecto", () => {
  const body = construirMensajeFcm({
    token: "tok-123",
    title: "Conflicto resuelto",
    body: "El administrador resolvió tu recaudación",
  });

  assertEquals(body.message.token, "tok-123");
  assertEquals(body.message.notification.title, "Conflicto resuelto");
  assertEquals(
    body.message.notification.body,
    "El administrador resolvió tu recaudación",
  );
  assertEquals(body.message.android.priority, "high");
  assertEquals(
    body.message.android.notification.channel_id,
    ANDROID_CHANNEL_CONFLICTOS,
  );
});

Deno.test("omite `data` cuando no se aportan claves", () => {
  const sinData = construirMensajeFcm({
    token: "t",
    title: "x",
    body: "y",
  });
  assertEquals(sinData.message.data, undefined);

  const conObjetoVacio = construirMensajeFcm({
    token: "t",
    title: "x",
    body: "y",
    data: {},
  });
  assertEquals(conObjetoVacio.message.data, undefined);
});

Deno.test("incluye `data` para deep-link cuando hay claves", () => {
  const body = construirMensajeFcm({
    token: "t",
    title: "Conflicto resuelto",
    body: "Toca para ver el detalle",
    data: {
      tipo: "recaudacion_conflicto",
      recaudacion_id: "rec-789",
    },
  });

  assert(body.message.data !== undefined);
  assertEquals(body.message.data?.tipo, "recaudacion_conflicto");
  assertEquals(body.message.data?.recaudacion_id, "rec-789");
});

Deno.test("permite sobreescribir el canal para futuros eventos", () => {
  const body = construirMensajeFcm(
    { token: "t", title: "x", body: "y" },
    "avisos_generales",
  );
  assertEquals(body.message.android.notification.channel_id, "avisos_generales");
});

Deno.test("el payload es serializable a JSON sin pérdidas", () => {
  const body = construirMensajeFcm({
    token: "t",
    title: "Título",
    body: "Cuerpo con acentos áéí",
    data: { recaudacion_id: "rec-1" },
  });
  const round = JSON.parse(JSON.stringify(body));
  assertEquals(round, body);
});
