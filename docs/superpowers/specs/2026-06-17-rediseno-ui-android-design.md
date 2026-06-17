# Rediseño UI/UX de la app Android — "Confianza Industrial" aplicada de verdad

Fecha: 2026-06-17 · Estado: borrador para revisión · Alcance: app Android (técnico), las 28 pantallas.

## 1. Contexto y diagnóstico

La app **ya tiene una identidad propia decidida** (`.kiro/specs/recre/visual-identity.md`: **"Confianza Industrial"**, acento azul petróleo, fuente Geist, "el dato es el héroe"). Color y tipografía están implementados al 100% y son distintivos (nivel N26/Qonto). **Pero la app se ve genérica** por tres motivos, y este rediseño los ataca:

1. **Las pantallas esquivan la identidad.** Existe una librería propia de ~24 componentes (`AppCard`, `RecreButton`, `Field`, `SearchField`, `RecreShell`, `FilterChip`, `Skeleton`, `Sparkline`…) que **casi no se usa**: las 28 pantallas se montan con Material 3 pelado (`Scaffold + TopAppBar + LazyColumn de Card + OutlinedTextField + Button`). `AppCard`, `RecreButton`, `SearchField`, `RecreShell` tienen **cero usos** en pantallas.
2. **La base del sistema está a medias.** No hay `Shape.kt` (radios 12/16/20 sin aplicar → Material usa sus esquinas), no hay tokens de espaciado, y el *motion de firma* (count-up, flash al cuadrar, shake en descuadre, pulso offline) está documentado pero sin implementar.
3. **El flujo de recaudación** (lo que el técnico más usa) tiene la UX más floja e incoherente (p. ej. contadores con teclado del sistema, denominaciones con teclado propio).

**Objetivo:** una app **lista para vender**, con identidad propia coherente en **todas** las pantallas. **No es maquillaje** (cambiar un átomo Material por su wrapper no basta): es terminar la base, reconectar la librería y **re-componer cada pantalla** alrededor de "el dato es el héroe".

**Principio rector del rediseño:** no se toca dominio, datos ni lógica de negocio. Cada pantalla **conserva su comportamiento y sus datos**; cambia su composición visual y su jerarquía.

## 2. Principios de diseño (las 5 reglas bloqueadas)

Validadas con el usuario sobre las pantallas bandera (Denominaciones, Locales). Se aplican a TODAS las pantallas:

1. **El dato es el héroe.** Arriba siempre la cifra protagonista, grande y en Geist Mono tabular (€ apagado, dígito fuerte). El resto orbita.
2. **Un solo acento.** El petróleo (`#0E7490`; cian `#2BC4DD` en oscuro) aparece **solo** en la acción primaria, el ítem seleccionado, el foco y la pestaña activa. Nada más es azul.
3. **Estado = icono + texto + color**, nunca solo color. Verde = dinero positivo/cuadra; rojo = error/avería/descuadre; ámbar = pendiente/offline. Bloqueados a su significado.
4. **Tarjeta con borde 1px, no sombra ni gris plano.** Seleccionada/activa = borde petróleo. Sombras solo en overlays (menús, diálogos).
5. **Entrada propia.** Teclado in-app (`Keypad`) para toda cifra; **nunca** el IME del sistema. Coherente en todo el flujo.

## 3. Fundamentos a completar (sistema de diseño)

Sin esto no hay carácter, solo color. Es la **Fase 0**.

- **`ui/theme/Shape.kt` (nuevo)** — `RecreShapes` con radios de marca: small/medium 12dp, cards 16dp, sheets/diálogos 20dp, y `PillShape` (50%) para chips/badges. **Cablear en `Theme.kt`** (`MaterialTheme(shapes = RecreShapes, …)`), que hoy no pasa `shapes`.
- **`ui/theme/Spacing.kt` (nuevo)** — rejilla 4/8/12/16/24/32 como tokens (`RecreSpacing`), expuesta por CompositionLocal igual que `RecreColors`. Padding de tarjeta 16dp; separaciones 8/12dp.
- **Motion de firma** (`Motion.kt`) — materializar las animaciones de producto declaradas: `countup` 600ms (importe/neto que sube), `success-flash` 900ms (al cuadrar/sincronizar), `danger-shake` 400ms (descuadre), `offline-pulse` 1600ms, `sync-spin` 900ms; easing de marca `cubic-bezier(0.2,0,0,1)`. Respetar reduced-motion.
- **Reconexión de componentes (regla dura).** En pantallas queda **prohibido** instanciar `Card`, `OutlinedTextField`, `Button/OutlinedButton/TextButton`, `TopAppBar` de Material pelados. Se usan los wrappers propios (`AppCard`, `Field`/`SearchField`, `RecreButton`, `RecreShell`). Los wrappers de feature ya existentes (`locales/components/LocalCard`, `MaquinaCard`, `gestion/components/FormFields`, `recaudacion/components/CifrasResumenCard`) pasan a apoyarse en los componentes propios → varias pantallas heredan identidad de golpe.

