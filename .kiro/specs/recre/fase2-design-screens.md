# Fase 2 — Diseño UI/UX pantalla por pantalla (Confianza Industrial)

> Estado: **en curso**. Diseño visual concreto sobre la IA ya cerrada en `functional-audit-and-ia.md` y la identidad
> de `visual-identity.md`. Este documento **no redefine** tokens, motion ni componentes base: los **aplica**.
> - Paleta y tipografía → `visual-identity.md` (fuente de verdad).
> - IA, veredictos y patrones (T-1…T-13) → `functional-audit-and-ia.md` (fuente de verdad).
> - Sistema de diseño (motion, formas, catálogo de componentes, keypad denominaciones §5) → `design-system-plan.md`.
>
> Aquí: para **cada pantalla**, su layout en regiones, qué token/rol lleva cada elemento, estados (vacío/carga/error/
> offline), microinteracciones y el delta respecto a hoy. Se trabaja por **clusters de flujo**, no por orden alfabético.

## Leyenda de tokens (abreviada, ver `visual-identity.md`)
`primary` = acento petróleo (CTA/nav activo/foco) · `success` = solo dinero positivo/cuadra · `warning` = pendiente/
sin firmar/offline-stale · `danger` = solo error/avería/descuadre/conflicto · `muted` = secundario + símbolo `€` ·
`surface-1` card · `surface-2` fila alterna/input · `border` hairline 1px. Toda cifra: **Geist Mono `tabular-nums`**,
`€` en muted. Estado **nunca solo color** → icono + texto + (color). Acento ≤10% de pantalla.

## Componentes recurrentes (se definen una vez, se referencian en cada pantalla)
- **AppCard**: `surface-1`, `border` 1px, radio 12 (Android 16), padding 16. Elevación por borde (light) / luminancia (dark).
- **StatusChip**: fondo del rol al 12–16%, texto pleno del rol, dot + icono + label. Variantes: `cuadra`(success),
  `pendiente`(warning), `conflicto/avería`(danger), `offline/info`(neutro=muted, **no** rojo).
- **MoneyText**: Mono tabular, dígitos en `foreground`, `€` y separador de miles es-ES (`1.234,56`) en `muted`.
- **PrimaryCTA Android**: full-width, sticky inferior, 56dp, `primary`, **uno por pantalla**.
- **FieldNum**: input numérico que abre **teclado del sistema** (`Number`/`Decimal`), valor en Mono tabular, `surface-2`.
  *(Excepción: las denominaciones usan keypad in-app — ver `design-system-plan.md §5`.)*
- **ThumbNav** (Android): barra inferior 4 destinos (Locales · Histórico · Gestión · Ajustes), píldora `primaryContainer`
  en el activo, icono Phosphor fill en activo / regular en inactivo.
- **TopBarGlobal** (Android): 🔔 campana Alertas con badge + ↻ Sincronizar visible (estado: idle / girando / `warning` si stale).

---

# Cluster A — Flujo de recaudación (Android) · núcleo del producto

Cadena: **Locales (hub)** → **LocalDetalle** → **Máquina** → **Contadores** → **[OCR]** → **Denominaciones** → **Confirmación**.
Es el camino que el técnico recorre a diario, a una mano, al sol. Prioridad de legibilidad ~7:1 y CTA único por paso.

## A.1 · Locales (hub) — *rediseño estructural (T-2, T-9)*

**Objetivo**: el técnico elige el local que va a recaudar. Solo locales **con máquina instalada**. No es edición.

**Layout (regiones)**
```
┌─────────────────────────────────────────────┐
│ TopBarGlobal:  Recre        🔔(badge)  ↻     │  ← campana + sync (salen del antiguo ⋮)
├─────────────────────────────────────────────┤
│ 🔎  Buscar local o titular…                  │  ← búsqueda también por titular (nuevo)
├─────────────────────────────────────────────┤
│ ┌ LocalCard ───────────────────────────────┐ │
│ │ Bar Pepe                      ›           │ │
│ │ C/ Mayor 12 · Titular: J. Pérez            │ │
│ │ 3 máquinas · [⚠ 1 avería] [● pendiente]    │ │  ← contexto cruzado (T-5): avería/pendiente/deuda
│ └───────────────────────────────────────────┘ │
│ ┌ LocalCard … (filas 1px border, surface-1) ┐ │
│ └───────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ ThumbNav:  [Locales*]  Histórico  Gestión  Ajustes │
└─────────────────────────────────────────────┘
```

