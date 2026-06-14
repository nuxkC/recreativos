# Plan de diseño UI/UX — Recre 2026

> Estado: **propuesta para aprobación**. Capa de **sistema de diseño (motion · tipografía · forma · componentes) +
> roadmap**. **Fuentes de verdad separadas** (este doc las referencia, no las duplica): identidad/paleta →
> **`visual-identity.md`** (decidida: *Confianza Industrial*, azul petróleo); arquitectura de información →
> **`functional-audit-and-ia.md`** (auditoría profunda, autoritativa). Toda cifra económica sigue siendo **SSOT
> servidor**; el rediseño solo toca presentación e interacción.

## 0. Tesis

La app es funcional y cuadra contra la BBDD. El problema no es *qué hace*, sino *cómo se ve y se organiza*: hoy
es un MVP con Material3 / shadcn por defecto, navegación por menús-cajón, formularios largos sin jerarquía y
entrada de datos contra el teclado del sistema. El objetivo es llevarlo a **nivel producto de millones de usuarios**
sin reescribir la lógica: un **sistema de diseño con tokens**, una **arquitectura de información reorganizada**, un
**lenguaje de motion** coherente y la **ergonomía de campo** (teclado in-app, thumb-zone, offline-first).

Las tendencias 2026 que adoptamos son las de **madurez, no moda**: el motion como lenguaje (física de muelles, no
decoración), color perceptual (OKLCH), accesibilidad como requisito regulatorio (WCAG 2.2 / European Accessibility
Act, conformidad plena 2030) y densidad de datos honesta en el back-office. Lo que **evitamos**: sobre-animación
teatral, apilar "morphisms", autoplay, y estética sin función que envejece mal.

---

## 1. Dos superficies, un sistema

