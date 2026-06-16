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

- [x] **T-223** Backend tolva por avería: migración aditiva que añade `averia.afecta_tolva` + `importe_tolva`; tabla `tolva_movimiento` (merma/reposición) + vista `v_instalacion_tolva` (efectiva derivada, design.md §3.18); `crear_averia` con tolva inserta la `merma`; columna `recaudacion.reposicion_tolva`; RPC de admin para saldar/condonar la merma pendiente si la máquina se da de baja (edge case §5.6); tests pgTAP. Migración `20260612160000_tolva_modelo.sql`: `crear_averia` re-firmada a 8 args (los 2 de tolva con DEFAULT, llamadas de 6 args siguen resolviendo) e inserta la merma atómica; `saldar_tolva_pendiente(uuid,text)` admin-only; ledger con `chk_tolva_mov_origen`; RLS solo-lectura + REVOKE. Guardarraíles 07 (140)/08 (54) al día; test `12_tolva_modelo` (merma, tolva efectiva, saldo admin, constraints, permisos). *(this PR)*
- [x] **T-224** SSOT — recuperación de avería **antes del reparto**: `_shared/calculo.ts` + espejo `core/calculo/Calculo.kt` (reposición = `min(neto, pendiente_tolva)`, `base_reparto = neto − reposición`, mismos casos en test TS y Kotlin); integración en `calcular-recaudacion`/`crear-recaudacion` (inserta la `reposicion` en `tolva_movimiento` de forma atómica con la recaudación); `anular-recaudacion` revierte la reposición; orden vs. recuperación de deuda §5.5. *La tarea de más riesgo: SSOT espejo bit-a-bit.* `calcularRecaudacion` toma `pendienteTolva` y devuelve `reposicion_tolva`/`base_reparto` (mirror TS↔Kotlin, 5 casos en cada uno); migración `20260612170000`: `persistir_recaudacion` inserta la reposición atómica revalidando el pendiente vivo, `revertir_recuperaciones_recaudacion` la deshace, `chk_recaudacion_partes` rectificado a `parte_local+parte_empresa+reposicion_tolva=neto`. Tolva pendiente es por instalación (sin la carrera cruzada de la deuda por local). Test pgTAP `13_recaudacion_repone_tolva`. *(this PR)*
- [x] **T-225** Web + Android: la reposición de tolva por avería se muestra en el detalle/resumen de la recaudación (repuesto a tolva + base de reparto) y en la ficha de tolva de la instalación (teórica/efectiva/pendiente); ticket PDF con la línea de reposición. Partido por subproyecto en 4 PRs: ticket PDF (`pdf.ts`), web detalle recaudación (`recaudaciones/[id]` + tipo `Recaudacion`), web ficha tolva (tarjeta en el detalle del local, lee `v_instalacion_tolva`), android detalle (`CifrasResumenCard` + `RecaudacionHistoricaRow.reposicion_tolva`, server-fetched). **Previa offline correcta**: `v_instalacion_tolva.pendiente` se sincroniza a Room (`instalacion.pendiente_tolva`, migración Room v7) como la deuda, se pasa a `Calculo.kt` y la previa descuenta la reposición antes del reparto — así el desglose que separa el técnico cuadra con lo que persiste el servidor (no se le da de más al local). *(this PR)*
- [x] **T-226** Web + Android: **input de la merma de tolva al reportar avería** (cerraba el lazo de T-223: el backend ya insertaba la merma, pero ningún cliente podía activar `afecta_tolva`). El usuario marca "la avería pagó premio de la tolva" + importe (€) — visible solo si la máquina tiene instalación activa (la propia `crear_averia` lo exige, §5.6). **Android** (técnico): fluye offline igual que el resto — `averia_pendiente.afecta_tolva`/`importe_tolva` (migración Room v8) → `CrearAveriaParams` (`p_afecta_tolva`/`p_importe_tolva`, numeric vía `NumericStringSerializer`) → `crear_averia`, que inserta la `merma` atómica; además se **quita el campo "Coste"** del editor de recambios del formulario del técnico (no lo gestiona en campo; la columna y el back-office web siguen igual). **Web** (gestor): mismo bloque en el alta de `AveriaDialog` (gateado por `maquinaTieneInstalacionActiva`), `CrearAveriaInputSchema` (dinero `Decimal` → string, exige importe > 0 si afecta), la Server Action `crearAveria` pasa `p_afecta_tolva`/`p_importe_tolva`. Tests: repo Android (encolar persiste la merma; subida la envía a la RPC) y schema web (`CrearAveriaInputSchema`). *(this PR)*

