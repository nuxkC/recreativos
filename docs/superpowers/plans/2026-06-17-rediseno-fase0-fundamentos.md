# Rediseño UI — Fase 0: Fundamentos del sistema · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Terminar la base del sistema de diseño "Confianza Industrial" (formas, espaciado, motion de firma, iconos, guardarraíles y componentes nuevos) para que las fases 1–5 reskinen las pantallas sobre una base sólida, sin cambio visual grande todavía.

**Architecture:** Tokens y componentes en `android/app/src/main/java/com/recre/app/ui/theme/` y `ui/components/`, cableados en `RecreTheme`. Se respeta el patrón existente: `MaterialTheme` + CompositionLocals propios (`RecreColors`, `RecreMotion`). No se toca dominio ni datos. Un test JVM de guardarraíl impide que las pantallas vuelvan a usar Material pelado.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, JUnit4. Build con Gradle.

**Spec de referencia:** `docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md` (§3 Fundamentos, §3.1–3.5, §4 P8, §5 Motion).

---

## Comandos del proyecto (el motor no está en el PATH por defecto)

- **Compilar:** `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew -p android :app:assembleDebug`
- **Test unitario JVM** (el locale por defecto rompe nombres con tildes → forzar UTF-8):
  `JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew -p android :app:testDebugUnitTest --tests "<patrón>"`
- Trabaja en una rama: `git checkout -b feat/android-fase0-fundamentos`

---

### Task 1: Formas de marca (`RecreShapes`)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/theme/Shape.kt`
- Test: `android/app/src/test/java/com/recre/app/ui/theme/ShapeTest.kt`
- Modify: `android/app/src/main/java/com/recre/app/ui/theme/Theme.kt` (la llamada a `MaterialTheme(...)`)

- [ ] **Step 1: Escribe el test que falla**

```kotlin
package com.recre.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ShapeTest {
    @Test
    fun `radios de marca 12_16_20`() {
        assertEquals(RoundedCornerShape(12.dp), RecreShapes.small)
        assertEquals(RoundedCornerShape(12.dp), RecreShapes.extraSmall)
        assertEquals(RoundedCornerShape(16.dp), RecreShapes.medium)
        assertEquals(RoundedCornerShape(20.dp), RecreShapes.large)
        assertEquals(RoundedCornerShape(20.dp), RecreShapes.extraLarge)
    }

    @Test
    fun `pildora al 50 por ciento`() {
        assertEquals(RoundedCornerShape(percent = 50), PillShape)
    }
}
```

- [ ] **Step 2: Ejecuta el test y verifica que falla**

Run: `JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew -p android :app:testDebugUnitTest --tests "com.recre.app.ui.theme.ShapeTest"`
Expected: FAIL — `Unresolved reference: RecreShapes`.

- [ ] **Step 3: Crea `Shape.kt`**

```kotlin
package com.recre.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas de marca "Confianza Industrial" (radios 12/16/20). Sustituyen los radios
 * por defecto de Material 3, que `Theme.kt` no estaba sobreescribiendo.
 */
val RecreShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), // cards
    large = RoundedCornerShape(20.dp), // hojas / diálogos
    extraLarge = RoundedCornerShape(20.dp),
)

/** Píldora para chips y badges (StatusChip, FilterChip, NotificationBadge). */
val PillShape = RoundedCornerShape(percent = 50)
```

- [ ] **Step 4: Ejecuta el test y verifica que pasa**

Run: igual que Step 2. Expected: PASS (2 tests).

- [ ] **Step 5: Cablea `RecreShapes` en `Theme.kt`**

Localiza la llamada `MaterialTheme(colorScheme = ..., typography = Typography, content = ...)` en `Theme.kt` y añade el parámetro `shapes = RecreShapes`:

```kotlin
MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = RecreShapes,
    content = content,
)
```

- [ ] **Step 6: Compila**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew -p android :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/theme/Shape.kt \
        android/app/src/test/java/com/recre/app/ui/theme/ShapeTest.kt \
        android/app/src/main/java/com/recre/app/ui/theme/Theme.kt
