/**
 * Wrapper común para handlers de Edge Functions:
 *   * gestiona CORS preflight
 *   * captura DomainError y los convierte en respuesta JSON
 *   * captura cualquier error inesperado y devuelve 500
 *   * añade cabeceras CORS a todas las respuestas
 */

import { CORS_HEADERS, handleCorsPreflight } from "./cors.ts";
import { type DomainError, errorResponse, makeError } from "./errors.ts";

type Handler = (req: Request) => Promise<Response>;

function isDomainError(value: unknown): value is DomainError {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.message === "string";
}

/**
 * Envuelve un handler con manejo estándar de errores y CORS.
 */
export function withHandler(handler: Handler) {
  return async (req: Request): Promise<Response> => {
    const preflight = handleCorsPreflight(req);
    if (preflight) return preflight;

    try {
      const response = await handler(req);
      // Adjuntamos CORS a la respuesta de éxito.
      const merged = new Headers(response.headers);
      for (const [k, v] of Object.entries(CORS_HEADERS)) {
        merged.set(k, v);
      }
      return new Response(response.body, {
        status: response.status,
        headers: merged,
      });
    } catch (err) {
      if (isDomainError(err)) {
        return errorResponse(err, undefined, CORS_HEADERS);
      }
      console.error(JSON.stringify({
        level: "error",
        msg: "unhandled_edge_error",
        error: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : undefined,
      }));
      return errorResponse(
        makeError("internal_error", "Error interno del servidor"),
        500,
        CORS_HEADERS,
      );
    }
  };
}
