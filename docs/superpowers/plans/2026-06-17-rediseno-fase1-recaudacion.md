# Rediseño UI — Fase 1: Flujo de recaudación · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) o superpowers:executing-plans para implementar este plan tarea a tarea. Los pasos usan checkbox (`- [ ]`).

**Goal:** Re-skinear el flujo de recaudación (Contadores → Denominaciones×2 → Confirmación) con la identidad "Confianza Industrial", sin tocar el cálculo económico (SSOT servidor) ni la navegación de cadena.

**Architecture:** Solo cambia la **capa de presentación** de `feature/recaudacion/`. El `RecaudacionFlowViewModel` y `RecaudacionFlowState` NO se modifican (denominaciones siguen siendo `Map<String,Int>`, cifras siguen en `Cifras?` BigDecimal). Se reusan los fundamentos de Fase 0 (`CountUpText`, `RecreShapes`, `PillShape`, `RecreSpacing`, `RecreMotionDurations`, `RecreStandardEasing`) y los átomos ya existentes (`Keypad`, `MoneyText`, `AppCard`, `RecreButton`/`RecrePrimaryButton`, `Field`/`FieldNum`, `StepIndicator`, `StatusChip`, `RecreSnackbar`, `SignaturePad`). Cada pantalla migrada SALE de la allowlist del guardarraíl (`SinMaterialPeladoTest`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, JUnit4. Build con Gradle (JDK de Android Studio).

**Spec referencia:** `docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md` — §4 (P1/P2/P3/P5/P6/P7/P8), §5 (M1/M2/M3/M5), §6.1 (flujo, **diseño bloqueado**), §6.2 (LocalDetalle "Recaudar todas").

## Invariantes (no negociables en TODA la fase)

- **Money-safe:** las denominaciones son `Map<String,Int>` (clave `"0.10"`…`"50.00"`, valor = nº de piezas). El total se obtiene SIEMPRE de `viewModel.sumarDesgloseDe(map): BigDecimal`. Las cifras económicas (`Cifras`) son BigDecimal y vienen del ViewModel (espejo del servidor). **Jamás** `Float`/`Double` para dinero; **jamás** recalcular el reparto en la UI.
- **No tocar el ViewModel ni el state** salvo que un paso lo diga explícitamente (ninguno lo hace en Fase 1). El reskin es de Composables.
- **Conservar `testTag`s** (`RecaudacionTestTags.*`): hay tests instrumentados que dependen de ellos. Si mueves un nodo, el testTag viaja con él.
- **Navegación de cadena intacta:** `cadenaLocalId`, `CadenaState`, `saltarOTerminarCadena` y las rutas de `MainActivity.recaudacionGraph` no cambian de semántica.
- **Feedback nunca solo color:** todo estado lleva icono+texto además del color (P8/M6). `reduce-motion` respetado (las animaciones degradan a fade/instantáneo).

## Comandos de build

- **Compilar:** `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew -p android :app:assembleDebug` (o desde `android/`).
- **Unit test (paquete):** `cd android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "<patrón>"`.
- **Guardarraíl:** `--tests "com.recre.app.arch.SinMaterialPeladoTest"`.
- Builds largos: lanzarlos en segundo plano / sandbox (cierran el MCP si bloquean).

---

## File Structure

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `feature/recaudacion/denominaciones/DenominacionCard.kt` | Tarjeta compacta de denominación + chip flotante + ring seleccionado | **Crear** |
| `feature/recaudacion/denominaciones/DenominacionFormato.kt` | `etiquetaFacialDenominacion(key)` (formateo puro testeable) | **Crear** |
| `feature/recaudacion/denominaciones/DenominacionesScreen.kt` | Rejilla 3×3 + héroe sticky con count-up | **Modificar** |
| `ui/components/Feedback.kt` | `Modifier.successFlash(...)` y `Modifier.dangerShake(...)` (M3) | **Crear** |
| `feature/recaudacion/contadores/ContadoresScreen.kt` | Contadores con `Keypad` (P6), sin IME | **Modificar** |
| `feature/recaudacion/confirmacion/ConfirmacionScreen.kt` | Neto como héroe (count-up) + desglose limpio + CTA único | **Modificar** |
| `feature/recaudacion/components/CifrasResumenCard.kt` | Desglose sobre `AppCard` (no `Card` M3 gris) | **Modificar** |
| `feature/recaudacion/components/BaselineCambiadaDialog.kt` | Migrar a estilo propio (sale de allowlist) | **Modificar** |
| `feature/locales/LocalDetalleScreen.kt` | "Recaudar todas" solo si `instaladas.size > 1` | **Modificar** |
| `MainActivity.kt` (`recaudacionGraph`) | Cablear `StepIndicator` (M1) y chrome de cada paso | **Modificar** |
| `app/src/test/.../arch/SinMaterialPeladoTest.kt` | Retirar pantallas migradas de la allowlist | **Modificar** |
| Tests nuevos en `app/src/test/.../feature/recaudacion/` y `feature/locales/` | TDD del formateo y de la condición de "Recaudar todas" | **Crear** |

---

### Task 1: Formato facial de denominación (función pura, TDD)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionFormato.kt`
- Test: `android/app/src/test/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionFormatoTest.kt`

- [ ] **Step 1: Escribe el test que falla**

```kotlin
package com.recre.app.feature.recaudacion.denominaciones

import org.junit.Assert.assertEquals
import org.junit.Test

class DenominacionFormatoTest {
    @Test
    fun etiqueta_facial_monedas_y_billetes() {
        // Sub-euro: con decimales en coma. Euros enteros: sin ",00".
        assertEquals("0,10 €", etiquetaFacialDenominacion("0.10"))
        assertEquals("0,20 €", etiquetaFacialDenominacion("0.20"))
        assertEquals("0,50 €", etiquetaFacialDenominacion("0.50"))
        assertEquals("1 €", etiquetaFacialDenominacion("1.00"))
        assertEquals("2 €", etiquetaFacialDenominacion("2.00"))
        assertEquals("5 €", etiquetaFacialDenominacion("5.00"))
        assertEquals("50 €", etiquetaFacialDenominacion("50.00"))
    }
}
```

- [ ] **Step 2: Ejecuta y verifica fallo** (`Unresolved reference: etiquetaFacialDenominacion`).

Run: `... :app:testDebugUnitTest --tests "com.recre.app.feature.recaudacion.denominaciones.DenominacionFormatoTest"`

- [ ] **Step 3: Implementa el formateador**

```kotlin
package com.recre.app.feature.recaudacion.denominaciones

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val ES =
    DecimalFormatSymbols(Locale.forLanguageTag("es-ES")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

/**
 * Etiqueta del VALOR FACIAL de una denominación a partir de su clave money-safe
 * (`"0.10"`, `"1.00"`, `"50.00"`). Euros enteros sin decimales ("1 €", "50 €"),
 * sub-euro con coma ("0,10 €"). Sólo presentación: NO interviene en el cálculo.
 */
fun etiquetaFacialDenominacion(key: String): String {
    val valor = BigDecimal(key)
    val esEntero = valor.stripTrailingZeros().scale() <= 0
    val patron = if (esEntero) "#,##0" else "#,##0.00"
    return DecimalFormat(patron, ES).format(valor) + " €"
}
```

- [ ] **Step 4: Ejecuta y verifica que pasa** (PASS, 1 test).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionFormato.kt \
        android/app/src/test/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionFormatoTest.kt
git commit -m "feat(android): etiqueta facial de denominación (formateo es-ES) (rediseño F1)"
```

---

### Task 2: `DenominacionCard` — tarjeta compacta + chip flotante straddling

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionCard.kt`

> Construida SOBRE `AppCard` (overload clickable con `selected`): da el borde petróleo al seleccionar sin reintroducir `Card` pelado. El chip de cantidad flota en la esquina superior derecha **sobresaliendo media altura** (straddle), aparece con "pop" (scale+fade) y sólo si `cantidad > 0` (M2).

- [ ] **Step 1: Crea `DenominacionCard.kt`**

```kotlin
package com.recre.app.feature.recaudacion.denominaciones

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.theme.PillShape
import com.recre.app.ui.theme.RecreType

/**
 * Tarjeta compacta de una denominación (R3 del flujo). Sólo el valor facial,
 * centrado, en Geist Mono tabular. Seleccionada → borde petróleo (vía AppCard
 * `selected`). El [cantidad] aparece como chip flotante straddling el borde
 * superior-derecho, con "pop" al pasar de 0 (M2). Toda la tarjeta es un destino
 * tappable que activa la fila para el keypad.
 */
@Composable
fun DenominacionCard(
    etiqueta: String,
    cantidad: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AppCard(
            onClick = onSelect,
            selected = selected,
            contentDescription = "$etiqueta, $cantidad unidades",
            contentPadding = PaddingValues(vertical = 22.dp, horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = etiqueta,
                    style = RecreType.cifra,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        AnimatedVisibility(
            visible = cantidad > 0,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-10).dp) // straddle: sobresale ~media altura
                    .clearAndSetSemantics {}, // la cantidad ya va en el contentDescription
        ) {
            Text(
                text = cantidad.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, PillShape) // fondo píldora
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
```

> NOTA: `background(color, shape)` pinta y recorta la píldora en un paso (no hace falta `clip` aparte). Importa `androidx.compose.foundation.background`; puedes quitar el import `androidx.compose.ui.draw.clip` si tu IDE lo marca sin usar. El @Preview del catálogo (Fase 0) puede ampliarse luego; aquí no es obligatorio.

- [ ] **Step 2: Añade el import que falta**: `import androidx.compose.foundation.background`.

- [ ] **Step 3: Compila** → BUILD SUCCESSFUL.

Run: `... :app:assembleDebug`

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionCard.kt
git commit -m "feat(android): DenominacionCard (tarjeta compacta + chip flotante) (rediseño F1)"
```

---

### Task 3: Modificadores de feedback `successFlash` / `dangerShake` (M3)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/Feedback.kt`

> Dos `Modifier` reutilizables: destello verde al cuadrar/guardar (900 ms) y vibración horizontal en descuadre/validación (400 ms). Respetan `reduce-motion` (si está activo, no animan). Disparados por una clave booleana/trigger.

- [ ] **Step 1: Crea `Feedback.kt`**

```kotlin
package com.recre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreMotionDurations
import com.recre.app.ui.theme.RecreStandardEasing

/**
 * Destello de éxito (M3): superpone un velo verde que sube y se desvanece en
 * [RecreMotionDurations.SUCCESS_FLASH_MS] cuando [trigger] cambia a un valor nuevo.
 * El color va SIEMPRE acompañado de texto/icono en el llamador (no es feedback-solo-color).
 */
fun Modifier.successFlash(trigger: Any?): Modifier =
    composed {
        val alpha = remember { Animatable(0f) }
        val color = RecreColors.current.success
        LaunchedEffect(trigger) {
            if (trigger == null) return@LaunchedEffect
            alpha.snapTo(0.32f)
            alpha.animateTo(0f, tween(RecreMotionDurations.SUCCESS_FLASH_MS, easing = RecreStandardEasing))
        }
        drawWithContent {
            drawContent()
            if (alpha.value > 0f) drawRect(color = color.copy(alpha = alpha.value), size = size)
        }
    }

/**
 * Vibración de error (M3): sacude horizontalmente en [RecreMotionDurations.DANGER_SHAKE_MS]
 * cuando [trigger] cambia. Pensado para campos/tarjetas en descuadre o validación fallida.
 */
fun Modifier.dangerShake(trigger: Any?): Modifier =
    composed {
        val offsetX = remember { Animatable(0f) }
        LaunchedEffect(trigger) {
            if (trigger == null) return@LaunchedEffect
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec =
                    keyframes {
                        durationMillis = RecreMotionDurations.DANGER_SHAKE_MS
                        0f at 0
                        -10f at 60
                        10f at 140
                        -6f at 220
                        6f at 300
                        0f at RecreMotionDurations.DANGER_SHAKE_MS
                    },
            )
        }
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(offsetX.value.toInt(), 0)
            }
        }
    }
```

> Si el proyecto expone un flag de reduce-motion en el tema, envuelve las animaciones en `if (!reduceMotion)`. Si no, déjalo: el SO desactiva animaciones a nivel de `animationScale` y Compose lo respeta en `Animatable`/`tween`. Quita el import `LinearEasing`/`background`/`Color` si tu IDE los marca sin usar.

- [ ] **Step 2: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/Feedback.kt
git commit -m "feat(android): modificadores successFlash/dangerShake (M3) (rediseño F1)"
```

---

### Task 4: `DenominacionesScreen` — rejilla 3×3 + héroe con count-up

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionesScreen.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> **Diseño bloqueado (§6.1).** Sustituir la lista de filas (`DenominacionRow`) por una **rejilla 3×3** de `DenominacionCard` en orden ascendente (monedas arriba, billetes abajo), SIN scroll y SIN etiquetas de grupo. El `Keypad` sigue anclado abajo, siempre visible. La cabecera sticky (`BloqueProgreso`) muestra el héroe Contado con **`CountUpText`** + barra de progreso + `EstadoChip` (cuadra/faltan/sobran) con **`successFlash`** al cuadrar. Conserva el `activeKey`, el cableado del keypad y todos los `testTag`.

- [ ] **Step 1: Lee** `DenominacionesScreen.kt` completo y localiza: (a) la construcción de items/filas (`construirItems`, `DenominacionRow`, ~L300-380, L500-542), (b) `BloqueProgreso` (~L410-496) con el `MoneyText(Hero)` (L450) y `EstadoChip` (L470-496), (c) el `Keypad` (L260-279), (d) `activeKey`/`cambiarCantidad` (L129-139).

- [ ] **Step 2: Reemplaza la lista por la rejilla 3×3.** En el cuerpo central (entre la cabecera sticky y el keypad) pon una rejilla NO scrollable: 3 filas de 3 `DenominacionCard`, en el orden de `DENOMINACIONES_PERMITIDAS` (`core/calculo/Denominaciones.kt`: `0.10,0.20,0.50,1.00,2.00,5.00,10.00,20.00,50.00`). Patrón:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.recre.app.core.calculo.DENOMINACIONES_PERMITIDAS
import com.recre.app.ui.theme.RecreSpacing
// ...
@Composable
private fun RejillaDenominaciones(
    cantidades: Map<String, Int>, // map del modo activo (Total o Local)
    activeKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val claves = DENOMINACIONES_PERMITIDAS.map { it.setScale(2).toPlainString() } // "0.10".."50.00"
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RecreSpacing.md),
    ) {
        claves.chunked(3).forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RecreSpacing.md),
            ) {
                fila.forEach { key ->
                    DenominacionCard(
                        etiqueta = etiquetaFacialDenominacion(key),
                        cantidad = cantidades[key] ?: 0,
                        selected = key == activeKey,
                        onSelect = { onSelect(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
```

> El mapa de cantidades del modo activo ya lo tienes (Total → `state.denominacionesTotal`, Local → `state.denominacionesLocal`); pásalo. `onSelect(key)` debe fijar `activeKey = key` (la fila activa que dirige el keypad), igual que hacía el tap en `DenominacionRow`. Reusa `cambiarCantidad`/`onDenominacion*Change` SIN cambios. Coloca `RejillaDenominaciones` dentro de un contenedor con `Modifier.weight(1f)` entre cabecera y keypad para que NO scrollee y entren las 9.

- [ ] **Step 3: Héroe con count-up.** En `BloqueProgreso`, sustituye el `MoneyText(amount = total, size = Hero)` (L450) por:

```kotlin
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.MoneyTextSize
// El total es BigDecimal de viewModel.sumarDesgloseDe(map); CountUpText toma String exacto:
CountUpText(
    importe = total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
    size = MoneyTextSize.Hero,
)
```

Mantén el objetivo (`target`) y la barra de progreso como están (sólo presentación). Conserva el `MoneyText(Medium)` del objetivo si lo prefieres.

- [ ] **Step 4: Success-flash al cuadrar.** Aplica `Modifier.successFlash(trigger = cuadra)` al contenedor de la cabecera (o a `EstadoChip`), donde `cuadra` es el booleano de estado "cuadra" que ya calcula `EstadoChip` (derívalo: `total.compareTo(target) == 0`). El `EstadoChip` sigue mostrando icono+texto+color (nunca solo color).

- [ ] **Step 5: Migra los átomos restantes de la pantalla a la librería.** Si la pantalla usa `Button`/`OutlinedTextField`/`Card`/`TopAppBar` de M3, cámbialos por `RecrePrimaryButton`/`Field`/`AppCard`/(chrome P1 en Task 7). Quita los imports prohibidos. (El keypad y `MoneyText` ya son propios.)

- [ ] **Step 6: Compila** → BUILD SUCCESSFUL. Revisa visualmente en @Preview que las 9 tarjetas entran sin scroll.

- [ ] **Step 7: Retira la pantalla de la allowlist** del guardarraíl (si ya no importa Material pelado): borra la línea `"feature/recaudacion/denominaciones/DenominacionesScreen.kt"` de `SinMaterialPeladoTest.kt` y corre el guardarraíl → PASS.

Run: `... :app:testDebugUnitTest --tests "com.recre.app.arch.SinMaterialPeladoTest"`

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/recaudacion/denominaciones/DenominacionesScreen.kt \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): denominaciones en rejilla 3x3 con héroe count-up (rediseño F1)"
```

---

### Task 5: `ContadoresScreen` — entrada con `Keypad` (P6), sin IME

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/contadores/ContadoresScreen.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> **§6.1:** unifica el lenguaje numérico con Denominaciones. La lectura se muestra **en grande (mono)** y se introduce con el `Keypad` propio, **una a la vez** (entradas / salidas), sin `OutlinedTextField`/IME. La baseline (última lectura) como hint. El OCR (`ContadorOcrBoton` + escáner) se conserva.

- [ ] **Step 1: Lee** `ContadoresScreen.kt`: `CamposContadores` (L229-312, los dos `OutlinedTextField` L269/L291), el wiring `onEntradasChange`/`onSalidasChange` (L151-160), `BaselineHint` (L215-227), el botón OCR (L253-256) y `testTag`s.

- [ ] **Step 2: Sustituye los dos `OutlinedTextField` por dos celdas tappables + `Keypad`.** Modelo idéntico a Denominaciones: un `activeCampo` (`Entradas`/`Salidas`) selecciona qué lectura recibe los dígitos; cada celda muestra el valor actual en `RecreType.cifra`/`importeMedium` y, si está activa, borde petróleo (envuélvela en `AppCard(selected = ...)`). El `Keypad` (anclado abajo) enruta:

```kotlin
Keypad(
    onDigit = { d ->
        when (activeCampo) {
            Campo.Entradas -> viewModel.onContadorEntradasChange((state.contadorEntradasInput + d).take(MAX))
            Campo.Salidas  -> viewModel.onContadorSalidasChange((state.contadorSalidasInput + d).take(MAX))
        }
    },
    onBackspace = {
        when (activeCampo) {
            Campo.Entradas -> viewModel.onContadorEntradasChange(state.contadorEntradasInput.dropLast(1))
            Campo.Salidas  -> viewModel.onContadorSalidasChange(state.contadorSalidasInput.dropLast(1))
        }
    },
    onNext = { activeCampo = if (activeCampo == Campo.Entradas) Campo.Salidas else Campo.Entradas },
    nextLabel = stringResource(...),
    backspaceContentDescription = stringResource(...),
    nextContentDescription = stringResource(...),
)
```

> `MAX` = `RecaudacionFlowViewModel.MAX_CONTADOR_DIGITS` (12). El ViewModel ya valida `< baseline` y deriva `Cifras`; NO dupliques validación de negocio aquí. Conserva el error inline "no puede ser menor que la última lectura" (string `recaudacion_error_contador_menor`) mostrándolo bajo la celda activa. Mantén `BaselineHint` y `CifrasResumenCard`. Aplica `Modifier.dangerShake(trigger = ...)` a la celda cuando el valor cae por debajo de baseline (opcional pero deseado, M3).

- [ ] **Step 3: Cabecera P1 + CTA único.** Deja el `RecrePrimaryButton` "Continuar" como único CTA (Task 7 unifica el chrome). Conserva el botón OCR (migra `ContadorOcrBoton` a `RecreButton`/`IconAction` si usa `Button` M3).

- [ ] **Step 4: Compila** → BUILD SUCCESSFUL. Verifica que no queda `OutlinedTextField`/IME en la pantalla.

- [ ] **Step 5: Retira `ContadoresScreen.kt` (y `ContadorOcrCapture.kt`/`EscanerContadoresScreen.kt` si ya no importan Material pelado) de la allowlist.** Guardarraíl → PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/recaudacion/contadores/ \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): contadores con Keypad propio sin IME (P6) (rediseño F1)"
```