**Tokens / componentes**
- Cabecera con marca discreta; sync (↻) en `muted` idle, gira en `primary`, pasa a `warning` + texto "Sin sincronizar hace
  Xh" si stale (**T-1: el `SyncStaleBanner` rojo de hoy → `warning` neutro, no `danger`**).
- `LocalCard` = AppCard con: nombre 16/600, dirección+titular 13/440 `muted`, fila de StatusChips de contexto cruzado
  (avería=danger icono+texto, pendiente=warning, deuda=neutro con icono €). Chevron `›` `muted`.
- Nada de overflow ⋮: las 8 acciones se reparten en ThumbNav + campana + Ajustes (resuelve T-2 y T-9).

**Estados**: vacío = glifo grande `muted` + "No tienes locales con máquina instalada"; carga = skeletons de 3 cards;
offline = badge neutro pulsante lento en TopBar (no banner rojo); error de carga = card neutra con "Reintentar".

**Motion**: entrada de cards stagger 30ms; pulse lento del badge offline; sync gira a 180ms/loop. Respeta reduced-motion.

**Delta vs hoy**: elimina el ⋮ sobrecargado; añade nav de pulgar, campana, sync visible, contexto cruzado en la card y
búsqueda por titular. Saca config/sesión a Ajustes.

## A.2 · LocalDetalle — *rediseño (T-9)*

**Objetivo**: ver el local y **recaudar**. La acción dominante es "Recaudar todas las máquinas".

**Layout**
```
┌─────────────────────────────────────────────┐
│ ‹ Bar Pepe                                   │  ← back contextual
│ C/ Mayor 12        📞 llamar   🗺 mapa        │  ← teléfono→intent llamar, dirección→mapas (nuevo)
│ Contador base actualizado · Deuda 120,00 €   │  ← "baseline"→"Contador base"; deuda = dato en cabecera (degradado)
├─────────────────────────────────────────────┤
│ Máquinas (3)                                  │
│ ┌ MaquinaRow ──────────────────────────────┐ │
│ │ Cirsa Player  ·  [⚠ avería]   ›           │ │  ← acceso a histórico/averías por máquina
│ └───────────────────────────────────────────┘ │
│ …                                             │
├─────────────────────────────────────────────┤
│        ▟ RECAUDAR TODAS  (PrimaryCTA sticky)  │  ← CTA dominante
└─────────────────────────────────────────────┘
```

**Tokens**: "Recaudar todas" = PrimaryCTA `primary` sticky 56dp. "Ver deudas" deja de ser botón → se degrada a la línea
de cabecera (texto + importe MoneyText, neutro). Avería en MaquinaRow = StatusChip danger (icono+texto). Deuda en
cabecera con icono € `muted` (no rojo: deber dinero no es un error de la app).

**RBAC**: CONTABLE entra read-only (sin CTA de recaudar ni escritura) — gating real, no solo ocultar.

**Delta vs hoy**: CTA dominante claro; deuda degradada; teléfono/dirección accionables; rename baseline; acceso a hoja
de vida por máquina; gate de escritura.

## A.3 · Contadores (paso 1) — *rediseño (T-6, T-1, T-9)*

**Objetivo**: capturar la lectura de contadores (OCR o a mano) y mostrar la **previa** de recaudación.

**Layout**
```
┌─────────────────────────────────────────────┐
│ ‹ Recaudación · Bar Pepe       Paso 1 de 3   │  ← progreso de cadena
├─────────────────────────────────────────────┤
│  📷  Escanear contador (OCR)   ← vía principal│  ← OCR como acción primaria, no secundaria
│  ─────────  o introducir a mano  ─────────    │
│  Entrada actual   [   1.234  ] FieldNum num.  │  ← teclado NUMÉRICO del sistema (no QWERTY), tabular
│  Salida actual    [     987  ]                │
├─────────────────────────────────────────────┤
│ ┌ CifrasResumenCard (dato héroe) ───────────┐ │
│ │ Previa de recaudación                       │ │
│ │  342,00 €   (Mono tabular grande)           │ │  ← etiqueta "previa · el servidor recalcula" (SSOT)
│ │  Base 1.150 · Lectura 1.234 · +84 jugadas   │ │
│ └───────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│              Continuar →  (PrimaryCTA)         │  ← acento SOLO aquí
└─────────────────────────────────────────────┘
```

