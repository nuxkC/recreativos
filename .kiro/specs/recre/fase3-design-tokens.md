# Fase 3 — Tokens de diseno materializados (Confianza Industrial)

> Generado en la Fase 3. Valores: fuente de verdad `visual-identity.md`. Codigo listo para web (CSS vars + tailwind.config) y Android (Color/Type/Shape/Motion/Theme). NO redefine la identidad: la materializa. Ver `fase2-design-screens.md` para el uso por pantalla y `fase3-component-specs.md` para los componentes.

---

## Fase 3 — Grupo Color: ColorScheme Material3 (Android) + CSS vars/Tailwind (Web), Confianza Industrial (azul petroleo)  ·  `fase3-color`

## Mapa de roles de Color — valor (light / dark) → rol M3 / var CSS → uso

| Rol semantico | Light | Dark | M3 (slot) | CSS var | Tailwind | Uso |
|---|---|---|---|---|---|---|
| primary | `#0E7490` | `#2BC4DD` | `primary` | `--primary` | `primary` | Accion principal, FAB, link activo. Acento ≤10% pantalla |
| on-primary | `#FFFFFF` | `#06212A` | `onPrimary` | `--primary-foreground` | `primary-foreground` | Texto/icono sobre primary |
| secondary | `#E6F2F4` | `#16323A` | `secondary` | `--secondary` | `secondary` | Chips/superficies tenues de marca, accent |
| background | `#FAFBFC` | `#0B0C0E` | `background` | `--background` | `background` | Lienzo de pantalla |
| surface-1 | `#FFFFFF` | `#131519` | `surface` | `--surface-1`/`--card` | `surface-1`,`card` | Cards, dialogs, popovers |
| surface-2 | `#F4F6F8` | `#1B1E24` | `surfaceVariant`/`surfaceContainer` | `--surface-2`/`--muted` | `surface-2`,`muted` | Fondos sutiles, hover, headers sticky, keypad |
| foreground | `#11161B` | `#E7EAEE` | `onSurface`/`onBackground` | `--foreground` | `foreground` | Texto principal, digito de importe |
| muted | `#646B76` | `#9AA1AD` | `onSurfaceVariant`/`outline` | `--muted-foreground` | `muted-foreground` | Texto secundario, simbolo € |
| border | `#E3E6EA` | `#262A31` | `outlineVariant` | `--border`/`--input` | `border`,`input` | Separadores 1px, bordes de input/card |
| ring | `#0E7490` | `#2BC4DD` | (= primary) | `--ring` | `ring` | Anillo de foco = primary |
| **success** | `#0E8A55` | `#34D399` | extra (CL) | `--success` | `success` | SOLO dinero+/cuadra/completado. Nunca marca |
| success-subtle | `#D3F2E2` | `#0C4D34` | `successContainer` | `--success-subtle` | `success-subtle` | Fondo de badge/flash success |
| **warning** | `#B45309` | `#FBBF24` | extra (CL) | `--warning` | `warning` | SOLO pendiente/sin-firmar/offline-stale |
| warning-subtle | `#FCEBD2` | `#5A3A06` | `warningContainer` | `--warning-subtle` | `warning-subtle` | Fondo banner offline-stale (no rojo) |
| **danger** | `#DC2626` | `#F87171` | `error` | `--danger`/`--destructive` | `danger`,`destructive` | SOLO error/averia/descuadre/conflicto |
| danger-subtle | `#FADCDC` | `#5C2120` | `errorContainer` | `--danger-subtle` | `danger-subtle` | Fondo de error, shake descuadre |
| **info** | `#2563EB` | `#60A5FA` | `tertiary` | `--info` | `info` | Informativo, sincronizando |
| info-subtle | `#DBE7FE` | `#1C3A6B` | `tertiaryContainer` | `--info-subtle` | `info-subtle` | Fondo de chip "sincronizando" |

### Reglas de consumo (no negociables)
- success/danger/warning **solo** con su significado; nunca como color de marca. Estado = icono + texto + (color), nunca solo color.
- Tokens por **rol** (`bg-surface-2`, `text-success`), no por valor (`bg-emerald-100`). Migrar `badge.tsx` success/warning y `kpi-card.tsx` trend a roles.
- `--ring` == `--primary` (web y Android). on-primary dark = `#06212A` (oscuro sobre cian).
- Web AA minimo; Android objetivo ~7:1. Android **sin dynamicColor** (marca fija).

**Android**
```kotlin
// =====================================================================
// Color.kt — Recre Design System · Grupo Color (Fase 3)
// Identidad "Confianza Industrial" (azul petroleo). Paleta = fuente de
// verdad de visual-identity.md / design-system-plan.md. NO inventar hex:
// los valores marcados [PALETA] son canonicos; los *Container/on* son
// tintes/sombras derivados del rol canonico para rellenar los slots M3.
// =====================================================================
package com.recre.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------
// 1) PALETA CANONICA (no tocar: fuente de verdad)
// ---------------------------------------------------------------------

// --- Light
val RecrePrimaryLight     = Color(0xFF0E7490) // [PALETA] primary (petroleo)
val RecreOnPrimaryLight   = Color(0xFFFFFFFF) // [PALETA] on-primary
val RecreSecondaryLight   = Color(0xFFE6F2F4) // [PALETA] secondary (tint petroleo)
val RecreBackgroundLight  = Color(0xFFFAFBFC) // [PALETA] background
val RecreSurface1Light    = Color(0xFFFFFFFF) // [PALETA] surface-1 (cards, dialogs)
val RecreSurface2Light    = Color(0xFFF4F6F8) // [PALETA] surface-2 (fondos sutiles)
val RecreSuccessLight     = Color(0xFF0E8A55) // [PALETA] success (SOLO dinero+/cuadra)
val RecreWarningLight     = Color(0xFFB45309) // [PALETA] warning (SOLO pendiente/offline-stale)
val RecreDangerLight      = Color(0xFFDC2626) // [PALETA] danger (SOLO error/averia/descuadre)
val RecreInfoLight        = Color(0xFF2563EB) // [PALETA] info
val RecreBorderLight      = Color(0xFFE3E6EA) // [PALETA] border / outlineVariant
val RecreMutedLight       = Color(0xFF646B76) // [PALETA] muted (texto secundario)
val RecreRingLight        = Color(0xFF0E7490) // [PALETA] ring = primary

// --- Dark
val RecrePrimaryDark      = Color(0xFF2BC4DD) // [PALETA] primary (cian)
val RecreOnPrimaryDark    = Color(0xFF06212A) // [PALETA] on-primary (texto oscuro sobre cian)
val RecreSecondaryDark    = Color(0xFF16323A) // [PALETA] secondary
val RecreBackgroundDark   = Color(0xFF0B0C0E) // [PALETA] background
val RecreSurface1Dark     = Color(0xFF131519) // [PALETA] surface-1
val RecreSurface2Dark     = Color(0xFF1B1E24) // [PALETA] surface-2
val RecreSuccessDark      = Color(0xFF34D399) // [PALETA] success
val RecreWarningDark       = Color(0xFFFBBF24) // [PALETA] warning
val RecreDangerDark       = Color(0xFFF87171) // [PALETA] danger
val RecreInfoDark         = Color(0xFF60A5FA) // [PALETA] info
val RecreBorderDark       = Color(0xFF262A31) // [PALETA] border / outlineVariant
val RecreMutedDark        = Color(0xFF9AA1AD) // [PALETA] muted
val RecreRingDark         = Color(0xFF2BC4DD) // [PALETA] ring = primary

// Foreground neutro (texto principal sobre surface)
val RecreOnSurfaceLight   = Color(0xFF11161B) // casi-negro frio, contraste AA sobre surface-1
val RecreOnSurfaceDark    = Color(0xFFE7EAEE) // casi-blanco frio
val RecreScrim            = Color(0xFF000000)

// ---------------------------------------------------------------------
// 2) CONTAINERS DERIVADOS (rellenan slots M3; tinte/sombra del canonico)
//    Light: container = tint claro del rol; on* = version oscura legible.
//    Dark : container = sombra profunda del rol; on* = version clara.
// ---------------------------------------------------------------------

// --- Light containers
val RecrePrimaryContainerLight   = Color(0xFFCDE9F0) // tint de primary
val RecreOnPrimaryContainerLight = Color(0xFF002B38)
val RecreSecondaryContainerLight = Color(0xFFD5EAEE) // = secondary mas saturado
val RecreOnSecondaryContainerLight = Color(0xFF062A33)
val RecreSuccessContainerLight   = Color(0xFFD3F2E2)
val RecreOnSuccessContainerLight = Color(0xFF053B23)
val RecreWarningContainerLight   = Color(0xFFFCEBD2)
val RecreOnWarningContainerLight = Color(0xFF4A2103)
val RecreDangerContainerLight    = Color(0xFFFADCDC) // = errorContainer
val RecreOnDangerContainerLight  = Color(0xFF5C0F0F)
val RecreInfoContainerLight      = Color(0xFFDBE7FE)
val RecreOnInfoContainerLight    = Color(0xFF0B2A66)

// --- Dark containers
val RecrePrimaryContainerDark    = Color(0xFF0B4A58) // sombra de primary
val RecreOnPrimaryContainerDark  = Color(0xFFBDEAF4)
val RecreSecondaryContainerDark  = Color(0xFF1E4350)
val RecreOnSecondaryContainerDark = Color(0xFFC7E6EE)
val RecreSuccessContainerDark    = Color(0xFF0C4D34)
val RecreOnSuccessContainerDark  = Color(0xFFA9EFCE)
val RecreWarningContainerDark    = Color(0xFF5A3A06)
val RecreOnWarningContainerDark  = Color(0xFFFCE3B0)
val RecreDangerContainerDark     = Color(0xFF5C2120) // = errorContainer
val RecreOnDangerContainerDark   = Color(0xFFFBD0D0)
val RecreInfoContainerDark       = Color(0xFF1C3A6B)
val RecreOnInfoContainerDark     = Color(0xFFCADEFE)

// ---------------------------------------------------------------------
// 3) TOKENS SEMANTICOS DE DOMINIO (M3 no tiene slots para success/
//    warning/info/border/muted/surface-2/ring). Se inyectan por
//    CompositionLocal y se leen como MaterialTheme: RecreColors.current.success
// ---------------------------------------------------------------------
@Immutable
data class RecreSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,            // alias semantico de error
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val surface2: Color,          // surface-2 (M3 base solo da surface=surface-1)
    val border: Color,            // = outlineVariant (separadores 1px)
    val muted: Color,             // texto secundario
    val ring: Color,              // foco = primary
    val isLight: Boolean,
)

private val LightSemanticColors = RecreSemanticColors(
    success = RecreSuccessLight,
    onSuccess = Color(0xFFFFFFFF),
    successContainer = RecreSuccessContainerLight,
    onSuccessContainer = RecreOnSuccessContainerLight,
    warning = RecreWarningLight,
    onWarning = Color(0xFFFFFFFF),
    warningContainer = RecreWarningContainerLight,
    onWarningContainer = RecreOnWarningContainerLight,
    danger = RecreDangerLight,
    onDanger = Color(0xFFFFFFFF),
    dangerContainer = RecreDangerContainerLight,
    onDangerContainer = RecreOnDangerContainerLight,
    info = RecreInfoLight,
    onInfo = Color(0xFFFFFFFF),
    infoContainer = RecreInfoContainerLight,
    onInfoContainer = RecreOnInfoContainerLight,
    surface2 = RecreSurface2Light,
    border = RecreBorderLight,
    muted = RecreMutedLight,
    ring = RecreRingLight,
    isLight = true,
)

private val DarkSemanticColors = RecreSemanticColors(
    success = RecreSuccessDark,
    onSuccess = Color(0xFF053B23),
    successContainer = RecreSuccessContainerDark,
    onSuccessContainer = RecreOnSuccessContainerDark,
    warning = RecreWarningDark,
    onWarning = Color(0xFF3A2503),
    warningContainer = RecreWarningContainerDark,
    onWarningContainer = RecreOnWarningContainerDark,
    danger = RecreDangerDark,
    onDanger = Color(0xFF45100F),
    dangerContainer = RecreDangerContainerDark,
    onDangerContainer = RecreOnDangerContainerDark,
    info = RecreInfoDark,
    onInfo = Color(0xFF0A2247),
    infoContainer = RecreInfoContainerDark,
    onInfoContainer = RecreOnInfoContainerDark,
    surface2 = RecreSurface2Dark,
    border = RecreBorderDark,
    muted = RecreMutedDark,
    ring = RecreRingDark,
    isLight = false,
)

val LocalRecreColors = staticCompositionLocalOf { LightSemanticColors }

/** Acceso ergonomico a los tokens de dominio: `RecreColors.current.success`. */
object RecreColors {
    val current: RecreSemanticColors
        @Composable @ReadOnlyComposable
        get() = androidx.compose.runtime.compositionLocalof { LightSemanticColors }
            .let { androidx.compose.runtime.currentComposer.consume(LocalRecreColors) }
}

internal fun recreSemanticColors(dark: Boolean): RecreSemanticColors =
    if (dark) DarkSemanticColors else LightSemanticColors

// =====================================================================
// Theme.kt — RecreTheme: ColorScheme M3 (petroleo) + tokens de dominio.
// NO usa dynamicColor: la identidad de marca es fija (regla del proyecto).
// =====================================================================
/*
package com.recre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = RecrePrimaryLight,
    onPrimary = RecreOnPrimaryLight,
    primaryContainer = RecrePrimaryContainerLight,
    onPrimaryContainer = RecreOnPrimaryContainerLight,
    secondary = RecreSecondaryLight,
    onSecondary = RecreOnPrimaryContainerLight,
    secondaryContainer = RecreSecondaryContainerLight,
    onSecondaryContainer = RecreOnSecondaryContainerLight,
    // tertiary lo reservamos a info (estados "sincronizando"); no es marca.
    tertiary = RecreInfoLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = RecreInfoContainerLight,
    onTertiaryContainer = RecreOnInfoContainerLight,
    background = RecreBackgroundLight,
    onBackground = RecreOnSurfaceLight,
    surface = RecreSurface1Light,            // surface = surface-1
    onSurface = RecreOnSurfaceLight,
    surfaceVariant = RecreSurface2Light,     // surface-2 sutil
    onSurfaceVariant = RecreMutedLight,      // texto secundario sobre surface
    surfaceContainerLowest = RecreSurface1Light,
    surfaceContainerLow = RecreSurface2Light,
    surfaceContainer = RecreSurface2Light,
    surfaceContainerHigh = Color(0xFFEDF0F3),
    surfaceContainerHighest = Color(0xFFE7EBEF),
    error = RecreDangerLight,                // error == danger
    onError = Color(0xFFFFFFFF),
    errorContainer = RecreDangerContainerLight,
    onErrorContainer = RecreOnDangerContainerLight,
    outline = RecreMutedLight,               // bordes con enfasis
    outlineVariant = RecreBorderLight,       // separadores 1px (la mayoria)
    inverseSurface = RecreSurface1Dark,
    inverseOnSurface = RecreOnSurfaceDark,
    inversePrimary = RecrePrimaryDark,
    scrim = RecreScrim,
)

private val DarkColorScheme = darkColorScheme(
    primary = RecrePrimaryDark,
    onPrimary = RecreOnPrimaryDark,
    primaryContainer = RecrePrimaryContainerDark,
    onPrimaryContainer = RecreOnPrimaryContainerDark,
    secondary = RecreSecondaryDark,
    onSecondary = RecreOnSecondaryContainerDark,
    secondaryContainer = RecreSecondaryContainerDark,
    onSecondaryContainer = RecreOnSecondaryContainerDark,
    tertiary = RecreInfoDark,
    onTertiary = Color(0xFF0A2247),
    tertiaryContainer = RecreInfoContainerDark,
    onTertiaryContainer = RecreOnInfoContainerDark,
    background = RecreBackgroundDark,
    onBackground = RecreOnSurfaceDark,
    surface = RecreSurface1Dark,
    onSurface = RecreOnSurfaceDark,
    surfaceVariant = RecreSurface2Dark,
    onSurfaceVariant = RecreMutedDark,
    surfaceContainerLowest = RecreBackgroundDark,
    surfaceContainerLow = RecreSurface1Dark,
    surfaceContainer = RecreSurface2Dark,
    surfaceContainerHigh = Color(0xFF22262D),
    surfaceContainerHighest = Color(0xFF2A2F37),
    error = RecreDangerDark,
    onError = Color(0xFF45100F),
    errorContainer = RecreDangerContainerDark,
    onErrorContainer = RecreOnDangerContainerDark,
    outline = RecreMutedDark,
    outlineVariant = RecreBorderDark,
    inverseSurface = RecreSurface1Light,
    inverseOnSurface = RecreOnSurfaceLight,
    inversePrimary = RecrePrimaryLight,
    scrim = RecreScrim,
)

@Composable
fun RecreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalRecreColors provides recreSemanticColors(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography, // entra en el grupo Tipografia (Geist)
            // shapes = RecreShapes,  // entra en el grupo Forma (12/16/20)
            content = content,
        )
    }
}
*/
```

**Web**
```ts
/* =====================================================================
 * globals.css — Recre Design System · Grupo Color (Fase 3)
 * Reemplaza el bloque @layer base { :root / .dark } completo (zinc default).
 * Tokens por ROL, identidad "Confianza Industrial". Formato HEX (los valores
 * de la paleta son la fuente de verdad; no convertir a oklch para no derivar).
 * AA minimo en texto. --ring = primary. success/danger reservados a significado.
 * Recuerda: elimina ademas el `body{font-family:Arial...}` (grupo Tipografia)
 * y la sombra por defecto de Card/Button (grupo Forma).
 * ===================================================================== */
@layer base {
  :root {
    /* superficies / elevacion por capas (no por sombra) */
    --background: #FAFBFC;          /* lienzo */
    --foreground: #11161B;          /* texto principal */
    --surface-1: #FFFFFF;           /* cards, dialogs, popovers */
    --surface-2: #F4F6F8;           /* fondos sutiles, hovers, headers */
    --card: var(--surface-1);
    --card-foreground: #11161B;
    --popover: var(--surface-1);
    --popover-foreground: #11161B;

    /* marca: primary = petroleo (acento <=10% de pantalla) */
    --primary: #0E7490;
    --primary-foreground: #FFFFFF;  /* on-primary */
    --secondary: #E6F2F4;
    --secondary-foreground: #062A33;

    /* neutros */
    --muted: #F4F6F8;               /* fondo muted == surface-2 */
    --muted-foreground: #646B76;    /* texto secundario */
    --accent: #E6F2F4;              /* accent == secondary (tint petroleo) */
    --accent-foreground: #062A33;

    /* roles semanticos de dominio (ciudadanos de primera clase) */
    --success: #0E8A55;             /* SOLO dinero+/cuadra */
    --success-foreground: #FFFFFF;
    --success-subtle: #D3F2E2;      /* fondo badge success */
    --warning: #B45309;             /* SOLO pendiente/sin-firmar/offline-stale */
    --warning-foreground: #FFFFFF;
    --warning-subtle: #FCEBD2;
    --danger: #DC2626;              /* SOLO error/averia/descuadre/conflicto */
    --danger-foreground: #FFFFFF;
    --danger-subtle: #FADCDC;
    --info: #2563EB;
    --info-foreground: #FFFFFF;
    --info-subtle: #DBE7FE;

    /* destructive = alias de danger (compat shadcn) */
    --destructive: var(--danger);
    --destructive-foreground: #FFFFFF;

    /* bordes / inputs / foco */
    --border: #E3E6EA;
    --input: #E3E6EA;
    --ring: #0E7490;                /* ring == primary */

    --radius: 0.5rem;

    /* sidebar / nav unificado a marca (sin el azul-violeta huerfano) */
    --sidebar: #FFFFFF;
    --sidebar-foreground: #11161B;
    --sidebar-primary: #0E7490;
    --sidebar-primary-foreground: #FFFFFF;
    --sidebar-accent: #F4F6F8;
    --sidebar-accent-foreground: #11161B;
    --sidebar-border: #E3E6EA;
    --sidebar-ring: #0E7490;

    /* charts derivados de la marca (no grises) */
    --chart-1: #0E7490;
    --chart-2: #0E8A55;
    --chart-3: #2563EB;
    --chart-4: #B45309;
    --chart-5: #646B76;
  }

  .dark {
    --background: #0B0C0E;
    --foreground: #E7EAEE;
    --surface-1: #131519;
    --surface-2: #1B1E24;
    --card: var(--surface-1);
    --card-foreground: #E7EAEE;
    --popover: var(--surface-1);
    --popover-foreground: #E7EAEE;

    --primary: #2BC4DD;             /* cian */
    --primary-foreground: #06212A;  /* on-primary: texto oscuro sobre cian */
    --secondary: #16323A;
    --secondary-foreground: #C7E6EE;

    --muted: #1B1E24;
    --muted-foreground: #9AA1AD;
    --accent: #16323A;
    --accent-foreground: #C7E6EE;

    --success: #34D399;
    --success-foreground: #053B23;
    --success-subtle: #0C4D34;
    --warning: #FBBF24;
    --warning-foreground: #3A2503;
    --warning-subtle: #5A3A06;
    --danger: #F87171;
    --danger-foreground: #45100F;
    --danger-subtle: #5C2120;
    --info: #60A5FA;
    --info-foreground: #0A2247;
    --info-subtle: #1C3A6B;

    --destructive: var(--danger);
    --destructive-foreground: #45100F;

    --border: #262A31;
    --input: #262A31;
    --ring: #2BC4DD;

    --sidebar: #131519;
    --sidebar-foreground: #E7EAEE;
    --sidebar-primary: #2BC4DD;
    --sidebar-primary-foreground: #06212A;
    --sidebar-accent: #1B1E24;
    --sidebar-accent-foreground: #E7EAEE;
    --sidebar-border: #262A31;
    --sidebar-ring: #2BC4DD;

    --chart-1: #2BC4DD;
    --chart-2: #34D399;
    --chart-3: #60A5FA;
    --chart-4: #FBBF24;
    --chart-5: #9AA1AD;
  }
}

/* =====================================================================
 * tailwind.config.ts — theme.extend.colors (anadir a lo existente).
 * Tokens por ROL. Cada rol expone DEFAULT/foreground (+ subtle donde aplica).
 * ===================================================================== */
/*
colors: {
  background: "var(--background)",
  foreground: "var(--foreground)",
  surface: {
    1: "var(--surface-1)",
    2: "var(--surface-2)",
  },
  card:    { DEFAULT: "var(--card)",    foreground: "var(--card-foreground)" },
  popover: { DEFAULT: "var(--popover)", foreground: "var(--popover-foreground)" },
  primary:   { DEFAULT: "var(--primary)",   foreground: "var(--primary-foreground)" },
  secondary: { DEFAULT: "var(--secondary)", foreground: "var(--secondary-foreground)" },
  muted:     { DEFAULT: "var(--muted)",     foreground: "var(--muted-foreground)" },
  accent:    { DEFAULT: "var(--accent)",    foreground: "var(--accent-foreground)" },
  // roles semanticos (consumir SIEMPRE por rol, nunca emerald-100/amber-100)
  success: { DEFAULT: "var(--success)", foreground: "var(--success-foreground)", subtle: "var(--success-subtle)" },
  warning: { DEFAULT: "var(--warning)", foreground: "var(--warning-foreground)", subtle: "var(--warning-subtle)" },
  danger:  { DEFAULT: "var(--danger)",  foreground: "var(--danger-foreground)",  subtle: "var(--danger-subtle)" },
  info:    { DEFAULT: "var(--info)",    foreground: "var(--info-foreground)",    subtle: "var(--info-subtle)" },
  destructive: { DEFAULT: "var(--destructive)", foreground: "var(--destructive-foreground)" },
  border: "var(--border)",
  input:  "var(--input)",
  ring:   "var(--ring)",
  chart:   { 1:"var(--chart-1)", 2:"var(--chart-2)", 3:"var(--chart-3)", 4:"var(--chart-4)", 5:"var(--chart-5)" },
  sidebar: {
    DEFAULT: "var(--sidebar)", foreground: "var(--sidebar-foreground)",
    primary: "var(--sidebar-primary)", "primary-foreground": "var(--sidebar-primary-foreground)",
    accent: "var(--sidebar-accent)", "accent-foreground": "var(--sidebar-accent-foreground)",
    border: "var(--sidebar-border)", ring: "var(--sidebar-ring)",
  },
},
*/
```

> Alcance: SOLO el grupo Color (ColorScheme M3 + CSS vars/Tailwind). Tipografia, Forma y Motion son otros grupos de Fase 3.

Decisiones de mapeo:
1) M3 no tiene slots para success/warning/info/border/muted/surface-2/ring -> en Android se inyectan via `RecreSemanticColors` (CompositionLocal `LocalRecreColors`), provisto dentro de `RecreTheme`. Lectura: `RecreColors.current.success`. Lo canonico de M3 (primary/secondary/surface/error) se mapea directo; danger == `error`, info == `tertiary` (estado "sincronizando", no marca), surface-2 == `surfaceVariant`/`surfaceContainer`, border == `outlineVariant`, muted == `onSurfaceVariant`/`outline`.
2) Valores [PALETA] son la fuente de verdad (no convertidos a oklch para evitar deriva; design-system-plan los lista igual). Los `*Container`/`*-subtle` y los `on*` son derivados (tinte claro en light, sombra profunda en dark) calculados para legibilidad; ajustables sin tocar los canonicos.
3) Web: reemplaza el bloque `:root/.dark` de globals.css (zinc) por estos HEX. Elimina el `--sidebar-primary` azul-violeta huerfano y los chart grises (ahora derivan de marca). `--destructive` queda como alias de `--danger` para compat shadcn. Anade `surface-1/2`, `success/warning/danger/info` (+`-subtle`) que hoy no existen.
4) Pendiente de OTROS grupos (no aqui): borrar `body{font-family:Arial}` y mapear `fontFamily.sans/mono` (Tipografia); quitar `shadow` de Card/Button base, sombras solo en overlays (Forma); count-up/flash/shake/pulse (Motion); migrar `badge.tsx`/`kpi-card.tsx` a tokens por rol (consumo).

Verificacion: codigo no compilado en este entorno (sin gradle/tailwind build aqui). El snippet `RecreColors.current` usa una via segura via `currentComposer.consume`; si tu version de Compose lo prefiere, sustituir el getter por `@ReadOnlyComposable get() = LocalRecreColors.current` directamente (equivalente y mas idiomatico).

Ficheros objetivo (absolutos):
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Color.kt
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Theme.kt (bloque comentado al final del androidCode)
- /home/a/Escritorio/recre-main/web/src/app/globals.css (bloque :root/.dark)
- /home/a/Escritorio/recre-main/web/tailwind.config.ts (theme.extend.colors)


---

## Fase 3 — Tipografía (Geist Sans UI + Geist Mono tabular para cifras)  ·  `fase3-tipografia`

## Escala tipográfica — Tipografía (Geist Sans UI + Geist Mono tabular)

| Estilo nombrado | Uso | Familia | Web (px/peso · token) | Android (sp/peso · slot M3) |
|---|---|---|---|---|
| **importe** | Importe protagonista (KPI, cabecera recaudación). € muted + dígito foreground | Geist **Mono** tabular | 36 / 600 · `.text-importe` | 40 / 700 · `RecreType.importe` |
| **importeMedium** | Importe en fila/card de cifras, total denominaciones | Geist **Mono** tabular | 22 / 600 · `.num-tabular` | 22 / 600 · `RecreType.importeMedium` |
| **cifra** | Subtotal, contador, % inline | Geist **Mono** tabular | 16 / 500 · `.text-cifra` | 16 / 500 · `RecreType.cifra` |
| **cifraCaption** | Cifra menor, fecha mono, id | Geist **Mono** tabular | 12 / 500 · `.text-cifra-caption` | 13 / 500 · `RecreType.cifraCaption` |
| **kpi** | Número/título KPI cuando no es cifra mono | Geist Sans | 36 / 600 · `text-kpi` | 40 / 700 · `displaySmall`* |
| **h1** | Título de pantalla | Geist Sans | 24 / 600 · `text-h1` | 24 / 700 · `headlineMedium` |
| **h2** | Sección / título de card grande | Geist Sans | 18 / 600 · `text-h2` | 20 / 600 · `titleLarge` |
| **subtitle** | Subtítulo de card | Geist Sans | 16 / 600 · `font-semibold` | 16 / 600 · `titleMedium` |
| **body-lg** | Texto base (Android por defecto) | Geist Sans | 16 / 440 · `text-body-lg` | 16 / 450 · `bodyLarge` |
| **body** | Texto base web / secundario Android | Geist Sans | 14 / 440 · `text-body` | 14 / 440 · `bodyMedium` |
| **label** | Botón / etiqueta de acción | Geist Sans | 14 / 600 · `text-label` | 14 / 600 · `labelLarge` |
| **caption** | Pie, ayuda, metadato | Geist Sans | 12 / 500 · `text-caption` | 13 / 500 · `labelMedium` |
| **overline/badge** | Badge, overline | Geist Sans | 12 / 600 · `text-xs font-semibold` | 12 / 600 · `labelSmall` |