---

### Task 6: `ConfirmacionScreen` — neto como héroe (count-up) + desglose limpio

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/confirmacion/ConfirmacionScreen.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/components/CifrasResumenCard.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> **§6.1 (P2):** el **neto es el héroe** (cifra grande mono con **count-up** al responder el servidor). Debajo, desglose limpio (no 3 cards grises): migra `CifrasResumenCard` a `AppCard` con filas `FilaCifra` sobre `RecreDivider`. `SignaturePad` con esquina `RecreShapes.medium`. **CTA único** `RecrePrimaryButton` "Guardar y firmar" (mantén las condiciones de habilitado: firma no vacía && !guardando && !syncStale && !baselineCambiada). Snackbar de resultado con `RecreSnackbar` (M5).

- [ ] **Step 1: Lee** `ConfirmacionScreen.kt` (`FormularioBlock` L127, `CifrasResumenCard` L143, `SignaturePad` L172-176, botón Guardar L200-201, `PostGuardadoBlock` L232) y `CifrasResumenCard.kt` (la `Card` M3 en L41, `FilaCifra` L163, `formatEur` L196-200).

- [ ] **Step 2: Héroe neto con count-up.** Encima del desglose, añade el bloque héroe con el neto:

```kotlin
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.MoneyTextSize
val neto = state.cifras?.neto
if (neto != null) {
    CountUpText(
        importe = neto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
        size = MoneyTextSize.Hero,
    )
}
```

