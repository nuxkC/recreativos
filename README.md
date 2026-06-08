# Recre

Plataforma multi-tenant para empresas de explotación de máquinas recreativas. Cubre web (back-office), app Android (técnicos en ruta) y backend en Supabase.

## Estructura del monorepo

```
recre/
├── web/          # Next.js 14 + TypeScript + Tailwind + shadcn/ui (back-office)
├── android/      # Kotlin + Jetpack Compose (app técnicos)
├── supabase/     # Migraciones SQL + Edge Functions (Deno) + Storage
└── .kiro/        # Spec y steering files
```

| Carpeta | Stack | Estado |
|---|---|---|
| `web/` | Next.js 14 + TS + Tailwind + shadcn/ui | Pendiente init (T-04) |
| `android/` | Kotlin + Jetpack Compose + Hilt | Pendiente init (T-05) |
| `supabase/` | Postgres + Edge Functions (Deno) | Pendiente init (T-02, T-03) |

## Documentación

- [Spec](./.kiro/specs/recre/) — requisitos, diseño técnico y plan de tareas
- [Steering](./.kiro/steering/) — arquitectura y convenciones del proyecto
- [Guía de despliegue](./docs/despliegue.md) — Supabase, web (Vercel) y build del APK Android

## Antes de tocar código

Lee primero los steering files. Son la guía de cómo trabajamos en este repo:

- [`.kiro/steering/architecture.md`](./.kiro/steering/architecture.md) — principios, capas, SSOT, escalabilidad
- [`.kiro/steering/conventions.md`](./.kiro/steering/conventions.md) — git, commits, naming, librerías, antipatrones

## Estado del proyecto

En desarrollo activo. Plan de tareas en [`.kiro/specs/recre/tasks.md`](./.kiro/specs/recre/tasks.md).