\* `displaySmall` se mapea sólo como fallback M3; en pantalla el importe usa siempre `RecreType.importe` (mono tabular). La escala Android es la web **+1 paso** por uso en mano.

**Reglas:** (a) TODA cifra (importe/contador/%) usa Geist **Mono** con `tnum`/`tabular-nums` para que los dígitos no se desplacen en count-up ni descuadres. (b) El símbolo `€` va en color *muted*, el dígito en *foreground* (composición en el componente: `AnnotatedString` en Android, dos `<span>` en web). (c) Miles es-ES `1.234,56`. (d) El resto de UI es Geist **Sans**.

**Android**
```kotlin
package com.recre.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.recre.app.R

/**
 * Tipografía Recre — "Confianza Industrial".
 *
 * Dos familias:
 *  - Geist Sans para TODA la UI (texto, labels, títulos).
 *  - Geist Mono con cifras tabulares ("tnum") para TODO importe/contador/porcentaje,
 *    de modo que los dígitos no bailen al actualizarse (count-up, descuadres).
 *
 * Las fuentes son variables (un único fichero por familia). Hay que depositar en
 * res/font/:  geist_variable.ttf  y  geist_mono_variable.ttf  (los .woff de web no
 * sirven en Android; exportar/convertir las variables .ttf de Geist).
 * El peso se selecciona por eje de variación "wght", por eso cada estilo declara
 * su FontWeight y Compose lo resuelve sobre el mismo fichero.
 *
 * Escala Android = escala web +1 paso (pantalla a un brazo, en mano):
 *   importe 40/700 tabular · H1 24/700 · H2 20/600 · body 16/450 · caption 13/500.
 */

private fun geistVariable(weight: FontWeight) = Font(
    R.font.geist_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun geistMonoVariable(weight: FontWeight) = Font(
    R.font.geist_mono_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Familia de interfaz: todo lo que NO es una cifra. */
val GeistSans = FontFamily(
    geistVariable(FontWeight.W400),
    geistVariable(FontWeight.W450),
    geistVariable(FontWeight.W500),
    geistVariable(FontWeight.W600),
    geistVariable(FontWeight.W700),
)

/** Familia monoespaciada: reservada a cifras (importes, contadores, %). */
val GeistMono = FontFamily(
    geistMonoVariable(FontWeight.W500),
    geistMonoVariable(FontWeight.W600),
    geistMonoVariable(FontWeight.W700),
)

/** Activa los dígitos tabulares de Geist Mono ("tnum"): ancho fijo por glifo. */
private val tabularNums = FontFeatureSettings("tnum")

private val lineHeightTrim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Estilos NOMBRADOS de dinero/cifras. No forman parte del Typography de M3
 * (que no tiene slots para esto); se consumen directamente:
 *   Text(formatEur(importe), style = RecreType.importe)
 * El símbolo € va en color "muted" y el dígito en "foreground" — eso lo decide
 * el componente con AnnotatedString; aquí sólo se fija forma y métrica.
 */
object RecreType {
    /** Importe protagonista (cabecera de recaudación, KPI). 40/700 tabular. */
    val importe = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W700,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.em,
        fontFeatureSettings = tabularNums,
        lineHeightStyle = lineHeightTrim,
    )

    /** Importe en lista/fila (cards de cifras, totales de denominaciones). 22/600 tabular. */
    val importeMedium = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = tabularNums,
        lineHeightStyle = lineHeightTrim,
    )

    /** Cifra secundaria inline (subtotales, contadores, %). 16/500 tabular. */
    val cifra = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = tabularNums,
        lineHeightStyle = lineHeightTrim,
    )

    /** Cifra menor / metadato numérico (fechas mono, ids). 13/500 tabular. */
    val cifraCaption = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = tabularNums,
        lineHeightStyle = lineHeightTrim,
    )
}

/**
 * Typography de Material 3 mapeado a Geist Sans con la escala Android (+1 paso).
 * Convención de mapeo:
 *   displaySmall  -> importe (sólo como fallback M3; usar RecreType.importe en cifras)
 *   headlineMedium-> H1 24/700
 *   titleLarge    -> H2 20/600
 *   titleMedium   -> subtítulo de card 16/600
 *   bodyLarge     -> body 16/450  (texto por defecto)
 *   bodyMedium    -> body secundario 14/440
 *   labelLarge    -> botón/label 14/600
 *   labelMedium   -> caption 13/500
 *   labelSmall    -> badge/overline 12/600
 */
val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W700,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.01).em,
        lineHeightStyle = lineHeightTrim,
    ),
    titleLarge = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    titleSmall = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W450,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W440,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    bodySmall = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W440,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    labelLarge = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    labelMedium = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        lineHeightStyle = lineHeightTrim,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
        lineHeightStyle = lineHeightTrim,
    ),
)

```

**Web**
```ts
/* ─────────────────────────────────────────────────────────────────────────────
 * globals.css — Tipografía Recre (Geist Sans + Geist Mono tabular)
 *
 * 1) BORRAR el override que mata a Geist (líneas 5-7 actuales):
 *      body { font-family: Arial, Helvetica, sans-serif; }   ← ELIMINAR
 *    El family se aplica vía Tailwind (fontFamily.sans) + las variables Geist
 *    ya inyectadas por next/font en layout.tsx (--font-geist-sans/-mono).
 *
 * 2) Añadir estos tokens dentro de @layer base { :root { … } }
 *    (la familia mono activa tabular-nums por defecto para TODA cifra).
 * ──────────────────────────────────────────────────────────────────────────── */
@layer base {
  :root {
    /* Familias (espejo de las variables de next/font) */
    --font-sans: var(--font-geist-sans), ui-sans-serif, system-ui, sans-serif;
    --font-mono: var(--font-geist-mono), ui-monospace, "SFMono-Regular", monospace;

    /* Escala tipográfica web — size / line-height / weight */
    --fs-kpi: 2.25rem;      --lh-kpi: 2.5rem;     --fw-kpi: 600;   /* 36/40/600 */
    --fs-h1: 1.5rem;        --lh-h1: 2rem;        --fw-h1: 600;    /* 24/32/600 */
    --fs-h2: 1.125rem;      --lh-h2: 1.625rem;    --fw-h2: 600;    /* 18/26/600 */
    --fs-body: 0.875rem;    --lh-body: 1.375rem;  --fw-body: 440;  /* 14/22/440 */
    --fs-body-lg: 1rem;     --lh-body-lg: 1.5rem; --fw-body-lg: 440;/* 16/24/440 */
    --fs-caption: 0.75rem;  --lh-caption: 1rem;   --fw-caption: 500;/* 12/16/500 */
    --fs-label: 0.875rem;   --lh-label: 1.25rem;  --fw-label: 600; /* 14/20/600 botón/label */
  }
}

/* Cifras tabulares: TODA cifra (importe/contador/%) usa la familia mono con tnum.
 * En componentes: <span class="font-mono tabular-nums">…</span> o las clases
 * .text-importe / .text-cifra de abajo. El símbolo € va en .text-muted-foreground
 * y el dígito en color foreground (composición en el componente). */
@layer utilities {
  .num-tabular {
    font-family: var(--font-mono);
    font-feature-settings: "tnum" 1, "cv01" 1;
    font-variant-numeric: tabular-nums;
  }

  /* Estilos nombrados de cifras */
  .text-importe {
    font-family: var(--font-mono);
    font-size: var(--fs-kpi);
    line-height: var(--lh-kpi);
    font-weight: 600;
    letter-spacing: -0.01em;
    font-feature-settings: "tnum" 1;
    font-variant-numeric: tabular-nums;
  }
  .text-cifra {
    font-family: var(--font-mono);
    font-size: var(--fs-body-lg);
    line-height: var(--lh-body-lg);
    font-weight: 500;
    font-feature-settings: "tnum" 1;
    font-variant-numeric: tabular-nums;
  }
  .text-cifra-caption {
    font-family: var(--font-mono);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    font-weight: 500;
    font-feature-settings: "tnum" 1;
    font-variant-numeric: tabular-nums;
  }

  /* Estilos de texto UI (Geist Sans) */
  .text-kpi    { font-size: var(--fs-kpi);    line-height: var(--lh-kpi);    font-weight: 600; letter-spacing: -0.01em; }
  .text-h1     { font-size: var(--fs-h1);     line-height: var(--lh-h1);     font-weight: 600; letter-spacing: -0.01em; }
  .text-h2     { font-size: var(--fs-h2);     line-height: var(--lh-h2);     font-weight: 600; }
  .text-body   { font-size: var(--fs-body);   line-height: var(--lh-body);   font-weight: 440; }
  .text-body-lg{ font-size: var(--fs-body-lg);line-height: var(--lh-body-lg);font-weight: 440; }
  .text-caption{ font-size: var(--fs-caption);line-height: var(--lh-caption);font-weight: 500; }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * tailwind.config.ts — extender theme.extend (combinar con la config existente)
 * Mapea las familias Geist (hoy NO declaradas → body caía a Arial) y publica la
 * escala tipográfica como fontSize tokens con [size, { lineHeight, fontWeight }].
 * ──────────────────────────────────────────────────────────────────────────── */
// theme: {
//   extend: {
//     fontFamily: {
//       sans: ["var(--font-geist-sans)", "ui-sans-serif", "system-ui", "sans-serif"],
//       mono: ["var(--font-geist-mono)", "ui-monospace", "SFMono-Regular", "monospace"],
//     },
//     fontSize: {
//       // [fontSize, { lineHeight, fontWeight, letterSpacing }]
//       kpi:     ["2.25rem",  { lineHeight: "2.5rem",   fontWeight: "600", letterSpacing: "-0.01em" }],
//       h1:      ["1.5rem",   { lineHeight: "2rem",     fontWeight: "600", letterSpacing: "-0.01em" }],
//       h2:      ["1.125rem", { lineHeight: "1.625rem", fontWeight: "600" }],
//       "body-lg":["1rem",    { lineHeight: "1.5rem",   fontWeight: "440" }],
//       body:    ["0.875rem", { lineHeight: "1.375rem", fontWeight: "440" }],
//       caption: ["0.75rem",  { lineHeight: "1rem",     fontWeight: "500" }],
//       label:   ["0.875rem", { lineHeight: "1.25rem",  fontWeight: "600" }],
//     },
//     fontFeatureSettings: { tabular: '"tnum" 1' }, // helper opcional
//   },
// },
```

> Materializa SOLO el grupo Tipografía; color/forma/motion quedan a otros grupos.

ANDROID (Type.kt — reemplaza el `Typography()` placeholder):
- Requiere depositar las fuentes variables en `android/app/src/main/res/font/`: `geist_variable.ttf` y `geist_mono_variable.ttf`. Los `.woff` de web NO valen en Android; hay que exportar/convertir los `.ttf` variables de Geist (license OFL). Sin esos dos ficheros, `R.font.geist_variable`/`R.font.geist_mono_variable` no resuelven y NO compila — es el único prerequisito.
- Usa fuentes variables con eje `wght` (`FontVariation.weight`), así un solo fichero por familia cubre 400–700; requiere `minSdk 26+` para `FontVariation` (si el proyecto soporta <26, sustituir por ficheros estáticos por peso). Confírmese el `minSdk` en `app/build.gradle.kts`.
- `FontFeatureSettings("tnum")` activa los dígitos tabulares de Geist Mono.
- Añadidos pesos no estándar `W440`/`W450` (body) vía `FontWeight(440)`/`FontWeight(450)`; con fuente variable se interpolan correctamente. Si se usaran ficheros estáticos, redondear a W400.
- M3 no tiene slots para "importe"/"cifra": van en `object RecreType` y se consumen directos. El mapeo a `Typography` M3 cubre el resto de componentes existentes (titleMedium/bodySmall/labelSmall ya usados en LocalCard, MaquinaCard, EstadoMaquinaBadge, DenominacionesScreen).
- `Theme.kt` ya pasa `typography = Typography` (val homónimo); no requiere cambios para este grupo.

WEB:
- Acción imprescindible: BORRAR `body { font-family: Arial, Helvetica, sans-serif; }` de globals.css (líneas 5-7). Hoy Geist se carga en layout.tsx pero ese override lo anula.
- En tailwind.config.ts NO hay `fontFamily` declarado: añadir `fontFamily.sans = var(--font-geist-sans)` y `fontFamily.mono = var(--font-geist-mono)` (bloque comentado incluido). Con `fontFamily.sans` mapeado, el `antialiased` + variables de layout.tsx ya rinden Geist en todo el body.
- Cifras: la regla de oro es familia **mono + tabular-nums** en TODA cifra. Provistas utilidades `.num-tabular`/`.text-importe`/`.text-cifra` para no depender de recordar `font-mono tabular-nums` suelto (hoy es ad hoc en celdas de dinero).
- `kpi-card.tsx` hoy usa `text-2xl` (24px); el target KPI es 36/600 → migrar a `.text-importe` (cifra) o `text-kpi` (texto). Refactor de consumo fuera del alcance de este gruopo de tokens, pero los tokens ya lo soportan.
- Tailwind v3 (no v4): los `fontSize` tokens con tupla `[size, { lineHeight, fontWeight }]` son válidos; generan `text-kpi`, `text-h1`, etc.

Pesos del prompt respetados: web KPI 36/600, H1 24/600, H2 18/600, body 14/440, caption 12/500; Android (+1) importe 40/700 tabular, H1 24/700, body 16/450, caption 13/500.


---

## Fase 3 — Forma, espaciado y elevación (tokens)  ·  `fase3-forma-espaciado-elevacion`

## Forma, espaciado y elevación — tokens por rol

### Radios (forma)

| Token (web) | Valor web | Slot M3 (Android) | Valor Android | Uso |
|---|---|---|---|---|
| `--radius-sm` / `rounded-sm` | 6px | — (acotado a 12) | — | Chips, badges, inputs compactos |
| `--radius-md` / `rounded-md` | 8px | `extraSmall`, `small` | 12dp | Botones, inputs, selects |
| `--radius-lg` / `rounded-lg` (`--radius`) | 12px | `medium` | 16dp | Cards, popover, dropdown |
| `--radius-xl` / `rounded-xl` | 16px | `large`, `extraLarge` | 20dp | Dialog, sheet/drawer, contenedores destacados |
| `PillShape` (Android) / `rounded-full` | full | `RoundedCornerShape(50%)` | stadium | Chips de estado (EstadoMaquinaBadge) — excepción legítima |

Nota: web usa 6/8/12/16; Android usa 12/16/20 (+1 paso de marca, mínimo 12dp). El radio web 6px no tiene equivalente Android (allí el mínimo es 12dp).

### Espaciado (rejilla 4/8/12/16/24/32)

| Token web | Valor | Equivalente Tailwind nativo | Uso típico |
|---|---|---|---|
| `--space-1` / `grid-1` | 4px | `1` | Gap fino (icono↔texto), `py-0.5` en pills |
| `--space-2` / `grid-2` | 8px | `2` | Gap entre filas de lista, gap chips |
| `--space-3` / `grid-3` | 12px | `3` | Padding compacto, gap secciones |
| `--space-4` / `grid-4` | 16px | `4` | Padding base de card/contenedor |
| `--space-6` / `grid-6` | 24px | `6` | Padding de página, separación de bloques |
| `--space-8` / `grid-8` | 32px | `8` | Márgenes de sección amplios |

En Android la rejilla es la misma (4/8/12/16/24/32 dp); cards usan padding 16dp, separaciones 8/12dp, coherente con el grounding actual.

### Elevación

| Token | Light | Dark | Uso |
|---|---|---|---|
| `--elevation-flat` / `shadow-none` | sin sombra | sin sombra | Superficie base |
| `--elevation-border` / `shadow-border` | borde 1px `--border` | borde 1px `--border` | Cards y superficies elevadas — light: borde; dark: además sube surface-1→surface-2 (luminancia) |
| `--elevation-overlay` / `shadow-overlay` | sombra suave | sombra densa (negro) | Popover, dropdown, Command palette (Cmd+K) |
| `--elevation-modal` / `shadow-modal` | sombra media | sombra muy densa | Dialog, sheet/drawer, alert-dialog |

Regla: **sombras SOLO en overlays** (popover/menú/sheet/Cmd+K). Cards y botones pierden `shadow` y se apoyan en borde 1px (light) / luminancia de surface (dark). Android: la elevación es luminancia de superficie (surface-1→surface-2), sin `shadow` salvo en overlays nativos (ModalBottomSheet/Dialog).

**Android**
```kotlin
package com.recre.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Sistema de forma de "Confianza Industrial" para Android.
 *
 * Material3 expone 5 slots de forma (extraSmall..extraLarge). La identidad
 * Recre solo define tres radios de marca —12 / 16 / 20 dp— por lo que los
 * mapeamos a los slots que de verdad usan los componentes (cards, sheets,
 * diálogos, botones). Los radios pequeños de M3 se acotan para que ningún
 * componente caiga por debajo del mínimo de marca (12 dp).
 *
 * Mapeo de slots -> uso real:
 *  - extraSmall (12): chips, badges, campos, contenedores compactos.
 *  - small      (12): botones, contenedores compactos estándar.
 *  - medium     (16): cards (LocalCard, MaquinaCard, CifrasResumenCard…).
 *  - large      (20): bottom sheets, diálogos, contenedores destacados.
 *  - extraLarge (20): superficies grandes / FAB extendidos.
 *
 * Los componentes ya NO deben hardcodear RoundedCornerShape: deben leer
 * de MaterialTheme.shapes (p.ej. SignaturePad -> shapes.medium en vez de
 * RoundedCornerShape(12.dp)). El pill de EstadoMaquinaBadge es la única
 * excepción legítima (RoundedCornerShape(50) = totalmente redondeado, que
 * no es un radio de marca sino una forma "stadium" para chips de estado).
 */

// Radios de marca (única fuente de verdad; no repetir literales en componentes).
object RecreRadii {
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
}

/**
 * Forma "stadium" para chips/badges de estado (EstadoMaquinaBadge).
 * Se expone tokenizada para no volver a escribir RoundedCornerShape(50)
 * suelto por los componentes.
 */
val PillShape = RoundedCornerShape(percent = 50)

val RecreShapes = Shapes(
    extraSmall = RoundedCornerShape(RecreRadii.sm),
    small = RoundedCornerShape(RecreRadii.sm),
    medium = RoundedCornerShape(RecreRadii.md),
    large = RoundedCornerShape(RecreRadii.lg),
    extraLarge = RoundedCornerShape(RecreRadii.lg),
)

```

**Web**
```ts
/* ============================================================================
 * globals.css  —  Fase 3: Forma, espaciado y elevación (tokens por ROL)
 * Añadir dentro de :root (light). No dependen de modo salvo la elevación.
 * ==========================================================================*/
:root {
  /* --- Radios de marca (web: 6 / 8 / 12 / 16) --- */
  --radius-sm: 6px;    /* chips, badges, inputs compactos */
  --radius-md: 8px;    /* botones, inputs, selects */
  --radius-lg: 12px;   /* cards, popover, dropdown */
  --radius-xl: 16px;   /* dialog, sheet/drawer, contenedores destacados */

  /* Compat shadcn: --radius sigue siendo el ancla "lg" de los componentes ui/ */
  --radius: var(--radius-lg);

  /* --- Rejilla de espaciado (4 / 8 / 12 / 16 / 24 / 32) --- */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;

  /* --- Elevación (light: borde 1px; sombras SOLO en overlays) --- */
  --elevation-flat: 0 0 0 0 transparent;             /* cards/superficies: sin sombra, usar borde 1px */
  --elevation-border: 0 0 0 1px var(--border);       /* "elevación" de superficie via borde */
  --elevation-overlay:                                /* popover, dropdown, command (Cmd+K) */
    0 4px 12px -2px rgb(15 23 42 / 0.10),
    0 2px 4px -2px rgb(15 23 42 / 0.06);
  --elevation-modal:                                  /* dialog, sheet, alert-dialog */
    0 16px 40px -8px rgb(15 23 42 / 0.18),
    0 4px 12px -4px rgb(15 23 42 / 0.10);
}

/* --- Elevación en dark: la "elevación" es luminancia de surface, no sombra ---
 * Las superficies elevadas suben de surface-1 a surface-2 en vez de proyectar
 * sombra. Solo los overlays conservan sombra (más densa por el fondo oscuro). */
.dark {
  --elevation-flat: 0 0 0 0 transparent;
  --elevation-border: 0 0 0 1px var(--border);
  --elevation-overlay:
    0 4px 16px -2px rgb(0 0 0 / 0.55),
    0 2px 6px -2px rgb(0 0 0 / 0.40);
  --elevation-modal:
    0 20px 48px -8px rgb(0 0 0 / 0.65),
    0 6px 16px -4px rgb(0 0 0 / 0.45);
}

/* ============================================================================
 * tailwind.config.ts  —  theme.extend (sustituye el bloque borderRadius actual)
 * ==========================================================================*/
// theme: {
//   extend: {
//     borderRadius: {
//       // Radios de marca por ROL (6 / 8 / 12 / 16)
//       sm: "var(--radius-sm)",   // 6px  — chips, badges, inputs compactos
//       md: "var(--radius-md)",   // 8px  — botones, inputs, selects
//       lg: "var(--radius-lg)",   // 12px — cards, popover, dropdown
//       xl: "var(--radius-xl)",   // 16px — dialog, sheet/drawer
//     },
//     spacing: {
//       // Rejilla canónica 4/8/12/16/24/32 (alias semánticos; los nativos 1..8 siguen válidos)
//       "grid-1": "var(--space-1)",
//       "grid-2": "var(--space-2)",
//       "grid-3": "var(--space-3)",
//       "grid-4": "var(--space-4)",
//       "grid-6": "var(--space-6)",
//       "grid-8": "var(--space-8)",
//     },
//     boxShadow: {
//       // Elevación SOLO en overlays; superficies usan borde 1px (ver shadow-none + border)
//       none: "none",
//       border: "var(--elevation-border)",
//       overlay: "var(--elevation-overlay)", // popover, dropdown, command palette
//       modal: "var(--elevation-modal)",     // dialog, sheet, alert-dialog
//     },
//   },
// },
//
// MIGRACIÓN de componentes ui/ (Fase 3):
//   - Card:   quitar `shadow` -> usar `border` + `shadow-none` (rounded-lg = 12px).
//   - Button: quitar `shadow`; mantener rounded-md (8px).
//   - Dialog/AlertDialog content: rounded-xl (16px) + `shadow-modal`.
//   - Popover/Dropdown/Command (Cmd+K): rounded-lg + `shadow-overlay`.
//   - Badge: rounded-sm (6px).
```

> Alcance: solo el grupo "Forma, espaciado y elevación". No se tocan color/tipografía/motion (otras fases).

Ficheros destino:
- Android (nuevo): /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Shape.kt — hoy NO existe (radios M3 por defecto). Tras crearlo, hay que pasar `shapes = RecreShapes` a `MaterialTheme(...)` en /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Theme.kt (línea 32, junto a colorScheme/typography). Verifiqué que ese MaterialTheme aún no recibe parámetro `shapes`.
- Web (editar): /home/a/Escritorio/recre-main/web/src/app/globals.css (añadir tokens en :root y .dark) y /home/a/Escritorio/recre-main/web/tailwind.config.ts (sustituir el bloque `borderRadius` actual de las líneas 70-74, que solo define lg/md/sm derivados de `--radius`, por el bloque con sm/md/lg/xl + spacing + boxShadow).