**Tokens / detalle**
- **Guardián lock/sync → precondición**: el chequeo de lock se hace **al pulsar "Recaudar"** (en A.2), no como AlertDialog
  intrusivo aquí. Si está bloqueado, panel inline `warning` con "En uso por X · Reintentar", no modal.
- Estados tipo "no procede" / "baja confianza OCR" → **neutro `muted` + icono**, nunca rojo (T-1).
- CifrasResumenCard = dato héroe, MoneyText grande, etiqueta explícita "previa (el servidor recalcula)" para no prometer
  que es la cifra final (SSOT). 
- Inputs = FieldNum con `KeyboardType.Number`, valor Mono tabular.

**Estados**: OCR procesando = shimmer sobre el preview de cámara; baja confianza = chip neutro "Revisa la lectura";
sin permiso de cámara = fallback a entrada manual sin bloquear.

**Delta vs hoy**: OCR primario; teclado numérico; lock como precondición + panel inline; recolor de estados; previa
etiquetada como no-final.

## A.4 · Denominaciones (paso 2) ⭐ — *ya especificada en `design-system-plan.md §5`*

Esta pantalla tiene su **diseño completo** (keypad in-app anclado, bloque persistente Objetivo/Total/Diferencia,
validación icono+texto+color "Faltan/Sobran X €", modos Total y Local, distribución de los 16 elementos, borrador).
No se reduplica aquí — ver `design-system-plan.md §5.1–§5.4`. En la cadena ocupa el **Paso 2 de 3**.

Único apunte de coherencia visual con este cluster: el bloque persistente superior usa MoneyText tabular; "Cuadra" =
StatusChip success con check; "Faltan/Sobran" = danger con icono ⚠ + texto; el keypad respeta el área segura inferior y
**nunca tapa** el bloque de progreso. "Continuar" se habilita solo al cuadrar.

## A.5 · Confirmación (paso 3) — *mantener + rejerarquizar*

**Objetivo**: revisar cifras finales (servidor), firmar e imprimir.

**Layout**
```
┌─────────────────────────────────────────────┐
│ ‹ Recaudación · Bar Pepe       Paso 3 de 3   │
│ (en cadena: "Máquina 2 de 3")                │
├─────────────────────────────────────────────┤
│ ┌ Cifras finales (unificadas, jerarquía) ───┐ │
│ │ Neto            420,00 €                    │ │  ← count-up tabular al responder servidor (firma de producto)
│ │ Parte local     126,00 €                    │ │
│ │ Parte empresa   294,00 €   (dominante)      │ │  ← parte_empresa = neto − parte_local, el héroe
│ └───────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ Firme aquí                                    │
│ ┌─────── lienzo de firma a pantalla ───────┐ │
│ │                                            │ │
│ └──────────────────────────  [Limpiar]──────┘ │
├─────────────────────────────────────────────┤
│        🖨  IMPRIMIR (Bluetooth)  PrimaryCTA   │  ← Bluetooth = único CTA primario
└─────────────────────────────────────────────┘
```

**Tokens / detalle**
- Las **dos cards de cifras** de hoy se unifican en una con jerarquía (parte_empresa dominante). Count-up tabular cuando
  llega la respuesta del servidor (subraya SSOT).
- Firma a pantalla completa con placeholder "Firme aquí" + "Limpiar". `BackHandler` → diálogo "¿Descartar firma?".
- Feedback de error de guardado = banner `danger` con reintento (hoy falta).
- Imprimir BT = PrimaryCTA único; estado del impresor (conectado/no) como chip neutro.

**Delta vs hoy**: unifica cifras con jerarquía; firma full-screen + guard; feedback de error; "Máquina X de N" en cadena;
un solo CTA primario.

---

---

# Cluster B — Gestión + Inventario + Deudas (Android)

Zona de administración del técnico/gestor. Aquí vive el **CRUD** (a diferencia del hub operativo de Locales).

