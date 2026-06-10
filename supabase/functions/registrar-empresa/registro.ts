/**
 * T-200 — Lógica PURA del registro self-service (sin red ni entorno).
 *
 * Se extrae aquí lo testeable de `index.ts`: la clasificación de errores de
 * Auth y la decisión de si hacen falta credenciales. Así el handler queda fino
 * y estas reglas se cubren con `deno test`.
 */

/**
 * Heurística para distinguir el caso "email ya registrado" de otros errores de
 * Auth. Supabase no expone un código estable para esto, así que inspeccionamos
 * el mensaje (en minúsculas) buscando los términos habituales.
 */
export function esEmailDuplicado(message: string | undefined | null): boolean {
  if (!message) return false;
  const m = message.toLowerCase();
  return (
    m.includes("already") ||
    m.includes("registered") ||
    m.includes("exists") ||
    m.includes("duplicate")
  );
}

/**
 * Sin sesión previa, el registro abierto exige email y contraseña para crear
 * la cuenta de Auth. Con sesión, se asocia el usuario actual y no hacen falta.
 */
export function requiereCredenciales(
  haySesion: boolean,
  email: string | undefined,
  password: string | undefined,
): boolean {
  if (haySesion) return false;
  return !email || !password;
}