(El count-up arranca al recomponerse con el neto del servidor/espejo; `CountUpText` re-cuenta al cambiar el importe.)

- [ ] **Step 3: `CifrasResumenCard` → `AppCard`.** Sustituye la `Card`/`CardDefaults` de M3 por `AppCard` (decorativo). Conserva `FilaCifra` y `formatEur` (money-safe). Separa filas con `RecreDivider`. Si `!cifras.procede`, marca el bloque con rol warning (texto+icono, no solo color). Quita imports `Card`/`CardDefaults`.

- [ ] **Step 4: `SignaturePad` con esquina de marca.** Pásale `modifier = Modifier.clip(RecreShapes.medium)` (o el equivalente con `shape`) y mantén el resto.

- [ ] **Step 5: CTA + snackbar.** Botón Guardar → `RecrePrimaryButton` (si era `Button` M3). En el guardado correcto, muestra `RecreSnackbar` semántico (success) "Recaudación guardada"; en error, variante danger. (Reusa el snackbar host de la pantalla/shell.)

- [ ] **Step 6: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 7: Retira `ConfirmacionScreen.kt` y `components/CifrasResumenCard.kt` (y `RecuperacionResumenCard.kt` si aplica) de la allowlist.** Guardarraíl → PASS.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/recaudacion/confirmacion/ \
        android/app/src/main/java/com/recre/app/feature/recaudacion/components/ \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): confirmación con neto-héroe count-up y desglose limpio (P2) (rediseño F1)"