## Patrón compartido — `ListaGestion` (Licencias · Máquinas · Locales-gestor · Instalaciones)
Las 4 listas comparten un mismo esqueleto; cada pantalla solo cambia columnas y acciones.
```
┌─────────────────────────────────────────────┐
│ ‹ Licencias                         🔎  + │   │  ← back + búsqueda + FAB/acción "Añadir"
├─────────────────────────────────────────────┤
│ [ Filtros: chips de estado/CCAA (scroll) ]   │
├─────────────────────────────────────────────┤
│ ┌ EntidadRow (AppCard) ─────────────────────┐│
│ │ Título 16/600              [StatusChip]  › ││
│ │ subtítulo 13/440 muted · dato cruzado link ││  ← T-12 cross-link (a local/licencia/recaudación)
│ └────────────────────────────────────────────┘│
├─────────────────────────────────────────────┤
│ ThumbNav: Locales  Histórico  [Gestión*] Ajustes│
└─────────────────────────────────────────────┘
```
- **Alta/edición = bottom sheet / drawer** (no pantalla nueva): formulario con FieldNum/selectores, CTA primario "Guardar".
- **Eliminar/cerrar**: confirmación destructiva (texto + acción `danger`) — **cablear las acciones muertas** (T-13).
- Estados: vacío = glifo + "Aún no hay X · Añade el primero"; carga = skeletons; error = card neutra "Reintentar".
- Estado de entidad **siempre** StatusChip icono+texto (nunca color solo). Caducidad licencia próxima = `warning`, no rojo.

## B.1 · Gestión (hub) — *rediseño (T-9)*
Rejilla de accesos a Inventario (Licencias·Máquinas·Locales·Instalaciones) + Deudas. Cards grandes con icono de dominio,
título y contador ("12 máquinas"). Sin lógica de negocio aquí; es índice. Acento solo en el icono activo del ThumbNav.

## B.2 · Licencias (gestor) — *patrón ListaGestion*
Columnas: titular, CCAA, nº licencia, **caducidad** (chip `warning` si <30 días, neutro si vigente). Alta/edición drawer con
**combobox CCAA**. Cross-link a local/máquina asociada (T-12).

## B.3 · Máquinas (gestor) — *patrón ListaGestion*
Columnas: modelo/fabricante, nº serie, estado (instalada/almacén/avería). Avería = chip `danger` icono+texto. Acceso a
**hoja de vida** de la máquina (histórico + averías) desde la row (T-12). Contador base de la máquina visible (deriva la
base de instalación — no se teclea en instalación).

## B.4 · Locales (gestor) — *patrón ListaGestion · NO fusionar con Locales-hub*
CRUD de locales (alta/edición/baja, datos de titular, dirección, reparto parteLocal por defecto). Microcopy que lo
distingue del hub: aquí "Administrar locales"; el hub es "Recaudar". Reutiliza **solo el componente visual de fila**, no
la pantalla. Teléfono/dirección accionables.

## B.5 · InstalacionesGestor — *rediseño (T-13)*
Lista de instalaciones (máquina↔local). Columnas cruzadas: máquina, local, fecha alta, contador base derivado. **Cablear
`eliminar()/cerrar()`** que hoy están muertos, con confirmación destructiva y manejo de permiso "denegado permanente".
Alta = drawer: elegir máquina + local; la **base se deriva** (solo lectura), no se teclea.

## B.6 · Deudas (gestión) — *centro de mando (mantener rol, repulir)*
Es el **centro de mando completo** de deuda (el detalle de local redirige aquí, T-218/T-219). Layout: KPI total adeudado
(MoneyText, **neutro con icono €**, no rojo — deber no es error) + ledger con origen. Cada línea de origen `recaudacion`
**enlaza a esa recaudación** (T-12, deep-link). Filtros por local/estado. Registrar cobro = drawer con FieldNum.

---

# Cluster C — Averías y Cambio de placa (Android)

## C.1 · Avería (reportar) — *mantener, recolor coherente*
Único sitio (con denominaciones-descuadre) donde el `danger` es legítimo. Layout: máquina (contexto), tipo de avería
(selector), descripción, **foto opcional** (CameraX), y si procede **merma de tolva** (T-226: la avería registra merma).
CTA primario "Reportar avería" `danger`-tinted o `primary` según convención (mantener `primary` para CTA, el estado avería
se comunica en chips/iconos). Feedback de envío + cola offline (chip neutro "Se enviará al sincronizar").

