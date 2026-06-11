# Recre — Plan de tareas

Plan dividido en fases. Cada tarea está pensada para ser un PR pequeño y verificable. Los identificadores `T-XX` se referencian en commits y PRs.

## Fase 0 — Cimientos del proyecto

- [x] **T-01** Crear monorepo con tres directorios: `web/`, `android/`, `supabase/`. Añadir `.editorconfig`, `.gitignore`, `README.md` raíz. *(PR #3)*
- [x] **T-02** Inicializar proyecto Supabase local (`supabase init`) con migraciones bajo `supabase/migrations/`. *(PR #4)*
- [x] **T-03** Crear estructura de Edge Functions en `supabase/functions/` y plantilla compartida (`_shared/`). *(PR #4)*
- [x] **T-04** Inicializar Next.js 14 en `web/` con TypeScript, Tailwind, shadcn/ui, supabase-js. Login funcional. *(PR #5)*
- [x] **T-05** Inicializar proyecto Android en `android/` (Kotlin + Compose + Supabase SDK + Room + WorkManager). Pantalla login funcional. *(PR #5)*
- [x] **T-06** Definir variables de entorno y secretos (`.env.example` en cada subproyecto). *(PR #5: `web/.env.example` y `android/local.properties.example`)*

## Fase 1 — MVP funcional

### Modelo de datos y RLS

- [x] **T-10** Migración: tablas `empresa`, `usuario`, `empresa_usuario`. *(this PR)*
- [x] **T-11** Migración: tablas `licencia`, `maquina`, `local`, `instalacion` con índices únicos parciales. *(this PR)*
- [x] **T-12** Migración: tablas `recaudacion`, `cambio_placa`, `recaudacion_lock`, `lectura_no_recaudada`, `alerta`. *(this PR)*
- [x] **T-13** Funciones SQL: `obtener_baseline`, `semanas_iso_entre`. *(this PR)*
- [x] **T-14** Triggers `updated_at` y validaciones de denominaciones. *(this PR)*
- [x] **T-15** Políticas RLS por tabla y rol. *(this PR)*
- [x] **T-16** Vistas `v_instalacion_actual`, `v_recaudaciones_*`, `v_alertas_pendientes`. *(this PR)*
- [x] **T-17** Buckets de Storage con políticas RLS y helpers de signed URL. *(this PR)*
- [x] **T-18** Tests SQL básicos (pgTAP o scripts) para baseline y cálculo de semanas con casos verificables. *(this PR)*

### Edge Functions

- [x] **T-20** `calcular-recaudacion` (lectura, no persiste). *(PR #8)*
- [x] **T-21** `crear-recaudacion` (validación + recálculo + conflicto + PDF + Storage). *(PR #8)*
- [x] **T-22** `crear-cambio-placa`. *(PR #8)*
- [x] **T-23** `cerrar-instalacion`. *(this PR)*
- [x] **T-24** `adquirir-lock`, `liberar-lock` (con TTL). *(this PR)*
- [x] **T-25** Generación de PDF de ticket (server-side). *(PR #8 — `_shared/pdf.ts`)*
- [x] **T-26** `anular-recaudacion`, `resolver-conflicto`. *(this PR)*
- [x] **T-27** `invitar-usuario`, `reimprimir-ticket`. *(this PR)*

### Web

- [x] **T-30** Layout con sidebar, selector de empresa, sesión persistente. *(this PR)*
- [x] **T-31** CRUD de Licencias. *(this PR)*
- [x] **T-32** CRUD de Máquinas. *(this PR)*
- [x] **T-33** CRUD de Locales. *(this PR)*
- [x] **T-34** CRUD de Instalaciones (con cierre). *(this PR)*
- [x] **T-35** Pantalla de Recaudaciones (listado y detalle, descarga PDF, anular). *(this PR)*
- [x] **T-36** Pantalla de Cambios de placa (listado y detalle). *(this PR)*
- [x] **T-37** Pantalla de Conflictos con flujo de resolución. *(this PR)*
- [x] **T-38** Dashboard con tarjetas y alertas. *(this PR)*
- [x] **T-39** Pantalla de Equipo (invitar, rol, desactivar). *(this PR)*
- [x] **T-40** Pantalla de Ajustes de empresa. *(this PR)*

### App Android

- [x] **T-50** Login + selector de empresa + sesión persistente. *(PR #18)*
- [x] **T-51** Sync inicial a Room (locales, máquinas, instalaciones, baselines, parámetros de empresa). *(PR #19)*
- [x] **T-52** Lista de locales con buscador y pull-to-refresh. *(PR #20)*
- [x] **T-53** Detalle de local con lista de máquinas y estados. *(PR #20)*
- [x] **T-54** Pantalla de contadores con cálculo y manejo de bruto < tasa. *(this PR)*
- [x] **T-55** Pantalla de denominaciones (componente reusable, validación de suma exacta). *(this PR)*
- [x] **T-56** Pantalla de firma y confirmación. *(this PR)*
- [x] **T-57** Persistencia local (cola de recaudaciones offline) con WorkManager. *(this PR)*
- [x] **T-58** Lock optimista cuando hay red. *(this PR)*
- [x] **T-59** Bloqueo si > 48 h sin sync. *(this PR)*
- [x] **T-60** Flujo "Recaudar todas" en cadena con orden fijo y "Saltar a la siguiente" en bruto<tasa. *(this PR)*
- [x] **T-61** Cambio de placa. *(this PR)*
- [x] **T-62** Vinculación Bluetooth con AGPTEK PT210 + impresión ESC/POS. *(PR #24)*
- [x] **T-63** Mis recaudaciones (histórico personal y reimpresión). *(this PR)*
- [x] **T-64** Notificaciones en app de resolución de conflictos. *(this PR)*
- [x] **T-65** Ajustes (impresora, sync forzado, cambio de empresa, logout). *(this PR)*
- [x] **T-66** Sección Gestión (rol >= gestor): CRUD de Licencias en la app. *(this PR)*
- [x] **T-67** Sección Gestión: CRUD de Máquinas en la app. *(this PR)*
- [x] **T-68** Sección Gestión: CRUD de Locales en la app. *(this PR)*
- [x] **T-69** Sección Gestión: CRUD de Instalaciones (alta y cierre) en la app. *(this PR)*
- [x] **T-70** Bloqueo claro de operaciones de gestión cuando no hay conexión. *(this PR)*
- [x] **T-71** Notificación por email al técnico cuando se resuelve un conflicto (Edge Function + plantilla). *(this PR)*

### QA y release

- [x] **T-80** Pruebas E2E web (Playwright) de flujos clave.
- [x] **T-81** Pruebas instrumentadas Android del flujo de recaudación.
- [x] **T-82** Documentación de despliegue (Supabase, hosting web, build APK).
- [ ] **T-83** Beta privada con datos reales de la empresa piloto.

## Fase 2 — Operativa avanzada

- [x] **T-100** Foto de contadores con OCR pre-rellenando los inputs (Android ML Kit / cloud OCR).
- [x] **T-101** Notificaciones push con FCM (sustituye al email para resolución de conflictos y añade más eventos).
- [x] **T-102** Resumen mensual por email al titular del local (cron Edge Function).
- [x] **T-103** Informes avanzados con gráficas históricas en web.
- [x] **T-104** Exportación a CSV/Excel para gestoría.
- [x] **T-105** Multi-impresora en la app (varios modelos térmicos además de la PT210).
- [x] **T-106** Modo claro/oscuro y i18n preparada (sin más idiomas todavía).

## Fase 3 — Plataforma

- [x] **T-200** Onboarding self-service de nuevas empresas (registro, prueba).
- [ ] **T-201** Facturación / planes (si se SaaS-ifica).
- [x] **T-202** Auditoría completa con tabla de eventos (`audit_log`).
- [x] **T-203** Boletines digitales de instalación (si interesa).
- [ ] **T-204** API pública para integraciones futuras.
- [x] **T-210** Lockdown de escritura: toda escritura vía función (RPC `SECURITY DEFINER` / Edge `service_role`); REVOKE de INSERT/UPDATE/DELETE a `authenticated`/`anon` en todas las tablas de dominio; guardarraíl pgTAP global. Ver design.md §12.1.

## Convenciones

- Migraciones SQL en orden con timestamp `YYYYMMDDhhmmss_descripcion.sql`.
- Edge Functions con tests unitarios cuando contengan lógica numérica.
- Web: tests con Vitest + Playwright. Componentes con shadcn/ui.
- Android: tests instrumentados de los flujos de recaudación.
- PRs pequeños y enfocados a un único `T-XX`.
