# Auditoría funcional PROFUNDA + Arquitectura de información — Recre

> Estado: **revisión/aprobación del usuario**. Fase 1 (análisis funcional + IA), consciente de la identidad ya
> confirmada (*Confianza Industrial*, azul petróleo) pero **sin diseño pixel-perfect** (eso es Fase 2).
> Método: **22 agentes Opus 4.8** leyeron la implementación completa (Composable/page + ViewModel + estados +
> sub-componentes) de las **44 pantallas**. **548 funcionalidades** enumeradas.
> Supersede al borrador superficial previo (que marcaba solo el 2% para cambio por usar agentes de localización).

## 0. Alcance del análisis

| Métrica | Valor |
|---|---|
| Pantallas auditadas | 44 (29 Android + 15 web) |
| Funcionalidades enumeradas | 548 |
| Marcadas para cambio | **292 (53%)** |
| Desglose | 127 rediseñar-interacción · 60 añadir · 26 promover · 18 mover · 18 fusionar · 14 eliminar · 14 degradar · 10 renombrar · 3 dividir |

Conclusión: la mayoría de funciones **existen y funcionan**, pero más de la mitad están **mal jerarquizadas, mal
coloreadas, duplicadas, enterradas o incompletas** respecto a la identidad y la IA que acordamos. Los problemas no son
aleatorios: se repiten en **13 patrones transversales** (§1). Arreglar esos patrones arregla decenas de pantallas a la vez.

---

## 1. Hallazgos transversales (los patrones que se repiten)

### T-1 · Mal uso del color de estado (el más extendido) 🔴
Rojo de error usado para cosas que **no son errores**: banners de *offline* (Locales, Deudas, Contadores), tarjetas
*"sin permiso"* (Gestión, Máquinas, Averías), *"no procede"* (resultado válido), *baja confianza OCR*, estado *"baja"*
de máquina. Y verde usado para *"activa/instalada"* (verde debe ser **solo dinero positivo**). → **Política de color
estricta**: rojo solo error/avería; verde solo positivo; *offline/permiso/info* = neutro; **siempre icono+texto**,
nunca color solo (técnico al sol + ~8% daltonismo). Afecta a ~15 pantallas.

### T-2 · El menú overflow (⋮) de Android es el problema estructural raíz
`LocalesScreen` concentra **8 acciones heterogéneas** en el ⋮: Sincronizar, Histórico, Alertas, Impresora, Gestión,
Cambiar empresa, Cerrar sesión. → **Barra de navegación de pulgar** (Locales · Histórico · Gestión · Ajustes) +
**campana** para Alertas + **Sincronizar visible** + Ajustes/Impresora/Cambiar-empresa/Cerrar-sesión dentro de Ajustes.
Resuelve de golpe Locales, Gestión, Histórico, Alertas, Ajustes, Impresora.

### T-3 · Conceptos y accesos duplicados
- **Los dos "Locales" de Android NO se fusionan** (decisión de producto, corrige el veredicto del agente): `LocalesScreen`
  (raíz) es **operación/acceso** — el técnico elige local para **recaudar** y solo aparecen los que **tienen máquina
  instalada**; `LocalesGestorScreen` (en Gestión) es **CRUD administrativo**, en pie de igualdad con Licencias/Máquinas/
  Instalaciones. Propósito, datos y modo distintos → **se mantienen separados**. Único arreglo: **microcopy** para que el
  mismo nombre no confunda (p.ej. "Locales" / acceso vs "Gestión › Locales" / catálogo).
- **Histórico y Alertas duplicados** (overflow + atajos en Ajustes). → fuente única.
- **Cambiar empresa duplicado** (Android overflow + Ajustes; Web EmpresaSwitcher + UserMenu). → una entrada.

### T-4 · "Conflictos" (web) es una sección redundante
Su contenido es Recaudaciones con `estado=conflicto`, y ni siquiera resuelve (la resolución vive en el detalle de
recaudación). → **eliminar del sidebar**, absorber como **vista filtrada de Recaudaciones** + **KPI accionable** en el
Dashboard. (Confirmado por 3 agentes independientes: Recaudaciones, Conflictos y Shell.)

