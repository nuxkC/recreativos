/**
 * Helpers de Storage: subida de evidencias y signed URLs.
 *
 * Convención de paths: `<bucket>/<empresa_id>/<resto>`. La policy RLS
 * comprueba que `<empresa_id>` coincide con una empresa del caller.
 */

import type { SupabaseClient } from "@supabase/supabase-js";

import { makeError } from "./errors.ts";

const SIGNED_URL_TTL_SECONDS = 600; // 10 min, ver design.md §7

export type Bucket =
  | "firmas"
  | "fotos-contadores"
  | "tickets"
  | "logos"
  | "cambios-placa"
  | "boletines";

/** Decodifica una imagen base64 (con o sin prefijo data:) a Uint8Array + mime. */
export function decodeBase64Image(input: string): { bytes: Uint8Array; mime: string } {
  const match = input.match(/^data:(image\/\w+);base64,(.*)$/);
  let mime = "image/png";
  let payload = input;
  if (match && match[1] && match[2]) {
    mime = match[1];
    payload = match[2];
  }
  // atob a Uint8Array
  const binary = atob(payload);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return { bytes, mime };
}

/** Sube un blob a un bucket y devuelve la ruta completa (sin signed URL). */
export async function uploadToBucket(
  supabase: SupabaseClient,
  bucket: Bucket,
  path: string,
  body: Uint8Array | Blob,
  contentType: string,
): Promise<string> {
  const { error } = await supabase.storage.from(bucket).upload(path, body, {
    contentType,
    upsert: true,
  });
  if (error) {
    throw makeError("internal_error", `No se pudo subir a ${bucket}`, error.message);
  }
  return path;
}

/** Genera una signed URL de corta duración para un objeto. */
export async function createSignedUrl(
  supabase: SupabaseClient,
  bucket: Bucket,
  path: string,
  expiresIn = SIGNED_URL_TTL_SECONDS,
): Promise<string> {
  const { data, error } = await supabase.storage.from(bucket).createSignedUrl(path, expiresIn);
  if (error || !data) {
    throw makeError("internal_error", `No se pudo firmar URL de ${path}`, error?.message);
  }
  return data.signedUrl;
}