### 3.1 Mapa de componentes existentes (qué se hace con cada uno)

Casi ninguno se usa hoy en pantallas; el rediseño **los enchufa** (y extiende unos pocos para las interacciones nuevas). No se tira ninguno: son las piezas del molde.

| Componente | Rol en el rediseño | Acción |
|---|---|---|
| `RecreShell` | Cabecera de todas las pantallas (P1) | **Conectar** (+ variante home con marca) |
| `AppCard` | Tarjeta-entidad base (P3) | **Extender** (seleccionable + slot chip flotante) |
| `RecreButton` | Sustituye `Button`/`OutlinedButton`/`TextButton` | **Conectar** (+ press scale/ripple) |
| `Field` / `SearchField` | Sustituyen `OutlinedTextField` (P5/P4) | **Conectar** |
| `FilterChip` | Filtros de las listas (P4) | **Conectar** |
| `StatusChip` | Estado = color+texto en cada tarjeta (P3) | **Conectar** (parcial→total) |
| `MoneyText` | Toda cifra (el "dato héroe", P2) | **Conectar** + count-up |
| `Keypad` | Entrada numérica en todo el flujo (P6) | **Extender** (contadores, cobros) |
| `Skeleton` | Carga (shimmer) | **Conectar** (reemplaza spinners) |
| `EmptyState` / `ErrorState` | Estados vacío/error (P7) | **Conectar** |
| `Sparkline` | Mini-tendencia en histórico/deudas | **Conectar** |
| `SharedTransition` | Motor de transiciones entre pantallas (M1) | **Conectar** |
| `RecreSnackbar` | Confirmaciones/errores (M5) | **Conectar** |
| `IconAction` / `SyncControl` / `OfflineBadge` | Acciones e indicadores de la barra (P1) | **Conectar** |
| `Collapsible` / `Tooltip` / `SegmentedControl` / `StepIndicator` | Plegables, ayudas, toggles, pasos | **Conectar**/parcial→total |
| `Motion` (tokens) | Vocabulario de la capa de movimiento | **Extender** (Fase 0) |

## 4. Patrones compartidos (el molde nuevo)

El rediseño define **un puñado de patrones** y los aplica. Esto es lo que hace que el CRUD se "reskinee solo".

- **(P1) Chrome de app — `RecreShell`.** Cabecera propia. En pantallas top-level (tabs): marca "Recre" con el acento + acciones (sync `SyncControl`, incidencias ⚠, campana 🔔). En secundarias: título + back. Sustituye al `TopAppBar` gris en los 26 sitios.
- **(P2) Bloque héroe.** Cifra protagonista mono arriba según la pantalla: *Objetivo/Contado* (denominaciones), *X por recaudar* (locales), *neto* (confirmación), *importe* (histórico), *saldo* (deudas).
- **(P3) Tarjeta-entidad — `AppCard`.** Borde 1px; título = héroe de la fila; `StatusChip` (icono+texto+color) de estado; acción primaria `RecreButton` petróleo cuando procede. Es la fila de local, máquina, recaudación, alerta, incidencia, licencia, etc.
- **(P4) Patrón lista.** `RecreShell` + `SearchField` + `FilterChip` (filtros) + `LazyColumn` de (P3) + `Skeleton` al cargar / `EmptyState` / `ErrorState` propios. Reemplaza el "LazyColumn de Card Material".
- **(P5) Patrón formulario.** `Field` agrupados por secciones; `StepIndicator` si es multipaso; `RecreButton` primario único; validación inline. Reemplaza "Column de OutlinedTextField + Button".
- **(P6) Entrada numérica única — `Keypad`.** Toda cifra (contadores, denominaciones, importes de cobro) se teclea con el keypad in-app; la celda es un destino tappable, nunca un `TextField`/IME.
- **(P7) Estados** uniformes: `Skeleton` (carga), `EmptyState` (vacío), `ErrorState` (error con reintento). Nunca un `CircularProgressIndicator` pelado en medio de la pantalla.