Decisiones de mapeo:
- Android tiene 5 slots M3 pero la marca solo define 3 radios (12/16/20). Mapeo: extraSmall+small→12, medium→16, large+extraLarge→20. Así ningún componente baja del mínimo de marca (12dp) ni hay radios fuera de escala.
- Web mantiene `--radius` = `--radius-lg` (12px) por compatibilidad: los componentes shadcn ui/ derivan md/sm de `--radius`, así que el ancla debe seguir existiendo. Card pasa de `rounded-xl` (12) a `rounded-lg` (que ahora también es 12) — sin cambio visual, pero ya tokenizado.
- Elevación cumple la regla "sombras solo en overlays": defino `shadow-border` para superficies (borde 1px en light) y reservo sombra real a overlay/modal. En dark las sombras de overlay son más densas (fondo #0B0C0E) y las superficies suben de surface-1 a surface-2 (luminancia) en vez de proyectar sombra.

Migraciones pendientes que habilitan estos tokens (fuera del entregable de tokens, pero necesarias para que surtan efecto):
- web: quitar `shadow` de card.tsx y button.tsx; aplicar `shadow-overlay` a popover/dropdown/command y `shadow-modal` a dialog/alert-dialog/sheet; badge.tsx a `rounded-sm`.
- android: SignaturePad.kt usa `RoundedCornerShape(12.dp)` hardcode → debe leer `MaterialTheme.shapes.medium`; EstadoMaquinaBadge.kt `RoundedCornerShape(50)` → `PillShape`; AveriaUi ya usa `shapes.small` (ahora 12dp, correcto).

Los valores rgb() de sombra usan slate-900 (15 23 42) en light y negro puro en dark; son tintes de sombra neutros, no tokens de color de marca, por eso van inline y no como rol de paleta.


---

## Motion — duraciones, easings y animaciones firma (Recre · Confianza Industrial)  ·  `motion`

## Motion — tokens compartidos (web ↔ Android)

### Duraciones y easings

| Token | Valor | Web | Android | Uso |
|---|---|---|---|---|
| `duration.fast` | 120ms | `--motion-duration-fast` | `RecreMotion.DurationFast` | tap, chips, fade de popover/Cmd+K |
| `duration.default` | 150ms | `--motion-duration-default` | `RecreMotion.DurationDefault` | color/opacidad, crossfade, hover, entrada de cards |
| `duration.slow` | 180ms | `--motion-duration-slow` | `RecreMotion.DurationSlow` | transiciones de layout |
| `ease.standard` | `cubic-bezier(0.2,0,0,1)` | `--motion-ease-standard` | `RecreMotion.EasingStandard` | curva de marca (entra rápido, frena suave, sin overshoot) |
| `ease.accelerate` | `cubic-bezier(0.4,0,1,1)` | `--motion-ease-accelerate` | `RecreMotion.EasingAccelerate` | elementos que salen |
| `ease.decelerate` | `cubic-bezier(0,0,0,1)` | `--motion-ease-decelerate` | `RecreMotion.EasingDecelerate` | elementos que entran |

### Animaciones firma

| Animación | Disparador | Duración | Web | Android | Notas |
|---|---|---|---|---|---|
| **count-up** | respuesta del servidor (neto / `parte_empresa`) | 600ms | `@number-flow/react` (presentación) | `RecreMotion.countUp()` + `animateFloat` tabular | solo presenta el valor ya calculado server-side; nunca recalcula |
| **popover / Cmd+K** | abrir popover, command palette | 120ms | `.motion-popover-in` (fade + 4px) | `RecreMotion.popoverFade()` + `popoverOffset()` | fade + 4px, **sin rebote** (tween, no spring) |
| **offline-pulse** | badge offline-stale (warning/ámbar) | 1600ms ½ciclo, loop | `.motion-offline-pulse` | `RecreMotion.offlinePulse()` `Reverse` | pulso lento del alpha (1 → 0.45); es **warning**, no danger |
| **sync-spin** | sincronización en curso | 900ms/giro, loop | `.motion-sync-spin` | `RecreMotion.syncSpin()` lineal | giro continuo a velocidad constante |
| **success-flash** | al sincronizar correctamente | 900ms | `.motion-success-flash` | `RecreMotion.successFlash()` + `flashTint` | pico de tinte `success` (18%) y vuelta; verde **solo** confirma |
| **danger-shake** | descuadre de denominaciones | 400ms | `.motion-danger-shake` | `RecreMotion.dangerShake()` + `Modifier.offset` | oscilación ±8px que decae; rojo **solo** descuadre/error |

### reduced-motion (cómo se desactiva)

| Plataforma | Mecanismo | Resultado |
|---|---|---|
| Web | `@media (prefers-reduced-motion: reduce)` + `MotionConfig reducedMotion="user"` (motion 12.x) | animaciones firma a `none`; transiciones a 0.01ms; loops a 1 iteración |
| Android | `LocalAccessibilityManager` / `animator_duration_scale=0` → `RecreMotionState.reducedMotion` | `decorative()` cambia el spec a `tween(0)` (snap al valor final); el estado/color/icono se mantienen |

**Android**
```kotlin
package com.recre.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.IntOffset

/**
 * Tokens de movimiento de Recre ("Confianza Industrial"): calmado y funcional.
 *
 * M3 no tiene slots para motion semantico de marca (count-up, shake de descuadre, etc.),
 * asi que se modela aqui como capa propia. Cuando se suba el Compose BOM a >= 2025.10.00
 * estas duraciones/easings pueden delegar en MaterialTheme.motionScheme; el contrato
 * publico (RecreMotion.*) se mantiene para no tocar los call sites.
 */
object RecreMotion {

    // --- Duraciones (ms). Rango de marca 120-180; el resto deriva de el. ---
    const val DurationInstant: Int = 0
    const val DurationFast: Int = 120      // tap, chips, fade de popover/Cmd+K
    const val DurationDefault: Int = 150   // color/opacidad, crossfade, entrada de cards
    const val DurationSlow: Int = 180      // transiciones de layout algo mayores

    // Animaciones firma con tempo propio fuera del rango base (lo pide su semantica).
    const val DurationCountUp: Int = 600   // count-up del neto/parte_empresa al responder el servidor
    const val DurationSuccessFlash: Int = 900  // flash success al sincronizar (ida+vuelta)
    const val DurationDangerShake: Int = 400   // shake de descuadre
    const val DurationOfflinePulse: Int = 1600 // medio ciclo del pulso offline (lento)
    const val DurationSyncSpin: Int = 900      // un giro completo del spinner de sync

    /** Desplazamiento del fade de popover/Cmd+K: aparece 4px y asienta, sin rebote. */
    const val PopoverOffsetPx: Int = 4

    // --- Easings. "standard" = la curva de marca cubic-bezier(0.2, 0, 0, 1). ---
    /** Curva estandar de Recre: entra rapido y frena suave, sin overshoot. */
    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Acelera al salir (elementos que desaparecen). */
    val EasingAccelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    /** Entra desacelerando (elementos que aparecen). */
    val EasingDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    /** Para el shake: simetrico, mantiene la energia en ambos sentidos. */
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // --- AnimationSpecs reutilizables (lo que consumen los animate*AsState). ---
    fun <T> fast(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationFast, easing = EasingStandard)

    fun <T> default(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationDefault, easing = EasingStandard)

    fun <T> slow(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationSlow, easing = EasingStandard)

    /** Spec del count-up tabular: el valor ya viene calculado del servidor, esto es solo presentacion. */
    fun <T> countUp(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationCountUp, easing = EasingStandard)

    /** Entrada de popover/Cmd+K: fade del alpha. El offset se anima aparte (ver popoverOffset). */
    fun <T> popoverFade(): FiniteAnimationSpec<T> =
        tween(durationMillis = DurationFast, easing = EasingDecelerate)

    /** Slide de 4px -> 0 del popover/Cmd+K, sin rebote (tween, no spring). */
    fun popoverOffset(): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = DurationFast, easing = EasingDecelerate)

    /**
     * Pulso lento del badge offline-stale (warning/ambar). Atenua el alpha y vuelve,
     * en bucle. Usar con rememberInfiniteTransition.animateFloat(0.45f .. 1f).
     */
    fun offlinePulse(): AnimationSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = DurationOfflinePulse, easing = EasingStandard),
        repeatMode = RepeatMode.Reverse,
    )

    /** Giro continuo del spinner de sincronizacion (0f -> 360f, lineal para velocidad constante). */
    fun syncSpin(): AnimationSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = DurationSyncSpin, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    )

    /**
     * Flash success al sincronizar: pico de tinte verde y vuelta a 0.
     * Animar un factor 0f..1f y mezclarlo sobre el fondo (lerp con success).
     */
    fun successFlash(): FiniteAnimationSpec<Float> = keyframes {
        durationMillis = DurationSuccessFlash
        0f at 0 using EasingDecelerate
        1f at (DurationSuccessFlash / 3) using EasingStandard  // pico rapido
        0f at DurationSuccessFlash using EasingAccelerate       // desvanece lento
    }

    /**
     * Shake danger ante descuadre: 4 oscilaciones horizontales que decaen.
     * Animar un offset en px y aplicarlo como Modifier.offset { IntOffset(value, 0) }.
     * El pico (8 px) es percibible sin marear; respeta reduced-motion (ver rememberRecreMotion).
     */
    fun dangerShake(): FiniteAnimationSpec<Float> = keyframes {
        durationMillis = DurationDangerShake
        0f at 0
        -8f at 60
        8f at 120
        -6f at 200
        4f at 280
        0f at DurationDangerShake using EasingEmphasized
    }
}

/**
 * Estado de motion resuelto para reduced-motion. Cuando el usuario pide menos animacion
 * (Ajustes de Android > "Eliminar animaciones": Settings.Global animator_duration_scale = 0),
 * Compose lo refleja en AccessibilityManager. Aqui lo exponemos como flag para que las
 * animaciones firma puedan degradarse a un cambio instantaneo de estado.
 */
data class RecreMotionState(
    val reducedMotion: Boolean,
) {
    /** En reduced-motion, las animaciones decorativas se saltan (snap al valor final). */
    fun <T> decorative(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reducedMotion) tween(durationMillis = RecreMotion.DurationInstant) else spec
}

val LocalRecreMotion = compositionLocalOf { RecreMotionState(reducedMotion = false) }

/**
 * Resuelve si hay que reducir el movimiento leyendo la escala de animaciones del sistema.
 * Llamar en RecreTheme y proveer LocalRecreMotion con el resultado.
 */
@Composable
@ReadOnlyComposable
fun rememberRecreMotionState(): RecreMotionState {
    val manager = LocalAccessibilityManager.current
    // animator_duration_scale = 0 => el sistema devuelve un timeout sin escalar:
    // si recommend == original, no se aplica escalado y se asume animaciones activas.
    val reduced = manager?.let {
        it.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = 1000L,
            containsIcons = false,
            containsText = false,
            containsControls = false,
        ) == 1000L && isAnimationScaleZero()
    } ?: false
    return RecreMotionState(reducedMotion = reduced)
}

/**
 * Mezcla un color base con el tinte de flash segun un factor 0f..1f (presentacion del success-flash).
 * No anima: solo combina; el factor lo provee successFlash().
 */
fun flashTint(base: Color, tint: Color, factor: Float): Color = Color(
    red = base.red + (tint.red - base.red) * factor,
    green = base.green + (tint.green - base.green) * factor,
    blue = base.blue + (tint.blue - base.blue) * factor,
    alpha = base.alpha,
)

// La lectura directa de Settings.Global requiere Context; en la practica se inyecta desde el
// nivel de Activity/Theme. Stub para mantener el fichero autocontenido y compilable.
private fun isAnimationScaleZero(): Boolean = false
```

**Web**
```ts
/* ============================================================================
   Motion tokens — Recre "Confianza Industrial"
   Pegar en web/src/app/globals.css (dentro de @layer base, junto a :root/.dark)
   Las animaciones firman: count-up se hace con @number-flow/react (presentacion
   del valor server-side); aqui van pulse/flash/shake/spin/popover y los tokens.
   ============================================================================ */

:root {
  /* --- Duraciones (rango de marca 120-180ms) --- */
  --motion-duration-instant: 0ms;
  --motion-duration-fast: 120ms;     /* tap, fade de popover/Cmd+K */
  --motion-duration-default: 150ms;  /* color/opacidad, crossfade, hover */
  --motion-duration-slow: 180ms;     /* transiciones de layout */

  /* Animaciones firma con tempo propio */
  --motion-duration-countup: 600ms;
  --motion-duration-success-flash: 900ms;
  --motion-duration-danger-shake: 400ms;
  --motion-duration-offline-pulse: 1600ms;
  --motion-duration-sync-spin: 900ms;

  /* --- Easings --- */
  --motion-ease-standard: cubic-bezier(0.2, 0, 0, 1); /* curva de marca */
  --motion-ease-accelerate: cubic-bezier(0.4, 0, 1, 1);
  --motion-ease-decelerate: cubic-bezier(0, 0, 0, 1);
  --motion-ease-linear: linear;

  /* Desplazamiento de entrada de popover/Cmd+K (fade + 4px, sin rebote) */
  --motion-popover-offset: 4px;

  /* Transiciones compuestas listas para usar */
  --motion-transition-colors:
    color var(--motion-duration-default) var(--motion-ease-standard),
    background-color var(--motion-duration-default) var(--motion-ease-standard),
    border-color var(--motion-duration-default) var(--motion-ease-standard),
    fill var(--motion-duration-default) var(--motion-ease-standard),
    stroke var(--motion-duration-default) var(--motion-ease-standard);
  --motion-transition-popover:
    opacity var(--motion-duration-fast) var(--motion-ease-decelerate),
    transform var(--motion-duration-fast) var(--motion-ease-decelerate);
}

/* --- Keyframes de las animaciones firma --- */

/* Popover / Cmd+K: aparece 4px y asienta, sin rebote */
@keyframes recre-popover-in {
  from { opacity: 0; transform: translateY(var(--motion-popover-offset)); }
  to   { opacity: 1; transform: translateY(0); }
}

/* Badge offline-stale (warning/ambar): pulso lento del opacity */
@keyframes recre-offline-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.45; }
}

/* Spinner de sincronizacion: giro continuo a velocidad constante */
@keyframes recre-sync-spin {
  to { transform: rotate(360deg); }
}

/* Flash success al sincronizar: pico de tinte verde y vuelta */
@keyframes recre-success-flash {
  0%   { background-color: transparent; }
  30%  { background-color: color-mix(in srgb, var(--success) 18%, transparent); }
  100% { background-color: transparent; }
}

/* Shake danger ante descuadre: oscilacion horizontal que decae */
@keyframes recre-danger-shake {
  0%   { transform: translateX(0); }
  15%  { transform: translateX(-8px); }
  30%  { transform: translateX(8px); }
  50%  { transform: translateX(-6px); }
  70%  { transform: translateX(4px); }
  100% { transform: translateX(0); }
}

/* --- Clases utilitarias (consumen los tokens) --- */
.motion-popover-in   { animation: recre-popover-in var(--motion-duration-fast) var(--motion-ease-decelerate) both; }
.motion-offline-pulse { animation: recre-offline-pulse var(--motion-duration-offline-pulse) var(--motion-ease-standard) infinite; }
.motion-sync-spin     { animation: recre-sync-spin var(--motion-duration-sync-spin) var(--motion-ease-linear) infinite; }
.motion-success-flash { animation: recre-success-flash var(--motion-duration-success-flash) var(--motion-ease-standard) both; }
.motion-danger-shake  { animation: recre-danger-shake var(--motion-duration-danger-shake) var(--motion-ease-standard) both; }

/* --- reduced-motion: una sola regla apaga TODO el movimiento decorativo --- */
@media (prefers-reduced-motion: reduce) {
  .motion-popover-in,
  .motion-offline-pulse,
  .motion-sync-spin,
  .motion-success-flash,
  .motion-danger-shake {
    animation: none !important;
  }
  *,
  *::before,
  *::after {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}

/* ============================================================================
   tailwind.config.ts — theme.extend (mapea los tokens a utilidades de Tailwind)
   ============================================================================ */
/*
export default {
  theme: {
    extend: {
      transitionTimingFunction: {
        standard: "var(--motion-ease-standard)",
        accelerate: "var(--motion-ease-accelerate)",
        decelerate: "var(--motion-ease-decelerate)",
      },
      transitionDuration: {
        fast: "120ms",
        DEFAULT: "150ms",
        slow: "180ms",
      },
      keyframes: {
        "recre-popover-in": {
          from: { opacity: "0", transform: "translateY(var(--motion-popover-offset))" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "recre-offline-pulse": {
          "0%,100%": { opacity: "1" },
          "50%": { opacity: "0.45" },
        },
        "recre-sync-spin": { to: { transform: "rotate(360deg)" } },
        "recre-success-flash": {
          "0%": { backgroundColor: "transparent" },
          "30%": { backgroundColor: "color-mix(in srgb, var(--success) 18%, transparent)" },
          "100%": { backgroundColor: "transparent" },
        },
        "recre-danger-shake": {
          "0%,100%": { transform: "translateX(0)" },
          "15%": { transform: "translateX(-8px)" },
          "30%": { transform: "translateX(8px)" },
          "50%": { transform: "translateX(-6px)" },
          "70%": { transform: "translateX(4px)" },
        },
      },
      animation: {
        "popover-in": "recre-popover-in 120ms var(--motion-ease-decelerate) both",
        "offline-pulse": "recre-offline-pulse 1600ms var(--motion-ease-standard) infinite",
        "sync-spin": "recre-sync-spin 900ms linear infinite",
        "success-flash": "recre-success-flash 900ms var(--motion-ease-standard) both",
        "danger-shake": "recre-danger-shake 400ms var(--motion-ease-standard) both",
      },
    },
  },
};
*/
```

> FUENTE DE VERDAD respetada: durations 120/150/180ms y easing de marca cubic-bezier(0.2,0,0,1) tal cual en brief y visual-identity.md L83-85. Las 6 animaciones firma (count-up, popover/Cmd+K fade+4px, offline-pulse, sync-spin, success-flash, danger-shake) materializadas en ambas plataformas con el mismo vocabulario.

REGLAS DE COLOR aplicadas en el motion: success-flash usa SOLO el token --success (confirma), danger-shake se reserva a descuadre/error, y el pulso de offline es WARNING (ámbar), corrigiendo el gap detectado (hoy SyncStaleBlocker usa errorContainer/rojo para algo que es offline-stale). El motion no introduce colores literales: tinta sobre tokens por rol.

REDUCED-MOTION en ambas: web vía prefers-reduced-motion (apaga keyframes y colapsa transiciones) — combinable con MotionConfig reducedMotion="user" de motion 12.x cuando se integre; Android vía RecreMotionState.decorative() que degrada cualquier spec a tween(0) sin perder el cambio de estado (el valor salta, el icono+texto siguen comunicando).

COUNT-UP es PRESENTACIÓN, no cálculo: en web lo hace @number-flow/react (plan §3.2) y en Android RecreMotion.countUp() anima un Float tabular; ninguno recalcula la cifra — respeta el SSOT server-side. Tipografía mono tabular sigue siendo responsabilidad del token de tipografía (otro grupo).

COMPATIBILIDAD DE STACK: el código compila en el stack ACTUAL (Compose BOM 2024.11.00 / Material3 1.3.x / Kotlin 2.0.21) usando solo androidx.compose.animation.core (tween/keyframes/infiniteRepeatable/CubicBezierEasing) y androidx.compose.ui.platform.LocalAccessibilityManager — sin dependencias nuevas. El contrato público RecreMotion.* está diseñado para delegar en MaterialTheme.motionScheme (MotionScheme.expressive()) cuando se suba el BOM a >=2025.10.00 (design-system-plan §2.4/§3.1), sin cambiar los call sites.

GAP DE INTEGRACIÓN abierto (no resuelto aquí, es fuera de scope del grupo): rememberRecreMotionState() incluye un stub isAnimationScaleZero() porque la lectura fiable de Settings.Global.ANIMATOR_DURATION_SCALE necesita Context; al cablear RecreTheme hay que proveer LocalRecreMotion leyendo ese ajuste desde la Activity. Mientras tanto el flag es false (animaciones activas) por defecto seguro.

ARCHIVO DESTINO Android: nuevo /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Motion.kt (no existe hoy). Debe proveerse LocalRecreMotion en RecreTheme (Theme.kt L27-37). ARCHIVO DESTINO web: bloque CSS en /home/a/Escritorio/recre-main/web/src/app/globals.css y extensión en /home/a/Escritorio/recre-main/web/tailwind.config.ts (hoy sin keyframes/durations custom).


---

## Fase 3 - Ensamblado del tema: RecreTheme (Compose) + wiring globals.css/tailwind (web)  ·  `fase3-theme-assembly`

## Mapeo de roles → ColorScheme M3 (Android) y tokens (Web)

| Rol de marca | Light | Dark | Slot M3 (Android) | Var CSS (Web) | Uso |
|---|---|---|---|---|---|
| background | #FAFBFC | #0B0C0E | `background` | `--background` | Lienzo de la app |
| surface-1 | #FFFFFF | #131519 | `surface` | `--surface-1` / `--card` | Cards/popovers base |
| surface-2 | #F4F6F8 | #1B1E24 | `surfaceVariant` + `RecreTheme.colors.surface2` | `--surface-2` | Elevación por capa (no sombra) |
| primary | #0E7490 | #2BC4DD | `primary` | `--primary` | Acento marca ≤10% |
| on-primary | #FFFFFF | #06212A | `onPrimary` | `--primary-foreground` | Texto sobre primary |
| secondary | #E6F2F4 | #16323A | `primaryContainer`/`secondaryContainer` | `--secondary` | Superficie teñida de marca |
| success | #0E8A55 | #34D399 | `RecreTheme.colors.success` | `--success` | SOLO dinero+/cuadra |
| warning | #B45309 | #FBBF24 | `tertiary` + `RecreTheme.colors.warning` | `--warning` | SOLO pendiente/sin-firmar/offline-stale |
| danger | #DC2626 | #F87171 | `error` + `RecreTheme.colors.danger` | `--danger`/`--destructive` | SOLO error/avería/descuadre/conflicto |
| info | #2563EB | #60A5FA | `RecreTheme.colors.info` | `--info` | Informativo neutro |
| border | #E3E6EA | #262A31 | `outline` + `RecreTheme.colors.border` | `--border`/`--input` | Borde 1px (elevación light) |
| muted | #646B76 | #9AA1AD | `onSurfaceVariant` + `RecreTheme.colors.muted` | `--muted-foreground` | Texto secundario, símbolo € |
| ring | #0E7490 | #2BC4DD | (= primary) | `--ring` | Foco de teclado/accesibilidad |

## Wire-up final (lo que ensambla la Fase 3)

| Pieza | Android (`RecreTheme`) | Web |
|---|---|---|
| Color | `ColorScheme` light/dark + `CompositionLocal` para success/warning/danger/info/surface2/border/muted | `:root` y `.dark` por rol en `globals.css` + `theme.extend.colors` → `var(--...)` |
| Tipografía | `typography = RecreTypography` (Geist Sans + Geist Mono tabular) | `fontFamily.sans/mono` → `var(--font-geist-*)`; escala `fontSize` (kpi/h1/h2/body/caption); borrar override Arial |
| Forma | `shapes = RecreShapes` (12/16/20) | `borderRadius` 6/8/12/16 |
| Dynamic color | Ausente a propósito (marca fija) | n/a |
| Tema oscuro | `isSystemInDarkTheme()` + status bar por luminancia | `.dark` vía next-themes (`class`) |
| Motion | (en componentes; respetar reduced-motion) | `transitionTimingFunction.recre` 120-180ms + keyframes fade-up/flash-success/shake/pulse-slow |

**Android**
```kotlin
// ============================================================================
// Theme.kt — Ensamblado final del tema "Confianza Industrial" (azul petróleo).
// Wire-up: ColorScheme (light/dark) + Typography (Geist) + Shapes + tokens
// semánticos de dominio (success/warning/danger/info/border/muted/surface-2)
// que Material3 NO tiene como slots. dynamicColor SIEMPRE off: marca propia.
//
// Asume que Color.kt expone los tokens por rol (ver bloque de referencia al
// final), Type.kt expone `RecreTypography` y Shape.kt expone `RecreShapes`.
// ============================================================================
package com.recre.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// Tokens semánticos de dominio. Material3 no tiene slots para success/warning/
// info ni para la distinción surface-1/surface-2; los transportamos aparte vía
// CompositionLocal y se consumen con `RecreTheme.colors.success`, etc.
// Regla de color innegociable: success=dinero+/cuadra, danger=error/avería/
// descuadre, warning=pendiente/sin-firmar/offline-stale. Nunca como marca.
// ---------------------------------------------------------------------------
@Immutable
data class RecreSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val info: Color,
    val onInfo: Color,
    // surface-2: capa de elevación por encima de surface-1 (cards anidadas,
    // campos, footers). surface-1 viaja en ColorScheme.surface.
    val surface2: Color,
    // Borde y texto secundario tokenizados (ColorScheme los deriva mal del primary).
    val border: Color,
    val muted: Color,
)

private val LightSemantic = RecreSemanticColors(
    success = Color(0xFF0E8A55),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFFB45309),
    onWarning = Color(0xFFFFFFFF),
    danger = Color(0xFFDC2626),
    onDanger = Color(0xFFFFFFFF),
    info = Color(0xFF2563EB),
    onInfo = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4F6F8),
    border = Color(0xFFE3E6EA),
    muted = Color(0xFF646B76),
)

private val DarkSemantic = RecreSemanticColors(
    success = Color(0xFF34D399),
    onSuccess = Color(0xFF06212A),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF06212A),
    danger = Color(0xFFF87171),
    onDanger = Color(0xFF06212A),
    info = Color(0xFF60A5FA),
    onInfo = Color(0xFF06212A),
    surface2 = Color(0xFF1B1E24),
    border = Color(0xFF262A31),
    muted = Color(0xFF9AA1AD),
)

private val LocalRecreColors = staticCompositionLocalOf { LightSemantic }

// ---------------------------------------------------------------------------
// ColorScheme M3. Mapeo de la paleta de marca a slots Material para que TODO el
// esquema derivado (containers, outline, etc.) hable petróleo, no Indigo.
//   primary        = acento de marca (≤10% pantalla)
//   secondary      = secondary de la paleta (chips/realces suaves)
//   background      = #FAFBFC / #0B0C0E
//   surface         = surface-1 (#FFFFFF / #131519)
//   surfaceVariant  = surface-2 (#F4F6F8 / #1B1E24)  ← elevación
//   error           = danger
//   outline         = border
//   onSurfaceVariant= muted (texto secundario)
// ---------------------------------------------------------------------------
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F2F4),   // secondary: superficie teñida de marca
    onPrimaryContainer = Color(0xFF06212A),
    secondary = Color(0xFF0E7490),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6F2F4),
    onSecondaryContainer = Color(0xFF0B3A45),
    tertiary = Color(0xFFB45309),           // warning como terciario (estados ámbar)
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCEFD9),
    onTertiaryContainer = Color(0xFF5A2A05),
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF11151A),
    surface = Color(0xFFFFFFFF),            // surface-1
    onSurface = Color(0xFF11151A),
    surfaceVariant = Color(0xFFF4F6F8),     // surface-2
    onSurfaceVariant = Color(0xFF646B76),   // muted
    outline = Color(0xFFE3E6EA),            // border
    outlineVariant = Color(0xFFEDEFF2),
    error = Color(0xFFDC2626),              // danger
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E3),
    onErrorContainer = Color(0xFF7F1212),
    inverseSurface = Color(0xFF1B1E24),
    inverseOnSurface = Color(0xFFF4F6F8),
    inversePrimary = Color(0xFF2BC4DD),
    scrim = Color(0xFF000000),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF2BC4DD),
    onPrimary = Color(0xFF06212A),          // texto oscuro sobre cian
    primaryContainer = Color(0xFF16323A),   // secondary dark
    onPrimaryContainer = Color(0xFFCDE9EF),
    secondary = Color(0xFF2BC4DD),
    onSecondary = Color(0xFF06212A),
    secondaryContainer = Color(0xFF16323A),
    onSecondaryContainer = Color(0xFFCDE9EF),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF06212A),
    tertiaryContainer = Color(0xFF3A2A07),
    onTertiaryContainer = Color(0xFFFCE3B0),
    background = Color(0xFF0B0C0E),
    onBackground = Color(0xFFE8EAEE),
    surface = Color(0xFF131519),            // surface-1
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = Color(0xFF1B1E24),     // surface-2
    onSurfaceVariant = Color(0xFF9AA1AD),   // muted
    outline = Color(0xFF262A31),            // border
    outlineVariant = Color(0xFF20242A),
    error = Color(0xFFF87171),              // danger
    onError = Color(0xFF06212A),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFF7C9C9),
    inverseSurface = Color(0xFFE8EAEE),
    inverseOnSurface = Color(0xFF1B1E24),
    inversePrimary = Color(0xFF0E7490),
    scrim = Color(0xFF000000),
)

@Composable
fun RecreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // dynamicColor INTENCIONADAMENTE ausente: la identidad de marca es fija.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val semanticColors = if (darkTheme) DarkSemantic else LightSemantic

    // Status bar transparente con iconos según luminancia del background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalRecreColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RecreTypography, // Geist Sans (UI) + Geist Mono tabular (Type.kt)
            shapes = RecreShapes,         // radios 12/16/20 (Shape.kt)
            content = content,
        )
    }
}

/**
 * Acceso a los tokens semánticos de dominio fuera del catálogo M3.
 * Uso: `RecreTheme.colors.success`, `RecreTheme.colors.warning`, etc.
 */
object RecreTheme {
    val colors: RecreSemanticColors
        @Composable get() = LocalRecreColors.current
}

// ============================================================================
// REFERENCIA — contratos esperados de los otros ficheros del grupo (Fase 2).
// No se redeclaran aquí; este bloque solo documenta lo que RecreTheme consume.
//
// Type.kt:
//   private val Geist = FontFamily(Font(R.font.geist_regular, W400), ...)
//   private val GeistMono = FontFamily(Font(R.font.geist_mono, W700))  // tabular
//   val RecreTypography = Typography(
//       displayLarge = TextStyle(font=GeistMono, 40.sp, W700, FeatureSettings tnum),//importe
//       headlineLarge= TextStyle(font=Geist, 24.sp, W700),  // H1
//       titleLarge   = TextStyle(font=Geist, 18.sp, W600),  // H2
//       bodyLarge    = TextStyle(font=Geist, 16.sp, W450),  // body
//       labelSmall   = TextStyle(font=Geist, 13.sp, W500),  // caption
//   )
//
// Shape.kt:
//   val RecreShapes = Shapes(
//       small = RoundedCornerShape(12.dp),
//       medium = RoundedCornerShape(16.dp),
//       large = RoundedCornerShape(20.dp),
//   )
// ============================================================================
```

**Web**
```ts
/* ===========================================================================
 * globals.css — Tokens "Confianza Industrial" por ROL (no por valor).
 * Reemplaza el bloque @layer base { :root / .dark } del shadcn neutro.
 * Sintaxis hex para que el wiring de tailwind.config (var(--...)) sea directo.
 * IMPORTANTE: borrar el `body { font-family: Arial... }` de las líneas 5-7.
 * =========================================================================== */

@layer base {
  :root {
    /* Superficies y texto */
    --background: #FAFBFC;
    --foreground: #11151A;
    --surface-1: #FFFFFF;   /* card/popover base */
    --surface-2: #F4F6F8;   /* elevación por capa, no por sombra */
    --card: #FFFFFF;
    --card-foreground: #11151A;
    --popover: #FFFFFF;
    --popover-foreground: #11151A;

    /* Marca: acento ≤10% de pantalla */
    --primary: #0E7490;
    --primary-foreground: #FFFFFF;     /* on-primary */
    --secondary: #E6F2F4;
    --secondary-foreground: #0B3A45;
    --accent: #E6F2F4;
    --accent-foreground: #0B3A45;

    /* Texto secundario / mutado */
    --muted: #F4F6F8;
    --muted-foreground: #646B76;

    /* Roles semánticos de dominio (NO existen en shadcn base) */
    --success: #0E8A55;       --success-foreground: #FFFFFF;
    --warning: #B45309;       --warning-foreground: #FFFFFF;
    --danger: #DC2626;        --danger-foreground: #FFFFFF;
    --info: #2563EB;          --info-foreground: #FFFFFF;
    /* destructive = danger (alias para componentes shadcn existentes) */
    --destructive: #DC2626;   --destructive-foreground: #FFFFFF;

    /* Bordes / inputs / focus ring (ring = primary, no gris) */
    --border: #E3E6EA;
    --input: #E3E6EA;
    --ring: #0E7490;

    /* Radios: 6/8/12/16 — base 8 */
    --radius: 0.5rem;

    /* Sidebar realineada al primary de marca */
    --sidebar: #FFFFFF;
    --sidebar-foreground: #11151A;
    --sidebar-primary: #0E7490;
    --sidebar-primary-foreground: #FFFFFF;
    --sidebar-accent: #E6F2F4;
    --sidebar-accent-foreground: #0B3A45;
    --sidebar-border: #E3E6EA;
    --sidebar-ring: #0E7490;
  }

  .dark {
    --background: #0B0C0E;
    --foreground: #E8EAEE;
    --surface-1: #131519;
    --surface-2: #1B1E24;
    --card: #131519;
    --card-foreground: #E8EAEE;
    --popover: #131519;
    --popover-foreground: #E8EAEE;

    --primary: #2BC4DD;
    --primary-foreground: #06212A;     /* texto oscuro sobre cian */
    --secondary: #16323A;
    --secondary-foreground: #CDE9EF;
    --accent: #16323A;
    --accent-foreground: #CDE9EF;

    --muted: #1B1E24;
    --muted-foreground: #9AA1AD;

    --success: #34D399;       --success-foreground: #06212A;
    --warning: #FBBF24;       --warning-foreground: #06212A;
    --danger: #F87171;        --danger-foreground: #06212A;
    --info: #60A5FA;          --info-foreground: #06212A;
    --destructive: #F87171;   --destructive-foreground: #06212A;

    --border: #262A31;
    --input: #262A31;
    --ring: #2BC4DD;

    --sidebar: #131519;
    --sidebar-foreground: #E8EAEE;
    --sidebar-primary: #2BC4DD;
    --sidebar-primary-foreground: #06212A;
    --sidebar-accent: #16323A;
    --sidebar-accent-foreground: #CDE9EF;
    --sidebar-border: #262A31;
    --sidebar-ring: #2BC4DD;
  }
}

/* ===========================================================================
 * tailwind.config.ts — wiring por ROL (theme.extend). Reemplaza colors{} y
 * añade fontFamily, fontSize (escala), borderRadius (12/16) y motion.
 * Geist ya se carga en layout.tsx vía next/font/local.
 * =========================================================================== */

const config: Config = {
  darkMode: ["class"],
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "var(--background)",
        foreground: "var(--foreground)",
        // surfaces por capa
        "surface-1": "var(--surface-1)",
        "surface-2": "var(--surface-2)",
        card: { DEFAULT: "var(--card)", foreground: "var(--card-foreground)" },
        popover: { DEFAULT: "var(--popover)", foreground: "var(--popover-foreground)" },
        primary: { DEFAULT: "var(--primary)", foreground: "var(--primary-foreground)" },
        secondary: { DEFAULT: "var(--secondary)", foreground: "var(--secondary-foreground)" },
        muted: { DEFAULT: "var(--muted)", foreground: "var(--muted-foreground)" },
        accent: { DEFAULT: "var(--accent)", foreground: "var(--accent-foreground)" },
        // Roles semánticos de dominio (consumir por rol, nunca emerald/amber crudos)
        success: { DEFAULT: "var(--success)", foreground: "var(--success-foreground)" },
        warning: { DEFAULT: "var(--warning)", foreground: "var(--warning-foreground)" },
        danger: { DEFAULT: "var(--danger)", foreground: "var(--danger-foreground)" },
        info: { DEFAULT: "var(--info)", foreground: "var(--info-foreground)" },
        destructive: { DEFAULT: "var(--destructive)", foreground: "var(--destructive-foreground)" },
        border: "var(--border)",
        input: "var(--input)",
        ring: "var(--ring)",
        sidebar: {
          DEFAULT: "var(--sidebar)",
          foreground: "var(--sidebar-foreground)",
          primary: "var(--sidebar-primary)",
          "primary-foreground": "var(--sidebar-primary-foreground)",
          accent: "var(--sidebar-accent)",
          "accent-foreground": "var(--sidebar-accent-foreground)",
          border: "var(--sidebar-border)",
          ring: "var(--sidebar-ring)",
        },
      },
      fontFamily: {
        // Geist como base; mono tabular para TODA cifra (clase font-mono tabular-nums)
        sans: ["var(--font-geist-sans)", "system-ui", "sans-serif"],
        mono: ["var(--font-geist-mono)", "ui-monospace", "monospace"],
      },
      fontSize: {
        // Escala del design system (size, lineHeight, weight)
        kpi: ["2.25rem", { lineHeight: "2.5rem", fontWeight: "600" }],   // 36/600
        h1: ["1.5rem", { lineHeight: "2rem", fontWeight: "600" }],       // 24/600
        h2: ["1.125rem", { lineHeight: "1.625rem", fontWeight: "600" }], // 18/600
        body: ["0.875rem", { lineHeight: "1.375rem", fontWeight: "440" }], // 14/440
        caption: ["0.75rem", { lineHeight: "1rem", fontWeight: "500" }], // 12/500
      },
      borderRadius: {
        xl: "1rem",                          // 16
        lg: "0.75rem",                       // 12
        DEFAULT: "var(--radius)",            // 8
        md: "calc(var(--radius) - 2px)",     // 6
        sm: "calc(var(--radius) - 4px)",     // 4
      },
      transitionTimingFunction: {
        // curva estándar del sistema (entrada/salida sin rebote)
        recre: "cubic-bezier(0.2, 0, 0, 1)",
      },
      transitionDuration: { fast: "120ms", base: "150ms", slow: "180ms" },
      keyframes: {
        "fade-up": { from: { opacity: "0", transform: "translateY(4px)" }, to: { opacity: "1", transform: "translateY(0)" } },
        "flash-success": { "0%,100%": { backgroundColor: "transparent" }, "50%": { backgroundColor: "var(--success)" } },
        "shake": { "0%,100%": { transform: "translateX(0)" }, "25%": { transform: "translateX(-4px)" }, "75%": { transform: "translateX(4px)" } },
        "pulse-slow": { "0%,100%": { opacity: "1" }, "50%": { opacity: "0.55" } },
      },
      animation: {
        "fade-up": "fade-up 150ms cubic-bezier(0.2,0,0,1)",   // popover, Cmd+K
        "flash-success": "flash-success 600ms ease-out",       // al sincronizar
        "shake": "shake 180ms cubic-bezier(0.2,0,0,1)",        // descuadre
        "pulse-slow": "pulse-slow 2s ease-in-out infinite",    // offline
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};
```

> Decisiones del ensamblado:

1. Roles que M3 no tiene (success/warning/info y surface-1 vs surface-2): se transportan vía `RecreSemanticColors` + `staticCompositionLocalOf`, expuestos como `RecreTheme.colors.*`. Además se "duplican" en slots M3 análogos (warning→tertiary, danger→error, surface-2→surfaceVariant) para que los componentes shadcn/M3 ya existentes (CifrasResumenCard usa primaryContainer/errorContainer; EstadoMaquinaBadge usa tertiaryContainer) hereden la paleta correcta sin reescritura inmediata.

2. dynamicColor: deliberadamente NO se implementa (sin Build.VERSION ni dynamicLightColorScheme). Confirmado el gap #6: la marca es fija. Se documenta en comentario para que nadie lo "arregle".

3. Status bar: se añade el SideEffect que faltaba (gap señalado), iconos claros/oscuros según `background.luminance()`. Requiere `androidx.core:core-ktx` (ya presente) y `WindowCompat`.

4. Web — pasos manuales imprescindibles fuera del CSS/config: (a) borrar `body { font-family: Arial, Helvetica, sans-serif }` de globals.css líneas 5-7, que mata Geist; (b) `--destructive` queda como alias de `--danger` para no romper button/badge shadcn existentes mientras se migran; (c) refactor pendiente de badge.tsx y kpi-card.tsx para consumir `bg-success`/`text-warning` (tokens) en vez de emerald/amber literales — fuera del alcance del wire-up de tema, pero ya tienen el rol disponible.

5. Sidebar dark: el `--sidebar-primary` azul-violeta huérfano (oklch 0.488 0.243 264) queda eliminado; ahora apunta al cian de marca #2BC4DD.

6. Contratos asumidos (Fase 2): `RecreTypography` en Type.kt, `RecreShapes` en Shape.kt, y los tokens por rol en Color.kt. Theme.kt aquí ya NO referencia Indigo/Slate. Si Color.kt aún expone solo la paleta provisional, los `Color(0xFF...)` inline de este fichero son autosuficientes y compilan igualmente; la recomendación es moverlos a Color.kt como constantes nombradas por rol.

Validación recomendada: compilar Android con JAVA_HOME=/snap/android-studio/current/jbr (`./gradlew assembleDebug`); para web, `npm --prefix web run lint` tras quitar el override Arial. No he ejecutado builds: este entregable es el ensamblado de tema, sin tocar componentes que disparen el next build roto preexistente.

Ficheros objetivo:
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Theme.kt
- /home/a/Escritorio/recre-main/web/src/app/globals.css
- /home/a/Escritorio/recre-main/web/tailwind.config.ts



---

# Addendum correctivo (Fase 3 · ronda 2)

> Resuelve los 11 problemas transversales detectados por la verificacion adversarial: rol `state-neutral`, variantes `-text` con contraste WCAG verificado, ColorScheme Material3 completo, familias de chip, presupuesto de acento, paridad de iconos, bindings de motion + gate de a11y, matriz de dinero y tipificacion de inputs de texto. **AA es suelo obligatorio en ambos modos; ~7:1 en dark y en texto -text de light.**

## Rol state-neutral + variantes -text (ADDENDUM correctivo de contraste · Grupo Color Fase 3)  ·  `fase3-state-neutral-and-text-variants`

### Rol `state-neutral` + variantes `-text` — valor (light/dark) · ratio verificado · uso

| Rol / token | Light (par texto/fondo) | Ratio light | Dark (par texto/fondo) | Ratio dark | Uso (regla) |
|---|---|---|---|---|---|
| **success-text** | `#076138` / `#FFFFFF` | **7.56:1** AAA | `#34D399` / `#131519` | ~7:1 | TEXTO pequeno y etiqueta soft-chip de dinero+/cuadra. JAMAS el fill `#0E8A55` como texto (4.39, falla AA) |
| success-text / soft-chip | `#076138` / success@12% | ~6.x AA | `#34D399` / success@16% | ~7:1 | Etiqueta de soft-chip success |
| **danger-text** | `#A81818` / `#FFFFFF` | **7.48:1** AAA | `#F87171` / `#131519` | ~7:1 | TEXTO de error/averia/descuadre. Fill `#DC2626` solo icono/relleno/cifra grande |
| danger-text / soft-chip | `#A81818` / danger@12% | **6.05:1** AA | `#F87171` / danger@16% | ~7:1 | Etiqueta de soft-chip danger |
| **warning-text** | `#8A3D0A` / `#FFFFFF` | **7.63:1** AAA | `#FBBF24` / `#131519` | ~7:1 | TEXTO de pendiente/sin-firmar/offline-stale |
| **info-text** | `#1D4ED8` / `#FFFFFF` | ~5.9:1 AA | `#60A5FA` / `#131519` | ~7:1 | TEXTO info; oscurecer mas si se exige 7:1 en light |
| **primary-text** | `#0E7490` / `#FFFFFF` | 5.36:1 AA fuerte | `#2BC4DD` / `#131519` | ~7:1 | Enlace/acento texto pequeno (no se exige 7:1) |
| muted (ya existe) | `#646B76` / `#FFFFFF` | 5.38:1 AA | `#9AA1AD` / `#131519` | ~7:1 | Texto secundario/metadato |
| **on-danger (fill)** | `#FFFFFF` / `#DC2626` | AA | `#3A0A0A` / `#F87171` | OSCURO (blanco=2.77 FALLA) | Texto/icono sobre badge/boton danger relleno |
| **on-warning (fill)** | `#FFFFFF` / `#B45309` | AA | `#3A2503` / `#FBBF24` | OSCURO (blanco falla) | Texto/icono sobre fill warning |
| **state-neutral** (bg) | `#F4F6F8` (=surface-2) | — | `#1B1E24` (=surface-2) | — | Superficie de estado NEUTRA (no marca). offline/info/permiso RBAC/deuda EUR/impresor/'no procede'/baja-confianza OCR |
| state-neutral-border | `#E3E6EA` (=border) | — | `#262A31` (=border) | — | Borde 1px del estado neutro |
| state-neutral-foreground | `#11161B` / `#F4F6F8` | ~15:1 AAA | `#E7EAEE` / `#1B1E24` | ~12:1 AAA | Etiqueta principal del estado neutro |
| state-neutral-muted | `#646B76` / `#F4F6F8` | ~5.0:1 AA | `#9AA1AD` / `#1B1E24` | ~6.5:1 AA | Metadato secundario del estado |

### Matriz de dinero (deber NO es error → NUNCA danger)

| Caso economico | Cifra grande (KPI/importe) | Texto pequeno | Color | Acompanamiento |
|---|---|---|---|---|
| **positivo / cuadra** | fill `success` `#0E8A55`/`#34D399` | `success-text` `#076138`/`#34D399` | verde | — |
| **deuda / saldo a deber** | `foreground` NEUTRO `#11161B`/`#E7EAEE` | `foreground` neutro | neutro (state-neutral) | icono € obligatorio; NUNCA danger ni warning |
| **descuadre / error de cuadre** | fill `danger` `#DC2626`/`#F87171` | `danger-text` `#A81818`/`#F87171` | rojo | icono de alerta |

**Android**
```kotlin
// =====================================================================
// Color.kt — ADDENDUM correctivo (Grupo Color, Fase 3 · ronda 2)
// NO redefine los canonicos ya escritos. Anade:
//   (a) variantes -text oscurecidas (texto pequeno / etiqueta soft-chip en LIGHT)
//   (b) rol state-neutral (superficie de estado NEUTRA, no marca)
//   (c) on-danger / on-warning OSCUROS para fills en DARK
// Ratios WCAG verificados en el PREAMBLE (no recalcular a ojo).
// Se inyectan extendiendo RecreSemanticColors (CompositionLocal LocalRecreColors).
// =====================================================================

// --- LIGHT: variantes -text (TEXTO pequeno y ETIQUETA de soft-chip) -----------
// El 'fill' del rol se reserva a iconos, rellenos y cifras grandes; el texto usa -text.
val RecreSuccessTextLight = Color(0xFF076138) // 7.56:1 sobre #FFFFFF · 6.x sobre success@12%
val RecreDangerTextLight  = Color(0xFFA81818) // 7.48:1 sobre #FFFFFF · 6.05 sobre danger@12%
val RecreWarningTextLight = Color(0xFF8A3D0A) // 7.63:1 sobre #FFFFFF
val RecreInfoTextLight    = Color(0xFF1D4ED8) // ~5.9:1 sobre #FFFFFF (AA fuerte; oscurecer si se exige 7:1)

// --- DARK: variantes -text (el fill ya cumple ~7:1 como texto sobre fondos oscuros) -
// En DARK las -text == fill (no hace falta oscurecer; el problema de contraste es LIGHT).
val RecreSuccessTextDark = Color(0xFF34D399)
val RecreDangerTextDark  = Color(0xFFF87171)
val RecreWarningTextDark = Color(0xFFFBBF24)
val RecreInfoTextDark    = Color(0xFF60A5FA)

// --- on-fill OSCUROS para badges/botones rellenos en DARK ----------------------
// Blanco sobre danger-dark (#F87171) = 2.77 FALLA; sobre warning-dark (#FBBF24) tambien.
// El texto/icono sobre estos fills va OSCURO.
val RecreOnDangerFillDark  = Color(0xFF3A0A0A) // texto/icono oscuro sobre fill danger en DARK
val RecreOnWarningFillDark = Color(0xFF3A2503) // texto/icono oscuro sobre fill warning en DARK
// (LIGHT: on-danger/on-warning fill siguen siendo #FFFFFF como ya definido.)

// --- ROL state-neutral: superficie de ESTADO neutra (NO marca, NO secondary) ----
// Para: offline / info-neutro / permiso RBAC / deuda EUR / estado impresor /
//       'no procede' / 'baja confianza OCR'. Superficie gris de estado + texto
//       foreground/muted + ICONO. Distinto de 'muted' (solo texto) y de
//       'secondary' (color de MARCA tonal petroleo).
// LIGHT: superficie = surface-2 (#F4F6F8); texto = foreground (#11161B) o muted (#646B76).
val RecreStateNeutralBgLight     = Color(0xFFF4F6F8) // == surface-2 (estado neutro al ~100%, percibido 12-16%)
val RecreStateNeutralBorderLight = Color(0xFFE3E6EA) // == border
val RecreStateNeutralFgLight     = Color(0xFF11161B) // foreground (etiqueta de estado)
val RecreStateNeutralMutedLight  = Color(0xFF646B76) // muted (metadato secundario)  5.38:1
// DARK: superficie = surface-2 (#1B1E24); texto = foreground (#E7EAEE) o muted (#9AA1AD).
val RecreStateNeutralBgDark      = Color(0xFF1B1E24) // == surface-2
val RecreStateNeutralBorderDark  = Color(0xFF262A31) // == border
val RecreStateNeutralFgDark      = Color(0xFFE7EAEE) // foreground
val RecreStateNeutralMutedDark   = Color(0xFF9AA1AD) // muted

// ---------------------------------------------------------------------
// Extension de RecreSemanticColors (anadir estos campos a la data class ya
// existente; abajo el bloque a fusionar — NO es una clase nueva).
// ---------------------------------------------------------------------
/*
@Immutable
data class RecreSemanticColors(
    // ... campos ya existentes (success, onSuccess, ..., muted, ring, isLight) ...

    // -- ADDENDUM: variantes -text (texto pequeno / etiqueta soft-chip) --
    val successText: Color,
    val dangerText: Color,
    val warningText: Color,
    val infoText: Color,
    // -- ADDENDUM: rol state-neutral (superficie de estado neutra) --
    val stateNeutralBg: Color,      // fondo del chip/superficie de estado neutro
    val stateNeutralBorder: Color,  // borde 1px del estado neutro
    val stateNeutralFg: Color,      // etiqueta principal del estado
    val stateNeutralMuted: Color,   // metadato secundario del estado
)
*/

// Valores LIGHT a fusionar dentro de LightSemanticColors:
/*
private val LightSemanticColors = RecreSemanticColors(
    // ... canonicos ya existentes ...
    onDanger = Color(0xFFFFFFFF),   // LIGHT: blanco sobre fill danger OK
    onWarning = Color(0xFFFFFFFF),  // LIGHT: blanco sobre fill warning OK
    successText = RecreSuccessTextLight,
    dangerText  = RecreDangerTextLight,
    warningText = RecreWarningTextLight,
    infoText    = RecreInfoTextLight,
    stateNeutralBg     = RecreStateNeutralBgLight,
    stateNeutralBorder = RecreStateNeutralBorderLight,
    stateNeutralFg     = RecreStateNeutralFgLight,
    stateNeutralMuted  = RecreStateNeutralMutedLight,
)
*/

// Valores DARK a fusionar dentro de DarkSemanticColors:
/*
private val DarkSemanticColors = RecreSemanticColors(
    // ... canonicos ya existentes ...
    onDanger  = RecreOnDangerFillDark,   // DARK: texto OSCURO sobre fill danger (blanco falla)
    onWarning = RecreOnWarningFillDark,  // DARK: texto OSCURO sobre fill warning
    successText = RecreSuccessTextDark,
    dangerText  = RecreDangerTextDark,
    warningText = RecreWarningTextDark,
    infoText    = RecreInfoTextDark,
    stateNeutralBg     = RecreStateNeutralBgDark,
    stateNeutralBorder = RecreStateNeutralBorderDark,
    stateNeutralFg     = RecreStateNeutralFgDark,
    stateNeutralMuted  = RecreStateNeutralMutedDark,
)
*/

// ---------------------------------------------------------------------
// SOFT CHIP (helper de consumo): fondo = rol@12% (light) / @16% (dark);
// TEXTO/ICONO = la variante -text (NUNCA el fill). Asi cumple AA en LIGHT.
// Alpha sobre el FILL del rol (no sobre -text).
// ---------------------------------------------------------------------
/*
@Composable @ReadOnlyComposable
fun softChipBg(roleFill: Color): Color =
    roleFill.copy(alpha = if (RecreColors.current.isLight) 0.12f else 0.16f)

// Uso:
//   val c = RecreColors.current
//   Surface(color = softChipBg(c.danger)) { Text("Avería", color = c.dangerText) }
*/

// ---------------------------------------------------------------------
// MATRIZ DE DINERO (helper de consumo). Deber NO es error: NUNCA danger.
// ---------------------------------------------------------------------
/*
enum class MoneyTone { Positivo, NeutroDeuda, Descuadre }

// color del IMPORTE segun su rol economico:
@Composable @ReadOnlyComposable
fun moneyColor(tone: MoneyTone, esCifraGrande: Boolean): Color {
    val c = RecreColors.current
    return when (tone) {
        // positivo / cuadra: cifra grande -> fill success; texto pequeno -> success-text
        MoneyTone.Positivo    -> if (esCifraGrande) c.success else c.successText
        // deuda / saldo a deber: NEUTRO (foreground) + icono €. NO color de error.
        MoneyTone.NeutroDeuda -> RecreColors.current.let { /* foreground */ MaterialThemeOnSurface() }
        // descuadre / error de cuadre: danger (fill grande) o danger-text (texto)
        MoneyTone.Descuadre   -> if (esCifraGrande) c.danger else c.dangerText
    }
}
// NeutroDeuda usa onSurface (foreground) del MaterialTheme; el icono € lo aporta el componente.
*/
```

**Web**
```ts
/* =====================================================================
 * globals.css — ADDENDUM correctivo (Grupo Color, Fase 3 · ronda 2)
 * Anade SOLO tokens nuevos al bloque :root/.dark ya existente
 * (NO redefine background/primary/success/... canonicos).
 *   (a) variantes -text oscurecidas (texto pequeno / etiqueta soft-chip)
 *   (b) rol state-neutral (superficie de estado NEUTRA, no marca)
 *   (c) on-danger / on-warning oscuros (fills en .dark)
 * Ratios WCAG verificados en el PREAMBLE. AA suelo legal (EAA); -text de LIGHT ~7:1.
 * ===================================================================== */
@layer base {
  :root {
    /* --- variantes -text (TEXTO pequeno y ETIQUETA de soft-chip en LIGHT) ---
       El 'fill' (--success/--danger/...) se reserva a iconos, rellenos y
       cifras grandes. El TEXTO pequeno usa estas -text oscurecidas. */
    --success-text: #076138;        /* 7.56:1 / #FFF · 6.x / success@12% */
    --danger-text:  #A81818;        /* 7.48:1 / #FFF · 6.05 / danger@12%  */
    --warning-text: #8A3D0A;        /* 7.63:1 / #FFF                       */
    --info-text:    #1D4ED8;        /* ~5.9:1 / #FFF (AA fuerte)           */
    /* primary/muted como texto pequeno: AA fuerte (no se exige 7:1) */
    --primary-text: #0E7490;        /* 5.36:1 / #FFF — = primary           */
    /* (muted ya existe: --muted-foreground #646B76, 5.38:1) */

    /* --- ROL state-neutral: superficie de ESTADO neutra (NO marca) ---
       offline / info-neutro / permiso RBAC / deuda EUR / estado impresor /
       'no procede' / 'baja confianza OCR'. Distinto de --secondary (marca
       tonal petroleo) y de --muted (solo texto). Fondo gris de estado +
       texto foreground/muted + ICONO. */
    --state-neutral:            #F4F6F8;  /* == surface-2 (superficie de estado) */
    --state-neutral-border:     #E3E6EA;  /* == border (1px)                     */
    --state-neutral-foreground: #11161B;  /* foreground: etiqueta de estado      */
    --state-neutral-muted:      #646B76;  /* muted: metadato secundario  5.38:1  */
  }

  .dark {
    /* -text: en DARK el fill ya cumple ~7:1 como texto sobre fondos oscuros,
       asi que -text == fill (el problema de contraste es LIGHT). */
    --success-text: #34D399;
    --danger-text:  #F87171;
    --warning-text: #FBBF24;
    --info-text:    #60A5FA;
    --primary-text: #2BC4DD;

    /* on-fill OSCUROS: blanco sobre danger-dark (#F87171)=2.77 y sobre
       warning-dark (#FBBF24) FALLA. Texto/icono sobre estos fills va OSCURO. */
    --danger-foreground:  #3A0A0A;  /* override DARK del on-danger (fill badge/boton) */
    --warning-foreground: #3A2503;  /* override DARK del on-warning (fill)            */
    --destructive-foreground: #3A0A0A;

    --state-neutral:            #1B1E24;  /* == surface-2 */
    --state-neutral-border:     #262A31;  /* == border    */
    --state-neutral-foreground: #E7EAEE;  /* foreground   */
    --state-neutral-muted:      #9AA1AD;  /* muted        */
  }
}

/* =====================================================================
 * tailwind.config.ts — theme.extend.colors (FUSIONAR con lo ya existente).
 * Anade -text a cada rol semantico y el rol state-neutral.
 * ===================================================================== */
/*
colors: {
  // ... roles ya existentes; AÑADIR a cada uno la clave `text`: --
  success: { DEFAULT: "var(--success)", foreground: "var(--success-foreground)", subtle: "var(--success-subtle)", text: "var(--success-text)" },
  warning: { DEFAULT: "var(--warning)", foreground: "var(--warning-foreground)", subtle: "var(--warning-subtle)", text: "var(--warning-text)" },
  danger:  { DEFAULT: "var(--danger)",  foreground: "var(--danger-foreground)",  subtle: "var(--danger-subtle)",  text: "var(--danger-text)"  },
  info:    { DEFAULT: "var(--info)",    foreground: "var(--info-foreground)",    subtle: "var(--info-subtle)",    text: "var(--info-text)"    },
  primary: { DEFAULT: "var(--primary)", foreground: "var(--primary-foreground)", text: "var(--primary-text)" },
  // ROL nuevo state-neutral (superficie de estado neutra, no marca):
  "state-neutral": {
    DEFAULT: "var(--state-neutral)",
    border: "var(--state-neutral-border)",
    foreground: "var(--state-neutral-foreground)",
    muted: "var(--state-neutral-muted)",
  },
},
*/

/* =====================================================================
 * SOFT CHIP (patron de consumo, NO token nuevo).
 *   fondo = rol@12% (light) / @16% (dark)   ·   texto/icono = -text (NO el fill)
 * En Tailwind v4 el alpha del fill via /12: bg-[color:var(--danger)]/12
 * Ejemplo soft-chip "Avería" (danger):
 *   <span class="bg-[color:var(--danger)]/12 text-danger-text dark:bg-[color:var(--danger)]/16">…</span>
 *   -> danger-text sobre danger@12% = 6.05:1 (AA cumplido en LIGHT).
 *
 * MATRIZ DE DINERO (clases de consumo; deber NO es error -> NUNCA danger):
 *   positivo/cuadra  -> cifra grande: text-success     · texto pequeno: text-success-text
 *   deuda/saldo      -> text-foreground  + icono €  (NEUTRO; nunca danger ni warning)
 *   descuadre/error  -> cifra grande: text-danger      · texto pequeno: text-danger-text
 * ===================================================================== */
```

> ADDENDUM correctivo de /home/a/Escritorio/recre-main/.kiro/specs/recre/fase3-design-tokens.md (Grupo Color, ronda 2). NO redefine los canonicos ya escritos; solo anade tokens.

Tres correcciones, coherentes con la infraestructura existente (CSS vars :root/.dark + RecreSemanticColors via LocalRecreColors):

1) Variantes -text (success-text #076138, danger-text #A81818, warning-text #8A3D0A, info-text #1D4ED8, primary-text #0E7490) como tokens de PRIMERA CLASE. Regla: el 'fill' del rol (--success/--danger/...) se reserva a iconos, rellenos y cifras grandes; el TEXTO pequeno y la ETIQUETA de soft-chip usan -text. Resuelve el fallo AA de LIGHT (ej. success fill #0E8A55 como texto = 4.39, falla; success-text #076138 = 7.56). En DARK -text == fill (el fill oscuro ya cumple ~7:1). Soft-chip: fondo = rol@12% (light)/@16% (dark), texto = -text (danger-text sobre danger@12% = 6.05, AA).

2) Rol state-neutral: superficie de ESTADO neutra (= surface-2) + texto foreground/muted + icono, para offline/info-neutro/permiso RBAC/deuda EUR/estado impresor/'no procede'/baja-confianza OCR. Explicitamente DISTINTO de 'muted' (solo texto) y de 'secondary' (color de MARCA tonal petroleo — no usar para estado). Web: 4 vars (--state-neutral, -border, -foreground, -muted) + sub-objeto Tailwind "state-neutral". Android: 8 propiedades (4 light + 4 dark) que extienden RecreSemanticColors.