### T-5 · Las listas no muestran contexto cruzado (el dato está solo en el detalle)
- **Máquinas (web)**: no dice en qué local/instalación está, ni averías abiertas (como columna ordenable).
- **Licencias (web)**: no avisa de caducidad próxima ni nº de instalaciones que la usan.
- **Locales (web)**: no muestra nº de máquinas activas ni estado de deuda.
- **Instalaciones (web)**: no muestra ingresos/productividad.
- **Android** `LocalCard`/`MaquinaCard`: sin avería/pendiente/deuda/conflicto por local.
→ traer datos de contexto cruzado a la lista (las **vistas SQL ya existen**, p.ej. agregados por máquina/local).

### T-6 · Entradas numéricas: keypad in-app SOLO en denominaciones; el resto, teclado numérico del sistema
Hoy todas las entradas numéricas abren el teclado **QWERTY** del sistema. La corrección tiene tres niveles:
- **Solo el extracto de denominaciones** (`DenominacionesScreen`, modos Total y Local) usa el **keypad in-app** anclado
  (tu requisito fijo): es la única pantalla con el problema ergonómico de abrir/cerrar y tapar casillas.
- **El resto** de campos numéricos (contadores, importes de avería/merma, contadores de cambio de placa, importes de
  deudas, tasa/% de instalación, valor de crédito) usa el **teclado del sistema con el tipo correcto**:
  `KeyboardType.Number`/`Decimal` (Android) o `inputMode="numeric"/"decimal"` (web) — **no QWERTY** — con `tabular-nums`
  en la presentación.
- **Fechas** tecleadas a mano (Licencia, Instalación) → **DatePicker** nativo.

### T-7 · RBAC incoherente / sin defensa en profundidad
- **Máquinas y Licencias (web)**: la **lista** la ven los 5 roles pero el **detalle** exige gestión → filas clicables
  que llevan a `/sin-permiso` (pared) para técnico/contable. → igualar gate, o detalle read-only para más roles.
- **Cambios de placa (web)**: visible a técnico, debería ser admin (como Auditoría).
- **Android (Gestión y sus hijas)**: el gate vive solo en el item de menú, no en la ruta/VM. → gate real en ruta.

### T-8 · "El dato no es el héroe" (incumple la identidad confirmada)
Dashboards en rejilla plana sin KPI dominante; importes sin `tabular-nums`; el dinero (parte_empresa, capital en la
calle, neto) enterrado o calculado en cliente. → **bento con KPI dominante**, tabular-nums en toda cifra, dinero arriba.

### T-9 · Pantallas que mezclan responsabilidades
`LocalesScreen` (directorio + notificaciones + config + sesión), `Ajustes` Android (config + dispatcher + sync),
`Gestión` (4 maestros CRUD + Deudas que debería destacar), `ContadoresScreen` (captura + guardián de lock/sync),
Detalle de recaudación (8 cards al mismo nivel), Deudas-local (operar + configurar %). → separar/jerarquizar.

### T-10 · Truncado silencioso y zonas horarias mal
Listas con `limit 200/300` sin aviso ni paginación (Recaudaciones, Cambios de placa, Auditoría, ledger). Filtros de
fecha en **UTC** en vez de la zona de la empresa (Recaudaciones, Auditoría). → paginar/avisar + usar zona de empresa.

### T-11 · Cmd+K (web) inexistente
Casi todas las vistas web proponen registrar sus acciones en una **command palette** (ir a recurso, crear, exportar,
filtrar conflictos). → introducir Cmd+K alimentada por `nav-config` + acciones de dominio.

### T-12 · Falta cross-linking entre recursos
El ledger de deuda con origen `recaudacion` no enlaza a esa recaudación; las columnas de instalación no enlazan a
local/licencia; las alertas no enlazan al recurso; el id de entidad en Auditoría no es clicable. → deep-links.

