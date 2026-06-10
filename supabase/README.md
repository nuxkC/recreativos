# Recre — Supabase

Backend del proyecto: Postgres con Row Level Security, Edge Functions en Deno y Storage privado.

## Estructura

```
supabase/
  config.toml             # configuración local de Supabase CLI
  seed.sql                # datos semilla para desarrollo
  migrations/             # migraciones SQL ordenadas por timestamp
  functions/
    deno.json             # imports compartidos, fmt y lint
    _shared/              # código común a todas las funciones (SSOT)
    <kebab-case>/         # 1 carpeta por función
```

## Requisitos

- [Supabase CLI](https://supabase.com/docs/guides/cli) `>= 2.x`
- Docker (para `supabase start`)
- Deno (incluido por la CLI al servir funciones)

## Comandos habituales

```bash
# levantar la stack local (db, auth, studio, edge runtime…)
supabase start

# aplicar migraciones + seed
supabase db reset

# crear una nueva migración
supabase migration new <descripcion-corta>

# servir las edge functions en local con hot reload
supabase functions serve

# desplegar una edge function
supabase functions deploy <nombre>
```

## Convenciones

- Migraciones **aditivas**: una vez aplicadas no se editan; se crea otra que rectifique.
- RLS en TODAS las tablas con `empresa_id`.
- Lógica de cálculo (recaudación, baseline, semanas) **solo** en `functions/_shared/` o funciones SQL. Web y Android consumen el resultado, nunca lo recalculan.

Ver `.kiro/specs/recre/design.md`, `.kiro/steering/architecture.md` y `.kiro/steering/conventions.md`.