3) on-fill OSCUROS en DARK: blanco sobre danger-dark #F87171 = 2.77 (FALLA) y sobre warning-dark #FBBF24 (falla). Override .dark de --danger-foreground/--warning-foreground/--destructive-foreground a #3A0A0A/#3A2503; en Android onDanger/onWarning de DarkSemanticColors a #3A0A0A/#3A2503. LIGHT sigue con blanco (#FFFFFF), que cumple.

Matriz de dinero incluida: positivo/cuadra = success(fill grande)/success-text(texto); deuda/saldo = foreground NEUTRO + icono € (state-neutral; deber NO es error, NUNCA danger ni warning); descuadre/error = danger(fill)/danger-text(texto).

Hex usados = los verificados del PREAMBLE; ratios no recalculados a ojo. Ficheros objetivo: web/src/app/globals.css (bloque :root/.dark), web/tailwind.config.ts (theme.extend.colors), android/app/src/main/java/com/recre/app/ui/theme/Color.kt (extender RecreSemanticColors + LightSemanticColors/DarkSemanticColors). Componentes que ya referencian este grupo (StatusChip soft-chip, MoneyText, badges danger/warning): consumir -text para texto y on-fill oscuro en dark.

Salvedad de verificacion: snippets no compilados en este entorno; el helper moneyColor() en NeutroDeuda debe devolver onSurface (foreground) del MaterialTheme (el placeholder MaterialThemeOnSurface() es ilustrativo — sustituir por MaterialTheme.colorScheme.onSurface en el componente).


