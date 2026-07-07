# Re-skin «Neón de sala» de la app Android — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Llevar la dirección visual «Neón de sala» (mockup aprobado: `docs/superpowers/specs/2026-07-07-neon-de-sala-mockup.html`, artifact <https://claude.ai/code/artifact/7ae41dfd-c79b-4ef6-9787-c6f1e31cdaaa>) a la app real de técnicos, por fases (una fase = un PR), sin reescribir pantallas.

**Architecture:** El design system «Confianza Industrial» (rediseño F0-F5, PRs #87-#95) ya centraliza color (`ui/theme/Color.kt` + `RecreColors` CompositionLocal), tipografía (`Type.kt`), forma, spacing y ~30 componentes en `ui/components/`. «Neón de sala» es una **evolución del tema OSCURO** (el primary dark ya es el cian #2BC4DD del mockup): (N0) re-tintar la paleta dark a superficies petróleo + añadir familia display Bricolage Grotesque y el token `accentBright`; (N1) añadir los componentes firma (odómetro animado, glow, dock píldora, keypad); (N2-N4) adoptar los componentes en las pantallas por tramos; (N5) guardarraíl y limpieza. Las pantallas heredan el 80% del cambio vía tokens; solo se tocan los bloques héroe/firma.

**Tech Stack:** Kotlin · Jetpack Compose (Material3, fuentes variables con `FontVariation`) · JUnit4 (unit JVM) · fuentes OFL (Bricolage Grotesque, Geist ya embebida).

## Global Constraints

- **SSOT de tokens:** los HEX canónicos viven en `.kiro/specs/recre/fase3-design-tokens.md`; toda modificación de `Color.kt` DEBE reflejarse allí en el mismo commit (anexo «Neón de sala»).
- **Solo el tema OSCURO se re-tinta.** El light scheme no cambia de valores en esta iniciativa. Los tokens NUEVOS (`accentBright`) reciben valor en AMBOS modos (light: `RecrePrimaryLight`).
- **Sin `dynamicColor`** (regla existente, `Theme.kt:12`); `RecreTheme` sigue a `isSystemInDarkTheme()` — NO forzar dark.
- **Semántica de color intacta:** success/warning/danger/info reservados a su significado; el cian es marca. El glow SOLO acompaña a primary/accentBright y al éxito del guardado.
- **Odómetro solo donde el dinero es el mensaje:** total de denominaciones, neto de confirmación, héroe de Mi caja, héroes de deudas. PROHIBIDO en el home (el héroe del home es un conteo, tipografía display, sin €).
- **Guardarraíl:** `SinMaterialPeladoTest` debe seguir verde en cada fase; NUNCA añadir ficheros a su allowlist.
- **Dinero:** `BigDecimal` + formateo existente (el odómetro recibe el String ya formateado por `MoneyText`/su util; no formatea él).
- **Textos por `res/values/strings.xml`;** esta iniciativa no añade strings nuevos salvo contentDescription del odómetro.
- Kotlin sin `!!`; comentarios en español (el porqué); identificadores en inglés salvo dominio.
- Commits: `feat(android): …` / `refactor(android): …`; si la iniciativa recibe `T-XX` en `.kiro/specs/recre/tasks.md`, añadirlo al final del mensaje.
- Cada fase (N0…N5) = una rama `feat/android-neon-<fase>` = un PR (<400 líneas de diff neto; si una fase se pasa, divídela en dos PRs por el corte de tasks indicado).

## Entorno de build (obligatorio en cada comando gradle)

Desde `android/`. El JDK solo está en el JBR de Android Studio y no se hereda; expórtalo **en el comando**. Unit tests con locale UTF-8. `compileDebugKotlin` es el gate rápido de compilación:

```bash
# Gate de compilación (tipos + recursos R):
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin
# Unit tests JVM (incluye el guardarraíl SinMaterialPeladoTest):
LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest
# Un test concreto:
LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.ui.components.OdometroColumnasTest"
```

Los builds largos pueden cortar la conexión del sandbox; si un comando gradle no devuelve, reintenta y reporta. No uses `assembleDebug` salvo para QA en dispositivo.

## Spec de referencia

- **Mockup navegable (35 pantallas):** `docs/superpowers/specs/2026-07-07-neon-de-sala-mockup.html` — ábrelo en un navegador. Las secciones «Tramo 1..9» mapean a las fases N2-N4 de este plan. El CSS del propio fichero es la referencia exacta de radios, tamaños y jerarquía.
- **Paleta del mockup → slots del tema** (la tabla completa está en Task 2).

---

## File Structure

| Fichero | Responsabilidad |
|---------|-----------------|
| `android/app/src/main/res/font/bricolage_grotesque_variable.ttf` (nuevo) | Fuente display OFL (variable, eje wght). |
| `ui/theme/Type.kt` (modificar) | Familia `BricolageDisplay`; `headlineMedium`/`titleLarge` pasan a display; nuevo `RecreType.displayHero`. |
| `ui/theme/Color.kt` (modificar) | Re-tinte petróleo de la paleta dark + token `accentBright` en `RecreSemanticColors`. |
| `ui/theme/Theme.kt` (modificar) | `surfaceContainerHigh/Highest` dark re-tintados (valores nombrados nuevos). |
| `ui/theme/Glow.kt` (nuevo) | `Modifier.neonGlow(color, radius)` — halo radial dibujado (minSdk 26, sin API de sombra de color). |
| `ui/components/OdometroText.kt` (nuevo) | Componente firma: cifra que rueda por dígito. Lógica pura `columnasOdometro()` JVM-testeable. |
| `test/…/ui/components/OdometroColumnasTest.kt` (nuevo) | Unit tests de la descomposición en columnas. |
| `ui/components/RecreShell.kt` (modificar) | `RecreBottomBar` → dock píldora flotante. |
| `ui/components/RecreButton.kt` (modificar) | `RecrePrimaryButton` con `neonGlow` opcional (héroes de flujo). |
| `ui/components/Keypad.kt` (modificar) | Teclas 16dp radius sobre surface-2; tecla OK primary. |
| `ui/components/PasoTopBar.kt` (modificar) | Segmento activo con glow sutil. |
| `feature/recaudacion/**` (modificar, fase N2) | Adopción: total/neto → `OdometroText`; éxito del guardado con anillo. |
| `feature/locales/LocalesScreen.kt` + `feature/cuadre/CuadreScreen.kt` + `feature/deudas/*` (fase N3) | Héroes display/odómetro. |
| `feature/historico/*`, `feature/alertas/*`, `feature/incidencias/*`, `feature/gestion/**`, `feature/ajustes/*`, `feature/impresora/*`, `feature/auth/LoginScreen.kt` (fase N4) | Ajustes puntuales (ticket papel, pips, wordmark). |
| `test/…/arch/SinColoresFueraDeTokensTest.kt` (nuevo, fase N5) | Guardarraíl: prohibido `Color(0x…)` nuevo en `feature/`. |
| `.kiro/specs/recre/fase3-design-tokens.md` (modificar) | Anexo «Neón de sala» con los HEX nuevos (SSOT). |

---

# FASE N0 — Tokens: tipografía display + paleta petróleo (PR 1)

### Task 1: Fuente Bricolage Grotesque + roles display

**Files:**
- Create: `android/app/src/main/res/font/bricolage_grotesque_variable.ttf`
- Modify: `android/app/src/main/java/com/recre/app/ui/theme/Type.kt`

**Interfaces:**
- Produces: `val BricolageDisplay: FontFamily` y `RecreType.displayHero: TextStyle` (34sp/W700, Bricolage, letterSpacing −0.02em) — los consumen las fases N1-N4.

- [ ] **Step 1: Descargar la fuente (OFL) y colocarla en res/font**

```bash
curl -L -o /tmp/bricolage.ttf "https://github.com/google/fonts/raw/main/ofl/bricolagegrotesque/BricolageGrotesque%5Bopsz%2Cwdth%2Cwght%5D.ttf"
cp /tmp/bricolage.ttf android/app/src/main/res/font/bricolage_grotesque_variable.ttf
ls -la android/app/src/main/res/font/
```
Expected: tres ficheros ttf (geist, geist_mono, bricolage). Si la URL cambió, buscar «Bricolage Grotesque» en github.com/google/fonts (licencia OFL, va en el propio ttf).

- [ ] **Step 2: Añadir la familia display en Type.kt** (tras el bloque de `GeistMono`, línea ~61)

```kotlin
@OptIn(ExperimentalTextApi::class)
private fun bricolageVariable(weight: FontWeight) =
    Font(
        R.font.bricolage_grotesque_variable,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** Familia display «Neón de sala»: titulares y héroes. NUNCA para cuerpo ni cifras. */
val BricolageDisplay =
    FontFamily(
        bricolageVariable(FontWeight.W600),
        bricolageVariable(FontWeight.W700),
    )
```

- [ ] **Step 3: Migrar los roles de titular a display y añadir el héroe**

En `Typography`, cambiar SOLO `fontFamily` de `headlineMedium` y `titleLarge` a `BricolageDisplay` (los tamaños/pesos no cambian; añadir `letterSpacing = (-0.02).em` a `headlineMedium`). En `RecreType`, añadir al final del object:

```kotlin
    /** Héroe display (conteo del home, titulares de tramo). Bricolage, NO cifras de dinero. */
    val displayHero =
        TextStyle(
            fontFamily = BricolageDisplay,
            fontWeight = FontWeight.W700,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.02).em,
            lineHeightStyle = lineHeightTrim,
        )
```

- [ ] **Step 4: Compilar**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin` (desde `android/`)
Expected: BUILD SUCCESSFUL (si falla con «resource font/bricolage… not found», el nombre del ttf tiene mayúsculas o guiones: debe ser snake_case exacto).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/res/font/bricolage_grotesque_variable.ttf android/app/src/main/java/com/recre/app/ui/theme/Type.kt
git commit -m "feat(android): familia display Bricolage para titulares (neón N0)"
```

### Task 2: Paleta dark petróleo + token accentBright

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/ui/theme/Color.kt`
- Modify: `android/app/src/main/java/com/recre/app/ui/theme/Theme.kt:76-77`
- Modify: `.kiro/specs/recre/fase3-design-tokens.md` (anexo)

**Interfaces:**
- Produces: `RecreColors.current.accentBright: Color` (cian vivo #67E3F4 en dark / #0E7490 en light). El resto de slots conservan nombre.

- [ ] **Step 1: Re-tintar los valores dark en Color.kt** (sección «--- Dark», líneas 38-52; y neutrales relacionados). Sustituir SOLO estos valores:

```kotlin
val RecreBackgroundDark = Color(0xFF0A1014) // [PALETA] background (petróleo profundo, neón N0)
val RecreSurface1Dark = Color(0xFF111A21) // [PALETA] surface-1
val RecreSurface2Dark = Color(0xFF182530) // [PALETA] surface-2
val RecreBorderDark = Color(0xFF22323D) // [PALETA] border / outlineVariant
val RecreMutedDark = Color(0xFF8FA6B0) // [PALETA] muted
val RecreMutedStrongDark = Color(0xFFB8CBD4) // muted-strong ≥7:1 sobre surface-2 (verificado abajo)
val RecreOnSurfaceDark = Color(0xFFEAF3F6) // casi-blanco frío (tinte petróleo)
```

Y los neutrales que derivan de superficie (secciones 3 y 3b):

```kotlin
val RecreStateNeutralBgDark = Color(0xFF182530) // == surface-2
val RecreStateNeutralBorderDark = Color(0xFF22323D) // == border
val RecreStateNeutralFgDark = Color(0xFFEAF3F6)
val RecreStateNeutralMutedDark = Color(0xFF8FA6B0)
val RecreNeutralChipBgDark = Color(0xFF22313C)
val RecreNeutralChipFgDark = Color(0xFFB3C4CD)
```

`RecrePrimaryDark`, `RecreOnPrimaryDark`, `RecreSecondaryDark` y todos los roles success/warning/danger/info NO cambian.

- [ ] **Step 2: Añadir accentBright a la data class y a ambas paletas**

En la sección 1, tras `RecreRingDark`:

```kotlin
val RecreAccentBrightDark = Color(0xFF67E3F4) // acento vivo: glow, icono activo del dock, odómetro
val RecreAccentBrightLight = Color(0xFF0E7490) // en light no hay neón: primary
```

En `RecreSemanticColors`, tras `val mutedStrong: Color,`:

```kotlin
    val accentBright: Color, // cian vivo para glow/estados activos (== primary en light)
```

Y en `LightSemanticColors` / `DarkSemanticColors`, tras `mutedStrong = …`:

```kotlin
        accentBright = RecreAccentBrightLight,   // (Light)
        accentBright = RecreAccentBrightDark,    // (Dark)
```

- [ ] **Step 3: Re-tintar los containers altos en Theme.kt** (dark scheme, líneas 76-77)

```kotlin
        surfaceContainerHigh = Color(0xFF1E2E3A),
        surfaceContainerHighest = Color(0xFF243542),
```

- [ ] **Step 4: Verificar contraste de los pares nuevos** (script rápido, desde la raíz)

```bash
python3 - <<'EOF'
def lum(h):
    r,g,b=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    f=lambda c: c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
    return 0.2126*f(r)+0.7152*f(g)+0.0722*f(b)
def ratio(a,b):
    la,lb=sorted((lum(a),lum(b)),reverse=True)
    return (la+0.05)/(lb+0.05)
print('onSurface/surface1', round(ratio('EAF3F6','111A21'),2), '(>= 7 OK)')
print('muted/surface1', round(ratio('8FA6B0','111A21'),2), '(>= 4.5 OK)')
print('mutedStrong/surface2', round(ratio('B8CBD4','182530'),2), '(>= 7 OK)')
print('neutralChipFg/Bg', round(ratio('B3C4CD','22313C'),2), '(>= 7 OK)')
print('accentBright/surface1', round(ratio('67E3F4','111A21'),2), '(>= 4.5 OK)')
EOF
```
Expected: todos por encima del umbral indicado. Si alguno falla, aclara el fg en pasos de +4 en el canal L hasta cumplir y anota el HEX final en el spec (Step 5).

- [ ] **Step 5: Anexo SSOT en fase3-design-tokens.md**

Añadir al final del doc una sección `## Anexo — Re-tinte «Neón de sala» (2026-07)` con una tabla token→HEX viejo→HEX nuevo (los de Steps 1-3) y la regla «accentBright solo para glow/activos; light sin neón».

- [ ] **Step 6: Compilar + guardarraíl**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin` y después `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; suite verde (el re-tinte no cambia firmas).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/theme/Color.kt android/app/src/main/java/com/recre/app/ui/theme/Theme.kt .kiro/specs/recre/fase3-design-tokens.md
git commit -m "feat(android): paleta dark petroleo + accentBright (neón N0)"
```

### Task 3: Modifier.neonGlow

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/theme/Glow.kt`

**Interfaces:**
- Produces: `fun Modifier.neonGlow(color: Color, radius: Dp = 24.dp, alpha: Float = 0.35f): Modifier` — halo radial DETRÁS del contenido. Lo consumen RecreButton (N1), PasoTopBar (N1) y el anillo de éxito (N2).

- [ ] **Step 1: Implementación completa**

```kotlin
package com.recre.app.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Halo neón dibujado detrás del contenido. Se usa drawBehind con gradiente radial
 * porque minSdk 26 no soporta sombras de color (ambientShadowColor requiere API 28)
 * y elevation no admite tinte. El halo NO ocupa layout: se pinta fuera de bounds.
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 24.dp,
    alpha: Float = 0.35f,
): Modifier =
    drawBehind {
        val r = radius.toPx()
        val brush =
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = (maxOf(size.width, size.height) / 2f) + r,
            )
        drawRect(
            brush = brush,
            topLeft = Offset(-r, -r),
            size = size.copy(width = size.width + 2 * r, height = size.height + 2 * r),
        )
    }
```

- [ ] **Step 2: Compilar**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/theme/Glow.kt
git commit -m "feat(android): Modifier.neonGlow para halos de acento (neón N0)"
```

**Cierre de fase N0:** abrir PR `feat/android-neon-n0` («tokens: display + paleta petróleo + glow»). QA visual: instalar en dispositivo con tema oscuro y comprobar que TODAS las pantallas siguen legibles (el re-tinte es global). Squash & merge.

---

# FASE N1 — Componentes firma (PR 2)

### Task 4: OdometroText (TDD)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/OdometroText.kt`
- Test: `android/app/src/test/java/com/recre/app/ui/components/OdometroColumnasTest.kt`

**Interfaces:**
- Consumes: `RecreType.importe` / `RecreType.importeMedium` (Type.kt), `RecreColors.current.accentBright`.
- Produces:
  - `sealed interface ColumnaOdometro` con `data class Digito(val valor: Int)` y `data class Fijo(val caracter: Char)`
  - `fun columnasOdometro(texto: String): List<ColumnaOdometro>` (pura, JVM)
  - `@Composable fun OdometroText(texto: String, modifier: Modifier = Modifier, style: TextStyle = RecreType.importe, color: Color = Color.Unspecified)` — `texto` YA formateado (p. ej. `"1.284,50 €"` salido de la misma util que usa `MoneyText`; mira `MoneyTextFormatted` en `MoneyText.kt:185` para localizarla).

- [ ] **Step 1: Test que falla**

```kotlin
package com.recre.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class OdometroColumnasTest {
    @Test
    fun `descompone digitos y fijos preservando el orden`() {
        val columnas = columnasOdometro("1.284,50 €")
        assertEquals(
            listOf(
                ColumnaOdometro.Digito(1), ColumnaOdometro.Fijo('.'),
                ColumnaOdometro.Digito(2), ColumnaOdometro.Digito(8), ColumnaOdometro.Digito(4),
                ColumnaOdometro.Fijo(','), ColumnaOdometro.Digito(5), ColumnaOdometro.Digito(0),
                ColumnaOdometro.Fijo(' '), ColumnaOdometro.Fijo('€'),
            ),
            columnas,
        )
    }

    @Test
    fun `cadena sin digitos produce solo fijos`() {
        assertEquals(
            listOf(ColumnaOdometro.Fijo('—')),
            columnasOdometro("—"),
        )
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.ui.components.OdometroColumnasTest"`
Expected: FAIL (unresolved reference `columnasOdometro`).

- [ ] **Step 3: Implementación completa**

```kotlin
package com.recre.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.recre.app.ui.theme.RecreType

/** Columna del odómetro: un dígito que rueda o un carácter fijo (separador, €). */
sealed interface ColumnaOdometro {
    data class Digito(val valor: Int) : ColumnaOdometro
    data class Fijo(val caracter: Char) : ColumnaOdometro
}

/** Pura y JVM-testeable: descompone el texto formateado en columnas. */
fun columnasOdometro(texto: String): List<ColumnaOdometro> =
    texto.map { ch ->
        if (ch.isDigit()) ColumnaOdometro.Digito(ch.digitToInt()) else ColumnaOdometro.Fijo(ch)
    }

/**
 * Cifra que rueda por dígito, como el contador mecánico de una máquina (firma
 * «Neón de sala»). Recibe el texto YA formateado (mismo formateador que MoneyText):
 * este componente no sabe de BigDecimal. Cada dígito anima con un retardo
 * escalonado (45ms/columna) para el efecto de rodillo. Accesibilidad: el Row
 * expone el texto completo como una sola descripción; las columnas no son focables.
 */
@Composable
fun OdometroText(
    texto: String,
    modifier: Modifier = Modifier,
    style: TextStyle = RecreType.importe,
    color: Color = Color.Unspecified,
) {
    val colorFinal = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Row(
        modifier =
            modifier.semantics { contentDescription = texto },
        verticalAlignment = Alignment.Bottom,
    ) {
        columnasOdometro(texto).forEachIndexed { index, columna ->
            when (columna) {
                is ColumnaOdometro.Fijo ->
                    Text(
                        text = columna.caracter.toString(),
                        style = style,
                        color = colorFinal,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                is ColumnaOdometro.Digito ->
                    AnimatedContent(
                        targetState = columna.valor,
                        transitionSpec = {
                            val duracion = 500
                            val retardo = index * 45
                            (
                                slideInVertically(tween(duracion, retardo)) { alto -> alto } togetherWith
                                    slideOutVertically(tween(duracion, retardo)) { alto -> -alto }
                            )
                        },
                        label = "odometro-digito",
                    ) { digito ->
                        Text(
                            text = digito.toString(),
                            style = style,
                            color = colorFinal,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
            }
        }
    }
}
```

Nota: si `Motion.kt` (`LocalRecreMotion`) expone una duración estándar de énfasis, usa ese token en vez del literal 500 y déjalo comentado; no bloquees la task por ello.

- [ ] **Step 4: Verificar que pasa + compilar**

Run: `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.ui.components.OdometroColumnasTest"` y `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin`
Expected: PASS ×2 tests; BUILD SUCCESSFUL.

- [ ] **Step 5: Añadir previews light/dark** (mismo patrón que `MoneyTextPreviewDark`, `MoneyText.kt:385-393`) con `OdometroText("1.284,50 €")` y commit

```bash
git add android/app/src/main/java/com/recre/app/ui/components/OdometroText.kt android/app/src/test/java/com/recre/app/ui/components/OdometroColumnasTest.kt
git commit -m "feat(android): OdometroText, la cifra que rueda (neón N1)"
```

### Task 5: Dock píldora + CTA con glow + keypad

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/ui/components/RecreShell.kt:79-116` (`RecreBottomBar`)
- Modify: `android/app/src/main/java/com/recre/app/ui/components/RecreButton.kt:91-130` (`RecrePrimaryButton`)
- Modify: `android/app/src/main/java/com/recre/app/ui/components/Keypad.kt:191-230` (`KeypadKey`)

**Interfaces:**
- Consumes: `Modifier.neonGlow` (Task 3), `RecreColors.current.accentBright` (Task 2).
- Produces: `RecrePrimaryButton(…, glow: Boolean = false)` — parámetro NUEVO con default, no rompe llamadas existentes. `RecreBottomBar` y `Keypad` conservan firma.

- [ ] **Step 1: RecreBottomBar → píldora flotante.** Leer el composable actual (`RecreShell.kt:79`). Envolver el contenido en una `Surface` propia en vez de `NavigationBar` a ancho completo:

```kotlin
// Dentro de RecreBottomBar, la fila de destinos pasa a vivir en una píldora
// flotante: Surface redondeada 999dp, surface-1 al 92% + borde, separada 10dp
// del borde inferior. Los iconos activos usan accentBright.
Surface(
    modifier =
        modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .height(62.dp),
    shape = RoundedCornerShape(999.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    border = BorderStroke(1.dp, RecreColors.current.border),
    shadowElevation = 12.dp,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // …items existentes; tint del icono/label activo:
        // if (seleccionado) RecreColors.current.accentBright else RecreColors.current.muted
    }
}
```

Mantener EXACTAMENTE los 4 `TopLevelDestination` y sus labels (`nav_locales`…`nav_ajustes`), la semántica de selección y `navigateTab`. Ojo con el inset inferior: si el `Scaffold` del shell aplicaba `WindowInsets.navigationBars` a la NavigationBar, conservarlo en el padding de la Surface.

- [ ] **Step 2: RecrePrimaryButton con glow opt-in.** Añadir parámetro `glow: Boolean = false` y, en el Modifier del botón:

```kotlin
val modifierConGlow =
    if (glow) modifier.neonGlow(RecreColors.current.accentBright, radius = 20.dp, alpha = 0.3f)
    else modifier
```
Usar `modifierConGlow` donde se usaba `modifier`. Nada más cambia.

- [ ] **Step 3: Keypad.** En `KeypadKey`/`DigitKey`: shape a `RoundedCornerShape(16.dp)`, fondo `RecreColors.current.surface2`, borde `RecreColors.current.border`; la tecla de confirmación (la que hoy sea primary/tonal) pasa a fondo `MaterialTheme.colorScheme.primary` con contenido `onPrimary`. Respetar tamaños/touch targets actuales.

- [ ] **Step 4: Compilar + suite + previews**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin && LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest`
Expected: verde. Revisar los `@Preview` existentes de Keypad/RecreButton en Android Studio si hay dudas visuales.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/RecreShell.kt android/app/src/main/java/com/recre/app/ui/components/RecreButton.kt android/app/src/main/java/com/recre/app/ui/components/Keypad.kt
git commit -m "feat(android): dock pildora, CTA con glow y keypad neon (neón N1)"
```

### Task 6: PasoTopBar con segmento activo iluminado

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/ui/components/PasoTopBar.kt:125-156` (`PasosSegmentos`)

**Interfaces:** firma pública intacta.

- [ ] **Step 1:** En `PasosSegmentos`, el segmento activo pasa de su color actual a `MaterialTheme.colorScheme.primary` con `Modifier.neonGlow(RecreColors.current.accentBright, radius = 8.dp, alpha = 0.5f)`; los inactivos a `RecreColors.current.surface2`. Altura/anchos intactos.
- [ ] **Step 2:** Compilar (`:app:compileDebugKotlin`) → BUILD SUCCESSFUL. Verificar `PasoTopBarDarkPreview`.
- [ ] **Step 3:** Commit: `git add …/PasoTopBar.kt && git commit -m "feat(android): pasos del flujo con acento iluminado (neón N1)"`

**Cierre de fase N1:** PR `feat/android-neon-n1`. QA en dispositivo: navegar las 4 pestañas (dock), abrir un flujo de recaudación (pasos + keypad). Squash & merge.

---

# FASE N2 — Flujo de recaudación (PR 3)

> Referencia visual: mockup, sección «Tramo 3 · Flujo de recaudación». Las pantallas ya son fieles en contenido; aquí SOLO cambia presentación de héroes y momentos.

### Task 7: Denominaciones — total que rueda

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionesScreen.kt`

**Interfaces:**
- Consumes: `OdometroText(texto: String, style, color)` (Task 4).

- [ ] **Step 1: Localizar el total.** `grep -n "CountUpText\|MoneyText" android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionesScreen.kt` — el bloque «Total» del ProgresoInfo (barra sticky con Objetivo + Total + chip Cuadra/Faltan/Sobran).
- [ ] **Step 2: Sustituir el Text/CountUpText del TOTAL por el odómetro,** conservando el mismo String formateado que ya se pinta (no tocar el formateo ni el estado):

```kotlin
OdometroText(
    texto = totalFormateado, // el MISMO valor String que pintaba el componente anterior
    style = RecreType.importeMedium,
)
```
El chip Cuadra/Faltan/Sobran y «Objetivo:» no se tocan (StatusChip ya hereda el re-tinte N0).

- [ ] **Step 3: Compilar** → BUILD SUCCESSFUL. **Step 4: QA:** en emulador/dispositivo, contar denominaciones y ver rodar el total.
- [ ] **Step 5: Commit:** `git commit -m "feat(android): total de denominaciones con odometro (neón N2)"`

### Task 8: Confirmación — neto héroe + guardar con glow

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/confirmacion/ConfirmacionScreen.kt`

- [ ] **Step 1: Localizar el neto héroe** (`grep -n "importe\b\|MoneyText\|CountUpText" …/ConfirmacionScreen.kt`, bloque NetoHero) y sustituir su cifra por `OdometroText(texto = netoFormateado, style = RecreType.importe)` centrado, con el eyebrow existente encima.
- [ ] **Step 2: CTA «Guardar e imprimir»** — si usa `RecrePrimaryButton`, añadir `glow = true`.
- [ ] **Step 3:** Compilar → BUILD SUCCESSFUL. QA: llegar al paso 3 con datos de seed y verificar neto rodando + botón con halo.
- [ ] **Step 4: Commit:** `git commit -m "feat(android): neto heroe con odometro y CTA iluminado (neón N2)"`

### Task 9: GuardadoModal — anillo de éxito

**Files:**
- Modify: el composable del guardado por fases (vive con ConfirmacionScreen; localizar con `grep -rn "recaudacion_post_guardado_titulo" android/app/src/main/java/`)

- [ ] **Step 1:** En el estado de ÉXITO, envolver el icono check existente en:

```kotlin
Box(
    modifier =
        Modifier
            .size(84.dp)
            .neonGlow(RecreColors.current.success, radius = 28.dp, alpha = 0.3f)
            .background(
                color = RecreColors.current.successContainer,
                shape = RoundedCornerShape(34.dp),
            )
            .border(2.dp, RecreColors.current.success, RoundedCornerShape(34.dp)),
    contentAlignment = Alignment.Center,
) { /* icono check existente, tint = RecreColors.current.success */ }
```
Las fases (Registrando/Subiendo/Imprimiendo) y sus strings NO cambian.

- [ ] **Step 2:** Compilar → BUILD SUCCESSFUL. QA: guardar una recaudación (seed local) y ver el anillo.
- [ ] **Step 3: Commit:** `git commit -m "feat(android): anillo de exito en el guardado (neón N2)"`

**Cierre de fase N2:** PR `feat/android-neon-n2` + QA del flujo completo (contadores → denominaciones → confirmar → guardado) en dispositivo, cotejando contra el Tramo 3 del mockup. Squash & merge.

---

# FASE N3 — Home, Mi caja y deudas (PR 4)

### Task 10: Héroe del home en display (SIN dinero)

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt:211-240` (`AgendaHero`)

- [ ] **Step 1:** El conteo del héroe (`plurals agenda_hero_pendientes`) pasa a `RecreType.displayHero` con el NÚMERO en `RecreColors.current.warning` y el resto del texto en `onSurface` (AnnotatedString). El estado «Todo al día» (`agenda_hero_todo_al_dia`) usa el mismo estilo con el texto completo en `RecreColors.current.success`. PROHIBIDO añadir importes aquí (constraint global).
- [ ] **Step 2:** Compilar → BUILD SUCCESSFUL. QA: home con pendientes y con todo al día (cambiar seed o filtros).
- [ ] **Step 3: Commit:** `git commit -m "feat(android): heroe del home en display Bricolage (neón N3)"`

### Task 11: Mi caja — «Deberías llevar» rueda

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/cuadre/CuadreScreen.kt`

- [ ] **Step 1:** Localizar el héroe (`grep -n "cuadre_deberias_llevar" …/CuadreScreen.kt` y el importe debajo). Sustituir la cifra por `OdometroText(texto = deberiasLlevarFormateado, style = RecreType.importe)`. La tabla de denominaciones y el chip veredicto no cambian.
- [ ] **Step 2:** Compilar → BUILD SUCCESSFUL. QA con la semana de seed.
- [ ] **Step 3: Commit:** `git commit -m "feat(android): deberias llevar con odometro (neón N3)"`

### Task 12: Deudas — héroes de saldo y capital

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/deudas/DeudasLocalScreen.kt` (héroe «Deuda total del local»)
- Modify: `android/app/src/main/java/com/recre/app/feature/deudas/DeudasGestorScreen.kt` (héroe «Capital en la calle»)

- [ ] **Step 1:** En ambos, sustituir la cifra del héroe por `OdometroText(texto = …, style = RecreType.importe)`. Desgloses, cards y ledger intactos.
- [ ] **Step 2:** Compilar → BUILD SUCCESSFUL. QA con seed (Bar Gipuzkoa tiene deuda).
- [ ] **Step 3: Commit:** `git commit -m "feat(android): heroes de deudas con odometro (neón N3)"`

**Cierre de fase N3:** PR `feat/android-neon-n3`. Squash & merge.

---

# FASE N4 — Resto de tramos (PR 5)

> La mayor parte del re-skin de estas pantallas YA ocurrió vía tokens (N0) y componentes (N1). Esta fase son retoques puntuales; si al revisar una pantalla contra el mockup no hay diferencia sustancial, se anota «ya cubierta por tokens» y no se toca.

### Task 13: Ticket térmico del histórico

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/historico/HistoricoDetalleScreen.kt` (composable `TicketRecibo`; localizar con `grep -n "historico_ticket_titulo" …`)

- [ ] **Step 1:** El papel del ticket usa colores PROPIOS fijos (papel sobre sala oscura, igual en light y dark). Añadir en `Color.kt` (sección 1, con comentario):

```kotlin
val RecrePapelTicket = Color(0xFFF5F2EA) // papel térmico del ticket (fijo, ambos modos)
val RecrePapelTinta = Color(0xFF1C2326) // tinta sobre papel
```
Y en `TicketRecibo`: fondo del papel `RecrePapelTicket`, texto `RecrePapelTinta`, sello de estado con el color del rol (`successText` para Firme). El borde dentado inferior, si no existe, se omite (YAGNI).

- [ ] **Step 2:** Compilar → BUILD SUCCESSFUL. QA: abrir un detalle de recaudación.
- [ ] **Step 3: Commit:** `git commit -m "feat(android): ticket termico en papel fijo (neón N4)"`

### Task 14: Login — wordmark con punto neón

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/auth/LoginScreen.kt` (cabecera de marca, ~línea 190)

- [ ] **Step 1:** El título de marca pasa a AnnotatedString: `"recre"` en `BricolageDisplay` W700 44sp `onSurface` + `"·"` en `RecreColors.current.accentBright`. El claim (`auth_login_description`) y el formulario no cambian.
- [ ] **Step 2:** Compilar → BUILD SUCCESSFUL. **Step 3: Commit:** `git commit -m "feat(android): wordmark neon en login (neón N4)"`

### Task 15: Barrido de verificación de los tramos restantes

**Files:** ninguno a priori (solo si el barrido encuentra desviaciones).

- [ ] **Step 1:** Con el mockup abierto, recorrer en dispositivo: Alertas, Incidencias, Gestión (hub, listas, formularios), Ajustes (2 pestañas), Impresora, Selección de empresa, Sin acceso, Error de sesión, Histórico (lista y contexto), Detalle de local, Escáner.
- [ ] **Step 2:** Anotar en `docs/superpowers/plans/2026-07-07-reskin-neon-de-sala.md` (sección «Desviaciones N4», crearla al final) cada diferencia sustancial de jerarquía o componente (NO de contenido: el contenido del mockup ya es fiel al código). Para cada una: o se arregla en esta task con el patrón de las tasks 13-14 (localizar → snippet → compilar → commit), o se anota como «aceptada» con motivo.
- [ ] **Step 3: Commit** de lo que se haya tocado + la sección de desviaciones.

**Cierre de fase N4:** PR `feat/android-neon-n4`. Squash & merge.

---

# FASE N5 — Guardarraíl y limpieza (PR 6)

### Task 16: Guardarraíl de colores hardcodeados

**Files:**
- Create: `android/app/src/test/java/com/recre/app/arch/SinColoresFueraDeTokensTest.kt`

**Interfaces:** ninguna (test puro JVM, mismo patrón que `SinMaterialPeladoTest`).

- [ ] **Step 1: Test (falla si hay infractores nuevos):**

```kotlin
package com.recre.app.arch

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guardarraíl del re-skin «Neón de sala»: ninguna pantalla de feature/ debe
 * declarar colores literales (Color(0x…)): todo color sale de MaterialTheme,
 * RecreColors o los tokens nombrados de ui/theme. Allowlist = infractores
 * HEREDADOS al arrancar N5; regla: nunca añadir, solo retirar.
 */
class SinColoresFueraDeTokensTest {
    private val allowlist = setOf<String>(
        // rellenar en Step 2 con los infractores heredados exactos
    )

    @Test
    fun `feature sin Color hex literal`() {
        val raiz = File("src/main/java/com/recre/app/feature")
        val infractores =
            raiz.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains(Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")) }
                .map { it.relativeTo(raiz).path }
                .filterNot { it in allowlist }
                .toList()
        if (infractores.isNotEmpty()) {
            fail("Colores hex fuera de tokens en feature/: $infractores — usa RecreColors/MaterialTheme.")
        }
    }
}
```

- [ ] **Step 2:** Ejecutarlo, copiar los ficheros que fallen a la `allowlist` (herencia congelada) y volver a ejecutar → PASS.

Run: `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.arch.SinColoresFueraDeTokensTest"`

- [ ] **Step 3:** Suite completa verde + commit: `git commit -m "test(android): guardarrail de colores fuera de tokens (neón N5)"`

### Task 17: QA final y cierre

- [ ] **Step 1:** `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:assembleDebug` e instalar en dispositivo (`adb install -r app/build/outputs/apk/debug/app-debug.apk`).
- [ ] **Step 2:** Pasada completa contra el mockup (los 9 tramos), en dark Y en light (light no debe haber empeorado: solo cambian componentes, no su paleta).
- [ ] **Step 3:** `./gradlew lint` limpio de errores nuevos; suite completa verde.
- [ ] **Step 4:** Marcar la iniciativa en `.kiro/specs/recre/tasks.md` si tiene `T-XX`; PR final `feat/android-neon-n5`, squash & merge.

---

## Self-review (hecho al escribir el plan)

- **Cobertura del spec:** los 9 tramos del mockup → N2 (tramo 3), N3 (tramos 2/4/7-héroes), N4 (tramos 1/5/6/8/9 vía tokens + tasks 13-15), dock/keypad/pasos (transversales) → N1. El tramo 1 (acceso) solo necesita el wordmark (task 14): el resto es formulario estándar que hereda tokens.
- **Sin placeholders:** cada task tiene código o comando concreto; las tasks de pantalla dan el snippet OBJETIVO + grep de localización (el «before» exacto se ve al abrir el fichero; las firmas consumidas están definidas en tasks anteriores).
- **Consistencia de firmas:** `OdometroText(texto, modifier, style, color)` (Task 4) es lo que consumen 7/8/11/12; `neonGlow(color, radius, alpha)` (Task 3) es lo que consumen 5/6/9; `accentBright` (Task 2) es lo que consumen 4/5/6/14.

---

## Desviaciones N4 (barrido estático)

Barrido ESTÁTICO (a nivel de código, sin dispositivo) de los tramos que ninguna task N4 tocó, previo al guardarraíl N5. Ámbito: Alertas, Incidencias, Gestión (hub/listas/formularios), Ajustes (2 pestañas), Impresora, Selección de empresa, Sin acceso, Error de sesión, Histórico (lista/contexto/detalle), Detalle de local, Escáner de contadores. Excluidos por adopción previa: `auth/LoginScreen.kt`, `historico/components/TicketRecibo.kt`, `ui/theme/Color.kt`, y los tramos recaudación/cuadre/deudas/locales-home.

**Resultado: 0 infracciones de color y 0 de jerarquía. No se tocó código; el barrido es limpio.**

Comprobaciones (todas sobre las pantallas del ámbito):

- **Colores hex fuera de tokens (`Color(0x…)`):** cero. El único `Color(0x…)` de todo `feature/` es `recaudacion/components/SignaturePad.kt:42` (tramo excluido; irá a la allowlist congelada del guardarraíl N5, no es de mi ámbito). `grep '0x[0-9A-Fa-f]{6,8}'` en las carpetas del ámbito → `NONE_FOUND`.
- **Colores con nombre (`Color.Gray`/`White`/`Black`/…):** cero. Ninguna superficie ni texto usa gris/blanco/negro hardcodeado.
- **Fuente de color:** el 100 % del color sale de `MaterialTheme.colorScheme.*` (`surfaceVariant`, `secondaryContainer`, `errorContainer`, `tertiaryContainer`, `onSurface`, `onSurfaceVariant`, `primary`…). Por tanto el re-tinte N0 llega solo a todas estas pantallas; nada pelea con la paleta. (`alertas/AlertasScreen.kt:iconYColor` mapea cada `TipoAlerta` a un *container* del tema respetando semántica: conflicto→`errorContainer`, caducidad→`tertiaryContainer`, resto→`surfaceVariant`.)
- **`fontSize` manual:** cero en todo el ámbito. Todos los títulos/rótulos usan roles semánticos de `MaterialTheme.typography` (`titleMedium`/`titleSmall` para rótulos de sección y de ítem de lista; `headlineSmall` para el título centrado de `empresa/SinAccesoScreen` y `empresa/ErrorSesionScreen`).

Notas de jerarquía (aceptadas, NO son desviaciones):

- Según `ui/theme/Type.kt`, Bricolage (display) solo lo llevan `headlineMedium` (H1), `titleLarge` (H2) y `displayHero`. Estas pantallas son de utilidad (ajustes, impresora, listas/formularios de gestión, error/sin-acceso), no tramos-héroe, así que usar `titleMedium`/`titleSmall`/`headlineSmall` para sus rótulos es coherente con la escala: quedan por debajo de los titulares héroe que N2/N3 sí llevaron a Bricolage. No requieren el display font.
- `empresa/SinAccesoScreen` y `empresa/ErrorSesionScreen` titulan con `headlineSmall` (rol semántico, sin tamaño manual). El mockup no especifica estas pantallas de borde; su jerarquía actual es razonable. **Pendiente-QA-dispositivo (N5):** confirmar visualmente que el peso del titular casa con el resto en dark y light.
- **Escáner de contadores** (`recaudacion/contadores/EscanerContadoresScreen.kt`, tramo recaudación excluido de edición): revisado de todos modos — no declara ningún color, solo roles `typography.*`. Limpio. **Pendiente-QA-dispositivo (N5):** el overlay de cámara se valida mejor en vivo.

Pendiente para el QA humano de N5 (no bloquea el cierre de N4): pasada visual de estas pantallas contra el mockup en dark y light en dispositivo.