## 5. Sistema de movimiento y feedback

La capa que hace que la app se *sienta* premium (no solo que se vea bien). Tokens en `Motion.kt` (Fase 0); motor de transiciones en `SharedTransition.kt` (ya existe, sin usar). Regla transversal: todo respeta `reduce-motion` y el feedback **nunca es solo movimiento** (siempre hay texto/color/icono).

- **(M1) Transiciones entre pantallas.** Elemento compartido (`SharedTransition`): la tarjeta/nombre de un local "viaja" y se expande hacia su detalle; la máquina elegida continúa al entrar a recaudar. Las secundarias entran/salen deslizando lateral; diálogos y hojas suben desde abajo. El flujo de recaudación (Contadores → Denominaciones → Confirmación) avanza con el `StepIndicator` animándose paso a paso.
- **(M2) Micro-interacciones.** Botón: press *scale* 0.97 + onda propia petróleo. Denominación: al seleccionar, el anillo petróleo **crece** (spring); el chip de cantidad aparece con **"pop"** (escala+fade) al pasar de 0. Teclado: respuesta visual **+ háptica** por tecla. Filtros/segmented: el indicador activo **se desliza**, no salta.
- **(M3) Feedback de resultado (firma de producto).**
  - **Count-up** (600 ms): *Contado*, *neto* e importes del histórico **suben contando** al cambiar o al responder el servidor.
  - **Success-flash** (900 ms): destello verde + check al **cuadrar / guardar / sincronizar**.
  - **Danger-shake** (400 ms): campo/tarjeta **vibra** en descuadre o validación fallida.
  - **Háptica**: pulso corto en éxito (guardar/cuadrar), patrón de error en fallo. Prioritaria en campo (sol/guantes/ruido) sobre el sonido.
  - **Offline-pulse** (1600 ms) / **sync-spin** (900 ms): badge offline que **late**, icono de sync que **gira** y cierra con success-flash.
- **(M4) Carga y vacíos.** `Skeleton` con shimmer (nunca un spinner pelado); cross-fade esqueleto→contenido. Listas: entrada en **cascada** suave la primera vez. `EmptyState`/`ErrorState` con ilustración y reintento.
- **(M5) Confirmaciones.** `RecreSnackbar` propio con color semántico para *guardada / subida / error* (no el snackbar gris de Material).
- **(M6) Accesibilidad y ritmo.** Easing de marca `cubic-bezier(0.2,0,0,1)`; 120–180 ms para lo funcional, las firmas (count-up/flash/shake) en sus duraciones propias. `reduce-motion` → fade corto o instantáneo. Targets grandes y alto contraste (sol directo, una mano, guantes).

## 6. Pantalla por pantalla

Cada ficha indica el/los patrones que le tocan y los movimientos específicos. Salvo las bandera, el grueso es **aplicar P1–P7**.

### 6.1 Flujo de recaudación (el corazón — máxima prioridad)

**ContadoresScreen** — captura de lecturas antes de contar. Hoy: `OutlinedTextField` + IME (incoherente). → **P6**: lectura en grande (mono), entrada con `Keypad` propio, una máquina/contador a la vez, sin IME. Cabecera P1, CTA único `RecreButton`. Unifica el lenguaje numérico con Denominaciones.

**EscanerContadoresScreen** — OCR con cámara. Ya es bespoke (full-screen). → aplicar el *chrome* y los botones del lenguaje (RecreButton, IconAction); confianza del OCR como `StatusChip` (state-neutral, nunca rojo).