---

## ColorScheme Material3 completo derivado + paridad CSS (ADDENDUM correctivo de fase3-color)  ·  `fase3-color-m3-scheme-parity`

## Equivalencia M3 (Android) <-> CSS var (Web) — para no derivar a ojo

| Slot M3 (Android) | CSS var (Web) | Tailwind | Light | Dark | Consumidor canonico |
|---|---|---|---|---|---|
| `primary` | `--primary` | `primary` | `#0E7490` | `#2BC4DD` | CTA, FAB, link activo, ring |
| `onPrimary` | `--primary-foreground` | `primary-foreground` | `#FFFFFF` | `#06212A` | texto sobre primary |
| `primaryContainer` | `--primary-container` | `primary/container` | `#D6EBF1` | `#0B4A58` | **ThumbNav item activo**, chip primary |
| `onPrimaryContainer` | `--on-primary-container` | `primary/on-container` | `#0A4254` | `#BDEAF4` | texto/icono sobre primaryContainer |
| `secondary` | `--secondary` | `secondary` | `#E6F2F4` | `#16323A` | superficie tonal de marca (NO estado) |
| `onSecondary` | `--secondary-foreground` | `secondary-foreground` | `#0A4254` | `#C7E6EE` | texto sobre secondary |
| `secondaryContainer` | `--secondary-container` | `secondary/container` | `#D5EAEE` | `#1E4350` | **boton tonal** |
| `onSecondaryContainer` | `--on-secondary-container` | `secondary/on-container` | `#0A4254` | `#C7E6EE` | texto del boton tonal |
| `tertiary` (=info) | `--tertiary` / `--info` | `info` | `#2563EB` | `#60A5FA` | "sincronizando" (fill) |
| `onTertiary` | `--on-tertiary` / `--on-info` | `info/on` | `#FFFFFF` | `#06203F` | texto sobre info fill |
| `tertiaryContainer` | `--tertiary-container` / `--info-subtle` | `info/subtle` | `#DDE7FB` | `#14305C` | chip "sincronizando" |
| `onTertiaryContainer` | `--on-tertiary-container` / `--info-text` | `info/text` | `#1D4ED8` | `#60A5FA` | etiqueta del chip info |
| `background` | `--background` | `background` | `#FAFBFC` | `#0B0C0E` | lienzo |
| `onBackground` | `--foreground` | `foreground` | `#11161B` | `#E7EAEE` | texto / digito importe / **deuda EUR** |
| `surface` | `--surface-1` / `--card` | `surface-1`,`card` | `#FFFFFF` | `#131519` | cards, dialogs |
| `onSurface` | `--foreground` | `foreground` | `#11161B` | `#E7EAEE` | texto principal |
| `surfaceVariant` | `--surface-2` | `surface-2` | `#F4F6F8` | `#1B1E24` | fondos sutiles, keypad |
| `onSurfaceVariant` | `--muted-foreground` | `muted-foreground` | `#646B76` | `#9AA1AD` | texto secundario, simbolo EUR |
| `surfaceContainerHigh` | `--surface-container-high`* | — | `#EDF0F3` | `#22262D` | sticky header elevado |
| `surfaceContainerHighest` | `--surface-container-highest`* | — | `#E7EBEF` | `#2A2F37` | input/relleno sutil |
| `outline` | `--muted` (enfasis) | — | `#646B76` | `#9AA1AD` | borde con enfasis |
| `outlineVariant` | `--border` / `--input` | `border`,`input` | `#E3E6EA` | `#262A31` | separador 1px |
| `error` (=danger) | `--danger` / `--destructive` | `danger`,`destructive` | `#DC2626` | `#F87171` | fill error/averia/descuadre |
| `onError` | `--on-danger` | `danger/on` | `#FFFFFF` | `#3A0A0A` | texto sobre danger fill (**dark=oscuro**) |
| `errorContainer` | `--danger-subtle` | `danger/subtle` | `#FAE0E0` | `#45221F` | chip error |
| `onErrorContainer` | `--danger-text` | `danger/text` | `#A81818` | `#F87171` | etiqueta chip error |
| `inverseSurface` | `--surface-1` inverso | — | `#131519` | `#FFFFFF` | snackbar/tooltip |
| `inversePrimary` | `--primary` inverso | — | `#2BC4DD` | `#0E7490` | accion en snackbar |
| `scrim` | (negro) | — | `#000000` | `#000000` | velo de modal/sheet |

\* `surfaceContainerHigh/Highest` no tienen CSS var en el bloque base actual; se exponen como vars opcionales si web necesita el mismo escalon de elevacion (hoy web usa borde 1px, no escalones).

## Roles solo-dominio (M3 carece de slot) — paridad por CompositionLocal / CSS var

| Rol Recre | Android (`RecreColors.current.*`) | CSS var | Light | Dark | Uso |
|---|---|---|---|---|---|
| success fill | `success` | `--success` | `#0E8A55` | `#34D399` | icono/relleno/cifra+ (dinero cuadra) |
| success texto | `successText` | `--success-text` | `#076138` (7.56) | `#34D399` | TEXTO pequeno / etiqueta chip |
| warning fill | `warning` | `--warning` | `#B45309` | `#FBBF24` | pendiente/sin-firmar/offline-stale |
| warning texto | `warningText` | `--warning-text` | `#8A3D0A` (7.63) | `#FBBF24` | TEXTO / etiqueta chip |
| danger texto | `dangerText` | `--danger-text` | `#A81818` (7.48) | `#F87171` | TEXTO / etiqueta chip |
| info texto | `infoText` | `--info-text` | `#1D4ED8` (~5.9) | `#60A5FA` | TEXTO / etiqueta chip |
| muted | `muted` | `--muted-foreground` | `#646B76` (5.38) | `#9AA1AD` | texto secundario |
| **state-neutral** | `stateNeutralContainer` | `--state-neutral` | `#F4F6F8` | `#1B1E24` | offline/deuda EUR/RBAC/impresora/OCR-bajo (**NUNCA secondary**) |
| on state-neutral | `onStateNeutral` | `--state-neutral-foreground` | `#11161B` | `#E7EAEE` | texto del chip neutro + icono |

**Android**
```kotlin
// =====================================================================
// Color.kt — Recre DS · ADDENDUM correctivo del Grupo Color (Fase 3 r2)
// "Confianza Industrial" (azul petroleo). Completa el ColorScheme M3 y
// fija los pares texto/fondo con los ratios WCAG YA VERIFICADOS (no
// recalcular a ojo). Sustituye el bloque de Color.kt/Theme.kt anterior.
//
// PALETA = fuente de verdad (visual-identity.md). [PALETA] = canonico.
// Reglas de contraste aplicadas (prompt r2):
//  - DARK ya ~7:1. El problema es LIGHT -> variantes -text oscurecidas
//    para TEXTO pequeno y ETIQUETA de soft-chip. El 'fill' del rol se
//    reserva a iconos, rellenos y cifras grandes.
//  - SOFT CHIP: fondo = rol@12% (light) / @16% (dark); TEXTO = -text.
//  - DARK fill danger/warning (badge/boton): TEXTO OSCURO (blanco falla).
//  - success como TEXTO jamas el fill (#0E8A55 = 4.39 falla AA): usar -text.
//  - 'state-neutral' (offline/deuda EUR/RBAC/impresora/OCR-bajo): superficie
//    NEUTRA (surface-2) + foreground/muted + icono. NUNCA 'secondary'.
// =====================================================================
package com.recre.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------
// 1) PALETA CANONICA (no tocar). Light + Dark.
// ---------------------------------------------------------------------
// --- Light [PALETA]
val RecrePrimaryLight    = Color(0xFF0E7490) // primary (petroleo)  texto AA 5.36
val RecreOnPrimaryLight  = Color(0xFFFFFFFF) // on-primary
val RecreSecondaryLight  = Color(0xFFE6F2F4) // secondary (tint marca) — superficie, NO estado
val RecreBackgroundLight = Color(0xFFFAFBFC)
val RecreSurface1Light   = Color(0xFFFFFFFF)
val RecreSurface2Light   = Color(0xFFF4F6F8) // tambien base del rol state-neutral
val RecreSuccessLight    = Color(0xFF0E8A55) // fill: SOLO icono/relleno/cifra grande (texto=4.39 FALLA)
val RecreWarningLight    = Color(0xFFB45309) // fill warning
val RecreDangerLight     = Color(0xFFDC2626) // fill danger (= error M3)
val RecreInfoLight       = Color(0xFF2563EB) // info (= tertiary)
val RecreBorderLight     = Color(0xFFE3E6EA)
val RecreMutedLight      = Color(0xFF646B76) // texto secundario / simbolo EUR  (5.38 AA)
val RecreForegroundLight = Color(0xFF11161B) // texto principal / digito importe / DEUDA
val RecreRingLight       = Color(0xFF0E7490) // ring = primary

// --- Dark [PALETA]
val RecrePrimaryDark    = Color(0xFF2BC4DD) // primary (cian)
val RecreOnPrimaryDark  = Color(0xFF06212A) // on-primary: texto OSCURO sobre cian
val RecreSecondaryDark  = Color(0xFF16323A)
val RecreBackgroundDark = Color(0xFF0B0C0E)
val RecreSurface1Dark   = Color(0xFF131519)
val RecreSurface2Dark   = Color(0xFF1B1E24) // base state-neutral dark
val RecreSuccessDark    = Color(0xFF34D399)
val RecreWarningDark    = Color(0xFFFBBF24)
val RecreDangerDark     = Color(0xFFF87171)
val RecreInfoDark       = Color(0xFF60A5FA)
val RecreBorderDark     = Color(0xFF262A31)
val RecreMutedDark      = Color(0xFF9AA1AD)
val RecreForegroundDark = Color(0xFFE7EAEE)
val RecreRingDark       = Color(0xFF2BC4DD)

val RecreScrim = Color(0xFF000000)

// ---------------------------------------------------------------------
// 1b) VARIANTES -text (LIGHT) — TEXTO pequeno y ETIQUETA de soft-chip.
//     Ratios verificados sobre BLANCO (#FFFFFF). NO usar como fill.
//     En dark el fill ya contrasta como texto sobre fondo oscuro -> no
//     hay -text en dark; el texto de chip dark = el propio fill.
// ---------------------------------------------------------------------
val RecreSuccessTextLight = Color(0xFF076138) // 7.56:1  texto success en light
val RecreDangerTextLight  = Color(0xFFA81818) // 7.48:1  texto danger en light
val RecreWarningTextLight = Color(0xFF8A3D0A) // 7.63:1  texto warning en light
val RecreInfoTextLight    = Color(0xFF1D4ED8) // ~5.9:1  texto info en light (AA fuerte)
// En dark, el "text" de cada rol = su fill (sobre fondo oscuro ya ~7:1):
val RecreSuccessTextDark  = RecreSuccessDark
val RecreDangerTextDark   = RecreDangerDark
val RecreWarningTextDark  = RecreWarningDark
val RecreInfoTextDark     = RecreInfoDark

// ---------------------------------------------------------------------
// 2) CONTAINERS / SOFT-CHIP derivados (rellenan slots M3).
//    Light container ~ rol@12% sobre blanco; on*Container = la -text (AA).
//    Dark  container ~ rol@16% sobre fondo oscuro; on*Container = clara.
//    on-FILL en dark (onError/onWarning…) = OSCURO (blanco falla).
// ---------------------------------------------------------------------
// --- Light containers (soft-chip background = rol@~12%)
val RecrePrimaryContainerLight     = Color(0xFFD6EBF1) // primary@~12% (ThumbNav activo light)
val RecreOnPrimaryContainerLight   = Color(0xFF0A4254) // texto/icono sobre primaryContainer (>7:1)
val RecreSecondaryContainerLight   = Color(0xFFD5EAEE) // boton tonal (secondaryContainer)
val RecreOnSecondaryContainerLight = Color(0xFF0A4254) // = primary oscurecido (marca, AA fuerte)
val RecreSuccessContainerLight     = Color(0xFFDFF3E8) // success@~12%
val RecreOnSuccessContainerLight   = RecreSuccessTextLight // #076138 sobre chip = 6.05 AA
val RecreWarningContainerLight     = Color(0xFFF7E6D2) // warning@~12%
val RecreOnWarningContainerLight   = RecreWarningTextLight // #8A3D0A
val RecreDangerContainerLight      = Color(0xFFFAE0E0) // danger@~12% (= errorContainer)
val RecreOnDangerContainerLight    = RecreDangerTextLight  // #A81818 sobre chip = 6.05 AA
val RecreInfoContainerLight        = Color(0xFFDDE7FB) // info@~12%
val RecreOnInfoContainerLight      = RecreInfoTextLight    // #1D4ED8

// --- Dark containers (soft-chip background = rol@~16% sobre oscuro)
val RecrePrimaryContainerDark      = Color(0xFF0B4A58) // ThumbNav activo dark
val RecreOnPrimaryContainerDark    = Color(0xFFBDEAF4)
val RecreSecondaryContainerDark    = Color(0xFF1E4350) // boton tonal dark
val RecreOnSecondaryContainerDark  = Color(0xFFC7E6EE)
val RecreSuccessContainerDark      = Color(0xFF103D2C) // success@~16%
val RecreOnSuccessContainerDark    = RecreSuccessDark   // #34D399
val RecreWarningContainerDark      = Color(0xFF422D0A) // warning@~16%
val RecreOnWarningContainerDark    = RecreWarningDark   // #FBBF24
val RecreDangerContainerDark       = Color(0xFF45221F) // danger@~16% (= errorContainer)
val RecreOnDangerContainerDark     = RecreDangerDark    // #F87171
val RecreInfoContainerDark         = Color(0xFF14305C) // info@~16%
val RecreOnInfoContainerDark       = RecreInfoDark      // #60A5FA

// on-FILL: texto/icono SOBRE el fill solido del rol.
// Light: blanco sobre fill (todos los fills light pasan AA con blanco).
// Dark : OSCURO sobre fill (blanco sobre #F87171=2.77, sobre #FBBF24 peor).
val RecreOnSuccessFillLight = Color(0xFFFFFFFF)
val RecreOnWarningFillLight = Color(0xFFFFFFFF)
val RecreOnDangerFillLight  = Color(0xFFFFFFFF)
val RecreOnInfoFillLight    = Color(0xFFFFFFFF)
val RecreOnSuccessFillDark  = Color(0xFF06281A) // sobre #34D399
val RecreOnWarningFillDark  = Color(0xFF3A2503) // sobre #FBBF24
val RecreOnDangerFillDark   = Color(0xFF3A0A0A) // sobre #F87171 (prompt: on-danger dark)
val RecreOnInfoFillDark     = Color(0xFF06203F) // sobre #60A5FA

// ---------------------------------------------------------------------
// 3) TOKENS SEMANTICOS DE DOMINIO (M3 carece de slots para success/
//    warning/info/border/muted/surface-2/ring/state-neutral/-text/-fill).
//    Inyectados por CompositionLocal; lectura: RecreColors.current.success.
// ---------------------------------------------------------------------
@Immutable
data class RecreSemanticColors(
    // success
    val success: Color,            // FILL: icono/relleno/cifra grande (dinero+/cuadra)
    val successText: Color,        // TEXTO pequeno y etiqueta de chip
    val onSuccessFill: Color,      // texto/icono SOBRE fill solido
    val successContainer: Color,   // soft-chip bg
    val onSuccessContainer: Color, // = successText
    // warning
    val warning: Color,
    val warningText: Color,
    val onWarningFill: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    // danger (alias semantico de error)
    val danger: Color,
    val dangerText: Color,
    val onDangerFill: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    // info (= tertiary)
    val info: Color,
    val infoText: Color,
    val onInfoFill: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    // neutros / estructura
    val surface2: Color,           // surface-2
    val border: Color,             // = outlineVariant (1px)
    val muted: Color,              // texto secundario / simbolo EUR
    val ring: Color,               // foco = primary
    // ROL state-neutral (offline/info-pasiva/RBAC/deuda EUR/impresora/OCR-bajo)
    val stateNeutralContainer: Color, // superficie NEUTRA (NO secondary)
    val onStateNeutral: Color,        // foreground neutro + icono
    val onStateNeutralMuted: Color,   // metadato dentro del chip neutro
    val isLight: Boolean,
)

private val LightSemanticColors = RecreSemanticColors(
    success = RecreSuccessLight,
    successText = RecreSuccessTextLight,
    onSuccessFill = RecreOnSuccessFillLight,
    successContainer = RecreSuccessContainerLight,
    onSuccessContainer = RecreOnSuccessContainerLight,
    warning = RecreWarningLight,
    warningText = RecreWarningTextLight,
    onWarningFill = RecreOnWarningFillLight,
    warningContainer = RecreWarningContainerLight,
    onWarningContainer = RecreOnWarningContainerLight,
    danger = RecreDangerLight,
    dangerText = RecreDangerTextLight,
    onDangerFill = RecreOnDangerFillLight,
    dangerContainer = RecreDangerContainerLight,
    onDangerContainer = RecreOnDangerContainerLight,
    info = RecreInfoLight,
    infoText = RecreInfoTextLight,
    onInfoFill = RecreOnInfoFillLight,
    infoContainer = RecreInfoContainerLight,
    onInfoContainer = RecreOnInfoContainerLight,
    surface2 = RecreSurface2Light,
    border = RecreBorderLight,
    muted = RecreMutedLight,
    ring = RecreRingLight,
    stateNeutralContainer = RecreSurface2Light,   // gris de estado, NO secondary
    onStateNeutral = RecreForegroundLight,
    onStateNeutralMuted = RecreMutedLight,
    isLight = true,
)

private val DarkSemanticColors = RecreSemanticColors(
    success = RecreSuccessDark,
    successText = RecreSuccessTextDark,
    onSuccessFill = RecreOnSuccessFillDark,
    successContainer = RecreSuccessContainerDark,
    onSuccessContainer = RecreOnSuccessContainerDark,
    warning = RecreWarningDark,
    warningText = RecreWarningTextDark,
    onWarningFill = RecreOnWarningFillDark,
    warningContainer = RecreWarningContainerDark,
    onWarningContainer = RecreOnWarningContainerDark,
    danger = RecreDangerDark,
    dangerText = RecreDangerTextDark,
    onDangerFill = RecreOnDangerFillDark,
    dangerContainer = RecreDangerContainerDark,
    onDangerContainer = RecreOnDangerContainerDark,
    info = RecreInfoDark,
    infoText = RecreInfoTextDark,
    onInfoFill = RecreOnInfoFillDark,
    infoContainer = RecreInfoContainerDark,
    onInfoContainer = RecreOnInfoContainerDark,
    surface2 = RecreSurface2Dark,
    border = RecreBorderDark,
    muted = RecreMutedDark,
    ring = RecreRingDark,
    stateNeutralContainer = RecreSurface2Dark,
    onStateNeutral = RecreForegroundDark,
    onStateNeutralMuted = RecreMutedDark,
    isLight = false,
)

val LocalRecreColors = staticCompositionLocalOf { LightSemanticColors }

/** Acceso ergonomico: RecreColors.current.successText  (via idiomatica). */
object RecreColors {
    val current: RecreSemanticColors
        @Composable @ReadOnlyComposable
        get() = LocalRecreColors.current
}

internal fun recreSemanticColors(dark: Boolean): RecreSemanticColors =
    if (dark) DarkSemanticColors else LightSemanticColors

// =====================================================================
// Theme.kt — ColorScheme M3 COMPLETO (todos los slots) + tokens dominio.
// Sin dynamicColor (marca fija). danger==error, info==tertiary.
// =====================================================================
/*
package com.recre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary               = RecrePrimaryLight,
    onPrimary             = RecreOnPrimaryLight,
    primaryContainer      = RecrePrimaryContainerLight,   // ThumbNav activo
    onPrimaryContainer    = RecreOnPrimaryContainerLight,
    secondary             = RecreSecondaryLight,
    onSecondary           = RecreOnSecondaryContainerLight,
    secondaryContainer    = RecreSecondaryContainerLight, // boton TONAL
    onSecondaryContainer  = RecreOnSecondaryContainerLight,
    tertiary              = RecreInfoLight,                // info (estado "sincronizando"), NO marca
    onTertiary            = RecreOnInfoFillLight,
    tertiaryContainer     = RecreInfoContainerLight,
    onTertiaryContainer   = RecreOnInfoContainerLight,     // = info-text
    background             = RecreBackgroundLight,
    onBackground           = RecreForegroundLight,
    surface                = RecreSurface1Light,           // surface = surface-1
    onSurface              = RecreForegroundLight,
    surfaceVariant         = RecreSurface2Light,           // surface-2
    onSurfaceVariant       = RecreMutedLight,              // texto secundario sobre surface
    surfaceTint            = RecrePrimaryLight,            // tinte de elevacion = primary
    surfaceBright          = RecreSurface1Light,
    surfaceDim             = Color(0xFFEDEFF2),
    surfaceContainerLowest = RecreSurface1Light,
    surfaceContainerLow    = RecreSurface2Light,
    surfaceContainer       = RecreSurface2Light,
    surfaceContainerHigh   = Color(0xFFEDF0F3),
    surfaceContainerHighest= Color(0xFFE7EBEF),
    error                  = RecreDangerLight,             // error == danger (fill)
    onError                = RecreOnDangerFillLight,       // blanco (light)
    errorContainer         = RecreDangerContainerLight,
    onErrorContainer       = RecreOnDangerContainerLight,  // = danger-text #A81818
    outline                = RecreMutedLight,              // borde con enfasis
    outlineVariant         = RecreBorderLight,             // separadores 1px
    inverseSurface         = RecreSurface1Dark,
    inverseOnSurface       = RecreForegroundDark,
    inversePrimary         = RecrePrimaryDark,
    scrim                  = RecreScrim,
)

private val DarkColorScheme = darkColorScheme(
    primary               = RecrePrimaryDark,
    onPrimary             = RecreOnPrimaryDark,            // texto OSCURO sobre cian
    primaryContainer      = RecrePrimaryContainerDark,
    onPrimaryContainer    = RecreOnPrimaryContainerDark,
    secondary             = RecreSecondaryDark,
    onSecondary           = RecreOnSecondaryContainerDark,
    secondaryContainer    = RecreSecondaryContainerDark,
    onSecondaryContainer  = RecreOnSecondaryContainerDark,
    tertiary              = RecreInfoDark,
    onTertiary            = RecreOnInfoFillDark,
    tertiaryContainer     = RecreInfoContainerDark,
    onTertiaryContainer   = RecreOnInfoContainerDark,
    background             = RecreBackgroundDark,
    onBackground           = RecreForegroundDark,
    surface                = RecreSurface1Dark,
    onSurface              = RecreForegroundDark,
    surfaceVariant         = RecreSurface2Dark,
    onSurfaceVariant       = RecreMutedDark,
    surfaceTint            = RecrePrimaryDark,
    surfaceBright          = Color(0xFF2A2F37),
    surfaceDim             = RecreBackgroundDark,
    surfaceContainerLowest = RecreBackgroundDark,
    surfaceContainerLow    = RecreSurface1Dark,
    surfaceContainer       = RecreSurface2Dark,
    surfaceContainerHigh   = Color(0xFF22262D),
    surfaceContainerHighest= Color(0xFF2A2F37),
    error                  = RecreDangerDark,
    onError                = RecreOnDangerFillDark,        // OSCURO #3A0A0A (blanco falla 2.77)
    errorContainer         = RecreDangerContainerDark,
    onErrorContainer       = RecreOnDangerContainerDark,
    outline                = RecreMutedDark,
    outlineVariant         = RecreBorderDark,
    inverseSurface         = RecreSurface1Light,
    inverseOnSurface       = RecreForegroundLight,
    inversePrimary         = RecrePrimaryLight,
    scrim                  = RecreScrim,
)

@Composable
fun RecreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalRecreColors provides recreSemanticColors(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,   // grupo Tipografia
            // shapes = RecreShapes,   // grupo Forma
            content = content,
        )
    }
}
*/
```

**Web**
```ts
/* =====================================================================
 * globals.css — Recre DS · ADDENDUM correctivo Grupo Color (Fase 3 r2)
 * Paridad 1:1 con el ColorScheme M3 de Android. AÑADE/CORRIGE sobre el
 * bloque :root/.dark ya escrito (no lo redefine entero):
 *  1) Variantes -text LIGHT oscurecidas (ratios WCAG verificados) para
 *     TEXTO pequeno y ETIQUETA de soft-chip. El 'fill' del rol queda
 *     para icono/relleno/cifra grande.
 *  2) *-container = soft-chip (rol@12% light / @16% dark); su foreground
 *     = la -text (cumple AA en light).
 *  3) on-FILL dark = OSCURO (blanco sobre danger/warning dark FALLA).
 *  4) primary-container / secondary-container EXPLICITOS para que
 *     ThumbNav (primaryContainer) y boton tonal (secondaryContainer) no
 *     se deriven a ojo y casen con Android.
 *  5) Rol state-neutral: superficie NEUTRA (surface-2) + foreground/muted
 *     + icono. NUNCA secondary (es color de MARCA).
 * ===================================================================== */
@layer base {
  :root {
    /* --- primary / secondary CONTAINER (paridad M3) --- */
    --primary-container: #D6EBF1;            /* primary@~12% · ThumbNav activo */
    --on-primary-container: #0A4254;         /* >7:1 */
    --secondary-container: #D5EAEE;          /* boton TONAL */
    --on-secondary-container: #0A4254;       /* marca oscurecida (AA fuerte) */

    /* --- variantes -text LIGHT (TEXTO + etiqueta soft-chip). Ratios /BLANCO --- */
    --success-text: #076138;                 /* 7.56:1 — texto success */
    --danger-text:  #A81818;                 /* 7.48:1 — texto danger  */
    --warning-text: #8A3D0A;                 /* 7.63:1 — texto warning */
    --info-text:    #1D4ED8;                 /* ~5.9:1 — texto info (AA fuerte) */
    /* primary como texto = #0E7490 (5.36 AA fuerte); muted #646B76 (5.38 AA) */

    /* --- soft-chip: fondo = rol@~12%; FOREGROUND del chip = la -text --- */
    --success-subtle: #DFF3E8;  --success-subtle-foreground: var(--success-text);
    --warning-subtle: #F7E6D2;  --warning-subtle-foreground: var(--warning-text);
    --danger-subtle:  #FAE0E0;  --danger-subtle-foreground:  var(--danger-text);
    --info-subtle:    #DDE7FB;  --info-subtle-foreground:    var(--info-text);

    /* --- on-FILL (texto/icono SOBRE fill solido): blanco en light --- */
    --on-success: #FFFFFF;
    --on-warning: #FFFFFF;
    --on-danger:  #FFFFFF;     /* == --danger-foreground */
    --on-info:    #FFFFFF;

    /* --- ROL state-neutral (offline/deuda EUR/RBAC/impresora/OCR-bajo) --- */
    --state-neutral: var(--surface-2);            /* superficie NEUTRA, NO secondary */
    --state-neutral-foreground: var(--foreground);
    --state-neutral-muted: var(--muted-foreground);

    /* --- error/tertiary container alineados a M3 (paridad) --- */
    --error-container: var(--danger-subtle);
    --on-error-container: var(--danger-text);
    --tertiary: var(--info);
    --on-tertiary: var(--on-info);
    --tertiary-container: var(--info-subtle);
    --on-tertiary-container: var(--info-text);
  }

  .dark {
    --primary-container: #0B4A58;            /* ThumbNav activo dark */
    --on-primary-container: #BDEAF4;
    --secondary-container: #1E4350;          /* boton tonal dark */
    --on-secondary-container: #C7E6EE;

    /* en dark NO hay -text oscurecida: el texto de rol = su fill (sobre oscuro ~7:1) */
    --success-text: var(--success);          /* #34D399 */
    --danger-text:  var(--danger);           /* #F87171 */
    --warning-text: var(--warning);          /* #FBBF24 */
    --info-text:    var(--info);             /* #60A5FA */

    /* soft-chip dark = rol@~16% sobre oscuro; foreground = fill del rol */
    --success-subtle: #103D2C;  --success-subtle-foreground: var(--success);
    --warning-subtle: #422D0A;  --warning-subtle-foreground: var(--warning);
    --danger-subtle:  #45221F;  --danger-subtle-foreground:  var(--danger);
    --info-subtle:    #14305C;  --info-subtle-foreground:    var(--info);

    /* on-FILL dark = OSCURO (blanco sobre #F87171=2.77 / #FBBF24 FALLA) */
    --on-success: #06281A;
    --on-warning: #3A2503;
    --on-danger:  #3A0A0A;                   /* == --danger-foreground */
    --on-info:    #06203F;

    --state-neutral: var(--surface-2);
    --state-neutral-foreground: var(--foreground);
    --state-neutral-muted: var(--muted-foreground);

    --error-container: var(--danger-subtle);
    --on-error-container: var(--danger);
    --tertiary: var(--info);
    --on-tertiary: var(--on-info);
    --tertiary-container: var(--info-subtle);
    --on-tertiary-container: var(--info);
  }
}

/* =====================================================================
 * tailwind.config.ts — theme.extend.colors (MERGE con lo existente).
 * Cada rol semantico expone: DEFAULT(fill) / text / on / subtle /
 * subtle-foreground. Container de marca para tonales. state-neutral aparte.
 * REGLA DE CONSUMO:
 *   - texto pequeno / etiqueta de chip  -> text-success-text (NO text-success)
 *   - icono / relleno / cifra grande    -> text-success / bg-success
 *   - soft-chip                         -> bg-success-subtle text-success-text
 *   - texto sobre fill solido           -> text-on-success
 * ===================================================================== */
/*
colors: {
  primary: {
    DEFAULT: "var(--primary)", foreground: "var(--primary-foreground)",
    container: "var(--primary-container)", "on-container": "var(--on-primary-container)",
  },
  secondary: {
    DEFAULT: "var(--secondary)", foreground: "var(--secondary-foreground)",
    container: "var(--secondary-container)", "on-container": "var(--on-secondary-container)",
  },
  success: {
    DEFAULT: "var(--success)", text: "var(--success-text)", on: "var(--on-success)",
    subtle: "var(--success-subtle)", "subtle-foreground": "var(--success-subtle-foreground)",
  },
  warning: {
    DEFAULT: "var(--warning)", text: "var(--warning-text)", on: "var(--on-warning)",
    subtle: "var(--warning-subtle)", "subtle-foreground": "var(--warning-subtle-foreground)",
  },
  danger: {
    DEFAULT: "var(--danger)", text: "var(--danger-text)", on: "var(--on-danger)",
    subtle: "var(--danger-subtle)", "subtle-foreground": "var(--danger-subtle-foreground)",
  },
  info: {
    DEFAULT: "var(--info)", text: "var(--info-text)", on: "var(--on-info)",
    subtle: "var(--info-subtle)", "subtle-foreground": "var(--info-subtle-foreground)",
  },
  destructive: { DEFAULT: "var(--danger)", foreground: "var(--on-danger)" },
  "state-neutral": {
    DEFAULT: "var(--state-neutral)", foreground: "var(--state-neutral-foreground)",
    muted: "var(--state-neutral-muted)",
  },
},
*/
```

