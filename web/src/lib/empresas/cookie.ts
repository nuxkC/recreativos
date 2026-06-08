/**
 * Configuración de la cookie que persiste la empresa activa entre requests.
 *
 * Usamos cookie httpOnly: la lee el servidor (RSC + Server Actions) y nunca
 * el JavaScript del cliente. La empresa activa NO es un secreto de seguridad
 * (la autorización real la hace RLS), pero seguir el patrón evita exposición
 * accidental por XSS y deja la decisión en el servidor.
 */

export const EMPRESA_COOKIE_NAME = "recre_empresa_id";

const ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;

export const empresaCookieOptions = {
  httpOnly: true,
  sameSite: "lax",
  secure: process.env.NODE_ENV === "production",
  path: "/",
  maxAge: ONE_YEAR_SECONDS,
} as const;