### T-13 · Código muerto y promesas incumplidas
`eliminar()/cerrar()` nunca cableados en InstalacionesGestor; `localizedError()` muerto en varias listas; subtítulos
que prometen "logo"/"contadores"/"avisos de caducidad" que no existen; columna Email (Equipo) con placeholder. → limpiar.

---

## 2. Decisiones por pantalla — ANDROID

> Formato: **Pantalla** — *veredicto* — cambios clave.

**Arranque/acceso**
- **Splash** — *rediseñar* — 🐞 **bug**: offline con sesión persistida deja el spinner girando para siempre (el `Failure`
  de membresías se enmascara como `Loading`); sin caché local de membresías/empresa. + splash con marca + estado de
  error/reintento. Mover el *priming* de permiso de notificaciones al subsistema Alertas.
- **Login** — *mantener+huecos* — añadir "olvidé contraseña", toggle ver contraseña, mensaje "sin conexión" propio,
  limpiar `emailError/passwordError` del UiState (o cablear validación inline).
- **SeleccionarEmpresa** — *mantener* — entrada única (quitar "cambiar empresa" del overflow), subir la autoselección a
  `SessionRepository`, enriquecer cards (última usada, alertas por empresa).
- **SinAcceso** — *mantener* — CTA "Comprobar acceso" (reintentar), icono neutro (no rojo), diferenciar "invitación
  pendiente" vs "cuenta desactivada".

**Hub y recaudación**
- **Locales (hub)** — *rediseñar (estructural)* — ver T-2: nav de pulgar, campana, sync visible, sacar config/sesión a
  Ajustes; `SyncStaleBanner` a warning (no rojo); `LocalCard` con contexto cruzado; buscar también por titular.
- **LocalDetalle** — *rediseñar* — "Recaudar todas" como CTA dominante sticky; "Ver deudas" degradado a dato en
  cabecera; teléfono→llamar y dirección→mapas; renombrar "baseline" a "Contador base / Última lectura"; acceso por
  máquina a su histórico/averías; gating de escritura para CONTABLE.
- **Contadores (paso 1)** — *rediseñar* — mover el guardián lock/sync a **precondición previa** (al pulsar Recaudar);
  **teclado numérico del sistema** (`KeyboardType.Number`, no QWERTY); CifrasResumenCard como dato héroe etiquetado "previa (servidor recalcula)"; OCR como vía principal;
  lock como panel inline (no AlertDialog); recolorar "no procede"/"baja confianza" (no rojo); acento solo en "Continuar".
- **Denominaciones (paso 2)** ⭐ — *rediseñar* — **keypad in-app** anclado abajo; Objetivo/Total/Diferencia en bloque
  persistente; validación icono+texto+color ("Faltan/Sobran X €"); extraer la imputación de deuda a un paso/sheet
  previo; aclarar lenguaje "entregado vs retenido"; borrador para no perder un conteo de 18 campos.
- **Confirmación (paso 4)** — *mantener+rejerarquizar* — unificar las dos cards de cifras con jerarquía; firma a pantalla
  completa con "Firme aquí" + Limpiar; `BackHandler` "¿descartar firma?"; feedback de error de guardado; en cadena,
  "Máquina X de N"; Bluetooth = único CTA primario.

**Averías / inventario (dentro de Gestión)**
- **ReportarAvería** — *rediseñar* — layout de campo (categoría como chips grandes); importes (merma/coste) con teclado
  numérico/decimal del sistema; fusionar descripción+notas; cablear "pendientes por máquina"; microcopy a "tolva/merma".
- **AveríasMáquina** — *mantener* — renombrar mental a "Hoja de vida"; pre-gate de rol en "Resolver"; exponer lectura al
  técnico (hoy asimetría reporta-pero-no-ve).
- **MáquinasGestor** — *promover/rediseñar* — Gestión a pestaña de pulgar; crear "Detalle de máquina" que absorba
  ver-averías/ubicación; estado como chip icono+texto; columna de contexto (instalación/local + avería); FAB offline real.