```

---

### Task 7: `StepIndicator` (M1) + chrome P1 en los pasos del flujo

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/MainActivity.kt` (`recaudacionGraph` L548-611)
- Modify: las tres screens del flujo (cabecera)

> El flujo NO usa `StepIndicator` hoy (el progreso va sólo en el título). Cablea `StepIndicator` (Contadores 1 · Denominaciones 2 · Confirmación 3) que **se anima paso a paso** (M1), y unifica la cabecera con el chrome propio (P1: título + back, no `TopAppBar` gris). La cadena ("Máquina X de N") se mantiene como subtítulo.

- [ ] **Step 1: Lee** `StepIndicator.kt` (`ui/components/`) para su firma real (nº de pasos, paso actual, labels) y cómo lo usan `InstalacionFormScreen`/`MaquinaFormScreen`.

- [ ] **Step 2: Mapea paso→índice.** Contadores=1, Denominaciones(Total y Local)=2, Confirmación=3 (las dos sub-pantallas de denominaciones comparten el paso 2; muéstralo así o como 2a/2b según permita `StepIndicator`). Pásalo a cada screen y renderízalo bajo la cabecera P1.

- [ ] **Step 3: Chrome P1.** Si las screens montan `TopAppBar` M3, sustitúyelo por el chrome propio (cabecera de `RecreShell` en modo secundario: título + back). Conserva el subtítulo de cadena.