git commit -m "feat(android): formas de marca RecreShapes (12/16/20) cableadas en el tema (rediseño F0)"
```

---

### Task 2: Espaciado de marca (`RecreSpacing`)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/theme/Spacing.kt`
- Test: `android/app/src/test/java/com/recre/app/ui/theme/SpacingTest.kt`
- Modify: `Theme.kt` (añadir el provider al `CompositionLocalProvider` existente)

- [ ] **Step 1: Escribe el test que falla**

```kotlin
package com.recre.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTest {
    @Test
    fun `rejilla 4_8_12_16_24_32`() {
        val s = RecreSpacing()
        assertEquals(4.dp, s.xs)
        assertEquals(8.dp, s.sm)
        assertEquals(12.dp, s.md)
        assertEquals(16.dp, s.lg)
        assertEquals(24.dp, s.xl)
        assertEquals(32.dp, s.xxl)
    }
}
```

- [ ] **Step 2: Ejecuta y verifica fallo**

Run: `...testDebugUnitTest --tests "com.recre.app.ui.theme.SpacingTest"` → FAIL (`Unresolved reference: RecreSpacing`).

- [ ] **Step 3: Crea `Spacing.kt`**

```kotlin
package com.recre.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Rejilla de espaciado de marca (dp). Se consume con `RecreSpacing.current` vía el CompositionLocal. */
data class RecreSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val LocalRecreSpacing = staticCompositionLocalOf { RecreSpacing() }
```

- [ ] **Step 4: Ejecuta y verifica que pasa** (igual que Step 2 → PASS).

- [ ] **Step 5: Provee el CompositionLocal en `Theme.kt`**

En `Theme.kt` ya existe un `CompositionLocalProvider(LocalRecreColors provides ..., LocalRecreMotion provides ...)`. Añade `LocalRecreSpacing provides RecreSpacing()` a esa misma llamada (un argumento más, separado por coma).

- [ ] **Step 6: Compila** → `assembleDebug` BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/theme/Spacing.kt \
        android/app/src/test/java/com/recre/app/ui/theme/SpacingTest.kt \
        android/app/src/main/java/com/recre/app/ui/theme/Theme.kt
git commit -m "feat(android): tokens de espaciado RecreSpacing (4..32) por CompositionLocal (rediseño F0)"
```

---

### Task 3: Tokens de motion de firma

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/ui/theme/Motion.kt`
- Test: `android/app/src/test/java/com/recre/app/ui/theme/MotionTest.kt`

> Antes de empezar, ABRE `Motion.kt` y mira la estructura actual (`RecreMotionScheme` + `LocalRecreMotion`). Vas a AÑADIR las duraciones de firma como constantes públicas, sin tocar los 3 specs existentes.

- [ ] **Step 1: Escribe el test que falla**

```kotlin
package com.recre.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test
    fun `duraciones de firma`() {
        assertEquals(600, RecreMotionDurations.COUNT_UP_MS)
        assertEquals(900, RecreMotionDurations.SUCCESS_FLASH_MS)
        assertEquals(400, RecreMotionDurations.DANGER_SHAKE_MS)
        assertEquals(1600, RecreMotionDurations.OFFLINE_PULSE_MS)
        assertEquals(900, RecreMotionDurations.SYNC_SPIN_MS)
    }
}
```

- [ ] **Step 2: Ejecuta y verifica fallo** (`Unresolved reference: RecreMotionDurations`).

- [ ] **Step 3: Añade las constantes y el easing de marca a `Motion.kt`**

Añade al final de `Motion.kt` (fuera de cualquier clase existente):

```kotlin
import androidx.compose.animation.core.CubicBezierEasing

/** Duraciones de las animaciones de firma del producto (ms). Ver spec §5 M3. */
object RecreMotionDurations {
    const val COUNT_UP_MS = 600
    const val SUCCESS_FLASH_MS = 900
    const val DANGER_SHAKE_MS = 400
    const val OFFLINE_PULSE_MS = 1600
    const val SYNC_SPIN_MS = 900
    const val FAST_MS = 120
    const val DEFAULT_MS = 150
    const val SLOW_MS = 180
}

/** Curva de marca `cubic-bezier(0.2, 0, 0, 1)`. */
val RecreStandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
```

