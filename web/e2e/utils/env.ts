/**
 * Lectura tipada de las variables de entorno necesarias para las pruebas E2E.
 * Falla rápido y con un mensaje claro si falta alguna credencial requerida.
 */

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `Falta la variable de entorno "${name}". ` +
        "Configúrala (ver web/.env.example) antes de ejecutar las pruebas E2E.",
    );
  }
  return value;
}

export interface E2ECredentials {
  email: string;
  password: string;
}

export function getCredentials(): E2ECredentials {
  return {
    email: requireEnv("E2E_USER_EMAIL"),
    password: requireEnv("E2E_USER_PASSWORD"),
  };
}

export const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:3000";