- [ ] **Step 4: Compila** → BUILD SUCCESSFUL. Comprueba que el indicador avanza al navegar entre pasos.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/recre/app/MainActivity.kt \
        android/app/src/main/java/com/recre/app/feature/recaudacion/
git commit -m "feat(android): StepIndicator animado + chrome P1 en el flujo de recaudación (M1) (rediseño F1)"
```

---

### Task 8: `LocalDetalle` — "Recaudar todas" solo con ≥2 instaladas (TDD)

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/LocalDetalleScreen.kt` (L182)
- Create: `android/app/src/test/java/com/recre/app/feature/locales/RecaudarTodasVisibleTest.kt`

> **§6.2:** con una sola máquina el botón es redundante (basta su Recaudar) y el modo cadena no aporta ("1/1"). Verificado en el análisis: la ruta *single* cubre el caso; ocultarlo NO rompe la cadena.

- [ ] **Step 1: Extrae la condición a una función pura testeable.** En `LocalDetalleScreen.kt`, junto a la pantalla, añade:

```kotlin
/** "Recaudar todas" (modo cadena) sólo tiene sentido con 2+ máquinas instaladas. */
internal fun mostrarRecaudarTodas(maquinasInstaladas: Int): Boolean = maquinasInstaladas > 1
```

- [ ] **Step 2: Escribe el test que falla**