> ADDENDUM correctivo de `fase3-color` (no redefine el grupo entero). Coherente con fase3-design-tokens.md. Cambios respecto a la r1 ya escrita:

1) WCAG verificado, no a ojo. Se añaden las variantes -text LIGHT oscurecidas del prompt (success #076138 7.56:1, danger #A81818 7.48:1, warning #8A3D0A 7.63:1, info #1D4ED8 ~5.9:1) como par de TEXTO pequeno y ETIQUETA de soft-chip; el 'fill' del rol queda reservado a icono/relleno/cifra grande. success como texto JAMAS el fill #0E8A55 (4.39 falla AA).

2) Soft-chip corregido: fondo = rol@12% (light) / @16% (dark); foreground = la -text -> en light el `onSuccessContainer`/`onErrorContainer` pasan de los oscuros casi-negros de la r1 a las -text (#076138/#A81818), que dan ~6.05:1 sobre el chip y son el mismo color que el texto suelto (consistencia).

3) on-FILL dark = OSCURO. `onError` dark pasa de #45100F a #3A0A0A (blanco sobre #F87171 = 2.77 falla); idem warning/success/info fill en dark con texto oscuro. En M3 esto es `onError`/`onTertiary`; en dominio, `onDangerFill` etc.

4) Slots M3 completados que faltaban o estaban implicitos: surfaceTint, surfaceBright, surfaceDim, y la cadena surfaceContainerLowest..Highest en ambos modos; tabla de equivalencia M3<->CSS explicita para primaryContainer (ThumbNav) y secondaryContainer (boton tonal) -> ya no se derivan a ojo ni divergen entre plataformas.

5) Rol state-neutral nuevo (offline/info-pasiva/RBAC/deuda EUR/estado impresora/OCR-baja-confianza): superficie NEUTRA surface-2 + foreground/muted + icono. Sustituye el uso erroneo de `secondary` (color de marca) para estos estados. DINERO: deuda/saldo = foreground neutro + icono EUR (deber NO es danger).

6) Fix de implementacion: el getter `RecreColors.current` de la r1 estaba roto (`compositionLocalof` typo + `currentComposer.consume`); aqui es la via idiomatica `LocalRecreColors.current`.

Limitaciones: ratios tomados como verificados del prompt (no recalculados en este entorno); los hex de container @12/16% son aproximaciones del tinte sobre fondo (ajustables sin tocar canonicos). Codigo no compilado aqui (sin gradle/tailwind). info-text #1D4ED8 da ~5.9:1 (AA fuerte, no 7:1); si se exige sol-directo en info-texto, oscurecer a ~#1A45C0.

Ficheros objetivo (absolutos):
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Color.kt
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/Theme.kt
- /home/a/Escritorio/recre-main/web/src/app/globals.css
- /home/a/Escritorio/recre-main/web/tailwind.config.ts
- Doc donde insertar el addendum: /home/a/Escritorio/recre-main/.kiro/specs/recre/fase3-design-tokens.md (tras el bloque `fase3-color`).


---

## Tres familias de chip + presupuesto de acento + paridad de iconos (ADDENDUM correctivo Fase 3)  ·  `fase3-chips-acento-iconos`

## ADDENDUM correctivo — NO redefine `fase3-design-tokens.md` ni `fase3-component-specs.md`; los referencia.

### Tabla A — Las TRES familias de "chip" (no confundirlas)

| Eje | **StatusChip** (`C-StatusChip`, ya especificado) | **FilterChip** (nuevo) | **SegmentedControl** (nuevo) |
|---|---|---|---|
| Qué es | Indicador de **estado** de una entidad (recaudacion/maquina/sync/deuda). | **Toggle de filtro** sobre una lista/consulta. Multi-seleccion. | **Conmutador** entre 2-3 vistas/modos mutuamente excluyentes. |
| Interactivo | **No** (readonly). Es estado, no accion. | **Si** (tap = on/off). | **Si** (tap = cambia el modo). |
| Seleccion | n/a | 0..N activos (independientes). | Exactamente **1** activo (radio-like). |
| Color | **soft** por rol semantico (success/warning/danger/neutral/info). Fondo `--{rol}-chip-bg` opaco, texto `--{rol}-chip-fg` validado. **Nunca primary** (no es accion). | Neutro en reposo (`surface-1`+`border`); **seleccionado = primary outline + texto primary** sobre `secondary`. NO usa roles semanticos. | Pista `surface-2`; **segmento activo = `surface-1` + texto foreground + borde/indicador primary 1px**. El resto muted. |
| Forma | **pill** (full / `RoundedCornerShape(50)`). | pill (full). | contenedor `radius-md` 8 web / 12 dp Android; segmentos internos `radius-sm`. |
| Acento primary | 0% (prohibido). | Solo en el estado **seleccionado** (cuenta en el presupuesto). | Solo en el **indicador activo** (cuenta en el presupuesto). |
| Cuándo usar | "Averia", "Pendiente", "Offline", "Sincronizando", "Cuadra", deuda EUR (neutro). | "Solo con averia", "Pendientes", "Esta semana" sobre el hub/tabla. | "Total / Local", "Grafica / Tabla", "Dia / Semana / Mes". |
| Cuándo **NO** | Si necesita togglearse -> FilterChip. Si es accion -> Boton. | Si es 1-de-N excluyente -> SegmentedControl. Si comunica estado readonly -> StatusChip. | Si hay >3 opciones -> Tabs/Select. Si son filtros acumulables -> FilterChip. |
| a11y rol | `role` ninguno (texto+icono); en lista decorativo (`aria-hidden`, el estado va en el `aria-label` de la fila). | web `<button role="button" aria-pressed={on}>`; Android `Modifier.toggleable(role=Role.Checkbox)` + `stateDescription`. | web `role="radiogroup"` > hijos `role="radio" aria-checked`; Android `Modifier.selectableGroup()` + hijos `Modifier.selectable(role=Role.RadioButton)`. M3: `SingleChoiceSegmentedButtonRow`. |
| Token de seleccion | — | bg `--secondary`, borde `--primary` 1px, texto `--primary`, check Lucide/Phosphor `Check` antes del label. | indicador/texto `--primary`, fondo segmento `--surface-1`. |
| Min touch | 44px web / 48dp Android | 44px web / 48dp Android | 44px web / 48dp Android (alto del row) |

> StatusChip ya define sus pares texto/fondo precomputados (light ~14% / dark ~18%, fg validado AA light / ~7:1 dark): `--success-chip-*`, `--warning-chip-*`, `--danger-chip-*`, `--info-chip-*`, `--muted-chip-*` (neutral). FilterChip y SegmentedControl **no** crean roles nuevos: reusan `--primary`, `--secondary`, `--surface-1/2`, `--border`, `--muted-foreground`, `--foreground`. El "selected" de FilterChip que el spec de StatusChip marcaba como "[variante FilterChip, fuera del nucleo]" **se materializa aqui**.

### Tabla B — PRESUPUESTO DE ACENTO operativo (regla #3: primary <=10% de pantalla)

| Concepto | Definicion |
|---|---|
| Qué cuenta como "acento primary" | Pixeles con `--primary` como **fill** o como **outline/indicador activo** o **texto primary**: PrimaryCTA (fill), fila/tab de nav activo (indicador), FilterChip seleccionado (outline+texto), segmento activo de SegmentedControl (indicador), sparkline/serie primaria, link activo, focus ring (se excluye: es transitorio). El `--ring` de foco **no** cuenta. |
| Qué NO cuenta | Roles semanticos (success/warning/danger/info), neutros (foreground/muted/border/surface), `secondary` (marca **tonal**, no acento), MoneyText neutro. |
| Umbral | Suma de areas con acento primary <= **10%** del area visible del viewport (no del scroll completo). |
| Cómo medirlo | Auditoria por captura: exportar la pantalla, contar pixeles `--primary` (fill+outline+texto) / pixeles totales del viewport. Aprox. rapida: bounding-box de cada elemento con acento / area pantalla. Gate sugerido en revision de diseno, no en CI. |

**Regla de PRIORIDAD cuando concurren varios acentos (un solo "ancla" primary fuerte por pantalla):**

| Orden | Elemento | Tratamiento si concurre |
|---|---|---|
| 1 (gana) | **PrimaryCTA** (fill solido) | Es el ancla. **Maximo 1 fill primary por pantalla** (regla ya fijada en visual-identity / botones). |
| 2 | **Nav/Thumb activo** | Indicador (barra/punto) primary, NO fill de toda la celda. Si hay CTA fill, el nav se queda en indicador fino + icono fill. |
| 3 | **Fila/tab/segmento activo** (SegmentedControl, fila de tabla activa, FilterChip seleccionado) | **Outline + texto** primary sobre `secondary`/`surface-1`, **nunca fill**. Si ya hay CTA fill + nav activo, estos van a outline de 1px (no 2px) para no competir. |
| 4 (cede) | **Sparkline / serie** del KPI Bento | Si la pantalla ya satura acento (CTA fill + nav + chips), la serie pasa a `--chart-1` atenuada (mismo petroleo a ~60% o trazo 1px); el resto de series usa `--chart-2..5`. |

**Caso keypad denominaciones** (Android): el unico fill primary permitido es el **boton de confirmar** (PrimaryCTA). Las teclas del keypad son `surface-2` con texto foreground (neutras), NO primary (si fueran primary el teclado solo ya supera el 10%). El total en curso usa MoneyText neutro/foreground. Acento total estimado: ~4-6% (solo el CTA). 

**Caso KPI Bento**: 1 sparkline primary protagonista (la del KPI principal, p.ej. recaudacion neta) + cifras en foreground/success-text. Los demas KPIs usan series `--chart-2..5` o trazo muted. La fila/card de KPI seleccionada usa borde primary 1px (prioridad 3), no fondo. Acento total: sparkline (~3-5%) + 1 borde activo (~1%) -> bajo 10%.

### Tabla C — PARIDAD de iconos Lucide(web) <-> Phosphor(android), glifos de dominio obligatorios

| Glifo de dominio | Rol/uso | **Lucide** (web, 1.5px outline; fill solo nav activo) | **Phosphor** (android, `Regular`; `Fill` en seleccionado/24-28dp) | Notas |
|---|---|---|---|---|
| maquina (recreativa) | entidad maquina | `Gamepad2` | `GameController` | Glifo de dominio; mismo trazo en ambas rejillas. |
| tolva | tolva/hopper de la maquina | `PiggyBank` | `Coins` | No hay "hopper" nativo; convencion del proyecto. Mantener identico en toda la app. |
| denominacion | billete/moneda, keypad | `Banknote` | `Money` | Para importes-en-efectivo; el simbolo € va aparte. |
| firma | lienzo de firma / firmado | `PenLine` | `Signature` | Estado "firmado" = success-text + este icono. |
| impresora BT | impresora termica Bluetooth | `Printer` | `Printer` | El **estado** del impresor (conectado/no) es **neutral** (state-neutral), no success/danger por defecto. |
| averia | maquina averiada | `OctagonAlert` | `WarningOctagon` | Rol **danger**. Coherente con StatusChip error. |
| conflicto | conflicto de sync/datos | `GitMerge` | `GitMerge` | Rol **danger**. (Phosphor `GitMerge` existe.) |
| deuda EUR | saldo/deuda del local | `Euro` | `CurrencyEur` | Rol **state-neutral** (deber NO es error): foreground + icono €, NUNCA danger. |
| offline | sin conexion | `CloudOff` | `CloudSlash` | Rol **neutral/muted** (NUNCA rojo). Stale -> warning + `WifiOff`/`WifiSlash`. |
| pendiente | sin firmar / por hacer | `Clock` | `Clock` | Rol **warning**. |
| cuadra | recaudacion cuadra / OK | `CircleCheck` | `CheckCircle` | Rol **success** (icono fill ok; texto = success-text). |
| descuadre | descuadre de caja | `TriangleAlert` | `Warning` | Rol **danger** (distinto de "averia": triangulo vs octagono para diferenciar a un vistazo). |
| sincronizar | sync en curso / accion | `RefreshCw` (girando `Loader2`) | `ArrowsClockwise` (girando `CircleNotch`) | Rol **info**. Spinner girando solo si `!reduced-motion`; estatico `RefreshCw`/`ArrowsClockwise` con reduced-motion. |

> Reglas de paridad: (1) cada glifo de dominio se importa de UN unico modulo central por plataforma (`web/src/components/icons/domain.ts`, `android/.../ui/icons/DomainIcons.kt`) para que web y Android no diverjan. (2) El **rol semantico** del icono lo fija el componente (StatusChip/MoneyText), no el icono: el mismo `Euro`/`CurrencyEur` es neutral en deuda y nunca danger. (3) Trazo: Lucide `strokeWidth=1.5`; Phosphor `weight=Regular` (`Fill` solo en nav/tab activo y en chip de estado fuerte). (4) Tamanos: web 16/20px, Android 20/24dp (+1 paso de marca).

**Android**
```kotlin
// =====================================================================
// ADDENDUM Fase 3 — Chips (Filter/Segmented), presupuesto de acento e iconos
// NO redefine Color.kt / Type.kt / Shape.kt. Reusa:
//   RecreColors.current.*  (success/warning/danger/info/muted, statusChip(role))
//   MaterialTheme.colorScheme.primary/secondary/surface/onSurface...
//   MaterialTheme.shapes (medium=16dp), pill = RoundedCornerShape(50)
// StatusChip ya existe (ui/components/StatusChip.kt). Aqui solo se añaden
// las DOS familias interactivas y el catalogo de iconos de dominio.
// =====================================================================
package com.recre.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors

private val PillShape = RoundedCornerShape(50)

// ---------------------------------------------------------------------
// 1) FilterChip — TOGGLE de filtro (multi-seleccion). Neutro en reposo;
//    seleccionado = primary outline + texto primary sobre secondary.
//    NUNCA usa roles semanticos (no es estado, es filtro). M3 FilterChip
//    ya da el contrato a11y (toggleable + Role.Checkbox); aqui se viste.
// ---------------------------------------------------------------------
@Composable
fun RecreFilterChip(
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        enabled = enabled,
        shape = PillShape,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        // Indicador NO-color obligatorio: al seleccionar entra el Check (no solo color).
        leadingIcon = {
            val ic = if (selected) DomainIcons.Check else leadingIcon
            if (ic != null) Icon(ic, contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize))
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surface,                 // reposo: neutro
            labelColor = scheme.onSurfaceVariant,            // muted
            selectedContainerColor = scheme.secondary,       // marca tonal (NO fill primary)
            selectedLabelColor = scheme.primary,             // texto primary (acento, cuenta en presupuesto)
            selectedLeadingIconColor = scheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled, selected = selected,
            borderColor = scheme.outlineVariant,             // = border 1px
            selectedBorderColor = scheme.primary,            // outline primary 1px (acento, no fill)
            selectedBorderWidth = 1.dp,
        ),
        modifier = modifier.heightIn(min = 48.dp)            // touch comodo en campo
            .semantics { stateDescription = if (selected) "filtro activo" else "filtro inactivo" },
    )
}

// ---------------------------------------------------------------------
// 2) SegmentedControl — conmutador 1-de-N (2-3 opciones). Pista surface-2,
//    segmento activo surface-1 + indicador/texto primary. Role.RadioButton
//    via SingleChoiceSegmentedButtonRow (selectableGroup interno).
// ---------------------------------------------------------------------
@Composable
fun <T> RecreSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    require(options.size in 2..3) { "SegmentedControl: 2-3 opciones; >3 usar Tabs/Select" }
    val scheme = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.heightIn(min = 48.dp).selectableGroup(),
    ) {
        options.forEachIndexed { i, opt ->
            val isSel = opt == selected
            SegmentedButton(
                selected = isSel,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {
                    val ic = icon?.invoke(opt)
                    if (ic != null) Icon(ic, null, Modifier.size(18.dp))
                    else SegmentedButtonDefaults.Icon(isSel)  // Check no-color en activo
                },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = scheme.surface,           // surface-1
                    activeContentColor = scheme.primary,             // texto/indicador primary (acento)
                    activeBorderColor = scheme.primary,
                    inactiveContainerColor = scheme.surfaceVariant,  // surface-2 (pista)
                    inactiveContentColor = scheme.onSurfaceVariant,  // muted
                    inactiveBorderColor = scheme.outlineVariant,
                ),
                label = { Text(label(opt), style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

// ---------------------------------------------------------------------
// 3) Catalogo CENTRAL de iconos de dominio (Phosphor). Importar SIEMPRE
//    desde aqui para mantener paridad 1:1 con web (DomainIcons.ts Lucide).
//    El ROL semantico (success/danger/neutral) lo pone el componente que
//    los usa (StatusChip/MoneyText), NO el icono: p.ej. CurrencyEur en
//    deuda es NEUTRAL, jamas danger.
// ---------------------------------------------------------------------
// import com.adamglin.phosphoricons.* (o el paquete Phosphor del proyecto)
object DomainIcons {
    // val Maquina      = PhosphorIcons.Regular.GameController
    // val Tolva        = PhosphorIcons.Regular.Coins
    // val Denominacion = PhosphorIcons.Regular.Money
    // val Firma        = PhosphorIcons.Regular.Signature
    // val ImpresoraBt  = PhosphorIcons.Regular.Printer        // estado = state-neutral
    // val Averia       = PhosphorIcons.Regular.WarningOctagon // rol danger
    // val Conflicto    = PhosphorIcons.Regular.GitMerge       // rol danger
    // val DeudaEur     = PhosphorIcons.Regular.CurrencyEur     // rol NEUTRAL (deber != error)
    // val Offline      = PhosphorIcons.Regular.CloudSlash     // rol muted (NUNCA rojo)
    // val Pendiente    = PhosphorIcons.Regular.Clock          // rol warning
    // val Cuadra       = PhosphorIcons.Regular.CheckCircle    // rol success
    // val Descuadre    = PhosphorIcons.Regular.Warning        // rol danger (triangulo, != averia octagono)
    // val Sincronizar  = PhosphorIcons.Regular.ArrowsClockwise// rol info (gira CircleNotch si !reduced-motion)
    // val Check        = PhosphorIcons.Regular.Check          // indicador no-color de seleccion
    val placeholder: ImageVector? = null // sustituir por los de arriba al cablear Phosphor
}

// ---------------------------------------------------------------------
// PRESUPUESTO DE ACENTO (regla #3 <=10% primary): no es codigo de runtime,
// es invariante de diseño. Prioridad cuando concurren acentos:
//   1 PrimaryCTA fill (MAX 1 por pantalla)  >  2 nav/thumb activo (indicador)
//   > 3 fila/segmento/filter seleccionado (OUTLINE+texto, nunca fill)
//   > 4 sparkline/serie (cede a chart-1 atenuada o trazo 1px si satura).
// Keypad: solo el CTA confirmar es fill primary; teclas = surface-2 neutras.
// KPI Bento: 1 sparkline primary protagonista; resto series chart-2..5.
// ---------------------------------------------------------------------
```

**Web**
```ts
/* =====================================================================
 * ADDENDUM Fase 3 — Chips (Filter/Segmented), presupuesto de acento, iconos.
 * NO redefine globals.css :root/.dark (paleta) ni la escala tipografica.
 * Reusa los tokens YA definidos: --primary, --secondary, --surface-1/2,
 * --border, --muted-foreground, --foreground, --ring (= --primary), y los
 * tokens de StatusChip --{rol}-chip-bg/--{rol}-chip-fg (T-227).
 * Aqui solo se añaden DOS tokens de conveniencia para los chips interactivos
 * (no son colores nuevos: son alias semanticos del rol primary/secondary).
 * ===================================================================== */
@layer base {
  :root {
    /* FilterChip seleccionado / SegmentedControl activo (alias, no color nuevo) */
    --chip-selected-bg: var(--secondary);     /* marca TONAL, no fill primary */
    --chip-selected-fg: var(--primary);       /* texto/indicador primary (acento) */
    --chip-selected-border: var(--primary);   /* outline 1px primary */
    /* Pista del SegmentedControl */
    --segmented-track: var(--surface-2);
    --segmented-active-bg: var(--surface-1);
  }
  /* .dark: hereda; --secondary y --primary ya cambian de valor en .dark. */
}

/* ── FilterChip (toggle de filtro, multi-seleccion) ──────────────────
 * Render: <button role="button" aria-pressed={selected}>.
 * Reposo neutro; seleccionado = secondary + outline primary + texto primary
 * + Check (Lucide) como indicador NO-color (estado nunca solo color). */
const filterChipVariants = cva(
  "inline-flex items-center gap-1.5 rounded-full px-3 min-h-[44px] " +
  "text-label font-sans transition-colors duration-150 " +
  "[transition-timing-function:cubic-bezier(0.2,0,0,1)] motion-reduce:transition-none " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 " +
  "disabled:opacity-50 disabled:pointer-events-none",
  {
    variants: {
      selected: {
        false: "bg-surface-1 text-muted-foreground border border-border hover:bg-surface-2",
        true:  "bg-[var(--chip-selected-bg)] text-[var(--chip-selected-fg)] " +
               "border border-[var(--chip-selected-border)]",
      },
    },
    defaultVariants: { selected: false },
  },
);

// interface RecreFilterChipProps { selected: boolean; onToggle(v:boolean):void;
//   label: string; icon?: React.ReactNode; disabled?: boolean }
// export function RecreFilterChip({ selected, onToggle, label, icon, disabled }: RecreFilterChipProps) {
//   return (
//     <button
//       type="button"
//       role="button"
//       aria-pressed={selected}
//       disabled={disabled}
//       onClick={() => onToggle(!selected)}
//       className={filterChipVariants({ selected })}
//     >
//       {/* indicador NO-color: Check al seleccionar; si no, el icono propio del filtro */}
//       {selected ? <Check className="size-4" aria-hidden /> : icon}
//       <span>{label}</span>
//     </button>
//   );
// }

/* ── SegmentedControl (conmutador 1-de-N, 2-3 opciones) ──────────────
 * Render: <div role="radiogroup"> con hijos <button role="radio" aria-checked>.
 * Pista surface-2; segmento activo surface-1 + texto/indicador primary. */
// const segmentBtn = (active: boolean) => cn(
//   "flex-1 inline-flex items-center justify-center gap-1.5 min-h-[44px] px-3 rounded-md",
//   "text-label font-sans transition-colors duration-150 motion-reduce:transition-none",
//   "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1",
//   active
//     ? "bg-[var(--segmented-active-bg)] text-[var(--chip-selected-fg)] " +
//       "shadow-none ring-1 ring-[var(--chip-selected-border)]"   // indicador primary 1px, NO fill
//     : "text-muted-foreground hover:text-foreground",
// );
// export function RecreSegmentedControl<T extends string>({ options, value, onChange, getLabel, getIcon }: {
//   options: T[]; value: T; onChange(v:T):void; getLabel(o:T):string; getIcon?(o:T):React.ReactNode;
// }) {
//   if (options.length < 2 || options.length > 3) throw new Error("Segmented: 2-3 opciones");
//   return (
//     <div role="radiogroup" className="inline-flex gap-1 rounded-md bg-[var(--segmented-track)] p-1 border border-border">
//       {options.map((o) => {
//         const active = o === value;
//         return (
//           <button key={o} role="radio" aria-checked={active} onClick={() => onChange(o)} className={segmentBtn(active)}>
//             {active ? <Check className="size-4" aria-hidden /> : getIcon?.(o)}
//             <span>{getLabel(o)}</span>
//           </button>
//         );
//       })}
//     </div>
//   );
// }

/* ── Catalogo CENTRAL de iconos de dominio (Lucide) ──────────────────
 * web/src/components/icons/domain.ts — importar SIEMPRE desde aqui para
 * mantener paridad 1:1 con android (DomainIcons.kt Phosphor). El ROL
 * semantico lo pone el componente consumidor (StatusChip/MoneyText), no el
 * icono: <Euro/> en deuda es NEUTRAL (foreground+icono €), JAMAS danger. */
// import {
//   Gamepad2, PiggyBank, Banknote, PenLine, Printer, OctagonAlert,
//   GitMerge, Euro, CloudOff, Clock, CircleCheck, TriangleAlert,
//   RefreshCw, Loader2, WifiOff, Check,
// } from "lucide-react";
// export const DomainIcons = {
//   maquina: Gamepad2,        // entidad maquina
//   tolva: PiggyBank,         // tolva/hopper (convencion proyecto)
//   denominacion: Banknote,   // billete/moneda, keypad
//   firma: PenLine,           // lienzo de firma / firmado (success-text)
//   impresoraBt: Printer,     // estado impresor = state-neutral
//   averia: OctagonAlert,     // rol danger
//   conflicto: GitMerge,      // rol danger
//   deudaEur: Euro,           // rol NEUTRAL (deber != error)
//   offline: CloudOff,        // rol muted (NUNCA rojo); stale -> WifiOff + warning
//   stale: WifiOff,           // datos antiguos -> warning
//   pendiente: Clock,         // rol warning
//   cuadra: CircleCheck,      // rol success
//   descuadre: TriangleAlert, // rol danger (triangulo != averia octagono)
//   sincronizar: RefreshCw,   // rol info (estatico con reduced-motion)
//   sincronizando: Loader2,   // spinner girando solo si !reduced-motion
//   check: Check,             // indicador no-color de seleccion (filter/segmented)
// } as const;
// Trazo: <Icon strokeWidth={1.5} />; tamaños 16/20px. fill solo en nav activo.

/* ── PRESUPUESTO DE ACENTO (regla #3 <=10% primary) — invariante de diseño:
 * cuenta primary como FILL/OUTLINE/TEXTO (no el focus ring transitorio).
 * Prioridad: 1) PrimaryCTA fill (MAX 1/pantalla) > 2) nav activo (indicador)
 * > 3) fila/segmento/FilterChip seleccionado (OUTLINE+texto, nunca fill)
 * > 4) sparkline/serie (cede a --chart-1 atenuada / 1px si satura).
 * Bento: 1 sparkline primary protagonista; resto series --chart-2..5. */
```

