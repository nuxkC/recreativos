package com.recre.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =====================================================================
// Design System "Confianza Industrial" — Grupo Color (Fase 3 · T-227).
// SSOT: .kiro/specs/recre/fase3-design-tokens.md. Los HEX [PALETA] son la
// fuente de verdad (no derivar). primary = petróleo (acento ≤10%); ring =
// primary; success/warning/danger/info reservados a su significado.
// M3 no tiene slots para success/warning/info/border/muted/surface-2/ring:
// se inyectan vía CompositionLocal (RecreColors.current.success).
// =====================================================================

// ---------------------------------------------------------------------
// 1) PALETA CANÓNICA (no tocar: fuente de verdad)
// ---------------------------------------------------------------------

// --- Light
val RecrePrimaryLight = Color(0xFF0E7490) // [PALETA] primary (petróleo)
val RecreOnPrimaryLight = Color(0xFFFFFFFF) // [PALETA] on-primary
val RecreSecondaryLight = Color(0xFFE6F2F4) // [PALETA] secondary (tint petróleo)
val RecreBackgroundLight = Color(0xFFFAFBFC) // [PALETA] background
val RecreSurface1Light = Color(0xFFFFFFFF) // [PALETA] surface-1 (cards, dialogs)
val RecreSurface2Light = Color(0xFFF4F6F8) // [PALETA] surface-2 (fondos sutiles)
val RecreSuccessLight = Color(0xFF0E8A55) // [PALETA] success (SOLO dinero+/cuadra)
val RecreWarningLight = Color(0xFFB45309) // [PALETA] warning (SOLO pendiente/offline-stale)
val RecreDangerLight = Color(0xFFDC2626) // [PALETA] danger (SOLO error/avería/descuadre)
val RecreInfoLight = Color(0xFF2563EB) // [PALETA] info
val RecreBorderLight = Color(0xFFE3E6EA) // [PALETA] border / outlineVariant
val RecreMutedLight = Color(0xFF646B76) // [PALETA] muted (texto secundario)
val RecreRingLight = Color(0xFF0E7490) // [PALETA] ring = primary
val RecreMutedStrongLight = Color(0xFF3F4651) // muted-strong: muted oscurecido ≥7:1 sobre surface-2 (€/%/descripción/chevron)

// --- Dark
val RecrePrimaryDark = Color(0xFF2BC4DD) // [PALETA] primary (cian)
val RecreOnPrimaryDark = Color(0xFF06212A) // [PALETA] on-primary (texto oscuro sobre cian)
val RecreSecondaryDark = Color(0xFF16323A) // [PALETA] secondary
val RecreBackgroundDark = Color(0xFF0A1014) // [PALETA] background (petróleo profundo, neón N0)
val RecreSurface1Dark = Color(0xFF111A21) // [PALETA] surface-1
val RecreSurface2Dark = Color(0xFF182530) // [PALETA] surface-2
val RecreSuccessDark = Color(0xFF34D399) // [PALETA] success
val RecreWarningDark = Color(0xFFFBBF24) // [PALETA] warning
val RecreDangerDark = Color(0xFFF87171) // [PALETA] danger
val RecreInfoDark = Color(0xFF60A5FA) // [PALETA] info
val RecreBorderDark = Color(0xFF22323D) // [PALETA] border / outlineVariant
val RecreMutedDark = Color(0xFF8FA6B0) // [PALETA] muted
val RecreRingDark = Color(0xFF2BC4DD) // [PALETA] ring = primary
val RecreAccentBrightDark = Color(0xFF67E3F4) // acento vivo: glow, icono activo del dock, odómetro
val RecreAccentBrightLight = Color(0xFF0E7490) // en light no hay neón: primary
val RecreMutedStrongDark = Color(0xFFB8CBD4) // muted-strong ≥7:1 sobre surface-2 (verificado abajo)

// Foreground neutro (texto principal sobre surface)
val RecreOnSurfaceLight = Color(0xFF11161B) // casi-negro frío, AA sobre surface-1
val RecreOnSurfaceDark = Color(0xFFEAF3F6) // casi-blanco frío (tinte petróleo)
val RecreScrim = Color(0xFF000000)