**DenominacionesScreen** — contar el dinero. **Diseño bloqueado:**
- Rejilla **3×3** de las 9 denominaciones (0,10·0,20·0,50·1·2·5·10·20·50), **orden ascendente** → monedas arriba, billetes abajo. **Sin etiquetas** de grupo.
- **Tarjeta compacta** por denominación: solo el valor facial. **Chip de cantidad flotante** en la esquina superior derecha, **superpuesto sobresaliendo media altura** (badge que straddlea el borde); visible solo si cantidad > 0.
- Seleccionada = **borde petróleo**. `Keypad` propio anclado abajo, **siempre visible**; la rejilla se dimensiona para que **entren todas sin scroll**.
- Cabecera sticky con **héroe Objetivo/Contado** (mono) + barra de progreso + estado *cuadra / faltan / sobran* (verde/ámbar) con **count-up** al sumar y **success-flash** al cuadrar.
- Subtotal por tarjeta **fuera** (vive en el total de cabecera). Reutiliza `MoneyText` + `Keypad` ya integrados.

**ConfirmacionScreen** — resumen + firma + confirmar. Hoy: pila de cards. → **P2** con el **neto como héroe** (cifra grande mono + count-up al responder el servidor), desglose limpio debajo (no 3 cards grises), `SignaturePad` (ya propio) con esquina `shapes.medium`, **CTA único** `RecreButton`. Momento "confirmar dinero" = peso visual.

### 6.2 Núcleo diario

**LocalesScreen (home)** — **diseño bloqueado:** P1 con marca; **héroe "X por recaudar"**; `SearchField` + `FilterChip` (Por recaudar / Al día / Todos); tarjetas-entidad (P3) de local: nombre = héroe, `StatusChip` por recaudar (ámbar) / al día (verde-neutral), nº máquinas + última recaudación, y `RecreButton` **Recaudar** petróleo cuando procede; **pendientes primero**. Tab bar con Locales activo (petróleo).

**LocalDetalleScreen** — local + sus máquinas. Hoy: pila de cards. → P2 cabecera con el local y sus cifras clave; máquinas como tarjeta-entidad (P3) que lideran con **estado + acción** (Recaudar / Reportar avería). Menos "montón de cards".

**HistoricoScreen** — histórico. → **P4**: cada recaudación lidera con **importe (mono) + fecha + `StatusChip`** (firme/anulada/conflicto). Filtros como `FilterChip`, no cards.

**HistoricoDetalleScreen** — detalle de una recaudación pasada. Hoy: 6 cards grises. → P2 con el **importe como héroe** arriba, desglose y metadatos en secciones limpias (no 6 cards), acciones con `RecreButton`/`IconAction`.

### 6.3 Avisos

**AlertasScreen** y **IncidenciasScreen** — → **P4** con tarjeta-entidad (P3): icono+texto+color del estado, acciones honestas (las de Incidencias ya definidas: Reintentar/Rehacer/Descartar). `EmptyState` propio (hoy Incidencias ni lo usa).

### 6.4 Gestión (CRUD admin) — el bloque que se reskinea en bloque

Comparten un único molde → se diseñan **dos patrones y se aplican a las ~9**.

**GestionScreen (hub)** — tarjetas de menú con icono + acento (P3 ligero).

**LicenciasGestor / MaquinasGestor / LocalesGestor / InstalacionesGestor** — **P4** (lista): `SearchField`, tarjeta-entidad con los datos clave + `StatusChip`, FAB de alta como `RecreButton`/IconAction, borrado con diálogo propio.

**LicenciaForm / LocalForm / InstalacionForm / MaquinaForm** — **P5** (formulario): `Field` por secciones; los multipaso (`InstalacionForm`, `MaquinaForm`) conservan `StepIndicator` (ya propio) sobre `AppCard`.

### 6.5 Deudas

**DeudasGestorScreen** — → P4; **héroe = saldo total** (mono); filas de deuda como tarjeta-entidad.

**DeudasLocalScreen** — la más sobrecargada (6 `OutlinedTextField`, 9 `TextButton`, 4 diálogos). → P2 (saldo héroe) + P5 para el cobro (`Field` + `Keypad` para el importe) + reducir el ruido de inputs; acciones agrupadas.

### 6.6 Operaciones de campo

**ReportarAveriaScreen** — → **P5** (5 campos hoy en OutlinedTextField → `Field`); categoría como `SegmentedControl`/chips; CTA único.

**AveriasMaquinaScreen** — → **P4** lista de averías de la máquina con `StatusChip` (abierta/resuelta).