## Fase 4 — Rediseño UI/UX (sistema de diseño "Confianza Industrial")

Implementa el rediseño visual y de IA especificado en `.kiro/specs/recre/` por las
cuatro fases de diseño: **auditoría + IA** (`functional-audit-and-ia.md`), **diseño
por pantalla** (`fase2-design-screens.md`), **tokens atómicos** (`fase3-design-tokens.md`)
y **specs de componentes** (`fase3-component-specs.md`). El **plan de fases y el orden**
vienen de `design-system-plan.md` §8 y `visual-identity.md`; **estas tareas refinan y
sustituyen esa "propuesta T-227+"** (inserta T-229 átomos base, por lo que la numeración
del §8 se desplaza +1 a partir de ahí). Regla fija del cliente: **teclado numérico in-app
solo en el extracto de denominaciones** (modos Total y Local); el resto de inputs numéricos
usan el teclado numérico/decimal del sistema y las fechas un DatePicker (T-232/T-233).

Independiente de la Fase 5 (dependencias) **salvo** T-230 (motion Android), que requiere
el Bloque 5 → **T-251** (Compose BOM ≥ 2025.10 para Material 3 Expressive / `MotionScheme`).

### F0 — Fundamentos (tokens · tipografía · átomos)

- **T-227** Materializa los tokens "Confianza Industrial". Dividida en web/Android (una rama/PR por plataforma; sin renumerar el resto del plan):
  - [x] **T-227a** Web: CSS vars + `tailwind.config` light/dark. Paleta de marca (petróleo) sustituye al zinc de shadcn; roles semánticos `success/warning/danger/info` (+`-subtle`), `surface-1/2`, charts/sidebar derivados de marca; **`-text` variants** reales (success-text `#076138`, danger-text `#A81818`, warning-text `#8A3D0A`, info-text `#1D4ED8`); `--muted` (superficie) vs `--muted-foreground` (texto); `on-danger`/`on-warning` de relleno en dark (`#3A0A0A`/`#3A2503`); rol `state-neutral` (deuda EUR/offline, nunca rojo); pares OPACOS precomputados `*-chip-bg/fg` para StatusChip; escala tipográfica, radios y rejilla de espaciado. Validado: Tailwind compila, Prettier, sin hex hardcodeado. Ver `fase3-design-tokens.md` + "Reconciliaciones (ronda 2)".
  - [x] **T-227b** Android: `Color.kt` + `Theme.kt`/`ColorScheme` M3 con los mismos roles, `RecreColors`/`LocalRecreColors` (slots que M3 no tiene: success/warning/info/border/muted/surface-2/ring), variantes `-text`, `on-danger`/`on-warning` oscuros en dark, rol `state-neutral`. Limpiado el placeholder Slate/Indigo/Red/Green y el marcador stale `T-65 / fase 2`. Sin `dynamicColor` (marca fija). Validado: `compileDebugKotlin` BUILD SUCCESSFUL.