// ---------------------------------------------------------------------
// 2) CONTAINERS DERIVADOS (rellenan slots M3; tinte/sombra del canónico)
// ---------------------------------------------------------------------

// --- Light containers
val RecrePrimaryContainerLight = Color(0xFFCDE9F0)
val RecreOnPrimaryContainerLight = Color(0xFF002B38)
val RecreSecondaryContainerLight = Color(0xFFD5EAEE)
val RecreOnSecondaryContainerLight = Color(0xFF062A33)
val RecreSuccessContainerLight = Color(0xFFD3F2E2)
val RecreOnSuccessContainerLight = Color(0xFF053B23)
val RecreWarningContainerLight = Color(0xFFFCEBD2)
val RecreOnWarningContainerLight = Color(0xFF4A2103)
val RecreDangerContainerLight = Color(0xFFFADCDC)
val RecreOnDangerContainerLight = Color(0xFF5C0F0F)
val RecreInfoContainerLight = Color(0xFFDBE7FE)
val RecreOnInfoContainerLight = Color(0xFF0B2A66)

// --- Dark containers
val RecrePrimaryContainerDark = Color(0xFF0B4A58)
val RecreOnPrimaryContainerDark = Color(0xFFBDEAF4)
val RecreSecondaryContainerDark = Color(0xFF1E4350)
val RecreOnSecondaryContainerDark = Color(0xFFC7E6EE)
val RecreSuccessContainerDark = Color(0xFF0C4D34)
val RecreOnSuccessContainerDark = Color(0xFFA9EFCE)
val RecreWarningContainerDark = Color(0xFF5A3A06)
val RecreOnWarningContainerDark = Color(0xFFFCE3B0)
val RecreDangerContainerDark = Color(0xFF5C2120)
val RecreOnDangerContainerDark = Color(0xFFFBD0D0)
val RecreInfoContainerDark = Color(0xFF1C3A6B)
val RecreOnInfoContainerDark = Color(0xFFCADEFE)

// ---------------------------------------------------------------------
// 3) RECONCILIACIONES (ronda 2): variantes -text, on-fill oscuros (dark)
//    y rol state-neutral. Ratios WCAG verificados en el spec.
// ---------------------------------------------------------------------

// LIGHT: -text (TEXTO pequeño / etiqueta soft-chip). El fill se reserva a
// iconos, rellenos y cifras grandes; el texto pequeño usa -text.
val RecreSuccessTextLight = Color(0xFF076138) // 7.56:1 sobre #FFFFFF
val RecreDangerTextLight = Color(0xFFA81818) // 7.48:1
val RecreWarningTextLight = Color(0xFF8A3D0A) // 7.63:1
val RecreInfoTextLight = Color(0xFF1D4ED8) // ~5.9:1 (AA fuerte)

// DARK: en oscuro el fill ya cumple ~7:1 como texto, así que -text == fill.
val RecreSuccessTextDark = Color(0xFF34D399)
val RecreDangerTextDark = Color(0xFFF87171)
val RecreWarningTextDark = Color(0xFFFBBF24)
val RecreInfoTextDark = Color(0xFF60A5FA)

// on-fill OSCUROS para rellenos en DARK (blanco sobre danger/warning dark falla).
val RecreOnDangerFillDark = Color(0xFF3A0A0A)
val RecreOnWarningFillDark = Color(0xFF3A2503)

// Rol state-neutral: superficie de estado NEUTRA (offline / RBAC / deuda EUR /
// 'no procede' / baja confianza OCR). NUNCA rojo. Distinto de muted (solo
// texto) y de secondary (color de marca).
val RecreStateNeutralBgLight = Color(0xFFF4F6F8) // == surface-2
val RecreStateNeutralBorderLight = Color(0xFFE3E6EA) // == border
val RecreStateNeutralFgLight = Color(0xFF11161B) // foreground (etiqueta)
val RecreStateNeutralMutedLight = Color(0xFF646B76) // muted (metadato) · 5.38:1
val RecreStateNeutralBgDark = Color(0xFF182530) // == surface-2
val RecreStateNeutralBorderDark = Color(0xFF22323D) // == border
val RecreStateNeutralFgDark = Color(0xFFEAF3F6)
val RecreStateNeutralMutedDark = Color(0xFF8FA6B0)