**CambioPlacaScreen** — → **P5** + **P6** (contadores nuevos con `Keypad`, no IME).

**ImpresoraScreen** — → P4/ajustes: filas de dispositivo + acciones (`RecreButton`); test de impresión con feedback de estado.

### 6.7 Ajustes y acceso

**AjustesScreen** — → **filas de ajuste** propias (no `Card` + `TextButton`); conserva `SegmentedControl` (tema). Agrupado por secciones.

**LoginScreen** — **momento de marca.** Hoy: formulario Material por defecto. → identidad: logotipo "Recre", fondo/acento petróleo, `Field` (usuario/clave) + `RecreButton`. Es la **primera impresión**.

**SeleccionarEmpresaScreen** — → tarjeta-entidad seleccionable (P3) por empresa/tenant.

**SinAccesoScreen** — → `EmptyState` con marca (no Column + Button pelado).

## 7. Identidad de marca (logo)

Mano libre dentro de "Confianza Industrial". Propuesta a iterar:
- **Wordmark "Recre"** en Geist, con un detalle del petróleo (p. ej. el punto de la "i"/acento, o un corte geométrico). Tono **industrial y sobrio**, no fintech-juguetón.
- **Monograma "R"** geométrico para icono de app y splash, sobre fondo petróleo (claro) / superficie-1 (oscuro).
- Se diseña como parte de la Fase 1 (Login/splash) y se valida aparte.

## 8. Alcance, fases y fuera de alcance

**Fases** (cada una entregable y QA-able por separado; el usuario instala el APK para QA visual):

- **Fase 0 — Fundamentos:** `Shape.kt`, `Spacing.kt`, vocabulario de movimiento (tokens M + `SharedTransition`), cableado en `Theme`, y reconexión de los wrappers de feature a componentes propios. Sin cambio visible aún salvo coherencia de esquinas/espaciado; la capa de feedback (M3) se aplica con cada pantalla en su fase.
- **Fase 1 — Flujo de recaudación:** Contadores, Denominaciones (3×3), Confirmación + marca/Login. Es el corazón y la pantalla más visible.
- **Fase 2 — Núcleo diario:** Locales (home), LocalDetalle, Histórico (+ detalle).
- **Fase 3 — Gestión + Deudas:** patrones P4/P5 aplicados al CRUD y a deudas.
- **Fase 4 — Resto:** Alertas, Incidencias, Ajustes, Operaciones de campo (avería, cambio placa, impresora), acceso (SeleccionarEmpresa, SinAcceso), Escáner.

**Fuera de alcance:** lógica de dominio, datos, edge functions, RLS, y la superficie **web** (back-office; es otra superficie con su propio lenguaje, aunque comparta tokens). No se cambian comportamientos ni flujos de navegación, solo composición visual.

**Riesgos y mitigación:**
- *Regresiones funcionales* → cada pantalla mantiene su `ViewModel`/estado; el rediseño es de composición. Validar con `assembleDebug` + QA manual (el usuario instala; firma distinta).
- *Accesibilidad* → los tokens ya traen contraste AA/AAA anotado; mantener "estado nunca solo por color".
- *Sol directo / una mano / guantes* → tamaños generosos, targets grandes, alto contraste (ya en la filosofía Confianza Industrial).
- *Alcance grande (28 pantallas)* → mitigado por los patrones compartidos: definidos una vez en Fase 0–2, aplicados en bloque en Fase 3–4.

## 9. Criterios de "hecho"

- Cero `TopAppBar`/`Card`/`OutlinedTextField`/`Button` de Material pelados en `feature/**`.
- `Shape.kt` + `Spacing.kt` cableados; motion de firma en las pantallas clave.
- Las 5 reglas visibles en cada pantalla (héroe, acento único, estado color+texto, tarjeta con borde, keypad propio).
- Denominaciones: 3×3, sin scroll, chip flotante, count-up/flash.
- Login con marca propia.
- **Movimiento/feedback**: transición de elemento compartido en el flujo diario; count-up/success-flash/danger-shake + háptica en recaudación; `Skeleton` en toda carga; `RecreSnackbar` en confirmaciones; `reduce-motion` respetado.
- Sin regresiones funcionales (comportamiento idéntico).
