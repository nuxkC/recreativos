# `registrar-empresa` (T-200)

Endpoint **público** de onboarding self-service. Crea (o asocia) un usuario de Auth y le da de alta
una empresa nueva en **periodo de prueba (trial) de 14 días**, dejándolo como `owner`.

## Contrato

`POST /functions/v1/registrar-empresa`

```jsonc
{
  "nombre_empresa": "Recreativos Pepe",
  "nombre_completo": "Pepe López",
  "email": "pepe@empresa.com", // requerido si NO hay sesión previa
  "password": "secreto123" // requerido si NO hay sesión previa
}
```

Respuesta `201`:

```jsonc
{
  "data": {
    "empresa_id": "uuid",
    "estado_suscripcion": "trial",
    "trial_inicio": "2026-05-23T10:00:00Z",
    "trial_fin": "2026-06-06T10:00:00Z",
    "usuario_creado": true
  }
}
```

Errores (`{ error: { code, message } }`):

- `validation_error` (400): input inválido o faltan email/password sin sesión.
- `conflict` (409): ya existe una cuenta con ese correo.
- `internal_error` (500): fallo creando la cuenta o el alta transaccional.

## Atomicidad / rollback

- El alta de **empresa + perfil + membresía owner** se hace en la función SQL
  `registrar_empresa_con_owner` (SECURITY DEFINER, restringida a service_role), que corre en una
  sola transacción: si algo falla, **nada** se persiste (no quedan empresas huérfanas).
- La creación del usuario de Auth ocurre fuera de esa transacción. Si el alta transaccional falla
  tras crear un usuario de Auth **nuevo**, la función elimina ese usuario (best-effort) para no
  dejar cuentas huérfanas.

## Seguridad — implicaciones de un registro abierto

Este endpoint permite que **cualquiera** cree cuentas y empresas. Mitigaciones ya aplicadas:

- Validación server-side de todo el input (Zod) y en la función SQL.
- `service_role` nunca llega al cliente; la función SQL está restringida a `service_role`.
- Las columnas de suscripción (`estado_suscripcion`, `trial_*`) están protegidas por trigger frente
  a modificaciones del cliente.
- Logging estructurado **sin PII** (no se registra el email).

### Follow-ups recomendados (fuera del alcance de T-200)

- **Rate limiting / anti-abuso**: límite por IP y captcha (hCaptcha/Turnstile, configurable en
  `supabase/config.toml` → `[auth.captcha]`). Hoy se confía en los límites por defecto de Supabase
  Auth (`[auth.rate_limit]`).
- **Verificación de email**: en local `enable_confirmations = false` y la función crea el usuario
  con `email_confirm: true` para permitir el acceso inmediato. En producción conviene exigir
  confirmación de email antes de operar (o limitar funcionalidad hasta verificar).
- **Bloqueo por expiración del trial**: T-201 (facturación/planes). T-200 solo expone el estado del
  trial de forma informativa.