// ---------------------------------------------------------------------
// 3b) PARES OPACOS DE SOFT-CHIP (StatusChip · C-StatusChip). Precomputados
//     POR ROL+MODO: fondo (-ChipBg) + contenido dot/icono/texto (-ChipFg).
//     OPACOS, NO alpha sobre transparent: el contraste no debe depender de
//     la fila/hover. fg distinto del bg, ratio AA light ~4.5:1 / dark ~7:1.
//     fg NO es el fill pleno del rol (el pleno falla AA como texto pequeño).
//     SSOT: fase3-component-specs.md (StatusChip · Tokens por elemento).
// ---------------------------------------------------------------------
// --- Light
val RecreSuccessChipBgLight = Color(0xFFDDEFE7) // success ~14% sobre #FFFFFF
val RecreSuccessChipFgLight = Color(0xFF0C784A) // 4.62:1 (el #0E8A55 pleno falla)
val RecreWarningChipBgLight = Color(0xFFF4E7DD)
val RecreWarningChipFgLight = Color(0xFFA74D08) // 4.67:1 (el #B45309 pleno falla)
val RecreDangerChipBgLight = Color(0xFFFAE1E1)
val RecreDangerChipFgLight = Color(0xFFC62222) // 4.63:1 (el #DC2626 pleno falla)
val RecreInfoChipBgLight = Color(0xFFE0E9FC)
val RecreInfoChipFgLight = Color(0xFF235EDF)
val RecreNeutralChipBgLight = Color(0xFFE9EAEC) // OFFLINE va aquí (muted), no danger
val RecreNeutralChipFgLight = Color(0xFF626974)

// --- Dark
val RecreSuccessChipBgDark = Color(0xFF193730) // success ~18% sobre surface-1
val RecreSuccessChipFgDark = Color(0xFF44D7A1) // 7.04:1
val RecreWarningChipBgDark = Color(0xFF3D341B)
val RecreWarningChipFgDark = Color(0xFFFBBF24) // 7.38:1
val RecreDangerChipBgDark = Color(0xFF3C2629)
val RecreDangerChipFgDark = Color(0xFFFAA0A0) // 7.06:1
val RecreInfoChipBgDark = Color(0xFF212F42)
val RecreInfoChipFgDark = Color(0xFF8DBEFB)
val RecreNeutralChipBgDark = Color(0xFF22313C)
val RecreNeutralChipFgDark = Color(0xFFB3C4CD)

// ---------------------------------------------------------------------
// 4) TOKENS SEMÁNTICOS DE DOMINIO (inyectados por CompositionLocal).
//    Lectura: RecreColors.current.success
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
    val danger: Color, // alias semántico de error
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val surface2: Color, // surface-2 (M3 base solo da surface = surface-1)
    val border: Color, // = outlineVariant (separadores 1px)
    val muted: Color, // texto secundario
    val ring: Color, // foco = primary
    val mutedStrong: Color, // muted ≥7:1 sobre surface-2 (€/%/iconos informativos; el dígito va en foreground)
    val accentBright: Color, // cian vivo para glow/estados activos (== primary en light)
    // -- ronda 2: variantes -text (texto pequeño / etiqueta soft-chip) --
    val successText: Color,
    val dangerText: Color,
    val warningText: Color,
    val infoText: Color,
    // -- ronda 2: rol state-neutral (superficie de estado neutra) --
    val stateNeutralBg: Color,
    val stateNeutralBorder: Color,
    val stateNeutralFg: Color,
    val stateNeutralMuted: Color,
    // -- soft-chip: pares OPACOS bg/fg por rol (StatusChip · C-StatusChip) --
    val successChipBg: Color,
    val successChipFg: Color,
    val warningChipBg: Color,
    val warningChipFg: Color,
    val dangerChipBg: Color,
    val dangerChipFg: Color,
    val infoChipBg: Color,
    val infoChipFg: Color,
    val neutralChipBg: Color,
    val neutralChipFg: Color,
    val isLight: Boolean,
)