## C.2 · CambioPlaca — *aislar (T-3)*
Operación poco frecuente y conceptualmente separada → no mezclar con recaudación. Flujo propio: máquina origen → nueva
placa/serie → confirmación. FieldNum numérico para serie. En web vive en Administración (ver Cluster H). Confirmación con
resumen del cambio y registro en auditoría.

---

# Cluster D — Arranque/acceso + Ajustes/Impresora/Alertas (Android)

## D.1 · Splash — *mantener*
Con sesión → Locales (hub); sin sesión → Login. Marca discreta sobre `background`, sin ruido. Logo mono-trazo `currentColor`.

## D.2 · Login — *repulir*
`emailError/passwordError` inline (hoy a veces mudos). Campos `surface-2`, CTA primario full-width. Único lugar donde se
puede permitir un toque de marca (gradiente sutil del acento en cabecera, opcional). Teclado: email→email, contraseña→texto.

## D.3 · Ajustes — *recoger config dispersa (T-9)*
Config pura: Cuenta · Empresa (+**Cambiar empresa** aquí, no en ⋮ del hub) · Impresora · Cerrar sesión. Lista de secciones
con icono de dominio. Cambiar empresa = bottom sheet con selector. Cerrar sesión = acción `danger` con confirmación.

## D.4 · Impresora (Bluetooth) — *repulir*
Emparejado/estado del impresor BT como chip neutro (conectado=info-neutro, no success verde salvo confirmación puntual).
Botón "Imprimir prueba". Manejo de permisos BT con fallback claro.

## D.5 · Alertas (campana) — *nueva ubicación desde TopBar*
Panel/pantalla de alertas accesible desde 🔔 del TopBarGlobal (sale del ⋮). Cada alerta **enlaza al recurso** (T-12):
avería→máquina, licencia por caducar→licencia, descuadre→recaudación. Caducidad próxima = `warning`; avería = `danger`;
informativas = neutro. Marcar leída / ir al recurso.

## D.6 · Sincronizar / Histórico — *repulir*
**Sincronizar**: estado global (última sync, pendientes en cola), botón forzar; stale = `warning` neutro (no rojo, T-1).
**Histórico**: lista de recaudaciones pasadas (destino del ThumbNav), filtros por local/fecha (DatePicker, no input libre,
T-6), cada fila enlaza al detalle/recaudación (T-12). Importes MoneyText tabular.

---

# Cluster E — Shell + Inicio (Web back-office)

## E.1 · Sidebar shell — *rediseño IA (T-2/T-11)*
```
┌──────────┬──────────────────────────────────────────┐
│ SIDEBAR  │ TOPBAR: EmpresaSwitcher   🔔Alertas  ⌘K   │
│ 240px    ├──────────────────────────────────────────┤
│ Inicio   │                                            │
│ Operación│   contenido de la vista                    │
│ Inventario  (Máquinas·Licencias·Locales·Instalac.)    │
│ Analítica│                                            │
│ Admin    │  (Equipo·Cambios placa·Ajustes)            │
└──────────┴──────────────────────────────────────────┘
```
- Nav agrupada por dominio (Inicio·Operación·Inventario·Analítica·Administración). Activo = píldora `primary` 12% + texto
  pleno + icono Lucide filled. RBAC: ítems según rol (no solo ocultar — gating de ruta).
- **TopBar**: EmpresaSwitcher (combobox), campana Alertas con badge, **⌘K** (T-11) alimentada por nav-config + acciones de
  dominio (ir a recurso, crear, exportar, filtrar conflictos).
- **Desaparecen** del nav: Conflictos (absorbido en Recaudaciones, T-4), Ajustes movido a Administración.

## E.2 · Inicio (Dashboard bento) — *rediseño (T-8)*
Rejilla **bento** de KPIs como dato-héroe: recaudación del periodo (MoneyText grande + sparkline `primary` + count-up),
pendientes de firma (`warning`), averías abiertas (`danger`), deuda total (neutro €). Cada bento **enlaza** a su lista
filtrada (T-12). Sin acento más allá del CTA/sparkline. Densidad alta pero respirada; números tabulares alineados.

---

# Cluster F — Operación web (Recaudaciones, Deudas)

## Patrón compartido — `TablaDensa` (listas web)
Header sticky `surface-2`, filas 44px alternas, importes **tabular a la derecha**, estado en StatusChip, acciones en hover.
Filtros en barra superior + ⌘K. Paginación/scroll virtual. Export CSV desde acción + ⌘K. Empty/loading/error como en Android.

