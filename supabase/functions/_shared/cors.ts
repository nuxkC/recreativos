/**
 * Cabeceras CORS para Edge Functions invocadas desde el navegador o la app.
 * En producción conviene restringir el origen al dominio real de la web.
 */

export const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-idempotency-key",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

/**
 * Si la request es un preflight CORS (OPTIONS), devuelve la respuesta adecuada.
 * En caso contrario, devuelve null y el caller continúa el flujo normal.
 */
export function handleCorsPreflight(req: Request): Response | null {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }
  return null;
}
