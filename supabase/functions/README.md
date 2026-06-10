# Edge Functions

Funciones serverless de Supabase escritas en TypeScript sobre Deno.

## Estructura

```
functions/
  _shared/                # código compartido entre funciones (SSOT)
    cors.ts               # cabeceras CORS y helper de preflight
    constants.ts          # denominaciones, roles, zona horaria default
    errors.ts             # tipos de error y helpers HTTP
  <kebab-case>/           # 1 carpeta por función
    index.ts              # entrada con Deno.serve(...)
  deno.json               # imports compartidos, fmt y lint
```

## Convenciones

- Una función = un endpoint pequeño y enfocado. Lógica reusable vive en `_shared/`.
- Validar entrada con Zod en cada función (frontera).
- Respuesta de éxito: `{ data: ... }`. Respuesta de error: `{ error: { code, message, details? } }`
  con HTTP status apropiado.
- No loggear PII (firmas, contadores del titular, etc.).
- Reutilizar `handleCorsPreflight` y `CORS_HEADERS` de `_shared/cors.ts`.

Ver `.kiro/steering/architecture.md` y `.kiro/steering/conventions.md` para el detalle completo.

## Ejecutar localmente

```bash
supabase functions serve
```

## Despliegue

```bash
supabase functions deploy <nombre>
```