- **MáquinaForm** — *rediseñar* — traducir estados (no códigos crudos); restringir estados derivados; aclarar "contador
  de fábrica"; error de serie duplicada inline; numéricos con teclado numérico del sistema + tabular-nums.
- **CambioPlaca** — *reagrupar* — acercar el feature aislado a operación de máquina; agruparlo con Recaudar/Reportar
  avería como las 3 operaciones de campo sobre una máquina; teclado numérico del sistema en los contadores; confirmación de impacto del baseline.

**Gestión y entidades**
- **Gestión (hub)** — *rediseñar* — promover a pestaña de pulgar; agrupar 4 maestros bajo "Inventario/Catálogo"; sacar
  **Deudas** a jerarquía superior (centro de mando, no 5ª card); aclarar el microcopy de los dos "Locales" (operativo vs
  catálogo CRUD) **sin fusionarlos**; "sin permiso" en neutro.
- **InstalacionesGestor** — *limpiar* — 🐞 eliminar código muerto `eliminar()/cerrar()`; destacar tasa/%local tabular;
  FAB offline real.
- **InstalaciónForm** — *rediseñar interacción* — DatePicker (no fechas a mano); dinero/% con teclado numérico/decimal del sistema; avisos en neutro;
  el cierre vive aquí (único hogar).
- **Licencias/LocalesGestor + Forms** — *promover* — gate de rol real en ruta; **`LocalesGestor` se mantiene separada**
  de la `LocalesScreen` operativa (CRUD admin vs acceso a recaudar: distinto propósito, datos y audiencia), solo se
  aclara el microcopy; estado como chip; unificar `LicenciaCard/LocalCard/MaquinaCard` en un **componente de fila visual**
  compartido (consistencia, no fusión de pantallas); DatePicker + combobox de CCAA; guard "descartar cambios".
- **DeudasGestor** — *promover* — sacar del hub Gestión a dominio propio; "Capital en la calle" como KPI; separar
  locales con deuda vs a 0; desglose tolva/préstamo en chips tabular.
- **DeudasLocal** — *rediseñar* — mover edición de % a config del Local; importes con teclado numérico/decimal del sistema; banner offline neutro;
  diferenciar tolva (merma a recuperar) vs préstamo (adelanto) icono+texto; ledger enlaza a su recaudación; CTA único.

**Histórico / alertas / ajustes / impresora**
- **Histórico** — *promover* — a pestaña de pulgar; eliminar atajo duplicado en Ajustes y entrada del overflow; permitir
  abrir filtrado a `estado=conflicto` desde una alerta.
- **HistóricoDetalle** — *mantener* — fetch por id directo (no depender de caché top-200); Bluetooth único CTA primario.
- **Alertas** — *promover* — a **campana con badge** en top bar global; eliminar duplicado en Ajustes; completar routing
  por tipo; marcar-leída como swipe; iconos por tipo, rojo solo conflicto.
- **Ajustes** — *adelgazar* — a config pura (Cuenta · Empresa+cambiar empresa · Impresora · Sesión); sacar Sincronización
  (a top bar) y Atajos (Histórico/Alertas a sus destinos); traducir el rol (no enum crudo); pasar a pestaña sin back.
- **Impresora** — *mantener* (la mejor construida) — entrada única desde Ajustes; manejar permiso "denegado permanente".

---

## 3. Decisiones por pantalla — WEB

**Shell + Inicio**
- **Sidebar** — *reordenar* — Inicio / **Operación** / Inventario / Analítica / Administración; "Dashboard"→"Inicio";
  **eliminar Conflictos** (→ filtro de Recaudaciones); quitar el bloque empresa+rol (queda en topbar); añadir MobileNav
  (Sheet) + **Command Palette Cmd+K**.
- **Topbar** — *consolidar* — un único "cambiar empresa" (EmpresaSwitcher); ThemeToggle al UserMenu; **campana de alertas**
  con badge; disparador de Cmd+K.