private val LightSemanticColors =
    RecreSemanticColors(
        success = RecreSuccessLight,
        onSuccess = Color(0xFFFFFFFF),
        successContainer = RecreSuccessContainerLight,
        onSuccessContainer = RecreOnSuccessContainerLight,
        warning = RecreWarningLight,
        onWarning = Color(0xFFFFFFFF), // LIGHT: blanco sobre fill warning OK
        warningContainer = RecreWarningContainerLight,
        onWarningContainer = RecreOnWarningContainerLight,
        danger = RecreDangerLight,
        onDanger = Color(0xFFFFFFFF), // LIGHT: blanco sobre fill danger OK
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
        mutedStrong = RecreMutedStrongLight,
        accentBright = RecreAccentBrightLight,
        successText = RecreSuccessTextLight,
        dangerText = RecreDangerTextLight,
        warningText = RecreWarningTextLight,
        infoText = RecreInfoTextLight,
        stateNeutralBg = RecreStateNeutralBgLight,
        stateNeutralBorder = RecreStateNeutralBorderLight,
        stateNeutralFg = RecreStateNeutralFgLight,
        stateNeutralMuted = RecreStateNeutralMutedLight,
        successChipBg = RecreSuccessChipBgLight,
        successChipFg = RecreSuccessChipFgLight,
        warningChipBg = RecreWarningChipBgLight,
        warningChipFg = RecreWarningChipFgLight,
        dangerChipBg = RecreDangerChipBgLight,
        dangerChipFg = RecreDangerChipFgLight,
        infoChipBg = RecreInfoChipBgLight,
        infoChipFg = RecreInfoChipFgLight,
        neutralChipBg = RecreNeutralChipBgLight,
        neutralChipFg = RecreNeutralChipFgLight,
        isLight = true,
    )

private val DarkSemanticColors =
    RecreSemanticColors(
        success = RecreSuccessDark,
        onSuccess = Color(0xFF053B23),
        successContainer = RecreSuccessContainerDark,
        onSuccessContainer = RecreOnSuccessContainerDark,
        warning = RecreWarningDark,
        onWarning = RecreOnWarningFillDark, // DARK: oscuro sobre fill warning
        warningContainer = RecreWarningContainerDark,
        onWarningContainer = RecreOnWarningContainerDark,
        danger = RecreDangerDark,
        onDanger = RecreOnDangerFillDark, // DARK: oscuro sobre fill danger
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
        mutedStrong = RecreMutedStrongDark,
        accentBright = RecreAccentBrightDark,
        successText = RecreSuccessTextDark,
        dangerText = RecreDangerTextDark,
        warningText = RecreWarningTextDark,
        infoText = RecreInfoTextDark,
        stateNeutralBg = RecreStateNeutralBgDark,
        stateNeutralBorder = RecreStateNeutralBorderDark,
        stateNeutralFg = RecreStateNeutralFgDark,
        stateNeutralMuted = RecreStateNeutralMutedDark,
        successChipBg = RecreSuccessChipBgDark,
        successChipFg = RecreSuccessChipFgDark,
        warningChipBg = RecreWarningChipBgDark,
        warningChipFg = RecreWarningChipFgDark,
        dangerChipBg = RecreDangerChipBgDark,
        dangerChipFg = RecreDangerChipFgDark,
        infoChipBg = RecreInfoChipBgDark,
        infoChipFg = RecreInfoChipFgDark,
        neutralChipBg = RecreNeutralChipBgDark,
        neutralChipFg = RecreNeutralChipFgDark,
        isLight = false,
    )

val LocalRecreColors = staticCompositionLocalOf { LightSemanticColors }

/** Acceso ergonómico a los tokens de dominio: `RecreColors.current.success`. */
object RecreColors {
    val current: RecreSemanticColors
        @Composable @ReadOnlyComposable
        get() = LocalRecreColors.current
}

internal fun recreSemanticColors(dark: Boolean): RecreSemanticColors =
    if (dark) DarkSemanticColors else LightSemanticColors