## F.1 · Recaudaciones (lista) — *rediseño (T-4, T-5)*
TablaDensa. Columnas: fecha, local (link), máquina, neto, parte_empresa (dominante), estado (cuadra/descuadre/pendiente
firma). **Absorbe Conflictos** (T-4): un filtro "Solo descuadres/conflictos" en vez de pantalla aparte. Columnas cruzadas:
titular, deuda generada (T-5). Fila → detalle.

## F.2 · Recaudación (detalle) — *rediseño (T-12)*
Cabecera con cifras finales unificadas (parte_empresa héroe, count-up). Desglose de denominaciones (solo lectura, tabular).
Firma renderizada. Estado. **Cross-links**: a local, máquina, licencia, y a la **deuda** que originó (T-12). Acciones:
reimprimir, exportar, marcar revisada (según RBAC).

## F.3 · Deudas (web) — *centro de mando (espejo de B.6)*
TablaDensa del ledger con origen; total adeudado KPI neutro €. Línea `recaudacion` → deep-link a la recaudación. Registrar
cobro = drawer. Filtros por local/estado/fecha (DatePicker). El detalle de local enlaza aquí, no duplica gestión.

---

# Cluster G — Inventario web (Licencias · Máquinas · Locales · Instalaciones)

Las 4 = `TablaDensa` + **drawer** de alta/edición (no página nueva). Mismas reglas de estado/cross-link que Android, en denso.
- **G.1 Licencias**: titular, CCAA (combobox en drawer), nº, caducidad (`warning` <30d). Cross-link a local/máquina.
- **G.2 Máquinas**: modelo/fabricante, serie, estado, contador base, hoja de vida (link a histórico+averías). Avería=`danger`.
- **G.3 Locales**: CRUD; titular, dirección, reparto parteLocal por defecto, teléfono. Distinto del hub Android (web no tiene
  hub operativo; aquí es gestión pura). Cross-link a instalaciones/recaudaciones.
- **G.4 Instalaciones**: máquina↔local, fecha, **base derivada** (solo lectura). Cablear cerrar/eliminar (T-13) con confirm.
  Columnas cruzadas máquina/local con links.

---

# Cluster H — Analítica · Administración · Auth (Web)

## H.1 · Analítica / Informes — *rediseño (T-8)*
Gráficas con paleta restringida (`primary` para series principales, neutros para el resto; `success`/`danger` solo donde
signifiquen dinero/alerta). Tablas tabulares exportables. Selector de rango con DatePicker. KPIs arriba (bento) + detalle
abajo. Sin decoración: el dato manda.

## H.2 · Equipo — *repulir (T-13)*
TablaDensa de usuarios: nombre, **email** (rellenar la columna placeholder, T-13), rol, estado. Invitar = drawer. Cambiar
rol/estado según RBAC (gating real). Acciones destructivas con confirm.

## H.3 · Cambios de placa (web) — *aislar (T-3)*
Vista propia en Administración (espejo de C.2 Android). TablaDensa de cambios con máquina, placa anterior/nueva, fecha,
operador. Registrar cambio = drawer. Enlaza a la máquina afectada y a auditoría.

## H.4 · Ajustes (web) — *config pura, mover a Administración*
Cuenta · Empresa (+cambiar) · Impresora · Sesión. Formularios en `surface-1`, sin lógica de negocio. Sale del nav principal.

## H.5 · Auth / Login (web) — *repulir*
Errores inline, CTA primario, toque de marca opcional en cabecera. Redirección por rol tras login.

## H.6 · Auditoría (web) — *repulir (T-12)*
TablaDensa de eventos; **el id de entidad se hace clicable** (deep-link al recurso, T-12). Filtros por tipo/fecha/usuario.
Solo lectura.

---

> **Cobertura**: Clusters A–H cubren las 44 pantallas auditadas (núcleo recaudación, gestión/inventario, averías/placa,
> acceso/ajustes en Android; shell/inicio, operación, inventario, analítica/admin/auth en Web). Conflictos (web) queda
> **absorbido** en Recaudaciones (T-4), no es pantalla. Próximo nivel (opcional Fase 3): spec atómica por componente
> (medidas dp/px exactas, tokens CSS/Compose, variantes de estado) cuando se quiera bajar a implementación.
