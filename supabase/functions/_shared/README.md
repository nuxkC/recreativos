# `_shared/`

Código reutilizado por todas las Edge Functions. **Single Source of Truth**.

## Reglas

- La lógica de cálculo, validación y schemas vive aquí. **No se duplica en cliente.**
- Solo funciones puras y constantes. Sin estado global.
- Si un archivo supera ~300 líneas, divide por dominio.
- Los archivos del dominio que aún no existen (calculo, schemas, validators, auth, storage, pdf,
  types) se irán creando en sus tareas correspondientes (T-13, T-20, T-21, T-25...).

## Notificaciones

- `email.ts` — envío vía Resend. Skip seguro sin `RESEND_API_KEY` (`email_skipped`).
- `push.ts` — envío push vía **FCM HTTP v1** (T-101). Mismo patrón que el email: resultado
  discriminado `sent | skipped | failed`.
  - `construirMensajeFcm()` es **pura** (sin red ni entorno) y está cubierta por `push.test.ts`.
  - `enviarPush()` firma un JWT RS256 con el service account de Firebase, lo intercambia por un
    access token OAuth2 y publica en FCM. **No es testeable sin credenciales reales**; se valida
    manualmente con un proyecto Firebase.
  - Credenciales (secrets del proyecto Supabase, nunca hardcodeadas):

    ```bash
    supabase secrets set FCM_PROJECT_ID="tu-proyecto"
    supabase secrets set FCM_CLIENT_EMAIL="...@tu-proyecto.iam.gserviceaccount.com"
    supabase secrets set FCM_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
    ```

  - Si falta cualquiera de las tres, `pushConfigurado()` es `false` y `enviarPush` devuelve
    `push_skipped` sin tocar la red.

## Auditoría (T-202)

- `audit.ts` — helper de la bitácora `audit_log` para los eventos que SOLO conoce la Edge Function
  (`usuario_invitado`, `rol_cambiado`); lo usa `invitar-usuario`. El resto de eventos de dominio
  (recaudación, cambio de placa, instalación) se auditan con **triggers SQL** en
  `migrations/20260522000000_create_audit_log.sql`, que cubren tanto las Edge Functions como los
  CRUD directos (web/android).
  - `construirRegistroAuditoria()` / `sanearDatos()` son **puras** y eliminan claves con PII (email,
    teléfono, firma, observaciones, titular…). Cubiertas por `audit.test.ts`.
  - `registrarAuditoria()` inserta con `service_role` (la RLS de `audit_log` bloquea el INSERT a
    clientes con JWT de usuario) y es **best-effort**: nunca lanza para no afectar a la operación.
  - Verificación de los triggers: `supabase test db` corre `tests/sql/04_audit_log.sql`, que
    inserta/actualiza filas en `recaudacion`, `instalacion` y `cambio_placa` y comprueba que el
    evento correspondiente aparece en `audit_log`.