- **Dashboard/Inicio** — *rediseñar* — **bento con héroe** recaudación/parte_empresa (hoy descartado); eliminar
  redundancia KPI↔card de licencias; KPIs clicables (conflictos→Recaudaciones filtrado, capital→Deudas); **alertas
  con deep-link**; componer por rol.

**Operación**
- **Recaudaciones (lista)** — *rediseñar entorno* — **absorber /conflictos**; separar el dropdown Estado en dos ejes
  (ciclo de vida vs salud-conflicto); paginación + aviso de truncado (hoy `limit 200` silencioso); fila de totals Σ
  tabular; 🐞 **corregir zona horaria** del filtro (usa UTC); acciones en Cmd+K.
- **Recaudación (detalle)** — *rejerarquizar* — Cifras (Neto/parte_empresa) y conflicto arriba; auditoría técnica a
  acordeón; conflicto con **delta explícito** (cliente−servidor, icono+texto); back contextual; revisar PII de la firma
  (admin-only); limpiar `revalidatePath('/conflictos')` al eliminar esa ruta.
- **Deudas (índice)** — *rediseñar* — 🐞 mover "Capital en la calle" de cálculo **client-side** a **RPC SQL (SSOT)** y
  promoverlo a KPI del Inicio; tabla densa con contexto cruzado; degradar locales a 0; revalidar `/deudas`.
- **Deudas (ficha local)** — *rediseñar* — sacar el override de % a Administración › Ajustes; exigir motivo en "Condonar"
  (irreversible); ledger enlaza a su recaudación + paginar/exportar; acciones en Cmd+K.
- **Conflictos** — *eliminar* — ver T-4.

**Inventario** (las 4 listas comparten T-5 + T-7 + drawers)
- **Máquinas** — *rediseñar* — 🐞 igualar RBAC lista/detalle; **columna "Local actual"**; promover averías a columna
  ordenable + filtro; orden + paginación; degradar Fabricante/Valor a opcionales; buscar por modelo; Cmd+K.
- **Licencias** — *rediseñar* — count de instalaciones por licencia; **columna caducidad** ("caduca en N días" + banner
  "N caducan en 30 días"); RBAC; alta/edición a **drawer**; combobox de CCAA.
- **Locales** — *rediseñar/fusionar* — columnas Máquinas activas + Deuda (contexto cruzado); fila clicable; **fusionar
  /nuevo en drawer**; sección "Instalaciones" en el detalle; buscar por titular/dirección.
- **Instalaciones** — *rediseñar* — promover Tasa/%local + **Ingresos acumulados** (RPC) al listado; columnas Local/
  Licencia navegables; KPI de explotación en el detalle; estado de boletín; "Cerrar" como acción de fila; badge "activa"
  en neutro (no verde).

**Analítica + Administración + Auth**
- **Informes** — *rediseñar* — recolorar series a la paleta (petróleo destacado, resto neutro); posicionar como
  drill-down del KPI del Inicio; toggle gráfica/tabla; **export CSV**; drill-down cruzado; comunicar el top-12 truncado.
- **CambiosPlaca** — *mover* — del dominio Operación a **Administración/Auditoría**; ajustar RBAC (no técnico); lightbox
  de foto; contador anterior + salto calculado; columnas de contexto cruzado; paginar.
- **Equipo** — *limpiar* — quitar item muerto "cambia rol desde la columna"; Estado como badge neutro; 🐞 resolver Email
  server-side (placeholder); modelar invitaciones pendientes (reenviar/revocar); invitar en Cmd+K.
- **Auditoría** — *completar* — **detalle JSON** (drawer/fila expandible); paginación; 🐞 zona horaria de empresa; id de
  entidad clicable; export CSV.
- **Ajustes (web)** — *rejerarquizar* — 3 bloques (Identidad fiscal · Parámetros de cálculo · Textos del ticket); quitar
  jerga de infra del texto visible; cross-link de % recuperación a Deudas; guardado por bloque para parámetros sensibles.