- [x] **T-228** Tipografía Android: `Type.kt` con `GeistSans`/`GeistMono` (fuentes variables vía eje `wght`, `FontVariation`), `RecreType` (importe/importeMedium/cifra/cifraCaption mono+tabular `tnum`) y `Typography` M3 mapeado (escala Android +1 paso, pesos no estándar `FontWeight(440)`/`FontWeight(450)`). Fuentes Geist convertidas WOFF1→TTF in-repo (las ya vendorizadas en web, OFL) a `res/font/geist_variable.ttf` + `geist_mono_variable.ttf`. Validado: `compileDebugKotlin` BUILD SUCCESSFUL (aapt acepta los TTF).
- [x] **T-229** Biblioteca de átomos base (los 44 átomos de `fase3-component-specs.md`) en ambas plataformas: botones (variantes/estados), `StatusChip`/badges, `MoneyText` (color solo positivo/negativo, nunca para deuda), inputs, `SegmentedControl`, `FilterChip`, `Avatar`, `Collapsible`, acciones de icono, skeletons, etc. Respeta los estados y dp/px exactos y las **erratas transversales (§0–§I)**: targets táctiles ≥ 48dp, dinero siempre `decimal.js`/`BigDecimal`, estado nunca solo-color (icono+texto+color). Sustituye el uso de la paleta placeholder previa.
  - _Progreso web (parcial):_ hechos y validados (tsc + tailwind sin warnings + vitest 65/65, sin colores/deps crudos). **Batch 1 (manual):** `MoneyText` (+helper money-safe `lib/money/format.ts` con test), `StatusChip`, `FilterChip`(+`FilterChipRow`), `SegmentedControl`, `Skeleton`(+`SkeletonCard`), `SubtotalSeparator`, `IconAction`, `OfflineBadge`, `NotificationBadge`, `StepIndicator`. **Batch 2 (workflow Opus, implementar→verificar adversarial):** `Field`/inputs money-safe (`FieldInput`/`FieldNumber`/`FieldSelect`; Combobox CCAA + DatePicker **diferidos**: requieren `cmdk`/`react-day-picker`, no instalados), `SearchField`, `EmptyState`, `ErrorState`, `ConfirmDialog`, `Collapsible`, `SyncControl`, `UserMenu`+`Avatar`. Migrados a tokens por rol `ui/badge`, `ui/card`/`ui/button` (sin shadow), `dashboard/kpi-card`. Tokens F0 añadidos para soportarlos: `--border-strong`, `.no-scrollbar`, `transitionTimingFunction.standard` (`ease-standard`). 5 majors de la verificación corregidos (targets táctiles ≥44px, `aria-disabled` vs `disabled`, nombre accesible de `FieldSelect`). **Batch 3 (workflow Opus, primitivos→compuestos):** primitivos `ui/{tooltip,sheet,popover,command,calendar}` (deps `@radix-ui/react-tooltip`/`react-popover`, `cmdk`, `react-day-picker` instaladas) + compuestos `Combobox` CCAA, `DatePicker`(`date-field`), `CommandPalette`, `Sparkline`. 6 majors corregidos (ARIA combobox al input, `bg-secondary` seleccionado + highlight `surface-2`, `DialogTitle/Description` sr-only en CommandDialog + panel 20vh/640px). Token F0 añadido: **`--muted-strong`** (≥7:1 para iconos/€/sufijos informativos) — resuelve deuda sistémica de `field`/`date-field`. **Build web 100% limpio** (sin warnings tailwind; `ease-standard`/`duration` tokenizados). Capa de **átomos web esencialmente completa**; quedan organismos/layout (ThumbNav/Sidebar/TopBar/DataTable/KPI-Bento/Snackbar) que pertenecen a fases F3-4 (IA), no al átomo base.
  - _Android (en curso):_ no había capa de átomos Compose (solo `EstadoMaquinaBadge`/`MaquinaCard` a nivel feature). Se crea `com.recre.app.ui.components/` sobre `RecreColors.current`/`RecreType` (T-227b/T-228). Por batches con `compileDebugKotlin` de validación. **Batch A (workflow Opus) — hecho y compila**: `MoneyText`(+`formatEur` canónico money-safe `BigDecimal`), `StatusChip` (+ pares `*ChipBg/Fg` añadidos a `Color.kt`), `RecreButton` (PrimaryCTA/Tonal/Texto/Destructivo+AlertDialog con foco en Cancelar), `AppCard`/`EntidadRow`/`LocalCard`, `Skeleton`, `Divider`, `OfflineBadge`, `IconAction`. 2 blockers de compilación + 9 majors a11y/fidelidad corregidos. **Batch B (workflow Opus parcial + completado a mano) — hecho y compila** (`compileDebugKotlin` BUILD SUCCESSFUL): `SegmentedControl`, `StepIndicator`, `NotificationBadge` (workflow); `FilterChip`(+`FilterChipRow`), `SyncControl`, `Collapsible`, `EmptyState`, `ErrorState` (a mano: 5 agentes del workflow cayeron por rate-limit). Reutilizan átomos de Batch A (`RecreTextButton`/`RecrePrimaryButton`/`MoneyText`). Major de a11y corregido (`SegmentedControl.groupLabel` era código muerto: `.semantics{}` vacío) + 2 blockers (`NotificationBadge` colisión `role` BadgeRole vs semantics; `Collapsible` import `Row`). `primary-selected` de FilterChip derivado opaco vía `compositeOver` (no hay token, evita hex crudo). **Batch C (a mano, Opus) — hecho y compila** (`compileDebugKotlin` BUILD SUCCESSFUL, sin warnings): `Field` (`FieldNum` money-safe entero/decimal + `FieldText` con toggle password + `FieldSelect` + `ComboboxCcaa` con "Sin coincidencias"), `SearchField`, `Sparkline` (Canvas, BigDecimal solo→coordenadas), `Tooltip` (long-press M3 overlay neutro). Token **`mutedStrong`** (#3F4651/#B6BCC6) añadido a `Color.kt` para paridad con web (€/%/iconos informativos ≥7:1 sobre surface-2; `muted` no llega). **Capa de átomos base Android = completa** (paridad con la web). FUERA del átomo base, a sus fases: organismos/layout `TopBarGlobal`/`ThumbNav`/`TablaDensa`/`KPI-Bento`/`Snackbar` → F3-4 (IA), igual que la web los difirió; pantallas `Keypad denominaciones` (T-231/F2), `Lienzo firma`, `OCR CameraOverlay` → features/F2; `DatePicker` Android → se añade en la pantalla que lo consuma (web también lo difirió); `Avatar`/`UserMenu` es **solo web**. **`formatEur` duplicados unificados — hecho** (`compileDebugKotlin` + `testDebugUnitTest` BUILD SUCCESSFUL): los formateadores money locales (`HistoricoScreen`, `HistoricoDetalleScreen`, `MaquinaCard`, `CifrasResumenCard` internal — consumido por `RecuperacionResumenCard`/`DenominacionesScreen`) borrados y apuntando al canónico `com.recre.app.ui.components.formatEur`; los de otro nombre (`eur` en `DeudasGestorScreen`/`DeudasLocalScreen`, `formatCoste` en `AveriaUi`) delegan al canónico. Efecto: ahora todos agrupan miles es-ES «1.234,56 €» y usan el menos tipográfico U+2212 (antes sin agrupación). `TicketEscPos.formatEur` se mantiene aparte (impresora ESC/POS, texto plano, no UI). Imports huérfanos (`RoundingMode`/`Locale`/`BigDecimal`) limpiados.

### F1 — Motion

- [x] **T-230** Android: vocabulario de motion expresivo (transiciones del plan de diseño). — **Hecho (capa propia, estable)**. ⚠️ El API oficial (`MaterialExpressiveTheme`/`MotionScheme`) es `internal` en material3 1.4.0 (lo que trae Compose BOM 2025.12); solo es público desde 1.5.0-alpha (prerelease, excluida por el plan). Materializado el vocabulario §2.4 (spatial.fast/default = muelles físicos, effects.default = tween 250 ms) como `RecreMotionScheme`/`LocalRecreMotion` (`ui/theme/Motion.kt`), espejo de `RecreColors`; provisto en `RecreTheme` y consumido ya por `SegmentedControl` (thumb = muelle espacial). Resto de consumidores con F5 (T-241-244). **Migración a `MotionScheme.expressive()` cuando entre material3 1.5.0 → T-258.** Validado: compileDebugKotlin + testDebugUnitTest 93/0 + lintDebug 0 errores.
- [x] **T-231** Web: animaciones con `motion` 12.x (transiciones, `AnimatePresence`, layout). Funciona en React 18.3 → **no depende** de la Fase 5. Ver `fase3-design-tokens.md` (bindings de motion). — **Hecho** (tsc/eslint/prettier verdes en lo tocado; `motion@12.40.0` instalado): materializados en `globals.css` los tokens de motion (`--motion-duration-*`/`--motion-ease-*`/transiciones compuestas) + las 5 animaciones firma CSS (`recre-popover-in`/`offline-pulse`/`sync-spin`/`success-flash`/`danger-shake`) con `@media (prefers-reduced-motion)`; espejo en `tailwind.config.ts` (`transitionTimingFunction`/`transitionDuration`/`keyframes`/`animation`). `MotionProvider` (`MotionConfig reducedMotion="user"`) cableado en `layout.tsx`. Primitivas reutilizables en `components/common/motion.tsx` (`FadeIn`, `MotionItem` con `layout`+`AnimatePresence`, presets `TRANSITION`/`MOTION_EASE`/`fadeInUp`). Count-up de cifras (presentación) queda para T-238 con `@number-flow`. Adopción por pantalla (listas/popovers/flash de sync) se cablea en F3/F4/F5 sobre estas primitivas.

### F2 — Teclado numérico y campos

- [x] **T-232** Keypad in-app + **pantalla de extracto de denominaciones** (modos Total y Local): el **único** teclado numérico in-app del producto, según `fase3-component-specs.md` (átomo Keypad) y `fase2-design-screens.md`. Dinero como string/`BigDecimal`; el preview descuenta tolva/recuperación pero **no recalcula** (SSOT server-side). — **Hecho** (`compileDebugAndroidTestKotlin` BUILD SUCCESSFUL: main + test). (1) Átomo `Keypad` (`ui/components/Keypad.kt`, R5): rejilla 3×4, 4ª fila weight 1f/1f/2f, teclas 64dp surfaceContainer + borde `outline` ≥3:1, dígito Geist Mono, Siguiente único acento primary, backspace muted, haptic tick `KEYBOARD_TAP` no LongPress, `navigationBarsPadding`, test tags `keypad-{0..9}/backspace/next`. (2) `DenominacionesScreen` reescrita R1-R5: topbar con confirmar descartar (`BackHandler` + flecha), RecuperacionResumenCard fija solo-lectura en Local (`reordenable=false`), lista agrupada Billetes/Monedas con subtotales + celda **Box readonly** dirigida por keypad (IME imposible) + auto-scroll a fila activa, BloqueProgreso sticky (Objetivo/Total héroe `MoneyText`/StatusChip Cuadra-Faltan-Sobran) + CTA `RecrePrimaryButton` gateado a cuadre exacto (`importesIguales`). ViewModel intacto; +11 strings i18n; test instrumentado adaptado al keypad (helper `tecleaCantidad`). Pendiente menor: borrador Room (persistencia offline del conteo) — el ViewModel ya mantiene el estado; la persistencia se aborda donde toque el flujo.
- [x] **T-233** Resto de campos numéricos → **teclado numérico/decimal del sistema** (importes, contadores, %, cantidades) y fechas → **DatePicker**; retira cualquier keypad in-app fuera de denominaciones. Web (`inputMode`/`type`) y Android (`KeyboardType`/`DatePicker`). — **Hecho** (web: tsc + ESLint + Prettier + Vitest 65/65; Android: `compileDebugKotlin` + `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL). **Fechas → DatePicker (17 inputs):** web migra los 13 `<input type="date">` al átomo `common/date-field` (Popover + react-day-picker, trigger read-only, es-ES dd/MM/aaaa, preservando los enlaces `min`/`max` desde↔hasta de informes y el `min` de cierre) en licencias, instalaciones (alta + cierre), nuevo préstamo y filtros (auditoría, recaudaciones, cambios de placa, informes); retira el `FieldDate` legacy basado en `<input type="date">` de `common/field.tsx` que duplicaba el átomo y dejaba una ruta de fecha que esquivaba el DatePicker. Android estrena el átomo `FieldDate` (`ui/components/Field.kt`): trigger read-only → `DatePickerDialog` M3 (la fecha NUNCA se teclea, IME imposible), valor String ISO `yyyy-MM-dd` igual que la web, `min`/`max` vía `SelectableDates`, textos de diálogo `android.R.string.ok`/`cancel` (localizados por el SO); convierte los 4 campos que se tecleaban como texto `YYYY-MM-DD` (LicenciaForm expedición/caducidad con caducidad ≥ expedición vía `minIso`, InstalacionForm inicio + cierre con cierre ≥ inicio). **Numéricos:** ya declaran el teclado del sistema en ambas plataformas (web `type=number` o `inputMode=decimal`; Android `KeyboardType.Number/Decimal`) → **cumplen T-233**; migrarlos a los átomos saneados `FieldNum` (web/Android) cambiaría `type=number`→`text` sin añadir saneado (regresión), así que se difiere al rediseño por pantalla (F3/F4): no es la regla de teclado de esta tarea. **Keypad in-app fuera de denominaciones:** ninguno que retirar (el único es el legítimo de T-232). **Cierra F2.**

### F3 — Arquitectura de información (Android)

- [x] **T-234** Navegación inferior (bottom nav) + FAB contextual según la IA de `functional-audit-and-ia.md`. — **Hecho** (`compileDebugKotlin` + `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL). Sustituye el menú overflow (⋮) sobrecargado de Locales —el problema estructural raíz T-2— por una **app shell de pulgar**: `RecreBottomBar` (4 pestañas **Locales · Histórico · Gestión · Ajustes**, `ui/components/RecreShell.kt`) + `RecreTopBarActions` globales (↻ sincronizar con spinner + 🔔 campana con badge), alimentadas por un `ShellViewModel` (`feature/shell/`) que comparte el conteo de alertas (`AlertasRepository.contarPendientes`) y el estado de sync (`SyncManager`) que ya usa Locales. **Sin Scaffolds anidados:** cada pestaña conserva su Scaffold y monta `bottomBar`/`actions` del shell (una sola Scaffold activa); las pantallas de detalle se abren full-screen sin barra. Navegación entre pestañas con `NavController.navigateTab` (patrón `popUpTo(Locales){saveState}` + `launchSingleTop` + `restoreState`; Locales = hogar). Histórico/Gestión/Ajustes pierden la flecha-atrás (ahora son pestañas); Locales pierde el overflow (cambiar empresa y cerrar sesión ya viven en Ajustes). **FAB contextual:** los FAB de alta siguen en cada gestor de Gestión (ya son contextuales); las 4 pestañas no tienen acción de creación primaria → sin FAB global. **Pendiente menor:** ocultar la pestaña Gestión por rol (hoy siempre visible; `GestionScreen` ya muestra "sin permiso" por defensa en profundidad como antes) → refinamiento posterior. Validación visual en emulador recomendada (la compilación es el listón aplicado).
- [x] **T-235** Ajustes con pestañas (tabs), refinando **T-65** (impresora, sync forzado, cambio de empresa, logout) sobre el nuevo sistema. — **Hecho** (`compileDebugKotlin` OK). `AjustesScreen` pasa a **2 pestañas** vía el átomo `SegmentedControl`: **Cuenta** (email + empresa activa/"cambiar" + cerrar sesión) y **Dispositivo** (sincronización + impresora Bluetooth). Se retira `AtajosCard` (Histórico es pestaña del bottom nav; Alertas viven en la campana del top bar) → Ajustes queda como **config pura** (IA §4). VM intacto (el estado ya estaba segmentado por dominio).
- [x] **T-236** Centro de alertas/campana (averías abiertas, sync pendiente, descuadres) en el top bar. — **Hecho** (`compileDebugKotlin` + `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL). El `ShellViewModel` agrega en el **badge de la campana** todo lo que requiere atención: alertas in-app del backend (**los descuadres llegan ya como alertas de tipo conflicto**, + licencias por caducar, locales sin recaudar, anuladas…) **más** los pendientes locales sin subir (recaudaciones + averías, vía `observarContadorPendientes` de Room) = la "sync pendiente". La pantalla `AlertasScreen` (centro de alertas) muestra, sobre la lista, un aviso **"sin sincronizar"** (tono neutro-info, nunca danger) con "Sincronizar ahora" cuando hay pendientes locales, reflejando lo mismo que cuenta el badge. **Cierra F3 (IA Android).**

### F4 — Arquitectura de información (Web)

- [x] **T-237** Paleta de comandos (`cmdk`): navegación y acciones rápidas (locales, máquinas, recaudaciones, deudas). — **Hecho** (tsc/eslint/vitest 65 verdes). La `CommandPalette` (cmdk, T-229) estaba completa pero **huérfana**; se monta transversal con un disparador visible `CommandMenu` (cliente) en el TopBar (botón-buscador con ⌘K anunciado vía `aria-keyshortcuts`; el atajo lo gestiona la propia paleta). Acciones rápidas ampliadas (altas de local/licencia/instalación en el grupo «Crear», gateadas por rol). App single-locale sin prefijo → `router.push("/ruta")` correcto.
- [x] **T-238** Dashboard con layout bento (tarjetas: capital en la calle, recaudación, averías, alertas) según `fase2-design-screens.md`. — **Hecho** (tsc/eslint/vitest 65 verdes). KPI **héroe** `HeroRecaudacion` (2×2): recaudación del mes como cifra grande tabular con **count-up** (`@number-flow/react`, diferido aquí desde T-231; respeta `prefers-reduced-motion`; capa decorativa `aria-hidden`, nombre accesible money-safe), **sparkline** `primary` (nueva query `obtenerSerieRecaudacionMensual`, serie de 6 meses, solo coordenadas) y variación vs. mes anterior. 4 KPIs alrededor, cada uno **deep-link** a su lista filtrada (T-12): capital→/deudas, averías→/maquinas?estado=averiada (danger), descuadres→/recaudaciones?estado=conflicto (warning), licencias (warning). `KpiCard` gana `href` (enlace estirado + hover). Sparkline/MoneyText (T-229) por fin con consumidor.
- [x] **T-239** Deudas como centro de mando con las 3 tarjetas (saldo / capital en la calle / actividad), reforzando T-218 sobre el nuevo sistema. Deuda en **neutral**, nunca rojo. — **Hecho** (tsc/eslint verdes). La cabecera de Deudas pasa de una tarjeta a **tres lentes**: saldo total adeudado (stock, dato-héroe), capital en la calle (composición tolva/préstamo) y actividad reciente (flujo: importe recuperado + nº movimientos en 30 días, nueva query `obtenerActividadDeuda` money-safe con `Decimal`). Toda cifra en **neutro con icono €** (MoneyText sin tono): deber no es error (T-1). El ledger de locales se conserva (ahora con MoneyText).
- [x] **T-240** Conflictos/descuadres mostrados **inline** (sin sección dedicada): en el contexto donde ocurren, con icono+texto+color de estado. — **Hecho** (tsc/eslint/vitest 65 verdes). Se **elimina** la sección «Conflictos» (T-4, redundante): ruta `/conflictos`, item del sidebar y ficheros huérfanos (`conflictos-table`, `lib/conflictos/queries`). Los descuadres se ven inline: en la **lista** vía el filtro `estado=conflicto` + badge warning (ya existían); en el **detalle**, el bloque pasa de amber crudo a tokens `warning` (T-1) y estrena un `StatusChip` con el **delta** del bruto (servidor − registrado, money-safe con `Decimal`, signo explícito). `ResolverConflicto` sigue inline en el detalle. La acción ⌘K «Filtrar conflictos» → «Ver descuadres» (`/recaudaciones?estado=conflicto`). **Cierra F4 (IA web).**

### F5 — Pulido e interacción

- [ ] **T-241** Skeletons de carga, pull-to-refresh (Android) y swipe actions en listados. — **En curso (solo falta swipe).** **Hecho:** (1) skeletons de carga en TODOS los listados (gestión Máquinas/Locales/Instalaciones/Averías + Deudas gestor/local) vía `ListSkeleton`; (2) `Modifier.animateItem()` en los listados de entidad con el muelle espacial de `RecreMotion` (T-230); (3) pull-to-refresh ya existía en los listados de técnico. **FALTA (aparcado):** swipe actions (`SwipeToDismissBox`) — pendiente de **definir la semántica por lista** (¿completar/averiar/archivar?); no se inventa para no meter gestos destructivos a ciegas.
- [ ] **T-242** Wizards multipaso (alta de instalación, alta de máquina) con progreso y validación por paso. — **En curso.** **Hecho:** alta de instalación como **wizard de 2 pasos** (Qué se instala → Condiciones) con `StepIndicator` (T-229) y "Siguiente" gateado por validez del paso (los 3 selects elegidos); edición y cierre sin tocar (siguen en form único). **FALTA:** wizard de alta de máquina (2 pasos).
- [ ] **T-243** Drawers y toasts (`sonner` web / equivalente Compose) consistentes con los tokens y el motion.
- [ ] **T-244** Transiciones de elemento compartido (shared-bounds/element): Compose en Android; en web vía `motion` (layout), realzable con View Transitions nativas cuando entre **T-256** (React 19).

### F6 — Accesibilidad

- [ ] **T-245** Barrido WCAG final (European Accessibility Act): AA 4.5:1 obligatorio en **ambos modos**, estado nunca solo-color, foco visible, `aria-live` que anuncia cambios (no el estado inicial), targets táctiles. Verifica las ratios de `fase3-component-specs.md` "Estado de verificación (ronda 2)".

## Fase 5 — Actualización de dependencias

Implementa `dependency-upgrade-plan.md` (Bloques 0–12) como tareas trazables. **Un bloque
coherente = una rama = un PR**; baseline verde y gates (lint/test/build, `JAVA_HOME` del
snap en Android, pgTAP + `deno test` en SSOT) **entre** bloques. Revalidar "latest" con
`npm view` / `maven-metadata.xml` antes de cada bloque. Los Bloques 1–8 son bajos de riesgo;
9–11 requieren migración (planificar aparte); 12 se difiere.

- [x] **T-246** Bloque 0 — Preparación: baseline verde + lockfiles commiteados; aislar/resolver `next build` roto por `@supabase/ssr` y el test dependiente de CRLF **antes** del Bloque 10. — **Hecho** (PR #27, mergeado). Lockfiles commiteados; baseline verde. *Nota:* `next build` roto por `@supabase/ssr` + test CRLF **siguen pendientes** de resolver antes de T-256 (Bloque 10).
- [x] **T-247** Bloque 1 — Web: patches/minors dentro de la major actual (react-query, react-hook-form, `decimal.js` 10.6 **alineado con supabase**, radix, recharts, sonner, tailwind 3.4.19, etc.). — **Hecho** (PR #27, mergeado). react-query/react-hook-form/radix/recharts/sonner/tailwind 3.4.19/decimal.js 10.6; tsc + Vitest verdes.
- [x] **T-248** Bloque 2 — Web: TypeScript al último **5.x** (aislado; `tsc --noEmit` + test). — **Hecho** (PR #27, mergeado). TypeScript 5.9.3; `tsc --noEmit` 0.
- [x] **T-249** Bloque 3 — Android: libs aisladas de bajo riesgo sin tocar Kotlin (appcompat, datastore, material, mockk, turbine, core-ktx, google-services). — **Hecho** (PR #28, mergeado). core-ktx 1.16/appcompat 1.7.1/material 1.13/datastore 1.1.7/google-services 4.4.4/turbine 1.2.1/mockk 1.13.17 (reflect 2.0.0); assembleDebug OK.
- [x] **T-250** Bloque 4 — Android: AGP 8.7.3 → **8.13.2** (Gradle 8.14.4 actual; **no** AGP 9 / Gradle 9). — **Hecho** (PR #28, mergeado). AGP 8.13.2 sobre Gradle 8.14.4.
- [x] **T-251** Bloque 5 — Android **ATÓMICO** ⚛️: Kotlin 2.0.21 → **2.1.21**, KSP **2.1.21-2.0.2** (prefijo == Kotlin), Compose BOM → **2025.10.00+** (idealmente 2025.12 — **habilita Material 3 Expressive**, desbloquea **T-230**), Hilt ~2.56.2, Room 2.7.2, coroutines 1.10.2, serialization ~1.8. Si algo falla, **revertir el bloque entero**. Probar DAO/migraciones Room. — **Hecho** (PR #29, mergeado). Kotlin 2.1.21 / KSP 2.1.21-2.0.2 (prefijo==Kotlin) / Compose BOM 2025.12.00 / Hilt 2.56.2 / Room 2.7.2 / mockk 1.14.7; assembleDebug + testDebugUnitTest 93/0. **Desbloquea T-230.**
- [x] **T-252** Bloque 6 — Android: lifecycle/activity/navigation-compose + WorkManager tras Kotlin alineado. — **Hecho** (PR #29, mergeado). lifecycle 2.9.4/activity-compose 1.10.1/navigation 2.9.8/hilt-nav+work 1.3.0/work 2.10.5.
- [ ] **T-253** Bloque 7 — Android: CameraX 1.5.x + MLKit + firebase-bom 34.x. **Verificación manual en dispositivo**: OCR de contadores (T-100) y push (T-101). — **Deps hechas** (PR #29, mergeado): CameraX 1.5.3 + MLKit + firebase-bom 34.14.1 (módulo `firebase-messaging`, no `-ktx`); assembleDebug OK. **FALTA: verificación manual en dispositivo** (OCR contadores T-100 + push T-101).
- [ ] **T-254** Bloque 8 — Android: tests instrumentados (`androidx-test-ext-junit` + espresso); `connectedAndroidTest` si hay emulador. — **Deps hechas** (PR #29, mergeado): androidx-test-ext-junit 1.3.0 + espresso 3.7.0. **FALTA: `connectedAndroidTest` en emulador/dispositivo.**
- [x] **T-255** Bloque 9 — **SSOT coordinado** web ⇄ supabase: `zod` 3→4 y `date-fns` 3→4 a la vez en `web/` y `supabase/functions/deno.json` (+ `@hookform/resolvers` 4/5). **Una sola PR** con pgTAP + `deno test` verdes. — **Hecho** (PR #31, mergeado). zod 4.4.3 + date-fns 4.4.0 (date-fns-tz 3.2.0) en web y `supabase/functions/deno.json`; tsc 0, Vitest 65/65, deno check 0, deno test 66/66.
- [ ] **T-256** Bloque 10 — Web mayor: React 19 + Next 15→16 incremental (params async, ESLint flat config, `next-intl` 4). Resolver antes el `next build`/`@supabase/ssr`. **Habilita** `useOptimistic` y View Transitions (realza **T-244**).
- [ ] **T-257** Bloque 11 — Web mayor: Tailwind v4 (CSS-first `@theme`, `@tailwindcss/postcss`, `tailwind-merge` 3, `tw-animate-css`, revalidar shadcn). **Validación visual** además de build; coordinar con los tokens de **T-227**.
- [ ] **T-258** Bloque 12 — **Diferido**: Gradle 9.5.1 + AGP 9 (al salir de alpha), Kotlin 2.2/2.4, Coil 3, TypeScript 6, supabase-kt 3.6 + ktor alineado, recharts 3, lucide-react 1.x. **Incluye** migrar la capa `RecreMotion` (T-230) al `MotionScheme.expressive()` oficial cuando material3 1.5.0 sea estable.

## Convenciones

- Migraciones SQL en orden con timestamp `YYYYMMDDhhmmss_descripcion.sql`.
- Edge Functions con tests unitarios cuando contengan lógica numérica.
- Web: tests con Vitest + Playwright. Componentes con shadcn/ui.
- Android: tests instrumentados de los flujos de recaudación.
- PRs pequeños y enfocados a un único `T-XX`.