- [ ] **Step 4: Ejecuta y verifica que pasa** (PASS).

- [ ] **Step 5: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/theme/Motion.kt \
        android/app/src/test/java/com/recre/app/ui/theme/MotionTest.kt
git commit -m "feat(android): tokens de motion de firma (count-up/flash/shake/pulse/spin + easing) (rediseño F0)"
```

---

### Task 4: Guardarraíl anti-Material pelado (test JVM con allowlist)

Impide que `feature/**` use `Card`/`Button`/`OutlinedTextField`/`TopAppBar` de Material directamente. Como hoy TODAS las pantallas los usan, el test arranca con una **allowlist** de los ficheros aún sin migrar; cada fase irá vaciándola hasta que quede vacía (sistema 100% conectado).

**Files:**
- Test: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

- [ ] **Step 1: Escribe el test (arranca en VERDE con la allowlist completa)**

```kotlin
package com.recre.app.arch

import java.io.File
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guardarraíl: ninguna pantalla de `feature/**` debe instanciar Material 3 pelado
 * (Card/Button/OutlinedButton/TextButton/OutlinedTextField/TopAppBar). Se usan los
 * wrappers propios (AppCard/RecreButton/Field/SearchField/RecreShell).
 *
 * La ALLOWLIST contiene los ficheros aún NO migrados. Cada fase del rediseño retira
 * de aquí los ficheros que migra. Cuando quede vacía, el sistema está 100% conectado.
 */
class SinMaterialPeladoTest {

    private val prohibidos = listOf(
        "androidx.compose.material3.Card",
        "androidx.compose.material3.Button",
        "androidx.compose.material3.OutlinedButton",
        "androidx.compose.material3.TextButton",
        "androidx.compose.material3.OutlinedTextField",
        "androidx.compose.material3.TopAppBar",
    )

    // Rellénala ejecutando el test una vez en modo "reportar" (ver comentario abajo)
    // y pegando aquí cada ruta relativa que aparezca. Se va vaciando por fases.
    private val allowlist = setOf(
        // p. ej. "feature/locales/LocalesScreen.kt",
    )

    @Test
    fun `feature no usa Material pelado salvo allowlist`() {
        val featureDir = File("src/main/java/com/recre/app/feature")
        val infractores = featureDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val texto = f.readText()
                prohibidos.any { texto.contains("import $it") }
            }
            .map { it.relativeTo(featureDir.parentFile.parentFile.parentFile.parentFile.parentFile).path }
            .map { it.removePrefix("com/recre/app/").replace('\\', '/') }
            .filter { it !in allowlist }
            .toList()

        if (infractores.isNotEmpty()) {
            fail(
                "Pantallas con Material pelado (migra a wrappers propios o añade a allowlist " +
                    "si es de una fase posterior):\n" + infractores.joinToString("\n"),
            )
        }
    }
}
```

- [ ] **Step 2: Ejecuta el test para LISTAR los infractores actuales**

Run: `JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew -p android :app:testDebugUnitTest --tests "com.recre.app.arch.SinMaterialPeladoTest"`
Expected: FAIL con la lista de ficheros (todas las pantallas hoy).

- [ ] **Step 3: Pega esa lista en `allowlist`** (cada ruta tipo `feature/.../XxxScreen.kt`), para que el test quede VERDE como baseline.

- [ ] **Step 4: Ejecuta y verifica que pasa** (PASS). A partir de ahora, cualquier pantalla NUEVA con Material pelado rompe el build; cada fase retira de la allowlist lo que migra.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "test(android): guardarraíl anti-Material pelado en feature/ con allowlist por fases (rediseño F0)"
```

---

### Task 5: Componente `DottedDivider` (separador punteado del ticket)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/DottedDivider.kt`

> Componente puramente visual; se verifica por compilación + `@Preview`, no por unit test.

- [ ] **Step 1: Crea `DottedDivider.kt`**

```kotlin
package com.recre.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors

/** Separador de puntos, estética de recibo térmico (TicketRecibo). */
@Composable
fun DottedDivider(modifier: Modifier = Modifier) {
    val color = RecreColors.current.border
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
            cap = Stroke.DefaultCap,
        )
    }
}

@Preview
@Composable
private fun DottedDividerPreview() {
    DottedDivider()
}
```