> Es un ADDENDUM correctivo de Fase 3 (ronda 2), no redefine la paleta ni la tipografia ya materializadas. Coherencia verificada contra fase3-design-tokens.md y fase3-component-specs.md.

Decisiones clave:
1) TRES familias claramente separadas por interactividad/seleccion: StatusChip (readonly, soft por rol, ya especificado en C-StatusChip; aqui NO se reabre, solo se referencia su tabla de tokens --{rol}-chip-bg/fg precomputados) vs FilterChip (toggle multi-seleccion, neutro->primary outline) vs SegmentedControl (1-de-N excluyente). El "selected" que el spec de StatusChip dejaba explicitamente "fuera del nucleo / variante FilterChip" se materializa aqui.
2) FilterChip y SegmentedControl NO crean roles de color nuevos: reusan --primary/--secondary/--surface-1/2/--border/--muted-foreground. Solo se añaden alias de conveniencia (--chip-selected-bg=secondary, -fg=primary, --segmented-track=surface-2). Seleccionado = OUTLINE+texto primary sobre secondary (marca tonal), NUNCA fill primary, para respetar el presupuesto de acento.
3) Indicador NO-color obligatorio en ambos interactivos: Check (Lucide/Phosphor) al seleccionar, ademas del color (regla "estado/seleccion nunca solo color").
4) a11y diferenciada y correcta: FilterChip = button + aria-pressed (web) / toggleable Role.Checkbox + stateDescription (Android, M3 FilterChip). SegmentedControl = radiogroup>radio aria-checked (web) / SingleChoiceSegmentedButtonRow + selectable Role.RadioButton (Android). StatusChip readonly: decorativo, el estado va al aria-label de la fila.
5) PRESUPUESTO DE ACENTO operativo: definicion de que cuenta (fill+outline+texto primary; el focus ring NO cuenta; secondary NO cuenta por ser marca tonal), umbral 10% del viewport, como medirlo (conteo de pixeles por captura) y REGLA DE PRIORIDAD de 4 niveles para resolver concurrencia (CTA fill > nav indicador > fila/segmento/filter outline > sparkline que cede). Casos resueltos: keypad (solo CTA confirmar es fill; teclas surface-2 neutras) y KPI Bento (1 sparkline primary, resto chart-2..5).
6) PARIDAD de iconos Lucide<->Phosphor para los 13 glifos obligatorios + Check, con su rol semantico fijado por el COMPONENTE (no el icono): deuda EUR = NEUTRAL (Euro/CurrencyEur, jamas danger), offline = muted (CloudOff/CloudSlash, nunca rojo), averia=octagono danger vs descuadre=triangulo danger (diferenciados a un vistazo), cuadra=success, pendiente=warning, sincronizar=info. Recomendacion firme: catalogo central unico por plataforma (web/src/components/icons/domain.ts y android .../ui/icons/DomainIcons.kt) para evitar deriva entre superficies.

Pendiente/dependencia: el codigo web y android usa simbolos de T-227 (tokens de StatusChip --{rol}-chip-bg/fg, RecreColors) y los paquetes de iconos (lucide-react ya presente; Phosphor: confirmar el paquete Compose del proyecto, p.ej. com.adamglin:phosphor-icons, antes de descomentar DomainIcons.kt). El M3 SegmentedButton requiere Material3 1.2.0+. No compilado/validado en este entorno (sin gradle/tailwind aqui).

Ficheros objetivo sugeridos (absolutos):
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/components/RecreFilterChip.kt (nuevo)
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/components/RecreSegmentedControl.kt (nuevo)
- /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/icons/DomainIcons.kt (nuevo, catalogo Phosphor)
- /home/a/Escritorio/recre-main/web/src/components/common/filter-chip.tsx y segmented-control.tsx (nuevos)
- /home/a/Escritorio/recre-main/web/src/components/icons/domain.ts (nuevo, catalogo Lucide)
- /home/a/Escritorio/recre-main/web/src/app/globals.css (añadir solo los 5 alias --chip-selected-*/--segmented-* en :root)


---

## Motion · bindings por átomo/plataforma + gate de accesibilidad (ADDENDUM correctivo)  ·  `fase3-motion-bindings-a11y-gate`

## Bindings de animación nombrada → átomo · plataforma · reduced-motion · live-region

| Animación (token motion) | Átomo (id) · pantallas | Web (binding) | Android (binding) | Disparador | reduced-motion (real) | aria-live / role |
|---|---|---|---|---|---|---|
| **count-up** (`--motion-duration-countup` 600ms / `RecreMotion.countUp()`) | MoneyTextCountUp · `C-MoneyText` (A.5/E.2/F.2, `C-HERO-01`) | `@number-flow/react` `animated={!reduced}`; capa `aria-hidden` | `rememberCountUpDisplay()` → `animateFloatAsState(decorative(countUp()))` | respuesta del servidor (neto / `parte_empresa`) | web `useReducedMotion()` salta al valor; Android `decorative()`→`tween(0)` snap | `<span sr-only aria-live="polite">` con importe exacto / `Modifier.moneyCountUpLiveRegion()` (Polite) |
| **offline-pulse** (`--motion-duration-offline-pulse` 1600ms / `offlinePulse()`) | OfflineBadge · `A-OfflineBadge` | `.motion-offline-pulse` (warning/ámbar) | `Modifier.offlinePulse(state)` (alpha 1→0.45) | estado offline-stale (no danger) | web `@media reduce`→`none`; Android `if(reducedMotion) return this` (badge estático 100%) | `role="status"` (badge ya es icono+texto+color) |
| **sync-spin** (`--motion-duration-sync-spin` 900ms / `syncSpin()`) | SyncControl spinner · `C-SYNC-01` | `.motion-sync-spin` sobre icono `aria-hidden` | `Modifier.syncSpin(state)` (rotationZ 0→360) | sincronización en curso | web `none`; Android `if(reducedMotion) return this` (icono quieto, texto comunica) | icono `aria-hidden`; texto "Sincronizando" en `role="status"` |
| **success-flash** (`--motion-duration-success-flash` 900ms / `successFlash()`+`flashTint`) | SyncControl éxito · `C-SYNC-01` | `.motion-success-flash` (toggle) | `rememberSuccessFlashColor(decorative(successFlash()))` | sincronización OK (verde solo confirma) | web `none`; Android `decorative()`→factor 0 (sin tinte) | `aria-live="polite"` "Sincronizado" |
| **danger-shake** (`--motion-duration-danger-shake` 400ms / `dangerShake()`) | Bloque progreso keypad / cifras · `C-KEYPAD-DENOM-AND` R4, `C-HERO-01` | `.motion-danger-shake` `key={intentos}` (decorativo) | `Modifier.dangerShake(trigger, state)` (offset ±8px) | descuadre / intento de continuar sin cuadrar | web `none`; Android `decorative()`→`tween(0)` sin desplazar | shake `aria-hidden`; chip "Faltan/Sobran X €" en la live-region del progreso |
| **popover/Cmd+K** (`--motion-duration-fast` 120ms / `popoverFade()`+`popoverOffset()`) | Command Palette / popover | `.motion-popover-in` (fade+4px) | `RecreMotion.popoverFade()` + `popoverOffset()` | abrir popover / palette | web `none` (`@media reduce`); Android `decorative()` snap | foco trasladado al panel; `role="dialog"`/`listbox` |

## Reduced-motion REAL por plataforma (cierra el gap del stub)

| Plataforma | Fuente del SO | API | Resultado |
|---|---|---|---|
| Web (CSS) | `prefers-reduced-motion: reduce` | `@media` ya escrito | `.motion-*`→`animation:none`; transiciones `0.01ms`; loops 1 iter |
| Web (React) | mismo media query | `MotionConfig reducedMotion="user"` (motion 12.x) + `useReducedMotion()` (`useSyncExternalStore`+`matchMedia`, SSR-safe) | `<motion.*>` colapsan; number-flow `animated={false}` |
| Android | `Settings.Global.ANIMATOR_DURATION_SCALE` | `rememberReduceMotionState()` (lee `getFloat(...,1f)`; `==0f`→reduced) provisto en `LocalRecreMotion` desde `RecreTheme` | `decorative()`→`tween(0)` snap; `Modifier.*`→`return this`; **sustituye el stub `isAnimationScaleZero()`** |

## GATE de accesibilidad por átomo (checklist obligatorio; cada átomo declara su par texto/fondo + ratio)

