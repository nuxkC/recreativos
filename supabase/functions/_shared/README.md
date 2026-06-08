# `_shared/`

Código reutilizado por todas las Edge Functions. **Single Source of Truth**.

## Reglas

- La lógica de cálculo, validación y schemas vive aquí. **No se duplica en cliente.**
- Solo funciones puras y constantes. Sin estado global.
- Si un archivo supera ~300 líneas, divide por dominio.
- Los archivos del dominio que aún no existen (calculo, schemas, validators, auth, storage, pdf,
  types) se irán creando en sus tareas correspondientes (T-13, T-20, T-21, T-25...).