> Verifica el nombre real del token de borde en `Color.kt` (`RecreColors.current.border`). Si el campo se llama distinto, ajústalo.

- [ ] **Step 2: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/DottedDivider.kt
git commit -m "feat(android): DottedDivider (separador punteado para el ticket) (rediseño F0)"
```

---

### Task 6: Dependencia y wrapper de Lottie

**Files:**
- Modify: `android/app/build.gradle.kts` (o `android/gradle/libs.versions.toml` si el proyecto usa version catalog — compruébalo primero)
- Create: `android/app/src/main/java/com/recre/app/ui/components/LottieIllustration.kt`

- [ ] **Step 1: Añade la dependencia**

Si hay `libs.versions.toml`, añade `lottie-compose = { module = "com.airbnb.android:lottie-compose", version = "6.4.0" }` y referencia `implementation(libs.lottie.compose)`. Si no, en `build.gradle.kts`: `implementation("com.airbnb.android:lottie-compose:6.4.0")`.

- [ ] **Step 2: Crea el wrapper `LottieIllustration.kt`**

```kotlin
package com.recre.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/** Ilustración animada propia (vacíos, onboarding, éxito). Asset JSON en res/raw. */
@Composable
fun LottieIllustration(
    rawRes: Int,
    modifier: Modifier = Modifier.size(160.dp),
    iterations: Int = LottieConstants.IterateForever,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(composition, iterations = iterations)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}
```

- [ ] **Step 3: Compila** → BUILD SUCCESSFUL (sin assets aún; se usarán en fases posteriores).

- [ ] **Step 4: Commit**

```bash
git add android/app/build.gradle.kts android/gradle/libs.versions.toml \
        android/app/src/main/java/com/recre/app/ui/components/LottieIllustration.kt
git commit -m "feat(android): integra Lottie (lottie-compose) + wrapper LottieIllustration (rediseño F0)"
```

---

### Task 7: Andamiaje de iconos propios (`RecreIcons`)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/icons/RecreIcons.kt`
- Create: 3–4 vector drawables iniciales en `android/app/src/main/res/drawable/` (p. ej. `ic_recaudar.xml`, `ic_averia.xml`, `ic_local.xml`, `ic_maquina.xml`) generados desde SVG con Android Studio (Vector Asset).

> Andamiaje: se crea el `object` + los primeros iconos de dominio; el set completo se amplía en cada fase al migrar sus pantallas.

- [ ] **Step 1: Genera 3–4 vector drawables** desde SVG propios (Android Studio → New → Vector Asset → Local file). Estilo: línea coherente, rejilla 24dp.

- [ ] **Step 2: Crea `RecreIcons.kt`**

```kotlin
package com.recre.app.ui.icons

import androidx.annotation.DrawableRes
import com.recre.app.R

/** Set de iconos propios de dominio. Sustituye los Icons.Filled.* de Material sueltos. */
object RecreIcons {
    @DrawableRes val Recaudar = R.drawable.ic_recaudar
    @DrawableRes val Averia = R.drawable.ic_averia
    @DrawableRes val Local = R.drawable.ic_local
    @DrawableRes val Maquina = R.drawable.ic_maquina
}
```

- [ ] **Step 3: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/icons/RecreIcons.kt \
        android/app/src/main/res/drawable/ic_*.xml
git commit -m "feat(android): andamiaje RecreIcons + primeros iconos de dominio (rediseño F0)"
```

---

### Task 8: `CountUpText` (cifra que cuenta)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/CountUpText.kt`
- Test: `android/app/src/test/java/com/recre/app/ui/components/CountUpFormatTest.kt`

> La animación es de Compose (no unit-testeable directamente); SÍ se testea el formateo es-ES de la cifra, que es la parte con lógica.

- [ ] **Step 1: Escribe el test que falla**

```kotlin
package com.recre.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CountUpFormatTest {
    @Test
    fun `formatea euros es-ES con dos decimales`() {
        assertEquals("1.200,00", formatearImporteEs("1200.00"))
        assertEquals("0,00", formatearImporteEs("0"))
        assertEquals("1.234,56", formatearImporteEs("1234.56"))
    }
}
```

