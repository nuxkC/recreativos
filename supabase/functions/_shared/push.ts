/**
 * Helper de envío de notificaciones push vía FCM HTTP v1 para Edge Functions.
 *
 * Sigue el mismo patrón que `_shared/email.ts` (T-71/T-102):
 *   - Lee las credenciales de un service account de Firebase desde el
 *     entorno (`FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY`),
 *     configuradas con `supabase secrets set`. NUNCA hardcodeadas.
 *   - Si faltan credenciales, NO envía y devuelve `skipped` (modo dev /
 *     entornos sin Firebase configurado), igual que el email.
 *   - Devuelve un resultado discriminado (`sent | skipped | failed`) para
 *     que el caller decida cómo loggear (sin PII) y cómo agregar.
 *
 * Separación de responsabilidades para testabilidad:
 *   - `construirMensajeFcm()` es PURA (sin red, sin entorno): construye el
 *     cuerpo `message` del payload HTTP v1. Se testea en `push.test.ts`.
 *   - `enviarPush()` orquesta credenciales + OAuth + POST. No es testable
 *     sin un proyecto Firebase real (documentado en el README de _shared).
 */

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

/** Datos mínimos para construir y enviar una notificación push. */
export interface PushMessage {
  /** Token de registro FCM del dispositivo destino. */
  token: string;
  /** Título visible de la notificación. */
  title: string;
  /** Cuerpo visible de la notificación. */
  body: string;
  /**
   * Datos de negocio para el deep-link en la app (todos string: FCM exige
   * que `data` sea un mapa string→string). P. ej. `{ tipo, recaudacion_id }`.
   */
  data?: Record<string, string>;
}

export type PushResult =
  | { status: "sent"; name?: string }
  | { status: "skipped"; code: "push_skipped" }
  | { status: "failed"; code: "push_provider_failed"; httpStatus: number; detail: string };

interface ServiceAccountEnv {
  projectId: string;
  clientEmail: string;
  privateKey: string;
}

/** Cuerpo `message` del payload FCM HTTP v1. Forma pura y serializable. */
export interface FcmMessageBody {
  message: {
    token: string;
    notification: { title: string; body: string };
    data?: Record<string, string>;
    android: {
      priority: "high" | "normal";
      notification: { channel_id: string };
    };
  };
}

/** Canal de notificaciones que la app crea en Android (debe coincidir). */
export const ANDROID_CHANNEL_CONFLICTOS = "conflictos";

/** `true` si hay credenciales de FCM configuradas en el entorno. */
export function pushConfigurado(): boolean {
  return leerServiceAccount() !== null;
}

/**
 * Construye el cuerpo `message` del payload FCM HTTP v1 para un token.
 *
 * PURA: no toca red ni entorno. El `channel_id` por defecto apunta al canal
 * de conflictos que crea la app Android; puede sobreescribirse para futuros
 * tipos de evento.
 */
export function construirMensajeFcm(
  msg: PushMessage,
  channelId: string = ANDROID_CHANNEL_CONFLICTOS,
): FcmMessageBody {
  const body: FcmMessageBody = {
    message: {
      token: msg.token,
      notification: { title: msg.title, body: msg.body },
      android: {
        priority: "high",
        notification: { channel_id: channelId },
      },
    },
  };
  // Solo adjuntamos `data` si trae claves: FCM rechaza un objeto vacío en
  // algunas versiones y, además, mantiene el payload mínimo.
  if (msg.data && Object.keys(msg.data).length > 0) {
    body.message.data = msg.data;
  }
  return body;
}

/**
 * Envía una notificación push a un token vía FCM HTTP v1.
 *
 * No loggea contenido: el caller decide qué registrar para no filtrar PII.
 * Skip seguro si faltan credenciales (`push_skipped`), igual que el email.
 */
export async function enviarPush(
  msg: PushMessage,
  channelId: string = ANDROID_CHANNEL_CONFLICTOS,
): Promise<PushResult> {
  const sa = leerServiceAccount();
  if (!sa) {
    return { status: "skipped", code: "push_skipped" };
  }

  const accessToken = await obtenerAccessToken(sa);
  const endpoint = `https://fcm.googleapis.com/v1/projects/${sa.projectId}/messages:send`;

  const resp = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(construirMensajeFcm(msg, channelId)),
  });

  if (!resp.ok) {
    const detail = await resp.text();
    return {
      status: "failed",
      code: "push_provider_failed",
      httpStatus: resp.status,
      detail,
    };
  }

  const result = (await resp.json().catch(() => ({}))) as { name?: string };
  return { status: "sent", name: result.name };
}

/* ------------------------------------------------------------------ */
/* OAuth de service account (no testeable sin credenciales reales).   */
/* ------------------------------------------------------------------ */

function leerServiceAccount(): ServiceAccountEnv | null {
  const projectId = Deno.env.get("FCM_PROJECT_ID");
  const clientEmail = Deno.env.get("FCM_CLIENT_EMAIL");
  const privateKey = Deno.env.get("FCM_PRIVATE_KEY");
  if (!projectId || !clientEmail || !privateKey) {
    return null;
  }
  return { projectId, clientEmail, privateKey };
}

/**
 * Obtiene un access token OAuth2 a partir del service account firmando un
 * JWT RS256 e intercambiándolo en el endpoint de Google. Token de corta
 * duración (1 h); aquí no se cachea porque cada invocación de la Edge
 * Function es efímera.
 */
async function obtenerAccessToken(sa: ServiceAccountEnv): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.clientEmail,
    scope: FCM_SCOPE,
    aud: GOOGLE_TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  };

  const encoder = new TextEncoder();
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claim))}`;

  const key = await importarPrivateKey(sa.privateKey);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(unsigned),
  );
  const jwt = `${unsigned}.${base64urlBytes(new Uint8Array(signature))}`;

  const resp = await fetch(GOOGLE_TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!resp.ok) {
    throw new Error(`OAuth token exchange falló: ${resp.status}`);
  }
  const json = (await resp.json()) as { access_token?: string };
  if (!json.access_token) {
    throw new Error("OAuth token exchange no devolvió access_token");
  }
  return json.access_token;
}

async function importarPrivateKey(pem: string): Promise<CryptoKey> {
  // Las claves en env suelen venir con `\n` escapados; los normalizamos.
  const normalized = pem.replace(/\\n/g, "\n");
  const body = normalized
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64url(input: string): string {
  return base64urlBytes(new TextEncoder().encode(input));
}

function base64urlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
