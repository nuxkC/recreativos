/**
 * Helper de envío de email vía Resend para Edge Functions.
 *
 * Centraliza el patrón ya usado por `enviar-email-tecnico` (T-71):
 *   - Lee `RESEND_API_KEY` y `RESEND_FROM` del entorno.
 *   - Si no hay `RESEND_API_KEY`, NO envía y devuelve `skipped` (modo dev /
 *     entornos sin proveedor configurado), igual que el resto del sistema.
 *   - Devuelve un resultado discriminado para que el caller decida cómo
 *     loggear (sin PII) y cómo agregar el resultado.
 *
 * NOTA DRY: a partir de este segundo uso (T-71 + T-102) el patrón Resend se
 * extrae aquí. `enviar-email-tecnico` se mantiene intacto por ahora; una
 * futura tarea puede migrarlo a este helper para eliminar la duplicación.
 */

const RESEND_ENDPOINT = "https://api.resend.com/emails";
const DEFAULT_FROM = "Recre <noreply@recre.app>";

export interface EmailMessage {
  to: string;
  subject: string;
  html: string;
  text: string;
}

export type EmailResult =
  | { status: "sent"; providerId?: string }
  | { status: "skipped"; code: "email_skipped" }
  | { status: "failed"; code: "email_provider_failed"; httpStatus: number; detail: string };

/** `true` si hay proveedor de email configurado en el entorno. */
export function emailConfigurado(): boolean {
  return Boolean(Deno.env.get("RESEND_API_KEY"));
}

/**
 * Envía un email a través de Resend. No loggea: el caller decide qué
 * registrar para evitar filtrar PII (direcciones, nombres del titular).
 */
export async function enviarEmail(msg: EmailMessage): Promise<EmailResult> {
  const apiKey = Deno.env.get("RESEND_API_KEY");
  if (!apiKey) {
    return { status: "skipped", code: "email_skipped" };
  }

  const from = Deno.env.get("RESEND_FROM") ?? DEFAULT_FROM;

  const resp = await fetch(RESEND_ENDPOINT, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [msg.to],
      subject: msg.subject,
      html: msg.html,
      text: msg.text,
    }),
  });

  if (!resp.ok) {
    const detail = await resp.text();
    return {
      status: "failed",
      code: "email_provider_failed",
      httpStatus: resp.status,
      detail,
    };
  }

  const result = (await resp.json().catch(() => ({}))) as { id?: string };
  return { status: "sent", providerId: result.id };
}