| Átomo (id) | Contraste (par texto/fondo · ratio) | Target ≥48dp/44px | Foco visible (ring 2px=primary) | reduced-motion | label / role / aria-live |
|---|---|---|---|---|---|
| MoneyTextCountUp `C-MoneyText` | dígito `foreground`/`surface-1` (light 16.9:1, dark ~13:1); € `muted` (light #646B76 5.38:1, dark #9AA1AD ~7:1) | n/a (texto); si KPI clicable el contenedor aporta target | contenedor clicable: `.focus-ring`/ring | sí (`animated={!reduced}` / `decorative`) | valor exacto en `aria-live="polite"`; signo "−" NO `aria-hidden` |
| OfflineBadge `A-OfflineBadge` | etiqueta `warning-text` #8A3D0A/`warning@12%` 6.x:1 (light); dark `warning-text`/`warning@16%` ~7:1 | badge no interactivo; si lo es, host ≥48dp | si interactivo: ring 2px | sí (`offlinePulse`/`.motion-offline-pulse`) | `role="status"`; icono+texto (nunca solo color) |
| SyncControl `C-SYNC-01` | botón `on-primary`/`primary` (light primary oscurecido #0A5060 9.02:1); texto estado `info-text`/`surface` AA | botón sync ≥48dp/44px | ring 2px=primary en `focus-visible` | sí (`syncSpin`+`successFlash`/`.motion-*`) | icono `aria-hidden`; "Sincronizando/Sincronizado" en `role="status"` |
| Keypad progreso `C-KEYPAD-DENOM-AND` R4 | Total `foreground`/`surface-1` ~18:1; chip Cuadra `on-success-container`#064E3B/`success-container`#D1FAE5 8.57:1; Faltan/Sobran `on-danger-container`#7F1D1D/#FEE2E2 8.20:1 | teclas 64dp (mín 48dp), gap 8dp; CTA ≥48dp | ring 2px=primary sobre tecla/fila; orden lógico | sí (`dangerShake`/`.motion-danger-shake` snap) | `role="status"` + `aria-live="polite" aria-atomic` ("Total … · Faltan/Sobran …"); shake `aria-hidden` |
| CifrasResumenCard/héroe `C-HERO-01` | importe `foreground`/`surface-1`; deuda = `foreground` NEUTRO + icono € (deber NO es danger) | celda no interactiva | host clicable: ring 2px | sí (count-up + flash/shake) | importe en live-region del héroe; estado icono+texto+color |

**Android**
```kotlin
// =====================================================================
// MotionBindings.kt + ReduceMotion.kt — ADDENDUM al grupo `motion` de
// fase3-design-tokens.md. NO redefine RecreMotion/RecreMotionState (ya
// existen en Motion.kt): los LIGA a sus atomos y CIERRA el gap abierto
// (lectura real de ANIMATOR_DURATION_SCALE; el stub isAnimationScaleZero()
// pasa a ser una funcion real). Stack actual: Compose BOM 2024.11.00,
// Material3 1.3.x, Kotlin 2.0.21. Sin dependencias nuevas.
// Atomos vinculados: C-MoneyText (MoneyTextCountUp, A.5/E.2/F.2),
// A-OfflineBadge, C-SYNC-01, C-KEYPAD-DENOM-AND (R4 progreso), C-HERO-01.
// =====================================================================
package com.recre.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// ---------------------------------------------------------------------
// 1) REDUCED-MOTION REAL (cierra el gap: stub isAnimationScaleZero()).
//    Fuente de verdad del SO: Settings.Global.ANIMATOR_DURATION_SCALE.
//    == 0f  => "Eliminar animaciones" activo => reducedMotion = true.
//    Se provee LocalRecreMotion en RecreTheme con ESTE valor.
// ---------------------------------------------------------------------

/**
 * Resuelve reduced-motion leyendo de verdad la escala de animaciones del SO.
 * Sustituye al `rememberRecreMotionState()` con stub: aqui SI hay Context.
 * Llamar en RecreTheme y proveer LocalRecreMotion con el resultado:
 *
 *   CompositionLocalProvider(LocalRecreMotion provides rememberReduceMotionState()) { ... }
 */
@Composable
fun rememberReduceMotionState(): RecreMotionState {
    val resolver = LocalContext.current.contentResolver
    // ANIMATOR_DURATION_SCALE refleja el toggle de accesibilidad y "Opciones de
    // desarrollador > Escala de animacion del Animator". 1f = normal; 0f = sin animacion.
    val scale = remember(resolver) {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f, // por defecto seguro: animaciones activas
        )
    }
    return RecreMotionState(reducedMotion = scale == 0f)
}

// ---------------------------------------------------------------------
// 2) BINDINGS por atomo. Cada uno consume RecreMotion.* (ya existente) y
//    pasa por state.decorative(...) para degradar a snap en reduced-motion
//    SIN perder el cambio de estado (valor/icono/texto siguen comunicando).
// ---------------------------------------------------------------------

/**
 * count-up tabular de MoneyTextCountUp (C-MoneyText; pantallas A.5/E.2/F.2).
 * PRESENTACION, no calculo: [target] llega ya calculado del servidor
 * (calcular-recaudacion / _shared/calculo.ts). El Float interpolado es
 * EFIMERO (solo display); el frame final y el texto accesible salen del
 * BigDecimal exacto. La capa de animacion es aria-hidden; el valor real se
 * anuncia por separado con Modifier.liveRegion (ver moneyCountUpLiveRegion).
 */
@Composable
fun rememberCountUpDisplay(target: Float, state: RecreMotionState): Float {
    val anim by animateFloatAsState(
        targetValue = target,
        animationSpec = state.decorative(RecreMotion.countUp()), // reduced => tween(0) = snap
        label = "money-count-up",
    )
    return anim
}

/**
 * Gancho aria-live del importe heroe (count-up). El valor visible de la
 * capa animada es aria-hidden; este modifier va en el Text con el importe
 * FORMATEADO exacto, para que TalkBack anuncie "1.234,56 €" al asentar
 * (no cada frame). Polite = no interrumpe. C-MoneyText / C-HERO-01.
 */
fun Modifier.moneyCountUpLiveRegion(importeFormateado: String): Modifier =
    this.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = importeFormateado
    }

/** offline-pulse: alpha 1f -> 0.45f en bucle (A-OfflineBadge). WARNING/ambar, NO danger. */
@Composable
fun Modifier.offlinePulse(state: RecreMotionState): Modifier {
    if (state.reducedMotion) return this // snap: badge estatico al 100% (icono+texto siguen)
    val transition = rememberInfiniteTransition(label = "offline-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = RecreMotion.offlinePulse(), // ya define Reverse + 1600ms
        label = "offline-pulse-alpha",
    )
    return this.graphicsLayer { this.alpha = alpha }
}

/** sync-spin: rotacion continua 0->360 lineal (C-SYNC-01, icono ArrowsClockwise). */
@Composable
fun Modifier.syncSpin(state: RecreMotionState): Modifier {
    if (state.reducedMotion) return this // snap: icono quieto; el texto "Sincronizando" comunica
    val transition = rememberInfiniteTransition(label = "sync-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = RecreMotion.syncSpin(),
        label = "sync-spin-angle",
    )
    return this.graphicsLayer { rotationZ = angle }
}

/**
 * success-flash al sincronizar OK (C-SYNC-01). Devuelve el color de fondo
 * ya mezclado con el tinte success segun el factor 0..1; usar como
 * background del contenedor que confirma. flashTrigger cambia para relanzar.
 */
@Composable
fun rememberSuccessFlashColor(
    base: Color,
    success: Color,
    flashTrigger: Any?,
    state: RecreMotionState,
): Color {
    val factor by animateFloatAsState(
        targetValue = 0f,
        animationSpec = state.decorative(RecreMotion.successFlash()),
        label = "success-flash-$flashTrigger",
    )
    return flashTint(base = base, tint = success, factor = factor)
}

/**
 * danger-shake del bloque de progreso del keypad / cifras ante descuadre
 * (C-KEYPAD-DENOM-AND R4). Oscilacion +-8px que decae. En reduced-motion no
 * desplaza: el chip "Faltan/Sobran X €" (icono+texto+color) ya comunica el error.
 */
@Composable
fun Modifier.dangerShake(shakeTrigger: Any?, state: RecreMotionState): Modifier {
    val offsetX by animateFloatAsState(
        targetValue = 0f,
        animationSpec = state.decorative(RecreMotion.dangerShake()),
        label = "danger-shake-$shakeTrigger",
    )
    return this.offset { IntOffset(offsetX.roundToInt(), 0) }
}

// ---------------------------------------------------------------------
// 3) Haptic del keypad gobernado por DOS ajustes (in-app + sistema). El
//    haptic es "movimiento" perceptivo: si reduced-motion o el ajuste in-app
//    estan off, no se emite. LIGERO (tick de tecla virtual), nunca LongPress.
// ---------------------------------------------------------------------
@Composable
fun rememberKeypadTick(hapticsHabilitadoInApp: Boolean): () -> Unit {
    val view = LocalView.current
    return remember(view, hapticsHabilitadoInApp) {
        {
            if (hapticsHabilitadoInApp) {
                // VIRTUAL_KEY = tick de tecla; respeta el ajuste de haptics del SO.
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
}

// ---------------------------------------------------------------------
// 4) aria-live del BLOQUE DE PROGRESO del keypad (R4). El Total y el Estado
//    (Cuadra/Faltan/Sobran) son una REGION VIVA: TalkBack anuncia el nuevo
//    "Total X € · Faltan Y €" al cambiar, sin que el usuario reenfoque.
//    Polite = espera a que termine de hablar. Se aplica al CONTENEDOR de los
//    3 numeros heroe, con un contentDescription compuesto y estable.
// ---------------------------------------------------------------------
fun Modifier.keypadProgresoLiveRegion(
    totalFormateado: String,
    estadoTexto: String, // "Cuadra" | "Faltan 12,50 €" | "Sobran 3,00 €"
): Modifier = this.semantics {
    liveRegion = LiveRegionMode.Polite
    contentDescription = "Total contabilizado $totalFormateado. $estadoTexto"
}

// =====================================================================
// CABLEADO en Theme.kt (reemplaza el provider con stub):
//
//   @Composable
//   fun RecreTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
//     CompositionLocalProvider(
//       LocalRecreColors provides recreSemanticColors(darkTheme),
//       LocalRecreMotion provides rememberReduceMotionState(), // <-- real, no stub
//     ) {
//       MaterialTheme(colorScheme = ..., typography = Typography, content = content)
//     }
//   }
//
// Consumo en un atomo (patron unico):
//   val motion = LocalRecreMotion.current
//   Box(Modifier.offlinePulse(motion)) { OfflineBadge(...) }      // A-OfflineBadge
//   Icon(..., modifier = Modifier.syncSpin(motion))               // C-SYNC-01
//   Text(formatEur(neto), Modifier.moneyCountUpLiveRegion(formatEur(neto)))  // C-MoneyText
//   Row(Modifier.dangerShake(intentos, motion)) { ChipEstado(...) }          // keypad R4
//   Row(Modifier.keypadProgresoLiveRegion(totalFmt, estadoTxt)) { ... }      // keypad R4
// =====================================================================
```

**Web**
```ts
/* =====================================================================
 * ADDENDUM web al grupo `motion` (fase3-design-tokens.md). NO redefine los
 * tokens/keyframes/clases .motion-* ya escritos: los LIGA a los atomos y
 * formaliza el reduced-motion REAL (CSS + React) y el aria-live del keypad.
 * Atomos: C-MoneyText (MoneyTextCountUp A.5/E.2/F.2), A-OfflineBadge,
 * C-SYNC-01, C-KEYPAD-DENOM-AND (R4 progreso), C-HERO-01.
 * ===================================================================== */

/* ----------------------------------------------------------------------
 * 4.A — App root: MotionConfig de motion 12.x respeta el ajuste del SO.
 *       Va en el provider raiz (junto al ThemeProvider). reducedMotion="user"
 *       hace que TODA <motion.*> y AnimatePresence colapsen cuando el usuario
 *       pide menos movimiento; complementa el @media (prefers-reduced-motion)
 *       que ya apaga los keyframes CSS .motion-*.
 * -------------------------------------------------------------------- */
// app/providers.tsx
import { MotionConfig } from "motion/react";
export function MotionProvider({ children }: { children: React.ReactNode }) {
  // "user" = sigue prefers-reduced-motion del SO (no fuerza ni ignora).
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>;
}

/* ----------------------------------------------------------------------
 * 4.B — Hook unico de reduced-motion (fuente: matchMedia). Lo consumen los
 *       atomos que NO son motion/* puros (p.ej. el count-up de number-flow).
 *       SSR-safe: arranca en false y se hidrata; suscrito a cambios en vivo.
 * -------------------------------------------------------------------- */
// lib/motion/use-reduced-motion.ts
import { useSyncExternalStore } from "react";
const QUERY = "(prefers-reduced-motion: reduce)";
function subscribe(cb: () => void): () => void {
  if (typeof window === "undefined") return () => {};
  const mq = window.matchMedia(QUERY);
  mq.addEventListener("change", cb);
  return () => mq.removeEventListener("change", cb);
}
export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(QUERY).matches, // client
    () => false,                            // server (sin animacion en SSR)
  );
}

/* ----------------------------------------------------------------------
 * 4.C — count-up de MoneyTextCountUp (C-MoneyText, A.5/E.2/F.2).
 *       number-flow PRESENTA el valor server-side; jamas recalcula. El frame
 *       final y el texto accesible salen del string/Decimal exacto. La capa
 *       animada es aria-hidden; un <span class="sr-only" aria-live="polite">
 *       lleva el valor exacto para el lector (no se anuncia cada frame).
 * -------------------------------------------------------------------- */
// components/common/money-text-count-up.tsx
import NumberFlow from "@number-flow/react";
import { useReducedMotion } from "@/lib/motion/use-reduced-motion";

export function MoneyTextCountUp({
  value,            // number EFIMERO solo para el display; NO es la fuente de verdad
  formatted,        // string exacto ya formateado es-ES desde el Decimal del servidor
}: { value: number; formatted: string }) {
  const reduced = useReducedMotion();
  return (
    <span className="num-tabular">
      {/* Capa visual animada: aria-hidden para no spamear al lector cada frame */}
      <NumberFlow
        value={value}
        aria-hidden
        animated={!reduced}            // reduced-motion => salta al valor final
        transformTiming={{ duration: 600, easing: "var(--motion-ease-standard)" }}
        format={{ style: "currency", currency: "EUR" }}
        locale="es-ES"
      />
      {/* Valor exacto para TalkBack/NVDA; polite no interrumpe */}
      <span className="sr-only" aria-live="polite">{formatted}</span>
    </span>
  );
}

/* ----------------------------------------------------------------------
 * 4.D — Bindings de clase por atomo (consumen las .motion-* ya definidas):
 *   A-OfflineBadge  -> .motion-offline-pulse   (warning/ambar, NO danger)
 *   C-SYNC-01 icono -> .motion-sync-spin
 *   C-SYNC-01 OK    -> .motion-success-flash    (toggle al sincronizar)
 *   keypad R4 / cifras descuadre -> .motion-danger-shake (key= n. de intento)
 * El @media (prefers-reduced-motion: reduce) ya escrito pone estas a none.
 * -------------------------------------------------------------------- */
// OfflineBadge: <span role="status" class="... motion-offline-pulse">…  (icono+texto, no solo color)
// SyncControl spinner: <PhosphorArrowsClockwise aria-hidden class="motion-sync-spin" />
// SyncControl exito:  <div class={cn(syncedOk && "motion-success-flash")} aria-live="polite">Sincronizado</div>
// Keypad descuadre:   <div key={intentos} className="motion-danger-shake"> <ChipEstado/> </div>

/* ----------------------------------------------------------------------
 * 4.E — aria-live del BLOQUE DE PROGRESO del keypad (R4, C-KEYPAD-DENOM-AND).
 *       Total + Estado son una region viva: el lector anuncia el nuevo
 *       "Total … · Faltan/Sobran …" al cambiar. role="status" == aria-live
 *       polite + atomic; el shake es decorativo (aria-hidden, key por intento).
 * -------------------------------------------------------------------- */
// components/features/arqueo/keypad-progreso.tsx
export function KeypadProgreso({
  totalFmt, objetivoFmt, estado,
}: { totalFmt: string; objetivoFmt: string;
     estado: { tipo: "cuadra" | "faltan" | "sobran"; difFmt?: string } }) {
  const estadoTexto =
    estado.tipo === "cuadra" ? "Cuadra"
    : estado.tipo === "faltan" ? `Faltan ${estado.difFmt}`
    : `Sobran ${estado.difFmt}`;
  return (
    <div role="status" aria-live="polite" aria-atomic="true">
      {/* contenido visual: 3 numeros heroe num-tabular + ChipEstado (icono+texto+color) */}
      <span className="sr-only">
        Objetivo {objetivoFmt}. Total contabilizado {totalFmt}. {estadoTexto}.
      </span>
      {/* … numeros visibles + chip … */}
    </div>
  );
}

/* ----------------------------------------------------------------------
 * 4.F — Utilidad de FOCO VISIBLE del gate (ring 2px = primary). Pegar en
 *       globals.css @layer utilities. Reemplaza outline del navegador.
 * -------------------------------------------------------------------- */
/*
@layer utilities {
  .focus-ring {
    outline: none;
  }
  .focus-ring:focus-visible {
    outline: 2px solid var(--ring);   // ring == primary
    outline-offset: 2px;
    border-radius: inherit;
  }
}
*/
```

> ADDENDUM correctivo al grupo `motion` de fase3-design-tokens.md (L1080-1456): NO redefine los tokens, keyframes ni el contrato público (`RecreMotion.*`, `RecreMotionState`, `LocalRecreMotion`, clases `.motion-*`, vars `--motion-*`). Solo (a) liga las 6 animaciones nombradas a sus átomos+plataformas con código consumible, (b) cierra el gap abierto de reduced-motion en Android y lo formaliza en web, y (c) añade el GATE de accesibilidad por átomo.

CIERRA EL GAP ANDROID: el stub `isAnimationScaleZero()` (declarado pendiente en L1454) se sustituye por `rememberReduceMotionState()`, que lee de verdad `Settings.Global.ANIMATOR_DURATION_SCALE` (==0f ⇒ reducedMotion). Hay que cablearlo en `RecreTheme` (Theme.kt) proveyendo `LocalRecreMotion provides rememberReduceMotionState()` en vez del `RecreMotionState(false)` por defecto. Es la lectura "real" que pedía el brief (NO se asume API web en Android).

REDUCED-MOTION REAL (ambas plataformas, no aspiracional): web combina las 3 capas ya existentes (`@media prefers-reduced-motion` apaga `.motion-*`) con `MotionConfig reducedMotion="user"` (motion 12.x) para `<motion.*>` y un hook `useReducedMotion()` SSR-safe (`useSyncExternalStore`+`matchMedia`) para number-flow. Android: `decorative()`→`tween(0)` para specs y `if(reducedMotion) return this` para los `Modifier.*` infinitos (pulse/spin), que de otro modo seguirían animando. En reduced-motion el VALOR/icono/texto/color se mantienen: solo desaparece el movimiento.

SSOT respetado: count-up es presentación. Web number-flow recibe `value` efímero pero el texto accesible (`aria-live`) y el frame final salen del string/Decimal exacto; Android anima un Float efímero y el `moneyCountUpLiveRegion` anuncia el importe formateado exacto (BigDecimal). Ningún binding recalcula cifras (calcular-recaudacion / _shared/calculo.ts siguen siendo la única fuente).

REGLAS DE COLOR del brief aplicadas en el gate: offline-pulse y OfflineBadge en WARNING/ámbar (offline-stale NO es danger); success-flash SOLO confirma (verde); danger-shake SOLO descuadre/error. El estado del keypad (Cuadra/Faltan/Sobran) y el badge offline cumplen "estado nunca solo color" → icono+texto+color. Deuda (héroe) = foreground neutro + icono €, nunca danger. Las variantes -text en LIGHT (success-text #076138, danger-text #A81818, warning-text #8A3D0A, info-text #1D4ED8) son las del par texto/fondo del gate para chips soft; los fills se reservan a iconos/cifras grandes (coherente con las CORRECCIONES DE CONTRASTE del brief).

GATE de accesibilidad = checklist de 5 ejes que TODO átomo declara: (1) contraste par texto/fondo + ratio numérico (AA suelo en ambos modos, ~7:1 garantizado en dark y en texto -text de light; primary/muted ~5.4 AA fuerte), (2) target ≥48dp Android / ≥44px web (teclas keypad 64dp), (3) foco visible ring 2px=primary (`.focus-ring`/`focus-visible` web; ring en tecla/fila Android), (4) reduced-motion real, (5) label/role/aria-live. Los ratios de chips/keypad provienen verbatim de `C-KEYPAD-DENOM-AND` (success on-container 8.57:1, danger on-container 8.20:1) — no recalculados a ojo.

ARIA-LIVE del bloque de progreso del keypad (R4): el spec ya pedía "región viva" para el Total/Estado; aquí se materializa con `role="status"` + `aria-live="polite"` + `aria-atomic="true"` (web `KeypadProgreso`) y `Modifier.liveRegion(LiveRegionMode.Polite)` con contentDescription compuesto estable (Android `keypadProgresoLiveRegion`). Polite = anuncia "Total X € · Faltan/Sobran Y €" al asentar, sin interrumpir; el shake es decorativo (`aria-hidden`, key por nº de intento). El importe del count-up usa la misma técnica (`sr-only aria-live` web / `moneyCountUpLiveRegion` Android) para no spamear frame a frame.

Ficheros destino (absolutos): Android nuevo /home/a/Escritorio/recre-main/android/app/src/main/java/com/recre/app/ui/theme/MotionBindings.kt (+ proveer LocalRecreMotion en Theme.kt L27-37). Web: hook /home/a/Escritorio/recre-main/web/src/lib/motion/use-reduced-motion.ts, provider en /home/a/Escritorio/recre-main/web/src/app/providers.tsx (o layout.tsx), `.focus-ring` en /home/a/Escritorio/recre-main/web/src/app/globals.css (@layer utilities), y los bindings de clase en los componentes de cada átomo. Dependencia de stack: number-flow (@number-flow/react) y motion 12.x ya previstos en design-system-plan §3.2; los tokens motion del addendum no añaden dependencias en Android (mismo androidx.compose.animation.core).

Sin compilar en este entorno (sin gradle/tailwind/next build): `Settings.Global.getFloat` con default 1f es seguro; `view.performHapticFeedback(VIRTUAL_KEY)` cubre el stack actual (para SegmentTick hace falta BOM ≥2024.09 + API; VIRTUAL_KEY es el fallback universal pedido por el spec del keypad). El hook web es SSR-safe (server snapshot=false). Verificar al integrar que `LocalRecreMotion` se provee una sola vez en RecreTheme (evitar doble provider).


---

## ADDENDUM Fase 3 — Matriz de dinero + tipificación de inputs de texto  ·  `fase3-tokens-addendum-dinero-inputs-texto`

### Matriz de DINERO (rol -> color por tamaño; signo)

| Caso de negocio | Rol | Cifra grande / KPI / icono | Texto pequeño / etiqueta | Signo | Regla |
|---|---|---|---|---|---|
| Recaudación neta, parte empresa, dinero+ | success | `success` fill (#0E8A55 / #34D399) | `success-text` (#076138 / #34D399) | sin `+` (salvo delta/trend) | verde SOLO dinero+/cuadra |
| Caja **cuadra** (resumen confirmado) | success | `success` fill | `success-text` | — | igual que dinero+ |
| **Deuda / saldo pendiente** (deber) | state-neutral | `foreground` + icono EUR | `foreground` neutro | sin signo de error | deber NO es error -> **NUNCA danger** |
| **Reposición de tolva / tasa / retención** (resta) | state-neutral | `muted-foreground` | `muted-foreground` | prefijo `−` (heredado, NUNCA aria-hidden) | resta informativa, neutra |
| **Descuadre / error de caja** (faltan/sobran que bloquea) | danger | `danger` fill (#DC2626 / #F87171) | `danger-text` (#A81818 / #F87171) | `−`/`+` según delta | rojo SOLO descuadre/error |
| Importe informativo sin connotación | plain | `foreground` | `foreground` | — | neutro |

Notas transversales: dinero jamás `float`/`Double`/`number` (BBDD `numeric(10,2)`, TS `decimal.js` string, Kotlin `BigDecimal`); Geist Mono tabular; el `−` NUNCA es `aria-hidden` (el lector debe anunciar el signo). En LIGHT el `fill` del rol se reserva a icono/relleno/cifra grande y el TEXTO pequeño usa `-text`; en DARK `-text == fill` (≥6.6:1 sobre surface-1).

### Tipificación de inputs de TEXTO (regla #7) — campo -> teclado -> componente

| Campo (ejemplos) | inputMode (web) / type | KeyboardType (Android) | Componente | Notas |
|---|---|---|---|---|
| **Denominaciones** (conteo de caja) | — (keypad in-app) | — (keypad in-app) | Keypad denominaciones | ÚNICO teclado in-app; el del sistema NO se abre |
| Contador / cantidad entera | `inputMode="numeric"` `type="text"` | `Number` | FieldNum | enteros; sin separador decimal |
| Importe editable a mano (tasa, % manual) | `inputMode="decimal"` | `Decimal` | FieldNum | fuera de keypad; coma decimal del sistema |
| Nombre, dirección, titular, descripción corta | `type="text"` | `Text` (Sentences) | FieldNum / Input | autocapitaliza frases |
| **Descripción/notas de avería** (ReportarAveria C.1) | `type="text"` multilínea | `Text` (Sentences, newline) | **TextArea** (nuevo) | multilínea, contador `n/max` mono, `min-h 88px` web / 3–8 líneas android |
| Email | `inputMode="email"` `type="email"` | `Email` (sin autocorrect) | FieldNum / Input | minúsculas, sin autocapitalizar |
| Teléfono | `inputMode="tel"` `type="tel"` | `Phone` | FieldNum / Input | dígitos + `+ ( ) -` |
| Búsqueda | `inputMode="search"` `type="search"` | `Text` + `ImeAction.Search` | **SearchField** (ya especificado) | lupa + clear; acción buscar |
| **Fechas** (instalación, caducidad, periodo) | — (no se teclea) | — (no se teclea) | **DatePicker** M3 / shadcn | trigger readOnly; nunca input libre |
| CCAA / select largo | — (combobox) | — (combobox) | Combobox CCAA (FieldNum select) | lista filtrable, no teclado libre |


**Android**
```kotlin
// =====================================================================
// ADDENDUM Fase 3 (ronda 2) — Color.kt / Type.kt / inputs
// NO redefine la paleta de fase3-design-tokens.md; AÑADE:
//  (1) variantes *-text para DINERO/etiqueta-soft en LIGHT
//  (2) rol state-neutral (deuda / offline / "no procede")
//  (3) MoneyRole tipado + selector de color (matriz de dinero)
//  (4) TextArea de descripción/notas + tipificación de teclado
// Coherente con RecreSemanticColors / RecreColors.current ya definidos.
// =====================================================================

// ---------- (1) Hex de las variantes -text (contraste verificado WCAG) ----------
// LIGHT: el FILL del rol se reserva a icono/relleno/cifra-grande; el TEXTO
// pequeño y la ETIQUETA de soft-chip usan la variante -text (oscurecida).
val RecreSuccessTextLight = Color(0xFF076138) // 7.56:1 sobre #FFFFFF
val RecreDangerTextLight  = Color(0xFFA81818) // 7.48:1
val RecreWarningTextLight = Color(0xFF8A3D0A) // 7.63:1
val RecreInfoTextLight    = Color(0xFF1D4ED8) // 6.70:1 (AA fuerte)
// DARK: el fill ya rinde ~7:1 como texto sobre surface-1/2 -> -text == fill.
val RecreSuccessTextDark  = RecreSuccessDark   // #34D399  (9.51 s1)
val RecreDangerTextDark   = RecreDangerDark    // #F87171  (6.61 s1)
val RecreWarningTextDark  = RecreWarningDark    // #FBBF24  (10.95 s1)
val RecreInfoTextDark     = RecreInfoDark       // #60A5FA  (7.19 s1)

// ---------- (2) state-neutral: superficie NEUTRA, NO 'secondary' (marca) ----------
// Usado por: deuda/saldo EUR (deber NO es error), offline, permiso RBAC,
// "no procede", estado impresor, baja confianza OCR. Texto foreground/muted + icono.
val RecreStateNeutralBgLight = RecreSurface2Light          // #F4F6F8
val RecreStateNeutralFgLight = RecreMutedLight             // #646B76 (5.38)
val RecreStateNeutralBgDark  = RecreSurface2Dark           // #1B1E24
val RecreStateNeutralFgDark  = RecreMutedDark              // #9AA1AD (7.03)

// Extiende RecreSemanticColors SIN romper el data class existente:
// añade estos campos al data class de fase3-design-tokens.md.
data class RecreMoneyColors(
    val successText: Color,   // cifra/etiqueta dinero+ como TEXTO
    val dangerText: Color,    // descuadre/error como TEXTO
    val warningText: Color,
    val infoText: Color,
    val neutralBg: Color,     // soft-chip de deuda/estado neutro
    val neutralFg: Color,     // texto deuda/saldo (foreground neutro)
)

val LightMoneyColors = RecreMoneyColors(
    successText = RecreSuccessTextLight, dangerText = RecreDangerTextLight,
    warningText = RecreWarningTextLight, infoText = RecreInfoTextLight,
    neutralBg = RecreStateNeutralBgLight, neutralFg = RecreStateNeutralFgLight,
)
val DarkMoneyColors = RecreMoneyColors(
    successText = RecreSuccessTextDark, dangerText = RecreDangerTextDark,
    warningText = RecreWarningTextDark, infoText = RecreInfoTextDark,
    neutralBg = RecreStateNeutralBgDark, neutralFg = RecreStateNeutralFgDark,
)
val LocalRecreMoneyColors = staticCompositionLocalOf { LightMoneyColors }

// ---------- (3) Matriz de dinero tipada (consumida por MoneyText, ya especificado) ----------
/**
 * Rol semántico de un importe. NO se decide por el signo aritmético sino por
 * el SIGNIFICADO de negocio. Regla no negociable:
 *  - Positive/Balanced  -> success (deber/haber a favor, cuadra)
 *  - Debt               -> NEUTRO foreground + icono EUR (deuda NO es error)
 *  - Mismatch           -> danger SOLO descuadre/error de caja
 *  - Deduction          -> NEUTRO con prefijo '−' (reposición tolva, tasa, retención)
 *  - Plain              -> foreground (importe informativo sin connotación)
 */
enum class MoneyRole { Positive, Balanced, Debt, Mismatch, Deduction, Plain }

/** El '−' de Deduction/negativos hereda este color; NUNCA aria-hidden. */
@Composable
fun moneyColorFor(role: MoneyRole, large: Boolean): Color {
    val m = LocalRecreMoneyColors.current
    val sem = RecreColors.current
    return when (role) {
        // cifra grande/KPI/icono -> FILL; texto pequeño -> -text
        MoneyRole.Positive, MoneyRole.Balanced ->
            if (large) sem.success else m.successText
        MoneyRole.Mismatch ->
            if (large) sem.danger else m.dangerText
        // deuda y reposición -> NEUTRO (nunca danger por "deber")
        MoneyRole.Debt, MoneyRole.Deduction -> m.neutralFg
        MoneyRole.Plain -> MaterialTheme.colorScheme.onSurface
    }
}

// ---------- (4a) TextArea de descripción / notas (ReportarAveria C.1) ----------
// Fuera de FieldNum: multilínea, contador de caracteres, teclado TEXTO del sistema.
@Composable
fun RecreTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int = 500,
    minLines: Int = 3,
    maxLines: Int = 8,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,        // teclado TEXTO del sistema
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,            // newline permitido
            ),
            shape = RoundedCornerShape(12.dp),           // Android radio 12
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,           // 13/500
                    color = if (isError) RecreColors.current.danger
                            else RecreColors.current.muted,
                )
            }
            Text(
                "${value.length}/$maxLength",
                style = RecreType.cifraCaption,                            // mono tabular 13
                color = if (value.length >= maxLength) RecreColors.current.danger
                        else RecreColors.current.muted,
                modifier = Modifier.semantics {
                    contentDescription = "${value.length} de $maxLength caracteres"
                },
            )
        }
    }
}

// ---------- (4b) Tipificación de teclado por tipo de campo (regla #7) ----------
/** Mapa único campo->teclado para todos los inputs de TEXTO fuera de FieldNum. */
object RecreFieldKeyboard {
    // Denominaciones: ÚNICO teclado in-app (keypad propio), NO del sistema.
    // FieldNum numérico genérico (contadores, cantidades enteras):
    val numero  = KeyboardOptions(keyboardType = KeyboardType.Number,  imeAction = ImeAction.Next)
    // Importes editables a mano fuera de keypad (tasa, % manual): coma decimal del sistema.
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val email   = KeyboardOptions(keyboardType = KeyboardType.Email,   imeAction = ImeAction.Next,
                                  autoCorrectEnabled = false)
    val telefono= KeyboardOptions(keyboardType = KeyboardType.Phone,   imeAction = ImeAction.Next)
    val texto   = KeyboardOptions(keyboardType = KeyboardType.Text,    imeAction = ImeAction.Next,
                                  capitalization = KeyboardCapitalization.Sentences)
    // Búsqueda: SearchField ya especificado (lupa+clear); teclado texto + acción buscar.
    val busqueda= KeyboardOptions(keyboardType = KeyboardType.Text,    imeAction = ImeAction.Search)
    // Fechas: NO se teclean -> DatePicker M3 (no KeyboardOptions).
}

```

**Web**
```ts
/* =====================================================================
 * ADDENDUM Fase 3 (ronda 2) — globals.css / tailwind.config.ts
 * NO redefine la paleta; AÑADE variantes -text de DINERO, rol state-neutral
 * y tokens de soft-chip. Coherente con :root/.dark de fase3-design-tokens.md.
 * ===================================================================== */
@layer base {
  :root {
    /* (1) variantes -text: TEXTO pequeño de dinero y ETIQUETA de soft-chip (LIGHT).
       El --success/--danger/... 'fill' se reserva a icono/relleno/cifra grande. */
    --success-text: #076138;   /* 7.56:1 sobre #FFF */
    --danger-text:  #A81818;   /* 7.48:1 */
    --warning-text: #8A3D0A;   /* 7.63:1 */
    --info-text:    #1D4ED8;   /* 6.70:1 (AA fuerte) */

    /* soft-chip = fill@12% (light); el TEXTO usa la variante -text. AA verificado. */
    --success-chip-bg: #E2F1EB; --success-chip-fg: #076138; /* 6.49 */
    --danger-chip-bg:  #FBE5E5; --danger-chip-fg:  #A81818; /* 6.21 */
    --warning-chip-bg: #F6EAE1; --warning-chip-fg: #8A3D0A; /* 6.46 */
    --info-chip-bg:    #E5ECFD; --info-chip-fg:    #1D4ED8; /* 5.66 */

    /* (2) rol state-neutral: superficie NEUTRA (NO 'secondary' = marca).
       Deuda/saldo EUR, offline, permiso RBAC, 'no procede', impresor, OCR-low. */
    --state-neutral-bg: var(--surface-2);   /* #F4F6F8 */
    --state-neutral-fg: var(--muted-foreground); /* #646B76 (5.38) */

    /* (3) tokens semánticos de DINERO (consumidos por MoneyText) */
    --money-positive: var(--success-text); /* texto */
    --money-positive-fill: var(--success); /* cifra grande/KPI/icono */
    --money-debt:     var(--foreground);   /* deuda NEUTRA, deber NO es error */
    --money-mismatch: var(--danger-text);  /* descuadre/error texto */
    --money-mismatch-fill: var(--danger);
    --money-deduction: var(--muted-foreground); /* '−' reposición/tasa, neutro */
  }
  .dark {
    /* DARK: el fill ya rinde ~7:1 como texto sobre surface-1/2 -> -text == fill */
    --success-text: var(--success);  /* #34D399  9.51 s1 */
    --danger-text:  var(--danger);   /* #F87171  6.61 s1 */
    --warning-text: var(--warning);  /* #FBBF24 10.95 s1 */
    --info-text:    var(--info);     /* #60A5FA  7.19 s1 */

    /* soft-chip = fill@16% over surface-1 (opaco precomputado) */
    --success-chip-bg: #18332D; --success-chip-fg: #34D399; /* 7.05 */
    --danger-chip-bg:  #382427; --danger-chip-fg:  #F87171; /* 5.24 AA */
    --warning-chip-bg: #38301B; --warning-chip-fg: #FBBF24; /* 7.83 */
    --info-chip-bg:    #1F2C3D; --info-chip-fg:    #60A5FA; /* 5.56 AA */

    --state-neutral-bg: var(--surface-2);   /* #1B1E24 */
    --state-neutral-fg: var(--muted-foreground); /* #9AA1AD (7.03) */

    --money-positive: var(--success-text);
    --money-positive-fill: var(--success);
    --money-debt:     var(--foreground);
    --money-mismatch: var(--danger-text);
    --money-mismatch-fill: var(--danger);
    --money-deduction: var(--muted-foreground);
  }
}

/* tailwind.config.ts — theme.extend.colors (AÑADIR a lo ya existente):
colors: {
  success: { DEFAULT: "var(--success)", foreground: "var(--success-foreground)",
             subtle: "var(--success-subtle)", text: "var(--success-text)",
             "chip-bg": "var(--success-chip-bg)", "chip-fg": "var(--success-chip-fg)" },
  warning: { DEFAULT: "var(--warning)", foreground: "var(--warning-foreground)",
             subtle: "var(--warning-subtle)", text: "var(--warning-text)",
             "chip-bg": "var(--warning-chip-bg)", "chip-fg": "var(--warning-chip-fg)" },
  danger:  { DEFAULT: "var(--danger)",  foreground: "var(--danger-foreground)",
             subtle: "var(--danger-subtle)",  text: "var(--danger-text)",
             "chip-bg": "var(--danger-chip-bg)",  "chip-fg": "var(--danger-chip-fg)" },
  info:    { DEFAULT: "var(--info)",    foreground: "var(--info-foreground)",
             subtle: "var(--info-subtle)",    text: "var(--info-text)",
             "chip-bg": "var(--info-chip-bg)",    "chip-fg": "var(--info-chip-fg)" },
  "state-neutral": { bg: "var(--state-neutral-bg)", fg: "var(--state-neutral-fg)" },
  money: { positive: "var(--money-positive)", "positive-fill": "var(--money-positive-fill)",
           debt: "var(--money-debt)", mismatch: "var(--money-mismatch)",
           "mismatch-fill": "var(--money-mismatch-fill)", deduction: "var(--money-deduction)" },
}
*/

/* ---------- (4) TextArea de descripción/notas (ReportarAveria C.1) ----------
   Fuera de FieldNum. shadcn <Textarea> + contador. Teclado TEXTO del sistema.
   El campo NO declara inputMode (texto por defecto); spellCheck on. */
/*
<div className="space-y-1.5">
  <Label htmlFor="descripcion">{t('averia.descripcion')}</Label>
  <Textarea
    id="descripcion"
    value={value}
    onChange={(e) => e.target.value.length <= maxLength && onChange(e.target.value)}
    rows={3}
    maxLength={maxLength}
    spellCheck
    aria-describedby="descripcion-counter"
    className="min-h-[88px] resize-y rounded-md text-[14px] aria-[invalid=true]:border-danger"
  />
  <div className="flex items-center justify-between">
    {error && <p className="text-[12px] text-danger-text">{error}</p>}
    <p id="descripcion-counter"
       className={cn('ml-auto font-mono tabular-nums text-[12px]',
         value.length >= maxLength ? 'text-danger-text' : 'text-muted-foreground')}>
      {value.length}/{maxLength}
    </p>
  </div>
</div>
*/

```

> ADDENDUM correctivo: añade tokens, NO redefine fase3-design-tokens.md. Encaje verificado con specs existentes:

1) Corrige el blocker de MoneyText (success #0E8A55 como TEXTO falla AA en light, 4.39:1). Solución: variantes `-text` oscurecidas para TEXTO/etiqueta; el `fill` queda para cifra grande/KPI/icono. Ratios calculados con WCAG (no a ojo): LIGHT success-text 7.56, danger-text 7.48, warning-text 7.63, info-text 6.70; soft-chips light (texto -text sobre fill@12%) success 6.49 / danger 6.21 / warning 6.46 / info 5.66 (todos AA). DARK: el fill rinde ≥6.6:1 sobre surface-1 -> `-text == fill`.

2) Matriz de dinero cierra el hueco success-vs-neutro-vs-nunca-danger: deuda/saldo = NEUTRO foreground + icono EUR (deber NO es error, regla no negociable); reposición de tolva / tasa / retención = NEUTRO con prefijo `−` heredado del dígito y NUNCA aria-hidden (coherente con MoneyText §grupo de signo, línea 532 de component-specs); descuadre/error = danger. El rol se decide por SIGNIFICADO de negocio (enum MoneyRole), no por el signo aritmético.

3) Rol `state-neutral` materializado (superficie surface-2 + muted/foreground), explícitamente NO `secondary` (que es color de marca tonal). Cubre deuda EUR, offline, permiso RBAC, "no procede", estado impresor, baja confianza OCR.

4) TextArea es NUEVO (no existía en component-specs): multilínea, contador de caracteres mono tabular, teclado TEXTO del sistema; para ReportarAveria C.1. SearchField y DatePicker se REFERENCIAN (ya especificados). Tabla campo->teclado->componente complementa la regla #7: keypad in-app SOLO denominaciones; numérico/decimal/email/tel/text via teclado del sistema; fechas siempre DatePicker (nunca input libre).

Pendiente de F0/T-227 (paleta real en globals.css / Color.kt) para que las clases text-success-text, text-money-debt, var(--*-chip-bg/-fg) y LocalRecreMoneyColors resuelvan; hoy son placeholders shadcn/Slate. Doc fuente: /home/a/Escritorio/recre-main/.kiro/specs/recre/fase3-design-tokens.md (líneas 340-495 :root/.dark, 619-660 RecreType) y fase3-component-specs.md (MoneyText, FieldNum, SearchField, keypad).



---

# Reconciliaciones de token (post-verificación · ronda 2)

> La verificación adversarial detectó nombres de token usados en el código de referencia que no existían aún aquí o que divergían entre Android y web. Se fijan como SSOT. Ratios recalculados con WCAG (verificados, no estimados).

## R1 · Variantes `-text` como utilidades reales (no "inventadas")
Varios snippets usaban `text-success-text` / `var(--danger-text)` sin definición en la capa CSS/Tailwind. Se materializan como utilidades de primera clase:

```css
/* globals.css */
:root{
  --success-text:#076138; /* 7.56:1 s/blanco · 6.21:1 s/success@12% */
  --warning-text:#8A3D0A; /* 7.63:1 */
  --danger-text:#A81818;  /* 7.48:1 · 6.21:1 s/danger@12% */
  --info-text:#1D4ED8;    /* 6.70:1 (cerca del 7:1, AA holgado) */
}
.dark{ --success-text:#34D399; --warning-text:#FBBF24; --danger-text:#F87171; --info-text:#60A5FA; }
```
```ts
// tailwind.config.ts → theme.extend.colors
'success-text':'var(--success-text)','warning-text':'var(--warning-text)',
'danger-text':'var(--danger-text)','info-text':'var(--info-text)',
```
Regla: el **texto** de rol en LIGHT usa SIEMPRE la variante `-text`; el `fill` del rol queda para iconos, rellenos y cifras grandes.

## R2 · `muted` es superficie; `muted-foreground` es texto
Convención shadcn heredada: `--muted` (#F4F6F8) es un FONDO (=surface-2); el texto secundario es `--muted-foreground` (#646B76). Snippets que escribían `text-muted` renderizan texto casi blanco (ilegible). **Corrección:** todo texto secundario web usa `text-muted-foreground`. (Android: `RecreColors.muted` = #646B76 SÍ es color de texto; la ambigüedad es solo de la capa web.)

## R3 · `on-danger`/`on-warning` sobre FILL en DARK = texto OSCURO
Blanco sobre `danger-dark` #F87171 = **2.77:1 (falla)**. Valores canónicos:
- `on-danger` (fill, dark) = **#3A0A0A** → 6.18:1
- `on-warning` (fill, dark) = **#3A2503** → 8.70:1

Sustituye menciones divergentes (#06212A, #45100F, "7.0:1"). En LIGHT, `on-danger`/`on-warning` sobre fill siguen siendo #FFFFFF.

## R4 · `secondaryContainer` ≔ `secondary` (un solo valor, ambas plataformas)
M3 auto-deriva `secondaryContainer` = #D5EAEE/#1E4350, distinto del `secondary` aprobado (#E6F2F4/#16323A). Android usaba `cs.secondaryContainer` y web `bg-secondary` → thumbs/botones tonales de color distinto. **Corrección:** el contenedor tonal (thumb de SegmentedControl, botón tonal) usa el token `secondary` (#E6F2F4/#16323A) en AMBAS plataformas; Android pasa color explícito `RecreColors.secondary`, no `cs.secondaryContainer`. Etiqueta sobre secondary = `secondary-foreground` #062A33 (**13.25:1**).

## R5 · `border` (filete) vs `outline` (límite reforzado ≥3:1)
`border` = outlineVariant (#E3E6EA) = filete 1px de bajo contraste (1.16:1 sobre surface-2 → invisible como "divisor reforzado"). Para separadores/bordes que deben **verse** (top de TotalsRow, borde de thumb activo, halo de badge neutro) usar `outline` (= `muted` #646B76): ≥4.96:1. Regla: filete decorativo→`border`; límite perceptible (WCAG 1.4.11)→`outline`.



---

## Anexo — Re-tinte «Neón de sala» (2026-07)

> Iniciativa `feat/android-neon-n0`. Se re-tinta **solo el tema OSCURO** hacia un petróleo profundo (azul-verdoso) que sirve de lienzo al acento cian, y se añade el token `accentBright` (glow / estados activos). Ningún valor `*Light` existente cambia; el único añadido en light es `RecreAccentBrightLight`. Roles `primary`/`onPrimary`/`secondary` dark y todos los roles success/warning/danger/info (fills, containers, texts, chips) **no cambian**. HEX literales; ratios recalculados con WCAG (no a ojo).

### Valores dark re-tintados (`Color.kt`)

| Token | HEX viejo | HEX nuevo |
| --- | --- | --- |
| `RecreBackgroundDark` | `#0B0C0E` | `#0A1014` |
| `RecreSurface1Dark` | `#131519` | `#111A21` |
| `RecreSurface2Dark` | `#1B1E24` | `#182530` |
| `RecreBorderDark` | `#262A31` | `#22323D` |
| `RecreMutedDark` | `#9AA1AD` | `#8FA6B0` |
| `RecreMutedStrongDark` | `#B6BCC6` | `#B8CBD4` |
| `RecreOnSurfaceDark` | `#E7EAEE` | `#EAF3F6` |
| `RecreStateNeutralBgDark` | `#1B1E24` | `#182530` |
| `RecreStateNeutralBorderDark` | `#262A31` | `#22323D` |
| `RecreStateNeutralFgDark` | `#E7EAEE` | `#EAF3F6` |
| `RecreStateNeutralMutedDark` | `#9AA1AD` | `#8FA6B0` |
| `RecreNeutralChipBgDark` | `#2B2E34` | `#22313C` |
| `RecreNeutralChipFgDark` | `#B6BBC4` | `#B3C4CD` |

### Containers altos dark (`Theme.kt`)

| Slot M3 (dark) | HEX viejo | HEX nuevo |
| --- | --- | --- |
| `surfaceContainerHigh` | `#22262D` | `#1E2E3A` |
| `surfaceContainerHighest` | `#2A2F37` | `#243542` |

### Token nuevo `accentBright`

| Token | HEX | Uso |
| --- | --- | --- |
| `RecreAccentBrightDark` | `#67E3F4` | cian vivo: glow, icono activo del dock, odómetro |
| `RecreAccentBrightLight` | `#0E7490` | en light no hay neón: == `primary` |

Acceso: `RecreColors.current.accentBright`. **Regla:** `accentBright` se reserva a *glow* y estados activos (dock, foco/pulso, cifra viva); en light no hay neón (colapsa a `primary`). No usarlo como color de texto de cuerpo ni como fill de rol semántico.

### Contraste verificado (WCAG, script del plan)

```
onSurface/surface1     15.63  (>= 7   OK)
muted/surface1          6.91  (>= 4.5 OK)
mutedStrong/surface2    9.31  (>= 7   OK)
neutralChipFg/Bg        7.44  (>= 7   OK)
accentBright/surface1  11.62  (>= 4.5 OK)
```

Todos los pares superan su umbral; no hizo falta aclarar ningún fg.
