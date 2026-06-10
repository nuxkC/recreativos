/**
 * Tipos de error de dominio y helpers HTTP para Edge Functions.
 *
 * Convención de respuesta:
 *   - éxito: { data: ... }
 *   - error: { error: { code, message, details? } }
 */

export const ERROR_CODES = [
  "validation_error",
  "auth_required",
  "forbidden",
  "not_found",
  "conflict",
  "lock_held",
  "stale_baseline",
  "insufficient_funds",
  "internal_error",
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export interface DomainError {
  code: ErrorCode;
  message: string;
  details?: unknown;
}

/** HTTP status sugerido para cada código de error. */
const STATUS_BY_CODE: Record<ErrorCode, number> = {
  validation_error: 400,
  auth_required: 401,
  forbidden: 403,
  not_found: 404,
  conflict: 409,
  lock_held: 409,
  stale_baseline: 409,
  insufficient_funds: 422,
  internal_error: 500,
};

/** Construye una respuesta JSON de éxito. */
export function jsonResponse<T>(data: T, status = 200, extraHeaders: HeadersInit = {}): Response {
  return new Response(JSON.stringify({ data }), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...extraHeaders,
    },
  });
}

/** Construye una respuesta JSON de error. */
export function errorResponse(
  error: DomainError,
  status?: number,
  extraHeaders: HeadersInit = {},
): Response {
  return new Response(JSON.stringify({ error }), {
    status: status ?? STATUS_BY_CODE[error.code],
    headers: {
      "Content-Type": "application/json",
      ...extraHeaders,
    },
  });
}

/** Helper rápido para crear DomainError. */
export function makeError(code: ErrorCode, message: string, details?: unknown): DomainError {
  return { code, message, details };
}