```kotlin
package com.recre.app.feature.locales

import org.junit.Assert.assertEquals
import org.junit.Test

class RecaudarTodasVisibleTest {
    @Test
    fun solo_visible_con_dos_o_mas_instaladas() {
        assertEquals(false, mostrarRecaudarTodas(0))
        assertEquals(false, mostrarRecaudarTodas(1))
        assertEquals(true, mostrarRecaudarTodas(2))
        assertEquals(true, mostrarRecaudarTodas(5))
    }
}
```

- [ ] **Step 3: Ejecuta y verifica fallo**, luego usa la función en la condición real. Cambia L182 `if (instaladas.isNotEmpty()) {` por `if (mostrarRecaudarTodas(instaladas.size)) {`.

- [ ] **Step 4: Ejecuta y verifica que pasa.**

Run: `... :app:testDebugUnitTest --tests "com.recre.app.feature.locales.RecaudarTodasVisibleTest"`

- [ ] **Step 5: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/locales/LocalDetalleScreen.kt \
        android/app/src/test/java/com/recre/app/feature/locales/RecaudarTodasVisibleTest.kt
git commit -m "feat(android): Recaudar todas solo con 2+ maquinas instaladas (rediseño F1)"
```

---

### Task 9: `BaselineCambiadaDialog` y remate del guardarraíl del flujo

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/recaudacion/components/BaselineCambiadaDialog.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> Migra el `AlertDialog` (si usa botones `TextButton`/`Button` M3) a botones propios y revisa que no queden imports prohibidos. Al terminar la fase, la allowlist debe haber perdido TODAS las entradas de `feature/recaudacion/...` que se hayan migrado.

- [ ] **Step 1: Lee** `BaselineCambiadaDialog.kt` (L25-58). Sustituye `TextButton`/`Button` por `RecreTextButton`/`RecrePrimaryButton`; conserva el `AlertDialog` (no está en la lista de prohibidos) y los strings.

- [ ] **Step 2: Compila** → BUILD SUCCESSFUL.

- [ ] **Step 3: Retira de la allowlist** todas las entradas `feature/recaudacion/...` ya migradas; ejecuta el guardarraíl → PASS. Lo que NO se haya migrado en Fase 1 (p. ej. `ContadorOcrCapture`/`EscanerContadoresScreen` si quedan con Material pelado) se DEJA en la allowlist con un comentario "pendiente F1.x".

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/recaudacion/components/BaselineCambiadaDialog.kt \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "refactor(android): BaselineCambiadaDialog con botones propios + allowlist al día (rediseño F1)"
```