- [ ] **Step 2: Ejecuta y verifica fallo** (`Unresolved reference: formatearImporteEs`).

- [ ] **Step 3: Crea `CountUpText.kt`** (composable + el formateador puro)

```kotlin
package com.recre.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.recre.app.ui.theme.RecreMotionDurations
import com.recre.app.ui.theme.RecreStandardEasing

private val ES = Locale("es", "ES")

/** Formatea un importe (String exacto) como "1.234,56" en es-ES. */
fun formatearImporteEs(importe: String): String {
    val df = DecimalFormat("#,##0.00", DecimalFormatSymbols(ES))
    return df.format(BigDecimal(importe))
}

/** Texto de importe que "cuenta" hasta su valor al cambiar (motion de firma, 600ms). */
@Composable
fun CountUpText(
    importe: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val objetivo = BigDecimal(importe).toFloat()
    val animado by animateFloatAsState(
        targetValue = objetivo,
        animationSpec = tween(RecreMotionDurations.COUNT_UP_MS, easing = RecreStandardEasing),
        label = "countup",
    )
    MoneyText(
        amount = formatearImporteEs(animado.toBigDecimal().toPlainString()),
        style = style,
        modifier = modifier,
    )
}
```

> `MoneyText` ya existe en `ui/components/MoneyText.kt`. ABRE su firma real y ajusta los nombres de parámetro (`amount`/`style`) a los que tenga; aquí se asume `MoneyText(amount: String, style: TextStyle, modifier)`.

- [ ] **Step 4: Ejecuta y verifica que pasa** (PASS, 1 test).

- [ ] **Step 5: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/CountUpText.kt \
        android/app/src/test/java/com/recre/app/ui/components/CountUpFormatTest.kt
git commit -m "feat(android): CountUpText + formateador es-ES (motion de firma) (rediseño F0)"
```

---

### Task 9: Reconexión de un wrapper de feature como prueba de patrón (`LocalCard` → `AppCard`)

Demuestra el camino que seguirán las fases: que los wrappers de feature se apoyen en los componentes propios. Se hace con UNO (`LocalCard`) como referencia.

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/components/LocalCard.kt`

- [ ] **Step 1: Lee `LocalCard.kt` y `ui/components/AppCard.kt`** para conocer sus firmas reales.

- [ ] **Step 2: Sustituye el `Card` de Material por `AppCard`** en `LocalCard`, conservando el contenido y los callbacks. (El cambio exacto depende de la firma de `AppCard`; mantén el mismo padding/onClick.)

- [ ] **Step 3: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 4: Verifica que el guardarraíl (Task 4) sigue verde** y retira `feature/locales/components/LocalCard.kt` de la allowlist si estaba.

Run: `...testDebugUnitTest --tests "com.recre.app.arch.SinMaterialPeladoTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/locales/components/LocalCard.kt \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "refactor(android): LocalCard se apoya en AppCard (prueba de patrón de reconexión) (rediseño F0)"
```

---

### Task 10: Galería de `@Preview` (catálogo del sistema)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/catalog/CatalogoPreviews.kt`

> Documentación viva: `@Preview` de los componentes clave en claro y oscuro. No entra en producción (es para Android Studio).

- [ ] **Step 1: Crea `CatalogoPreviews.kt`** con `@Preview` (uiMode claro y oscuro) envolviendo en `RecreTheme` los componentes ya disponibles: `DottedDivider`, `CountUpText`, y los existentes `StatusChip`, `RecreButton`, `AppCard`, `Field`, `MoneyText`. Ejemplo:

```kotlin
package com.recre.app.ui.catalog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.DottedDivider
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType

@Preview(name = "Catálogo claro")
@Preview(name = "Catálogo oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CatalogoPreview() {
    RecreTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CountUpText(importe = "1200.00", style = RecreType.importe)
            DottedDivider()
            // + StatusChip, RecreButton, AppCard, Field, MoneyText con sus firmas reales
        }
    }
}
```

