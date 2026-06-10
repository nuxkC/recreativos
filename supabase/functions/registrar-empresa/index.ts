/**
 * T-200 — Edge Function `registrar-empresa`.
 *
 * Onboarding self-service ABIERTO: crea (o asocia) un usuario de Auth y le da
 * de alta una empresa nueva en periodo de prueba (trial de 14 días), dejándolo
 * como `owner`.
 *
 * Flujo:
 *   1. Validamos el input con Zod.
 *   2. Resolvemos el usuario:
 *        · Si la petición trae un JWT de usuario válido → asociamos ESE usuario
 *          (ignoramos email/password). No se crea nada en Auth.
 *        · Si no hay sesión → creamos el usuario de Auth con email/password.
 *          Si el email ya existe → 409 (sin filtrar si la cuenta existe más
 *          allá de lo imprescindible para el alta).
 *   3. Llamamos a la función SQL transaccional `registrar_empresa_con_owner`
 *      (service_role) que crea empresa + perfil + membresía owner de forma
 *      ATÓMICA. Si algo falla, NADA se persiste (sin empresas huérfanas).
 *   4. Rollback del paso de Auth: si (3) falla y habíamos creado un usuario de
 *      Auth NUEVO en (2), lo eliminamos (best-effort) para no dejar cuentas
 *      huérfanas.
 *
 * Seguridad (endpoint público — ver README de la función):
 *   * Validación server-side de TODO el input.
 *   * La service_role key NUNCA viaja al cliente.
 *   * Logging estructurado SIN PII (sin email).
 *   * Follow-ups documentados: rate limiting / captcha anti-abuso y
 *     verificación de email antes de operar.
 */

import { ZodError } from "zod";

import { getServiceClient, getUserClient } from "../_shared/db.ts";
import { TRIAL_DIAS } from "../_shared/constants.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { RegistrarEmpresaInputSchema } from "../_shared/schemas.ts";
import { esEmailDuplicado, requiereCredenciales } from "./registro.ts";

interface RegistroRpcRow {
  empresa_id: string;
  estado_suscripcion: string;
  trial_inicio: string;
  trial_fin: string;
}

Deno.serve(withHandler(async (req: Request) => {
  if (req.method !== "POST") {
    throw makeError("validation_error", "Solo se admite POST");
  }

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw makeError("validation_error", "Body no es JSON válido");
  }

  let input;
  try {
    input = RegistrarEmpresaInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const service = getServiceClient();

  // -- Paso 2: resolver el usuario (sesión previa vs alta nueva). -------------
  const sesion = await usuarioDeLaSesion(req);

  let usuarioId: string;
  let usuarioCreado = false;

  if (sesion) {
    usuarioId = sesion;
  } else {
    const { email, password } = input;
    if (requiereCredenciales(false, email, password) || !email || !password) {
      throw makeError(
        "validation_error",
        "Sin sesión previa se requieren email y contraseña",
      );
    }
    const { data: created, error: createErr } = await service.auth.admin.createUser({
      email,
      password,
      // enable_confirmations está desactivado en local; en producción conviene
      // exigir verificación de email (ver follow-ups en el README).
      email_confirm: true,
    });

    if (createErr || !created?.user) {
      if (esEmailDuplicado(createErr?.message)) {
        throw makeError("conflict", "Ya existe una cuenta con ese correo");
      }
      throw makeError("internal_error", "No se pudo crear la cuenta", createErr?.message);
    }
    usuarioId = created.user.id;
    usuarioCreado = true;
  }

  // -- Paso 3: alta atómica de empresa + perfil + membresía owner. ------------
  const { data: rpcRaw, error: rpcError } = await service
    .rpc("registrar_empresa_con_owner", {
      p_usuario_id: usuarioId,
      p_nombre_empresa: input.nombre_empresa,
      p_nombre_completo: input.nombre_completo,
      p_trial_dias: TRIAL_DIAS,
    });

  const rpcData = rpcRaw as unknown as RegistroRpcRow[] | null;

  if (rpcError || !rpcData || rpcData.length === 0) {
    // -- Paso 4: rollback del usuario de Auth si lo habíamos creado aquí. ----
    if (usuarioCreado) {
      const { error: delErr } = await service.auth.admin.deleteUser(usuarioId);
      if (delErr) {
        console.error(JSON.stringify({
          level: "error",
          msg: "registrar_empresa_rollback_user_failed",
          error: delErr.message,
        }));
      }
    }
    // El usuario ya es owner de una empresa: alta self-service duplicada.
    if (rpcError?.message?.includes("usuario_ya_es_owner")) {
      throw makeError("conflict", "Este usuario ya es propietario de una empresa");
    }
    throw makeError("internal_error", "No se pudo registrar la empresa", rpcError?.message);
  }

  const row = rpcData[0]!;

  console.log(JSON.stringify({
    level: "info",
    msg: "empresa_registrada",
    empresa_id: row.empresa_id,
    usuario_creado: usuarioCreado,
  }));

  return jsonResponse({
    empresa_id: row.empresa_id,
    estado_suscripcion: row.estado_suscripcion,
    trial_inicio: row.trial_inicio,
    trial_fin: row.trial_fin,
    usuario_creado: usuarioCreado,
  }, 201);
}));

/**
 * Devuelve el `usuario_id` si la petición trae un JWT de usuario válido, o
 * `null` si es una petición anónima (solo apikey/anon). No lanza: la ausencia
 * de sesión es un caso esperado en el registro abierto.
 */
async function usuarioDeLaSesion(req: Request): Promise<string | null> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return null;
  try {
    const supabase = getUserClient(req);
    const { data, error } = await supabase.auth.getUser();
    if (error || !data.user) return null;
    return data.user.id;
  } catch {
    return null;
  }
}