---

## Cierre de Fase 1

- [ ] **Suite completa:** `... :app:testDebugUnitTest --tests "com.recre.app.feature.*" --tests "com.recre.app.ui.*" --tests "com.recre.app.arch.*"` → todo PASS.
- [ ] **`assembleDebug`** → BUILD SUCCESSFUL.
- [ ] **Instrumentados (si hay emulador):** `... :app:connectedDebugAndroidTest --tests "*recaudacion*"` → los testTags conservados deben mantener verdes los tests de flujo. (Si no hay emulador, el usuario instala el APK y valida a mano el flujo completo Contadores → Denominaciones×2 → Confirmación, incl. modo cadena.)
- [ ] **PR** de Fase 1 a `main`. El usuario instala el APK para QA visual (firma distinta).

**Diferido a fases posteriores (no Fase 1):** transiciones de elemento compartido `SharedTransition` entre máquina→flujo (M1, va con la migración de LocalDetalle/Histórico en su fase), la háptica por tecla del keypad si no está, y la ampliación de `RecreIcons` con el set definitivo. El "ring petróleo que crece con spring" (M2) sobre `DenominacionCard` es refinamiento de pulido (Fase 5); en Fase 1 basta el borde petróleo de `AppCard(selected)`.

**Salida:** el flujo de recaudación con identidad propia — denominaciones en rejilla 3×3 con chip flotante y héroe que cuenta, contadores con keypad unificado, confirmación con el neto como protagonista, "Recaudar todas" sólo cuando aporta, indicador de pasos animado, y todas las pantallas del flujo fuera de la allowlist de Material pelado.