| | **android/** — app de campo del técnico | **web/** — back-office |
|---|---|---|
| Usuario | Técnico en la calle, una mano, sol directo, cobertura mala | Gestor/contable en escritorio, alta densidad de datos |
| Prioridad | Ergonomía, pocos taps, legibilidad, offline-first | Información por pantalla, navegación rápida, claridad |
| Base | Jetpack Compose + **Material 3 Expressive** | Next.js + Tailwind + shadcn/ui + **Motion** |
| Motion | `MotionScheme.expressive()` (muelles físicos) | `motion` (ex Framer Motion) + layout/AnimatePresence |

Ambas comparten **los mismos tokens semánticos** (mismos roles de color, misma escala tipográfica, mismos tiempos de
motion 200–500 ms) materializados en cada plataforma. Un técnico y un gestor deben sentir que es **el mismo producto**.

---

## 2. Fundamentos del sistema de diseño (design tokens)

### 2.1 Color — migración a OKLCH, roles funcionales

Hoy: web ya usa `oklch()` en `globals.css` (bien); android usa una paleta Slate/Indigo *hardcodeada* en `Color.kt`
(Material3 plano). **Unificamos** sobre una paleta OKLCH perceptualmente uniforme, con **luminancia como eje de
elevación** y **chroma ≤ 0.15 (sRGB-safe)** con fallback P3 opcional.

Principios:
- **Dark mode sobre gris, no negro puro** (`oklch(0.18–0.22 …)` para superficies, no `#000`): elevación por capas de
  luminancia, sin halo/vibración.
- **Paleta restringida y funcional**: 1 primario (acción), 1 acento, y los **estados semánticos** como ciudadanos de
  primera clase (no rojo genérico).
- **Estados color-coded** (clave para el técnico en campo, legibles de un vistazo):
  - `success` / verde — completado, cuadra, recaudación procede
  - `warning` / ámbar — en progreso, próximo a caducar, descuadre menor
  - `danger` / rojo — avería, conflicto, descuadre que bloquea
  - `info` / azul — informativo, sincronizando

> ⚠️ **Paleta definitiva = `visual-identity.md` → "Confianza Industrial" (azul petróleo).** No se duplica la tabla
> completa aquí (evita deriva). Tokens clave (light / dark), expresables en OKLCH con chroma ≤ 0.15:

| Rol | Light | Dark | · | Rol | Light | Dark |
|---|---|---|---|---|---|---|
| background | `#FAFBFC` | `#0B0C0E` | · | success | `#0E8A55` | `#34D399` |
| surface-1 | `#FFFFFF` | `#131519` | · | warning | `#B45309` | `#FBBF24` |
| surface-2 | `#F4F6F8` | `#1B1E24` | · | danger | `#DC2626` | `#F87171` |
| **primary** | **`#0E7490`** | **`#2BC4DD`** | · | info | `#2563EB` | `#60A5FA` |
| border | `#E3E6EA` | `#262A31` | · | muted | `#646B76` | `#9AA1AD` |

> Regla de a11y: contraste ≥ **4.5:1** texto normal, ≥ **3:1** componentes y foco. **Nunca** comunicar estado solo
> por color: icono + texto siempre (≈350M personas con deficiencia de visión de color). Glass/blur deben tener
> superficie sólida alternativa cuando el sistema pide reducir transparencia.

### 2.2 Tipografía — jerarquía estricta desde el body

- **Web**: ya usa **Geist** (Sans + Mono) — excelente, lo mantenemos. `tabular-nums` obligatorio en toda cifra
  monetaria y contador (alineación de columnas).
- **Android**: hoy `Typography()` default sin fuente propia. Adoptar **una fuente variable workhorse** (Inter o la
  propia Geist en `/res/font`) y definir una escala explícita (display/headline/title/body/label) en `Type.kt`,
  con el **KPI dominante** (importe de recaudación) en el peso/tamaño mayor.
- Escalas fluidas con `clamp()` en web; reservar *kinetic type* (peso animado) para momentos puntuales, **nunca en
  tablas/datos densos**.

### 2.3 Espaciado, forma y elevación

- Escala de espaciado 4/8/12/16/24/32. Radios por token (`sm/md/lg`), ya presente en web; replicar en `Shapes` de
  Compose.
- **Glassmorphism solo funcional** (separar capas: sticky headers, barra de total del keypad, command palette) con
  fallback sólido. **Neumorphism**: descartado salvo, como mucho, un toggle puntual.
- Elevación por **luminancia**, no por sombras duras (sobre todo en dark).

### 2.4 Motion — tokens compartidos

Un único vocabulario de movimiento, materializado por plataforma. **Solo se anima lo que aclara, guía o confirma**;
200–500 ms; respetar siempre `prefers-reduced-motion` / "remove animations".

| Token | Web (`motion`) | Android (`MotionScheme.expressive()`) | Uso |
|---|---|---|---|
| `spatial.fast` | spring stiffness alta | `fastSpatialSpec()` | feedback de tap, chips |
| `spatial.default` | spring media | `defaultSpatialSpec()` | entrada de cards, layout |
| `effects.default` | tween 200–300 ms | `defaultEffectsSpec()` | color/opacidad, crossfade |

---

## 3. Sistema de motion por plataforma

### 3.1 Android — Material 3 Expressive (requiere Compose BOM ≥ 2025.10.00, ideal 2025.12.00)

- **Tema raíz**: `MaterialExpressiveTheme { motionScheme = MotionScheme.expressive() }`. Todas las animaciones leen
  de `MaterialTheme.motionScheme` (no springs ad hoc).
- **Listas** (locales, máquinas, recaudaciones, instalaciones): `Modifier.animateItem()` con `key = id` estable →
  inserción/borrado/reordenación animados.
- **Acciones rápidas**: `SwipeToDismissBox` (completar/averiar/archivar) con `backgroundContent` + haptic en el umbral.
- **Refresco**: `PullToRefreshBox` tras sync de WorkManager; indicador con `LoadingIndicator` Expressive.
- **Cargas**: **skeletons** (`valentinilk/compose-shimmer` o `eygraber/compose-placeholder-material3`) en vez de
  spinner a pantalla completa; `ContainedLoadingIndicator` para esperas cortas (< 5 s) como `calcular-recaudacion`.
- **Feedback de éxito/error con haptics**: `HapticFeedbackType.Confirm` al persistir (recaudación guardada,
  instalación creada), `Reject` en validación/avería. `Confirm` es el tipo más fiable entre dispositivos (no depender
  de `ToggleOn/Off`, silenciosos en algunos Pixel).
- **Transición lista→detalle**: `sharedBounds` en la card, `sharedElement` en la foto de máquina (envuelto en
  `SharedTransitionLayout`). ⚠️ **Experimental** (`@OptIn(ExperimentalSharedTransitionApi)`): aislar el opt-in.
- **Back animado**: `PredictiveBackHandler` con progreso en flujos largos (recaudación); `enableOnBackInvokedCallback`
  en manifest. Habilitar/deshabilitar callbacks según `UiState`.

### 3.2 Web — Motion + shadcn (sin upgrade de stack)

- **`motion` 12.x** (importar de `motion/react`, **no** `framer-motion`): la única pieza moderna que **no requiere
  upgrade** (funciona en React 18.3). En tablas grandes: `LazyMotion` + componente `m` (≈4.6 KB), animar **solo
  transform/opacity**, `layout` (FLIP) para reordenar/filtrar filas.
- **`MotionConfig reducedMotion="user"`** envolviendo la app; quitar clases `transition-*` de Tailwind en elementos
  que anime Motion (evita conflictos).
- **Command palette `cmdk`** (Cmd/Ctrl+K) para navegación rápida del back-office denso.
- **Drawers `vaul`** para detalle/edición responsive.
- **Toasts `sonner`** con `toast.promise` envolviendo llamadas a Edge Functions (pending→success/error).
- **`@number-flow/react`** para importes/KPIs que cambian — **solo presentación** del valor ya calculado server-side.
- **Bloqueado hasta upgrade** (React 19 + Next 15.2+): `useOptimistic` nativo y React **View Transitions**. Mientras
  tanto, estado optimista con **react-query** (`onMutate`/rollback), que el repo ya usa. Ver `dependency-upgrade-plan.md`.
- ❌ **No** meter `aceternity`/`magic-ui`/Three.js en pantallas de datos (bundle + ruido); reservados para marketing.

---

## 4. Arquitectura de información — reorganización

> Premisa del encargo: "algunas pantallas no tienen sentido donde están".
>
> ⚠️ **La IA autoritativa y al día es `functional-audit-and-ia.md` (auditoría profunda, 548 funcionalidades).** Este
> resumen es complementario; donde discrepe, **manda la auditoría**. Ya alineado abajo: Conflictos se elimina como
> sección, Cambios de placa va a Administración, Ajustes no lleva "Atajos", Deudas se promueve como centro de mando.

### 4.1 Android — de menú-cajón a navegación de pulgar

**Problema actual**: `LocalesScreen` mete en el overflow del `TopAppBar` *ajustes, impresora, histórico, alertas,
gestión* — todo escondido tras un menú, lejos del pulgar. `SeleccionarEmpresa` y `Ajustes` mezclan conceptos.

**Después — Bottom Navigation (thumb-zone) con 4 destinos + FAB**:

```
┌───────────────────────────────────┐
│  Recre            🔔(badge)  ⋯     │  top app bar mínima (empresa + alertas)
│                                   │
│   ░░░ contenido ░░░               │
│                                   │
│                      ╭─────╮      │
│                      │  +  │ FAB  │  acción primaria contextual (Recaudar)
│                      ╰─────╯      │
├───────────────────────────────────┤
│  🏠 Locales  🕘 Histórico  📊 Gestión  ⚙︎ Ajustes │  bottom nav
└───────────────────────────────────┘
```

| Hoy (overflow menu) | Después |
|---|---|
| Locales (hub) | **Locales** — destino primario (inicio), FAB "Recaudar todas" |
| Histórico (en menú) | **Histórico** — destino de bottom nav (con date-range + export) |
| Gestión (en menú) | **Gestión** — destino de bottom nav (técnico/gestor): grupo **Inventario** (máquinas · locales · licencias · instalaciones) + **Deudas** promovido como **centro de mando** destacado (no una pestaña más) |
| Ajustes (en menú) | **Ajustes** — destino de bottom nav, **config pura** en tabs: Cuenta · Empresa (+cambiar empresa) · Impresora · Sesión. **Sin "Atajos"** (Histórico/Alertas son destinos propios) |
| **Impresora** (en menú) | → movida **dentro de Ajustes** (config de hardware, no navegación primaria) |
| **Alertas** (en menú) | → **icono campana en top bar** con badge (no merece un slot de nav) |
| **Seleccionar empresa** (pantalla) | → accesible desde **Ajustes › Empresa** (switch) + sigue en el arranque |

Otros movimientos:
- **Averías**: hoy se llega desde `LocalDetalle → máquina → AveriasMaquina → Reportar`. Mantener, pero exponer
  "Reportar avería" como **acción de swipe** sobre la `MaquinaCard` y como atajo. Convertir `ReportarAveria` en
  **wizard de pasos** (tipo → descripción/foto → recambios) con validación por campo y preview de imagen.
- **Formularios largos** (`MaquinaForm`, `LicenciaForm`, `InstalacionForm`): a **wizard** si > 6 campos, con
  validación en tiempo real y date-range pickers (fin > inicio, auto-poblar caducidad).
- **`SinAcceso`**: dejar de ser callejón sin salida → botón "Comprobar de nuevo" / pull-to-refresh.
- (Idea abierta, **no confirmada** por la auditoría profunda, que mantiene "instalación") valorar microcopy más claro
  sin renombrar el término de dominio. Decidir en Fase 2.

### 4.2 Web — sidebar afinada + command palette + dashboard bento

**Sidebar (5 secciones, RBAC)** — reagrupada por *intención*, no por entidad:

| Sección | Contiene | Cambios clave |
|---|---|---|
| **Inicio** | Dashboard | → **bento grid** con KPI dominante (recaudación/`parte_empresa`), alertas como *feed* con acción rápida |
| **Operación** | Recaudaciones · **Deudas (Centro de Mando)** | **Conflictos se elimina como sección** → vista filtrada `estado=conflicto` de Recaudaciones + KPI en Inicio. Averías = parte del detalle de máquina, no sección |
| **Inventario** | Máquinas · Licencias · Locales · Instalaciones | +columnas de **contexto cruzado**; alta/edición en **drawer**. **Cambios de placa se mueve a Administración** (es auditoría) |
| **Analítica** | Informes | gráficos interactivos con drill-down + export CSV |
| **Administración** | Equipo · Auditoría · **Cambios de placa** · Ajustes | Auditoría con detalle JSON en drawer + paginación + export; Cambios de placa con RBAC admin |

Transversal:
- **Command palette (Cmd/Ctrl+K)** con `cmdk`: saltar a cualquier máquina/local/recaudación/instalación, ejecutar
  acciones rápidas. Es la mejora de navegación de mayor ROI en un back-office denso. ARIA explícito; server-side keys.
- **Conflictos (sin sección propia)**: vista filtrada de Recaudaciones; la **resolución** vive en el detalle de la
  recaudación con **delta cliente−servidor** explícito (icono+texto). KPI accionable "N pendientes" en Inicio.
- **Deudas = Centro de Mando** (ya es el centro de mando completo, ver memoria del proyecto): desglosar el saldo en
  **3 tarjetas visuales** (tolva · préstamos · capital en la calle), flujos (nuevo crédito/pago/condonación) como
  **tabs o cards colapsables**, y **% recuperación** como KPI destacado.

---

## 5. ⭐ Pantalla insignia: extracto de denominaciones con keypad in-app

> Este es el **requisito fijo** del encargo y el mayor salto de ergonomía. Aplica a `DenominacionesScreen`
> (android, `feature/recaudacion/denominaciones/`), **exclusivo del extracto de denominaciones** (modos Total y Local);
> el resto de campos numéricos de la app usan el teclado numérico/decimal del sistema. En web es **solo lectura**
> hoy (`DesgloseTable`); la entrada vive en móvil — el keypad es **android-first**.

### 5.1 Problema actual

Lista de `OutlinedTextField`, uno por denominación (`DENOMINACIONES_PERMITIDAS`: 0.10…100.00), con
`KeyboardType.Number` que **abre el teclado QWERTY del sistema**: 30+ taps para un desglose grande, el teclado
aparece/desaparece y **tapa casillas**, sin presets ni entrada en bloque, el formulario se repite si hay split local.

### 5.2 Diseño objetivo — keypad propio PERSISTENTE anclado al fondo

```
┌─────────────────────────────────────┐
│  Desglose · Total objetivo 1.234,50 €│  ← header sticky
├─────────────────────────────────────┤
│  BILLETES                            │
│   100 €   × [ 3 ]        300,00 €    │
│    50 €   × [ 12 ]       600,00 €    │  ← fila activa resaltada
│    20 €   × [ 5 ]        100,00 €    │     (auto-scroll para que se vea)
│    10 €   × [ 0 ]          0,00 €    │
│   ── subtotal billetes   1.000,00 € ──│
│  MONEDAS                             │
│     2 €   × [ … ]                    │  (lista scrollea sobre el keypad fijo)
├─────────────────────────────────────┤
│  TOTAL  1.000,00 €   ✓ cuadra        │  ← barra de total SIEMPRE visible
├─────────────────────────────────────┤
│   [ 1 ] [ 2 ] [ 3 ]                  │
│   [ 4 ] [ 5 ] [ 6 ]   keypad 3×4     │  ← anclado al fondo (thumb-zone)
│   [ 7 ] [ 8 ] [ 9 ]   teclas 48dp    │
│   [ ⌫ ] [ 0 ] [ Siguiente → ]        │
└─────────────────────────────────────┘
```

**Interacción** (una acción = un tap, el keypad nunca desaparece):
1. El usuario **toca una fila** → la denominación se resalta como activa.
2. **Teclea la cantidad** (entero) en el keypad → subtotal de fila y **TOTAL se actualizan en vivo**.
3. **"Siguiente →"** mueve el foco a la fila contigua (auto-scroll para mantenerla visible). El keypad y la barra de
   total **nunca quedan ocultos**.

**Estructura de tres niveles** (réplica de la hoja de arqueo, auditable): valor extendido por fila
(`cantidad × valor facial`), subtotales por grupo (billetes/monedas), **total general fijo abajo** con indicador
✓ cuadra / descuadre (icono + texto + color, nunca solo color).

### 5.3 Distribución de TODO lo que hay (modos Total y Local)

El keypad es solo una pieza. Esta es la **redistribución completa** de las 16 funcionalidades actuales. La pantalla se
reutiliza para los dos modos (un solo Composable parametrizado): **Total** (objetivo = `bruto`; cuentas TODO el efectivo
retirado) y **Local** (objetivo = lo que se *entrega* al local = `parte_local − recuperación de deuda`).

**Regiones (arriba → abajo), comunes a ambos modos:**
1. **Cabecera** — ← con confirmación *"¿Descartar el conteo?"* si ya hay piezas (hoy se pierde sin aviso) · **título que
   aclara qué montón cuentas** ("Desglose · efectivo total" vs "Entrega al local" — resuelve la jerga tolva/merma) · paso N/total.
2. **[solo modo Local] Cabecera fija "Recuperación"** — la `RecuperacionResumenCard` (hoy un item que scrollea y
   desaparece bajo las denominaciones) pasa a **cabecera fija colapsable, solo-lectura**: "Retenido para deuda X €" ·
   "Se entrega al local Y €". Explica por qué el objetivo es `pagado_local`.
3. **Lista de denominaciones** (scroll, en el medio) — fila = importe · **cantidad** (celda *readonly* que dirige el
   keypad) · **subtotal** (cantidad × valor, tabular-nums). La fila **activa** se resalta (el foco es el *único* acento
   petróleo además del CTA). En *nada que entregar* (Local con objetivo 0) la lista se sustituye por texto explicativo.
4. **Bloque de progreso persistente** (sobre el keypad, **nunca tapado**) — los 3 números clave juntos en tabular-nums,
   el **dato como héroe**: **Objetivo** · **Total contabilizado** · **Estado** (`✓ Cuadra` verde+check / `⚠ Faltan X €` /
   `⚠ Sobran X €`, icono+texto+color — ya **distingue falta de sobra**, hoy solo el signo en rojo). A la derecha, el
   **CTA "Continuar"** (petróleo) habilitado **solo si cuadra**.
5. **Keypad in-app** anclado al fondo (§5.2).

**Modo Local** (con la cabecera de recuperación fija):
```
┌─────────────────────────────────────┐
│ ←  Entrega al local          Paso 3/4│  título aclara "lo que se entrega"
├─────────────────────────────────────┤
│ ▸ Recuperación (fija, solo lectura) │  RecuperacionResumenCard ya no scrollea
│   Retenido 120,00 € · Entrega 380 € │  la reordenación de deuda se hizo ANTES
├─────────────────────────────────────┤
│  50 €   [  6 ]            300,00 €   │  lista de denominaciones (igual que Total)
│   …                                 │
├─────────────────────────────────────┤
│ Objetivo 380,00 € · Total 300,00 €  │  progreso persistente
│ ⚠ Faltan 80,00 €      [ Continuar ] │
├─────────────────────────────────────┤
│            … KEYPAD 3×4 …            │
└─────────────────────────────────────┘
```

**Mapa elemento por elemento (lo que hay → dónde va → qué cambia):**

| # | Hoy | Dónde va | Cambio |
|---|---|---|---|
| 1 | Lista 9 denominaciones + `OutlinedTextField` | Región 3 (fila por denominación) | el input pasa a celda *readonly* dirigida por el keypad |
| 2 | Keypad (no existe) | Región 5 (anclado abajo) | **NUEVO** |
| 3 | Objetivo (subtítulo gris de la top bar) | Región 4 | **promovido** de gris pequeño a dato héroe, tabular-nums |
| 4 | Total acumulado (footer) | Región 4 | promovido, junto a Objetivo/Estado |
| 5 | Diferencia (rojo + signo) | Región 4 (Estado) | `✓ Cuadra`/`⚠ Faltan`/`⚠ Sobran` con **icono+texto+color** |
| 6 | Botón Continuar | Región 4 (CTA petróleo) | mismo gate (solo si cuadra), reubicado a la barra de progreso |
| 7 | Back | Región 1 | **+ confirmación** "¿descartar conteo?" si hay piezas |
| 8 | Subtotal por fila | Región 3 (derecha) | tabular-nums |
| 9 | `RecuperacionResumenCard` | Región 2 (cabecera fija, solo Local) | deja de scrollear; **solo-lectura** |
| 10 | Reordenar imputación de deuda (↑↓) | **→ paso/sheet previo "Recuperación de deuda"** | **MOVIDO** fuera del conteo (es decisión de negocio, no contar billetes) |
| 11 | Estado *nada que entregar* | Región 3 (texto + Continuar habilitado) | igual, con lenguaje claro |
| 12 | Validación `suma == objetivo` | Región 4 (gate del CTA) | igual (sin tolerancia ni override) |
| 13 | Saneado / límite 6 dígitos | a nivel de keypad (solo emite dígitos) | igual |
| 14 | Reutilización Total/Local | mismo Composable parametrizado | igual |
| 15 | Jerga "tolva/merma" | título/ayuda por modo | **renombrado**: "todo lo retirado" (Total) vs "lo que se entrega" (Local) |
| 16 | Pérdida silenciosa del desglose | **borrador persistido** (Room) | **NUEVO**: no perder 18 campos por un back accidental (offline-first) |

**Resumen**: se **mantiene** la lógica de negocio (validación dura, saneado, reutilización por modo, *nada que entregar*);
se **mueve** fuera la reordenación de imputación de deuda; se **añade** keypad, bloque de progreso, lenguaje de estado
claro y borrador persistido; y se **promueve** el dato (Objetivo/Total/Diferencia) de adornos grises a héroe persistente.

### 5.4 Implementación

- **Android**: `BasicTextField(readOnly = true)` + `MutableInteractionSource` (re-hide en foco) → **el IME del
  sistema nunca aparece** (origen del problema). El valor se dirige **solo** desde los botones del keypad
  (`onValueChange`). Layout **teléfono** (1-2-3 arriba), backspace dedicado, tecla "Siguiente". Teclas **48dp con
  8dp de separación**, en la thumb-zone. **Haptic ligero** y consistente por pulsación (respetando ajuste del
  sistema), `Reject` en cantidad inválida.
- **Web** (si en el futuro se permite edición): `input readOnly` + `inputMode="numeric"` como hint + keypad de
  botones; `type="text"` (no `type="number"`); total con `aria-live`.
- **Alcance (exclusivo de denominaciones)**: el keypad in-app es **solo** para el extracto de denominaciones (modos
  Total y Local). El **resto** de entradas numéricas de la app (contadores, importes de avería/deuda, tasa/%, valor de
  crédito) usa el **teclado del sistema** con el tipo correcto: `KeyboardType.Number`/`Decimal` (Android) o
  `inputMode="numeric"/"decimal"` (web) — nunca QWERTY — y las **fechas** un **DatePicker**. El keypad se implementa como
  un `Keypad` aislado y reutilizable por higiene, pero **no se generaliza** a otras pantallas ahora.
- **Validación**: al **salir de la fila / confirmar**, no mientras teclea; limpiar error al corregir; permitir filas
  en cero/vacías sin error; backspace borra dígito, acción "poner fila a 0".
- **Accesibilidad**: en Compose el `Text` del dígito lo anuncia TalkBack solo; `contentDescription` solo en
  backspace/"Siguiente". Probar en ≥ 3 tamaños de pantalla con overlay de thumb-zone.
- **Invariante de dinero**: el keypad **solo captura cantidades** (enteros). El cálculo económico definitivo es
  **SSOT servidor** (`calcular-recaudacion` / `_shared/calculo.ts`). Presentación con `BigDecimal` (Kotlin) /
  `decimal.js`-string (TS), **nunca** `Double`/`number`.
- (Opcional) `DENOMINACIONES_PERMITIDAS` deja de estar *hardcoded* para poder configurarse por empresa.

---

## 6. Patrones transversales (ambas superficies)

- **Estados de carga**: skeletons > spinners. Definir de antemano el ciclo **empty / loading / error / success** de
  cada vista antes de implementar.
- **Listas accionables**: swipe actions (android) / row actions + drawer (web).
- **Vacíos útiles**: `PlaceholderPage` (web) ya existe; equivalente android con icono + acción.
- **Densidad honesta** (web): datos críticos arriba/izquierda, agrupar relacionados, separar con whitespace, paleta
  restringida; cuidado con los *pitfalls* (botones X diminutos, paginación minúscula).
- **Offline-first** (android): reforzar el patrón Room/WorkManager — ver detalle del trabajo, registrar y completar
  sin conexión, sync automático al volver; nunca perder el progreso introducido.

---

## 7. Accesibilidad como gate (no retrofit)

Definir **"hecho" = cumple a11y**. WCAG 2.2: target size ≥ 24px (real 44–48px), contraste 4.5:1 / 3:1 foco,
indicadores de foco visibles, no depender del color, `prefers-reduced-motion` respetado, glass con alternativa
sólida. Motivado por el **European Accessibility Act** (vigente 28-jun-2025, conformidad plena 2030).

---

## 8. Roadmap por fases (propuesta de tareas T-227+)

> Orden por **ROI y dependencia**. Cada fase = rama + PR (< 400 líneas, squash). El rediseño es **incremental**:
> primero los tokens (cambian todo "gratis"), luego la pieza insignia, luego IA, luego pulido de motion.

| Fase | Tareas propuestas | Entregable |
|---|---|---|
| **F0 — Fundamentos** | T-227 tokens OKLCH unificados (web `globals.css` + android `Color.kt`/`Type.kt`/`Shapes`); T-228 escala tipográfica android (fuente variable) | Sistema de color/tipo compartido, a11y de contraste verde |
| **F1 — Motion base** | T-229 `MaterialExpressiveTheme` + `MotionScheme` android (requiere Compose BOM ≥ 2025.10 — ver plan de deps); T-230 `motion` 12.x + `MotionConfig` web | Vocabulario de motion vivo |
| **F2 — ⭐ Keypad** | T-231 `Keypad` in-app + rework `DenominacionesScreen` (modos Total y Local); T-232 resto de campos numéricos al teclado del sistema con tipo correcto (`Number`/`Decimal`/`inputMode`) + DatePicker en fechas | Extracto de denominaciones ergonómico; resto coherente con teclado nativo |
| **F3 — IA android** | T-233 bottom navigation + FAB; T-234 Ajustes en tabs (+ impresora dentro, empresa switch); T-235 alertas a campana | Navegación de pulgar |
| **F4 — IA web** | T-236 command palette `cmdk`; T-237 dashboard bento + alertas feed; T-238 Deudas 3-cards + tabs; T-239 conflictos explicados | Back-office navegable y claro |
| **F5 — Pulido** | T-240 skeletons + pull-to-refresh + swipe actions android; T-241 wizards de formularios + validación; T-242 transiciones shared-bounds (experimental, aislado); T-243 drawers/toasts.promise web | Micro-interacciones |
| **F6 — A11y sweep** | T-244 auditoría WCAG 2.2 ambas superficies | Gate de accesibilidad |

> Dependencia con el plan de dependencias: **F1 android** necesita Compose BOM ≥ 2025.10.00 (Material 3 Expressive),
> que entra en el **Bloque 5/6** de `dependency-upgrade-plan.md`. El resto del rediseño **no** depende de los upgrades
> mayores (React 19 / Tailwind v4): se puede empezar ya sobre el stack actual.

---

## 9. Riesgos de diseño

- **Sobre-ingeniería de motion**: filtro estricto — si una animación no aclara/guía/confirma, se elimina.
- **Shared transitions experimentales** (android): aislar el opt-in; tener fallback sin shared-element.
- **Material 3 Expressive** requiere subir Compose BOM (acoplado a Kotlin) → coordinar con el plan de deps.
- **Tailwind v4 / React 19** habilitan piezas (tw-animate-css, View Transitions, `useOptimistic`) pero son migración
  mayor: el rediseño **no se bloquea** por ellas; se aprovechan cuando lleguen.
- **Homogeneización AI-driven**: el command palette empieza como navegador/acciones, no como chat genérico.