> Ajusta `RecreType.importe` y las firmas de los componentes existentes a las reales (lee `Type.kt` y cada componente).

- [ ] **Step 2: Compila** → BUILD SUCCESSFUL (verifica también que las previews renderizan en Android Studio).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/catalog/CatalogoPreviews.kt
git commit -m "docs(android): galería @Preview del sistema (claro+oscuro) (rediseño F0)"
```

---

### Task 11: Guía de voz y microcopy (documento)

**Files:**
- Create: `docs/superpowers/specs/2026-06-17-voz-y-microcopy.md`

> Artefacto de texto (no código): la voz de marca + reglas para reescribir errores/vacíos/botones. Lo consumen todas las fases al escribir copy.

- [ ] **Step 1: Escribe la guía** con: principios de voz (calmada, precisa, español llano, sin tecnicismos al técnico), tabla do/don't (p. ej. ❌ "Error 422: insufficient_funds" → ✅ "La caja no llega para cubrir la tasa. Revísalo."), y plantillas para los 7 estados (cargando, vacío, error de red, éxito, confirmación, descuadre, offline).

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-06-17-voz-y-microcopy.md
git commit -m "docs(spec): guía de voz y microcopy (rediseño F0)"
```

---

### Task 12: Base del patrón offline/sync (P8) — `OfflineBanner` + estados de `StatusChip`

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/OfflineBanner.kt`
- Modify: `android/app/src/main/java/com/recre/app/ui/components/StatusChip.kt` (añadir, si no existen, los estados `Pendiente`/`Subiendo`/`Sincronizado`/`Conflicto`)

> ABRE primero `StatusChip.kt` y `OfflineBadge.kt`/`SyncControl.kt` para reutilizar sus colores semánticos (`RecreColors.current.warning/info/success/danger`).

- [ ] **Step 1: Crea `OfflineBanner.kt`** (banner discreto "Sin conexión — se subirá al volver la red"), usando `RecreColors.current.warning` y `PillShape`/`RecreShapes.small`. Visible solo cuando `offline == true`.

```kotlin
package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors

/** Banner global discreto de "sin conexión" (P8). Mostrar solo si [visible]. */
@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val colors = RecreColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.warningContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.offline_banner))
    }
}
```

> Verifica el nombre real del token (`warningContainer` o equivalente en `Color.kt`) y añade el string `offline_banner` en `res/values/strings.xml` ("Sin conexión · se subirá al recuperar la red").

- [ ] **Step 2: Asegura los estados de sync en `StatusChip`** (pendiente=warning, subiendo=info, sincronizado=success, conflicto=danger), cada uno con icono+texto (nunca solo color).

- [ ] **Step 3: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/OfflineBanner.kt \
        android/app/src/main/java/com/recre/app/ui/components/StatusChip.kt \
        android/app/src/main/res/values/strings.xml
git commit -m "feat(android): base del patrón offline/sync — OfflineBanner + estados de StatusChip (P8) (rediseño F0)"
```

---

## Cierre de Fase 0

**Diferido a fases posteriores (aplicado por pantalla, no en F0):** el cableado de `SharedTransition` (transiciones de pantalla, Fase 1+), la accesibilidad real (labels TalkBack, escalado de fuente, targets — se valida pantalla a pantalla con la suite de cada fase), y la ampliación del set `RecreIcons` y los assets Lottie (se añaden al migrar cada pantalla).


- [ ] Ejecuta toda la suite de tests de tema/arquitectura:
  `JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew -p android :app:testDebugUnitTest --tests "com.recre.app.ui.theme.*" --tests "com.recre.app.ui.components.*" --tests "com.recre.app.arch.*"` → todo PASS.
- [ ] `assembleDebug` BUILD SUCCESSFUL.
- [ ] Abre un PR; el usuario instala el APK para QA (firma distinta). No debería verse cambio grande aún (coherencia de esquinas/espaciado); la base queda lista para la Fase 1.

**Salida:** tokens de forma/espaciado/motion cableados, guardarraíl activo, Lottie y `RecreIcons` listos, `DottedDivider`/`CountUpText` creados, y el patrón de reconexión probado con `LocalCard`. Las fases 1–5 construyen sobre esto.