- **Login/Registro** — *huecos* — "olvidé contraseña"; consumir `?registrado=1`; toggle contraseña; sanitizar `?next`
  (anti open-redirect); medidor de fortaleza; estado vacío accionable tras el alta.

---

## 4. Arquitectura de información objetivo

### Android
```
NAV DE PULGAR (barra inferior)            TOP BAR (global): 🔔 Alertas(badge) · ↻ Sincronizar
├─ Locales (hub; LocalCard con contexto)
│   └─ Local → CTA "Recaudar todas" + máquinas
│        └─ Máquina (detalle: contador base, deuda, 3 operaciones de campo: Recaudar · Reportar avería · Cambiar placa)
│             └─ RECAUDACIÓN: Contadores(teclado num.) → [OCR] → Denominaciones(keypad in-app) → Confirmar(firma)
├─ Histórico (+ detalle/reimpresión)
├─ Gestión (gestor+): [Inventario: Licencias·Máquinas·Locales·Instalaciones]  +  [Deudas = centro de mando, destacado]
└─ Ajustes (config pura: Cuenta · Empresa+cambiar · Impresora · Sesión)
```
Desaparecen: el menú ⋮ sobrecargado, los atajos de Ajustes, el feature aislado `cambio_placa`. (Las **dos** pantallas
"Locales" —operativa de recaudar y CRUD de Gestión— **se mantienen** separadas por decisión de producto; solo se aclara el nombre.)

### Web
```
SIDEBAR (RBAC)                                        TOPBAR: EmpresaSwitcher · 🔔Alertas · UserMenu(+tema) · Cmd+K
├─ Inicio        → Dashboard BENTO (héroe recaudación/parte_empresa, alertas deep-link, KPIs clicables)
├─ Operación     → Recaudaciones (incl. vista estado=conflicto) · Deudas (índice + ficha)
├─ Inventario    → Máquinas · Licencias · Locales · Instalaciones   (+columnas de contexto cruzado, alta/edición en drawer)
├─ Analítica     → Informes (drill-down del Inicio, export)
└─ Administración→ Equipo · Auditoría · Cambios de placa · Ajustes
```
Desaparece: la sección Conflictos. Aparece: Command Palette Cmd+K transversal.

---

## 5. Bugs y deuda técnica detectados (aparte del rediseño)
- 🐞 **Splash Android**: offline + sesión persistida → spinner infinito (`Failure`≡`Loading`); sin caché de membresías.
- 🐞 **Capital en la calle (web)** calculado client-side con decimal.js → debe ser **RPC SQL (SSOT)**.
- 🐞 **Zona horaria** en filtros de fecha de Recaudaciones y Auditoría (usan UTC, no la zona de la empresa).
- 🐞 **Truncado silencioso** (limit 200/300) en Recaudaciones, Cambios de placa, Auditoría, ledger → paginar/avisar.
- 🐞 **Email (Equipo)** muestra placeholder (pendiente resolución server-side, T-71).
- 🐞 **Código muerto**: `eliminar()/cerrar()` en InstalacionesGestor; `localizedError()` en varias listas.
- 🐞 **revalidatePath** inconsistente en acciones de Deudas (no revalidan `/deudas`).

## 6. Huecos funcionales (faltan; no es reubicación)
Recuperar contraseña (Login web+android) · verificación email + confirmar contraseña + T&C (Registro) · detalle JSON +
paginación + export CSV (Auditoría) · subida de logo (Ajustes web) · invitaciones pendientes reenviar/revocar (Equipo) ·
avisos de caducidad de licencias · estado vacío accionable de onboarding tras el alta.

## 7. Cómo seguimos
1. **Validas/ajustas** los patrones transversales (§1) y las decisiones por pantalla (§2–§3). Tu criterio de campo manda
   en lo dudoso.
2. Decidimos qué **bugs** (§5) se arreglan dentro del rediseño y cuáles van como tareas separadas.
3. Con la IA y los patrones cerrados, pasamos a **Fase 2 (diseño UI/UX)** pantalla por pantalla sobre *Confianza
   Industrial*, ya con la estructura correcta debajo.
