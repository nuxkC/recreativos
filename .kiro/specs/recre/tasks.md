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
- [x] **T-211** Redondeo opcional de la recaudación bruta por empresa: el bruto se lleva al múltiplo más cercano (config `empresa.redondeo_recaudacion`) falseando server-side la lectura de salidas, que se persiste como real para que la diferencia se arrastre vía baseline; rastro de auditoría (`contador_salidas_leido`, `recaudacion_bruta_real`, `redondeo_aplicado`). Backend SSOT+migración+RPC+edge (#6), toggle web en ajustes (#7), rastro en el detalle web (#9) y cálculo local espejo en Android con migración Room 3→4 (#10).

### Tolva y préstamos (deudas del local con recuperación)

- [x] **T-212** Modelo backend de tolva y préstamos: `instalacion.tolva` (informativo), tablas `credito_local` (tolva/préstamo, deuda del local) y `recuperacion` (libro mayor), vistas de saldo (`v_credito_local_saldo`, `v_local_saldo`), `empresa.porcentaje_recuperacion` + override `local.porcentaje_recuperacion`; RPCs `crear_prestamo`, `registrar_recuperacion_efectivo`, `condonar_credito`, `set_porcentaje_recuperacion_local`; `crear_instalacion` extendida con tolva + hook de traslado (`p_tolva_continua_credito_id`) y guardarraíl; `actualizar_ajustes_empresa` con el % de recuperación; RLS solo-lectura + REVOKE; tests pgTAP (`09_credito_local_recuperacion`) y guardarraíles 07/08 al día. Invariante "la tolva pertenece al local" documentada en design.md §3.14/§5.5. *(this PR)*
- [x] **T-213** Web: tolva en el alta de instalación; % de recuperación en Ajustes (empresa) y ficha de local (override, con "heredar"); ficha de local con libro mayor (saldo, deudas, abonos) + "nuevo préstamo" + "registrar pago en efectivo" + condonar (admin); recuperado/entregado en el detalle de recaudación; tarjeta "capital en la calle" en el dashboard. Nuevo feature `lib/deudas` + `components/deudas`. *(this PR)*
- [x] **T-214** Recaudación recupera deuda: helper SSOT `_shared/recuperacion.ts` (tolva→FIFO, orden manual) + integración en `crear-recaudacion` (persistencia atómica recaudación + recuperaciones vía RPC `persistir_recaudacion`); `recaudacion.recuperado_total` + `pagado_local` (generada); `desglose_local` cuadra con `pagado_local` (rectifica `chk_desglose_local_suma`); ticket PDF con recuperado/entregado; `anular-recaudacion` revierte vía `revertir_recuperaciones_recaudacion`; tests Deno + pgTAP `10_recaudacion_recupera_deuda`. El detalle web (recuperado/entregado) se muestra en T-213. *(this PR)*
- [x] **T-215** Android: sync de saldo/% por local (deudas abiertas en `credito_local`, `empresa_params.porcentaje_recuperacion`, override `local.porcentaje_recuperacion`); espejo Kotlin del preview de recuperación (`core/calculo/Recuperacion.kt`, mismos 8 casos que el test TS); `desglose_local` objetivo = `pagado_local` en el paso de denominaciones; orden de imputación manual (reordenable, viaja en `orden_recuperacion`); tolva en el alta de instalación (gestión); ficha de deudas (`feature/deudas`: saldo + deudas abiertas offline + pago en efectivo + nuevo préstamo + condonar admin + override del %) accesible desde el detalle del local; migración Room 4→5. *(PR #15; ajuste: si `pagado_local = 0` el paso de denominaciones oculta los inputs, PR #16)*

### Refinamientos de deudas (seguimiento de uso real)

- [x] **T-216** Concepto **obligatorio** al dar de alta un préstamo: `crear_prestamo` rechaza `notas` vacío (ERRCODE 22023, migración aditiva); web (`PrestamoInputSchema` requerido + form) y android (diálogo) lo exigen. La tolva no lo necesita (concepto implícito); abono/condonación siguen con notas opcional. *(PR #17)*
- [x] **T-217** Android: la recuperación de deuda se ve ya en el **resumen de cifras** de la pantalla de contadores (`CifrasResumenCard` muestra retenido + entregado/`pagado_local`), no solo en el desglose. *(PR #18)*
- [x] **T-218** Web: sección **Deudas** como centro de mando (sidebar, `ROLES_GESTION`): índice de locales con saldo (`v_local_saldo`) + capital en la calle; `/deudas/[localId]` reutiliza `DeudasLocal` (gestión completa). El detalle de local deja de gestionar deuda inline y **redirige** a `/deudas/[id]`. Query `listarLocalesConSaldo`. *(PR #19)*
- [x] **T-219** Android: sección **Deudas** en el hub de Gestión: índice de locales con su saldo (agrega `credito_local` offline) + capital en la calle; cada local abre su ficha de deudas existente (centro de mando). `CreditoLocalDao.observarPorEmpresa` + `DeudasGestor` (VM+screen) + ruta `GESTION_DEUDAS`. *(PR #20)*

## Averías y trazabilidad

Sistema de averías con **historial por máquina** (qué falla, qué se cambió) en dos
fases. **Fase 1** (T-220…T-222) es solo trazabilidad: no toca dinero ni el SSOT.
**Fase 2** (T-223…T-225) añade tolva teórica/efectiva y la recuperación compartida
del premio pagado de la tolva, que **modifica el SSOT del cálculo**. Ver design.md
§3.16–§3.18 y §5.6.

### Fase 1 — trazabilidad (no toca dinero ni SSOT)

- [x] **T-220** Modelo backend de averías: tablas `averia` (sin columnas de tolva todavía) y `averia_recambio` (design.md §3.16/§3.17); RPCs `SECURITY DEFINER` `crear_averia` (deriva el snapshot instalacion/local de la instalación activa), `actualizar_averia`, `resolver_averia`, `crear_recambio`, `eliminar_recambio`; helper interno `recalcular_estado_maquina` (pone/quita `maquina.estado='averiada'` según `pone_maquina_fuera_servicio`, devolviendo a instalada/almacén; no toca `baja`); índice `idx_averia_maquina` + vista `v_averia` (historial por máquina); RLS solo-lectura + REVOKE (escritura solo vía RPC); guardarraíles 07/08 al día; tests pgTAP `11_averias_modelo` (historial atraviesa instalaciones, transición de estado de máquina, recambios, cross-tenant, permisos). *(this PR)*
- [x] **T-221** Web: gestión de averías. Alta/edición/resolución + recambios; **historial de averías en el detalle de máquina** (`/maquinas/[id]`, hoja de vida que atraviesa instalaciones, con su `local` snapshot vía embed PostgREST); indicador de averías abiertas en el listado de máquinas. Feature `lib/averias` (types/schemas/queries/actions + test) + `components/averias` (historial, diálogo alta/edición, resolver, recambios, badge de estado). *(this PR)*
- [x] **T-222** Android: reporte de averías por el técnico desde el detalle de local/máquina (offline → sync, como la recaudación); listado/historial por máquina en Gestión; categoría + descripción + recambios. Migración Room (cola `averia_pendiente` v6 + DAO + subida reanudable). Cola offline (`AveriaUploadWorker`/`AveriaUploadManager`) que sube avería + recambios vía RPC sin duplicar al reintentar; historial en línea por máquina (`v_averia` + recambios embebidos) con acción de resolver. *(this PR)*

### Fase 2 — tolva teórica/efectiva + recuperación compartida (**modifica el SSOT**)

- [ ] **T-223** Backend tolva por avería: migración aditiva que añade `averia.afecta_tolva` + `importe_tolva`; tabla `tolva_movimiento` (merma/reposición) + vista `v_instalacion_tolva` (efectiva derivada, design.md §3.18); `crear_averia` con tolva inserta la `merma`; columna `recaudacion.reposicion_tolva`; RPC de admin para saldar/condonar la merma pendiente si la máquina se da de baja (edge case §5.6); tests pgTAP.
- [ ] **T-224** SSOT — recuperación de avería **antes del reparto**: `_shared/calculo.ts` + espejo `core/calculo/Calculo.kt` (reposición = `min(neto, pendiente_tolva)`, `base_reparto = neto − reposición`, mismos casos en test TS y Kotlin); integración en `calcular-recaudacion`/`crear-recaudacion` (inserta la `reposicion` en `tolva_movimiento` de forma atómica con la recaudación); `anular-recaudacion` revierte la reposición; orden vs. recuperación de deuda §5.5. *La tarea de más riesgo: SSOT espejo bit-a-bit.*
- [ ] **T-225** Web + Android: la reposición de tolva por avería se muestra en el detalle/resumen de la recaudación (repuesto a tolva + base de reparto) y en la ficha de tolva de la instalación (teórica/efectiva/pendiente); ticket PDF con la línea de reposición.

## Convenciones

- Migraciones SQL en orden con timestamp `YYYYMMDDhhmmss_descripcion.sql`.
- Edge Functions con tests unitarios cuando contengan lógica numérica.
- Web: tests con Vitest + Playwright. Componentes con shadcn/ui.
- Android: tests instrumentados de los flujos de recaudación.
- PRs pequeños y enfocados a un único `T-XX`.
