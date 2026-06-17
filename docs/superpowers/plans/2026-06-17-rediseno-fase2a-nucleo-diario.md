# Rediseño UI — Fase 2a: Núcleo diario (Locales · LocalDetalle · HistóricoDetalle) · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans para implementar este plan tarea a tarea. Los pasos usan checkbox (`- [ ]`).

**Goal:** Re-skinear el núcleo diario de la app (home de Locales, detalle de Local y detalle de Histórico con estética de ticket) con la identidad "Confianza Industrial", aplicando los patrones P1–P7, sin tocar lógica de dominio ni navegación.

**Architecture:** Solo cambia la **capa de presentación**. Los `ViewModel`/`UiState` (`LocalesViewModel`, `LocalDetalleViewModel`, `HistoricoDetalleViewModel`) NO se modifican. Se crean los átomos de **chrome propio** que faltan (`RecreTopBar` top-level + `RecreDetailTopBar` secundario + `RecreOverflowMenu`) y el componente **`TicketRecibo`**; el resto se compone con átomos ya existentes (`SearchField`, `RecreFilterChip`/`FilterChipRow`, `Skeleton`, `EmptyState`, `ErrorState`, `LocalCard`, `MaquinaCard`, `MoneyText`, `RecreDivider`/`RecreDottedDivider`, `StatusChip`, `IconAction`, `RecrePrimaryButton`/`RecreTonalButton`/`RecreTextButton`, `RecreBottomBar`/`RecreTopBarActions`). Cada pantalla migrada SALE de la allowlist del guardarraíl (`SinMaterialPeladoTest`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, JUnit4. Build con Gradle (JDK de Android Studio).

**Spec referencia:** `docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md` — §4 (P1/P2/P3/P4/P7/P8), §5 (M1/M3/M4), §6.2 (Núcleo diario; **LocalesScreen diseño bloqueado**), §8 (Fase 2).

> **Ámbito de ESTE plan (2a):** las 3 pantallas que son **UI pura** (sin backend). El **Histórico v2 a escala** (`HistoricoScreen`: paginación por cursor, filtros/búsqueda server-side, tabs Todo/Local/Máquina) es un **subsistema con backend** y va en un plan aparte (**Fase 2b**, ver `## Fase 2b (fuera de este plan)` al final). `HistoricoDetalleScreen` SÍ está aquí porque es reskin puro.

## Invariantes (no negociables en TODA la fase)

- **No tocar `ViewModel`/`UiState`** ni la navegación: el reskin es de Composables. Si una pantalla necesita un dato derivado (p. ej. "X por recaudar"), se calcula en una **función pura testeable** a partir del state ya existente, sin añadir campos al VM.
- **Money-safe:** todo importe se pinta con `MoneyText` (toma `BigDecimal` o el String ya formateado en es-ES). **Jamás** `Float`/`Double`; jamás formatear dinero a mano.
- **Estado nunca solo por color (P8/M6):** todo estado lleva icono+texto además del color (`StatusChip`).
- **Chrome propio (P1):** ninguna pantalla de `feature/**` importa `TopAppBar` Material; usan `RecreTopBar` (top-level) o `RecreDetailTopBar` (secundario). El `TopAppBar` Material solo vive DENTRO de la librería (`ui/components/`), que es donde el guardarraíl permite envolver Material.
- **Shared element intacto:** `LocalDetalleScreen` conserva `recreSharedBounds("local-nombre-$localId")` sobre el título (T-244).
- **Reusar estados de librería:** sustituir los `EmptyState`/`Loading`/`ErrorState` reimplementados a mano por los átomos `EmptyState`/`Skeleton`/`ErrorState`.

## Comandos de build

- **Compilar:** `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew -p android :app:assembleDebug` (o desde `android/`).
- **Unit test (paquete):** `cd android && JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew :app:testDebugUnitTest --tests "<patrón>"`.
- **Guardarraíl:** `--tests "com.recre.app.arch.SinMaterialPeladoTest"`.
- Builds largos: lanzarlos en el sandbox `ctx_execute` (cierran el MCP si bloquean).

---

## File Structure

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `ui/components/RecreTopBar.kt` | Chrome top-level (título marca + slot `actions`) y secundario (`RecreDetailTopBar`: back + título + slot `actions`). Envuelven `TopAppBar` M3 dentro de la librería | **Crear** |
| `ui/components/RecreOverflowMenu.kt` | Menú ⋮ propio (`IconAction` MoreVert + `DropdownMenu` M3) para acciones secundarias de una entidad | **Crear** |
| `feature/locales/LocalesScreen.kt` | Home: chrome P1 + héroe "X por recaudar" + `SearchField` + `FilterChipRow` + lista P3 + estados de librería | **Modificar** |
| `feature/locales/LocalesOrden.kt` | Función pura: filtra por chip (Por recaudar/Al día/Todos) y ordena (pendientes primero). TDD | **Crear** |
| `feature/locales/components/MaquinaCard.kt` | Tarjeta-entidad de máquina: lidera con estado+acción (Recaudar/Ver avería); secundarias al overflow ⋮ | **Modificar** |
| `feature/locales/LocalDetalleScreen.kt` | Detalle: chrome secundario + P2 (héroe = pendiente del local) + máquinas P3 + banners propios | **Modificar** |
| `feature/historico/components/TicketRecibo.kt` | Componente "recibo térmico": ancho estrecho, separadores punteados, mono tabular | **Crear** |
| `feature/historico/HistoricoDetalleScreen.kt` | Detalle con estética de ticket; conecta Ver PDF / Reimprimir (ya existen) | **Modificar** |
| `app/src/test/.../arch/SinMaterialPeladoTest.kt` | Retirar `LocalesScreen`, `LocalDetalleScreen`, `MaquinaCard`, `HistoricoDetalleScreen` de la allowlist | **Modificar** |
| Tests nuevos en `app/src/test/.../feature/locales/` | TDD de `LocalesOrden` | **Crear** |

---

### Task 1: Chrome propio — `RecreTopBar` (top-level) + `RecreDetailTopBar` (secundario)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/RecreTopBar.kt`

> P1. Dos cabeceras de la librería que envuelven `TopAppBar` M3 (permitido en `ui/`, prohibido en `feature/`). La top-level lleva título de marca + slot `actions` (donde va `RecreTopBarActions`); la secundaria lleva back + título (+ slot `actions` para el overflow). Colores sobre `surface`, sin sombra (la profundidad es el borde/escala).

- [ ] **Step 1: Crea `RecreTopBar.kt`**

```kotlin
package com.recre.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors

// =====================================================================
// Chrome de app (P1) — Design System "Confianza Industrial".
// SSOT: docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md §4 (P1).
//
// Envuelve TopAppBar M3 DENTRO de la librería para que las pantallas de feature/
// no importen Material pelado. Dos variantes:
//  - RecreTopBar:       top-level (tabs). Título + slot actions (RecreTopBarActions).
//  - RecreDetailTopBar: secundaria (detalle). Back + título + slot actions (overflow).
// Sobre surface, sin sombra (la elevación es por borde, regla del sistema).
// =====================================================================

@Composable
private fun recreTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = RecreColors.current.muted,
        actionIconContentColor = RecreColors.current.muted,
    )

/**
 * Cabecera de pantalla top-level (pestaña). [titulo] como marca/contexto y un
 * [subtitulo] muted opcional; [actions] aloja los iconos globales del shell
 * (sync, incidencias, alertas) vía RecreTopBarActions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecreTopBar(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitulo != null) {
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = RecreColors.current.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = actions,
        colors = recreTopBarColors(),
    )
}

/**
 * Cabecera de pantalla secundaria (detalle): flecha de back + [titulo]. El
 * [tituloModifier] permite enganchar el shared element del título (T-244). El
 * slot [actions] aloja el overflow ⋮ u otras acciones de la entidad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecreDetailTopBar(
    titulo: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tituloModifier: Modifier = Modifier,
    subtitulo: String? = null,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = tituloModifier,
                )
                if (subtitulo != null) {
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = RecreColors.current.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                enabled = backEnabled,
            )
        },
        actions = actions,
        colors = recreTopBarColors(),
    )
}
```

- [ ] **Step 2: Compila** → BUILD SUCCESSFUL.

Run: `... :app:assembleDebug`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/RecreTopBar.kt
git commit -m "feat(android): chrome propio RecreTopBar/RecreDetailTopBar (P1) (rediseño F2a)"
```

---

### Task 2: `LocalesScreen` (home) — héroe "X por recaudar" + búsqueda + filtros + estados de librería

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/locales/LocalesOrden.kt`
- Test: `android/app/src/test/java/com/recre/app/feature/locales/LocalesOrdenTest.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> **Diseño bloqueado (§6.2).** Chrome P1 (`RecreTopBar` con `RecreTopBarActions`), **héroe "X por recaudar"** (mono), `SearchField`, `FilterChipRow` (Por recaudar / Al día / Todos), lista P3 (`LocalCard`, **pendientes primero**), y `Skeleton`/`EmptyState` de librería. Conserva `RecreBottomBar`. NO cambia `LocalesViewModel`.

- [ ] **Step 1: Inspecciona `LocalResumen`** (con Serena: `find_symbol LocalResumen`) para confirmar el nombre del campo que indica "por recaudar" (booleano de pendiente o nº/importe pendiente) y si hay un importe agregable. Anota el campo real; el resto del task lo usa como `local.<campoPendiente>`.

- [ ] **Step 2: Escribe el test que falla** (`LocalesOrdenTest.kt`). Ajusta el constructor de `LocalResumen` a sus campos reales (rellena los obligatorios; lo relevante es el flag de pendiente):

```kotlin
package com.recre.app.feature.locales

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalesOrdenTest {
    private fun local(id: String, nombre: String, porRecaudar: Boolean) = /* construir LocalResumen real */

    @Test fun por_recaudar_filtra_solo_pendientes() {
        val data = listOf(local("1", "A", true), local("2", "B", false))
        assertEquals(listOf("1"), filtrarYOrdenar(data, FiltroLocal.PorRecaudar).map { it.id })
    }

    @Test fun al_dia_filtra_solo_no_pendientes() {
        val data = listOf(local("1", "A", true), local("2", "B", false))
        assertEquals(listOf("2"), filtrarYOrdenar(data, FiltroLocal.AlDia).map { it.id })
    }

    @Test fun todos_pone_pendientes_primero() {
        val data = listOf(local("1", "AlDia", false), local("2", "Pend", true))
        assertEquals(listOf("2", "1"), filtrarYOrdenar(data, FiltroLocal.Todos).map { it.id })
    }
}
```

- [ ] **Step 3: Ejecuta y verifica fallo** (`Unresolved reference: filtrarYOrdenar / FiltroLocal`).

- [ ] **Step 4: Implementa `LocalesOrden.kt`** (sustituye `porRecaudar(local)` por el campo real):

```kotlin
package com.recre.app.feature.locales

/** Filtros del home de Locales (§6.2). */
enum class FiltroLocal { PorRecaudar, AlDia, Todos }

/** Predicado "este local tiene algo por recaudar" derivado del state (sin tocar el VM). */
private fun porRecaudar(local: LocalResumen): Boolean = local.porRecaudar // ← campo real confirmado en Step 1

/**
 * Aplica el [filtro] y ordena con los pendientes primero (dentro de cada grupo,
 * respeta el orden de entrada del VM). Función pura: presentación, no recalcula
 * dinero ni toca el ViewModel.
 */
fun filtrarYOrdenar(locales: List<LocalResumen>, filtro: FiltroLocal): List<LocalResumen> {
    val filtrados = when (filtro) {
        FiltroLocal.PorRecaudar -> locales.filter { porRecaudar(it) }
        FiltroLocal.AlDia -> locales.filterNot { porRecaudar(it) }
        FiltroLocal.Todos -> locales
    }
    return filtrados.sortedByDescending { porRecaudar(it) } // estable: pendientes primero
}

/** Recuento para el héroe "X por recaudar". */
fun contarPorRecaudar(locales: List<LocalResumen>): Int = locales.count { porRecaudar(it) }
```

- [ ] **Step 5: Ejecuta y verifica que pasa** (3 tests PASS).

- [ ] **Step 6: Reskin de `LocalesScreen.kt`.** Cambios (líneas según versión actual):
  - **Imports:** quita `TopAppBar` (L28), `OutlinedTextField` (L24), `Card`/`CardDefaults` (L19-20), `TextButton` (L27). Añade `RecreTopBar`, `SearchField`, `FilterChipRow`/`FilterChipModel`, `EmptyState` (librería), `ListSkeleton`, `MoneyText`/`MoneyTextSize`, `RecrePrimaryButton`, y `androidx.compose.material3.Surface` para el banner.
  - **Cabecera (Scaffold.topBar, L77-105):** sustituye el `TopAppBar` por:
    ```kotlin
    topBar = {
        RecreTopBar(
            titulo = state.empresaNombre,
            subtitulo = formatSubtitulo(state), // reusar el helper existente
            actions = { RecreTopBarActions(onAlertasClick = onAlertasClick, onIncidenciasClick = onIncidenciasClick) },
        )
    },
    ```
    Conserva `bottomBar = { RecreBottomBar(current = TopLevelDestination.LOCALES, onSelect = onSelectTab) }` y el `PullToRefreshBox`.
  - **Héroe + filtros (encima del `LazyColumn`):** añade un encabezado de contenido con el héroe y los chips:
    ```kotlin
    val porRecaudar = contarPorRecaudar(state.locales)
    // Héroe "X por recaudar" (mono). Usa el plural string nuevo (Step 7).
    MoneyTextHeroLocales(porRecaudar) // o un Text con RecreType.cifra si no es importe; ver Step 7
    SearchField(
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        placeholder = stringResource(R.string.locales_buscar_placeholder),
        clearContentDescription = stringResource(R.string.action_clear),
    )
    FilterChipRow(
        chips = listOf(
            FilterChipModel("por_recaudar", stringResource(R.string.locales_filtro_por_recaudar)),
            FilterChipModel("al_dia", stringResource(R.string.locales_filtro_al_dia)),
            FilterChipModel("todos", stringResource(R.string.locales_filtro_todos)),
        ),
        selectedKeys = setOf(filtroKey),
        onToggle = { key, _ -> filtro = filtroDesdeKey(key) },
        onClear = { filtro = FiltroLocal.Todos },
        clearLabel = stringResource(R.string.action_clear),
    )
    ```
    `filtro` es estado local (`rememberSaveable { mutableStateOf(FiltroLocal.Todos) }`). La lista pasa por `filtrarYOrdenar(state.localesFiltrados, filtro)`.
  - **`BuscadorLocales` privado:** elimínalo (lo sustituye `SearchField`).
  - **`SyncStaleBanner` (L193-214):** `Card`+`TextButton` → `Surface(color = errorContainer, contentColor = onErrorContainer, shape = RecreShapes.medium)` con un `RecrePrimaryButton`/`RecreTonalButton` "Sincronizar".
  - **`EmptyState` privado (L224-256):** elimínalo y usa el de librería:
    ```kotlin
    EmptyState(
        icon = Icons.Filled.Storefront,
        title = stringResource(if (conQuery) R.string.locales_vacio_busqueda_titulo else R.string.locales_vacio_titulo),
        description = stringResource(if (conQuery) R.string.locales_vacio_busqueda_desc else R.string.locales_vacio_desc),
        filtered = conQuery,
    )
    ```
  - **Carga:** si `state` indica carga inicial sin datos, muestra `ListSkeleton(loadingLabel = stringResource(R.string.locales_cargando))` en vez del texto plano.

- [ ] **Step 7: Strings nuevos** en `res/values/strings.xml` (junto a los de locales): `locales_buscar_placeholder`, `action_clear` (si no existe), `locales_filtro_por_recaudar`="Por recaudar", `locales_filtro_al_dia`="Al día", `locales_filtro_todos`="Todos", `locales_por_recaudar_hero` (plural con `%d`), `locales_vacio_titulo`/`_desc`, `locales_vacio_busqueda_titulo`/`_desc`, `locales_cargando`. El héroe "X por recaudar": si NO es un importe (es un recuento), píntalo como `Text` con `RecreType.cifra` + etiqueta, NO `MoneyText` (que es money-safe para €). Si `LocalResumen` expone importe pendiente agregable, usa `MoneyText(size = Hero)` con la suma.

- [ ] **Step 8: Compila + retira de la allowlist.** Quita `"feature/locales/LocalesScreen.kt"` de `SinMaterialPeladoTest.kt`; guardarraíl → PASS.

Run: `... :app:assembleDebug && ... :app:testDebugUnitTest --tests "com.recre.app.arch.SinMaterialPeladoTest" --tests "com.recre.app.feature.locales.LocalesOrdenTest"`

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/locales/LocalesScreen.kt \
        android/app/src/main/java/com/recre/app/feature/locales/LocalesOrden.kt \
        android/app/src/test/java/com/recre/app/feature/locales/LocalesOrdenTest.kt \
        android/app/src/main/res/values/strings.xml \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): home de Locales con héroe/búsqueda/filtros y estados propios (P1/P4) (rediseño F2a)"
```

---

### Task 3: `RecreOverflowMenu` + `MaquinaCard` (estado+acción, secundarias al overflow)

**Files:**
- Create: `android/app/src/main/java/com/recre/app/ui/components/RecreOverflowMenu.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/components/MaquinaCard.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> §6.2: la máquina lidera con **estado + acción** (Recaudar / Ver avería); las secundarias (reportar avería, cambio de placa) van a un **overflow ⋮**. `DropdownMenu` M3 NO está en la lista prohibida, pero se envuelve en un átomo propio para uniformar.

- [ ] **Step 1: Crea `RecreOverflowMenu.kt`**

```kotlin
package com.recre.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Una acción del overflow: etiqueta ya localizada + callback. */
data class OverflowAccion(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * Menú ⋮ propio para las acciones SECUNDARIAS de una entidad (las primarias van
 * como RecreButton/StatusChip visibles). Trigger = IconAction MoreVert (muted,
 * neutro). [contentDescription] nombra la entidad ("Más acciones de {máquina}").
 */
@Composable
fun RecreOverflowMenu(
    contentDescription: String,
    acciones: List<OverflowAccion>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    IconAction(
        icon = Icons.Filled.MoreVert,
        contentDescription = contentDescription,
        onClick = { expanded = true },
        modifier = modifier,
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        acciones.forEach { accion ->
            DropdownMenuItem(
                text = { Text(accion.label) },
                enabled = accion.enabled,
                onClick = {
                    expanded = false
                    accion.onClick()
                },
            )
        }
    }
}
```

- [ ] **Step 2: Lee** `MaquinaCard.kt` (con Serena `find_symbol MaquinaCard`). Identifica qué Material pelado usa (Card/Button/etc.) y los 3 callbacks (`onRecaudarClick`, `onCambioPlacaClick`, `onReportarAveriaClick`).

- [ ] **Step 3: Reskin `MaquinaCard`.** Construye la tarjeta sobre `AppCard`/`EntidadRow`; lidera con nombre + `StatusChip` de estado (instalada/avería). Acción primaria visible (`RecrePrimaryButton`/`RecreTonalButton` "Recaudar", o "Ver avería" si tiene avería abierta). Mete `onCambioPlacaClick` y `onReportarAveriaClick` en `RecreOverflowMenu`:

```kotlin
RecreOverflowMenu(
    contentDescription = stringResource(R.string.maquina_mas_acciones, maquina.nombreOSerie),
    acciones = listOf(
        OverflowAccion(stringResource(R.string.maquina_accion_reportar_averia), onReportarAveriaClick),
        OverflowAccion(stringResource(R.string.maquina_accion_cambio_placa), onCambioPlacaClick),
    ),
)
```
Mantén la firma pública de `MaquinaCard` (mismos params) para no tocar `LocalDetalleScreen` todavía. Quita los imports Material pelado.

- [ ] **Step 4: Strings** nuevos: `maquina_mas_acciones` ("Más acciones de %1$s"), `maquina_accion_reportar_averia`, `maquina_accion_cambio_placa` (reusa los existentes si ya hay literales equivalentes).

- [ ] **Step 5: Compila + retira `MaquinaCard.kt` de la allowlist.** Guardarraíl → PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/recre/app/ui/components/RecreOverflowMenu.kt \
        android/app/src/main/java/com/recre/app/feature/locales/components/MaquinaCard.kt \
        android/app/src/main/res/values/strings.xml \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): RecreOverflowMenu + MaquinaCard con estado/acción y ⋮ (P3) (rediseño F2a)"
```

---

### Task 4: `LocalDetalleScreen` — chrome secundario + P2 (héroe pendiente) + botones propios

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/locales/LocalDetalleScreen.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> §6.2: cabecera P2 con el local y su **pendiente como héroe**; máquinas como `MaquinaCard` (ya reskineada en Task 3); "Recaudar todas" solo si `mostrarRecaudarTodas(...)` (ya implementado en F1). NO cambia `LocalDetalleViewModel`.

- [ ] **Step 1: Imports** — quita `TopAppBar` (L27), `OutlinedButton` (L23), `Button` (L16), `Card`/`CardDefaults` (L17-18), `TextButton` (L26), `IconButton`. Añade `RecreDetailTopBar`, `RecrePrimaryButton`, `RecreTonalButton`, `MoneyText`/`MoneyTextSize`, `androidx.compose.material3.Surface`.

- [ ] **Step 2: Cabecera (Scaffold.topBar, L61-74):** sustituye el `TopAppBar` por:
```kotlin
topBar = {
    RecreDetailTopBar(
        titulo = detalleNombre, // nombre del local (de state.detalle?.nombre ?: "")
        onBack = onBack,
        tituloModifier = Modifier.recreSharedBounds("local-nombre-$localId"), // conserva T-244
    )
}
```
(Si el nombre aún no cargó, pasa el placeholder actual.)

- [ ] **Step 3: `CabeceraLocal` (L243) → P2.** Añade el **héroe = pendiente del local** con `MoneyText(size = Hero)` (si `LocalDetalle` expone el importe pendiente; confírmalo con Serena `find_symbol LocalDetalle`). Mantén el resto de datos del local como filas limpias sobre `AppCard`.

- [ ] **Step 4: Botones de la lista (L169-194):** `OutlinedButton` "ver deudas" → `RecreTonalButton`; `Button` "Recaudar todas" → `RecrePrimaryButton` (conserva la condición `mostrarRecaudarTodas(detalle.maquinas.count { it.instalada }...)` ya existente).

- [ ] **Step 5: `SyncStaleBanner` (L216-241):** `Card`+`TextButton` → `Surface(errorContainer)` + `RecreTonalButton` (igual que en Locales).

- [ ] **Step 6: Estados** `Loading()`/`NoEncontrado()`/`SinMaquinas()` → `Skeleton`/`EmptyState` de librería (o consérvalos si ya son propios y limpios; el objetivo es quitar Material pelado, no reescribir lo que ya es propio).

- [ ] **Step 7: Compila + retira `LocalDetalleScreen.kt` de la allowlist.** Guardarraíl → PASS.

Run: `... :app:assembleDebug && ... :app:testDebugUnitTest --tests "com.recre.app.arch.SinMaterialPeladoTest"`

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/locales/LocalDetalleScreen.kt \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): LocalDetalle con chrome propio, héroe pendiente y máquinas P3 (P1/P2) (rediseño F2a)"
```

---

### Task 5: `TicketRecibo` — componente "recibo térmico"

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/historico/components/TicketRecibo.kt`

> §6.2 (HistoricoDetalle): emular el papel. Ancho estrecho tipo recibo, cabecera centrada, separadores **punteados** (`RecreDottedDivider`), cifras en **Geist Mono tabular** (`MoneyText`/`RecreType`). Mismo contenido que el ticket impreso (cabecera empresa, local/máquina, contadores, bruto/tasas/neto/partes, firma). Variantes conflicto/anulada como avisos.

- [ ] **Step 1: Lee** `HistoricoDetalleScreen.kt` `CabeceraCard`/`CifrasCard`/`CifraRow`/`KeyValue` (L262-407) y el tipo `RecaudacionHistorica` (Serena) para saber qué campos pinta el detalle (importes `BigDecimal`, contadores, fecha, firma, estado).

- [ ] **Step 2: Crea `TicketRecibo.kt`.** Composable que recibe `recaudacion: RecaudacionHistorica` y pinta el recibo sobre `AppCard` (ancho limitado, `Modifier.widthIn(max = 360.dp)` centrado), con secciones separadas por `RecreDottedDivider`:
  - Cabecera centrada: empresa + título "Recibo de recaudación".
  - `KeyValue` para local·máquina·fecha (label muted, valor `onSurface`).
  - Bloque contadores (entradas/salidas, mono).
  - Bloque cifras con `FilaCifraTicket(label, value: BigDecimal)` usando `MoneyText(size = Inline, role = ...)`: bruto, tasas, **neto** (destacado), parte empresa, parte local.
  - Firma (imagen/strokes si el detalle la trae; si no, "Firmado").
  - Estado: si anulada → banner `Surface(surfaceVariant)` con motivo; si conflicto → `Surface(errorContainer)` con aviso + cifras recalculadas. Icono+texto+color (P8).

  Reusa `RecreType` (mono tabular) y `MoneyText`. NADA de `Card`/`Button` pelados (este fichero NO está en la allowlist; debe nacer limpio).

- [ ] **Step 3: Compila** → BUILD SUCCESSFUL. Revisa el @Preview (añade uno con datos de muestra) en light/dark.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/historico/components/TicketRecibo.kt
git commit -m "feat(android): componente TicketRecibo (estética de recibo térmico) (rediseño F2a)"
```

---

### Task 6: `HistoricoDetalleScreen` — estética de ticket + reimpresión conectada

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/historico/HistoricoDetalleScreen.kt`
- Modify: `android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt`

> Sustituir las 6 cards grises por `TicketRecibo` (Task 5) + el bloque de acciones de reimpresión con botones propios. **Ver PDF** y **Reimprimir Bluetooth** YA existen: conecta los callbacks/estado actuales, no reconstruyas.

- [ ] **Step 1: Imports** — quita `TopAppBar` (L38), `Card`/`CardDefaults` (L27-28), `OutlinedButton` (L35), `Button` (L26). Sustituye `HorizontalDivider` (L31) por `RecreDivider`/`RecreDottedDivider`. Añade `RecreDetailTopBar`, `RecrePrimaryButton`, `RecreTonalButton`, `androidx.compose.material3.Surface`, `TicketRecibo`.

- [ ] **Step 2: Cabecera (Scaffold.topBar, L100-104):** `TopAppBar` → `RecreDetailTopBar(titulo = stringResource(R.string.historico_detalle_titulo), onBack = onBack)`.

- [ ] **Step 3: `Contenido` (L159):** sustituye la pila de cards (`CabeceraCard`/`AnulacionCard`/`ConflictoCard`/`CifrasCard`) por `TicketRecibo(recaudacion = recaudacion)`. Conserva el `Column` con `verticalScroll`. Borra los sub-composables que `TicketRecibo` reemplaza (`CabeceraCard` L262, `AnulacionCard` L292, `ConflictoCard` L317, `CifrasCard` L351, `CifraRow` L372, `KeyValue` L393) si ya no se usan.

- [ ] **Step 4: Acciones de reimpresión (L204, L221):** `OutlinedButton` "Ver PDF" → `RecreTonalButton` (loading = `state.descandoPdf`); `Button` "Reimprimir" → `RecrePrimaryButton` (loading = `state.imprimiendoBluetooth`). Conserva exactamente los callbacks actuales (`onReimprimirPdf`, `onReimprimirBluetooth`).

- [ ] **Step 5: `ImpresionStatusCard` (L409) y `ErrorCardCompact` (L463):** `Card`(semántico)+`OutlinedButton` → `Surface(color = ..., shape = RecreShapes.medium)` + `RecreTonalButton`; el `CircularProgressIndicator` (L427) se mantiene dentro del Surface (no es Material pelado prohibido; es un spinner inline aceptable aquí, pero si procede usa `StatusChip(spinning = true)`).

- [ ] **Step 6: Compila + retira `HistoricoDetalleScreen.kt` de la allowlist.** Guardarraíl → PASS.

Run: `... :app:assembleDebug && ... :app:testDebugUnitTest --tests "com.recre.app.arch.SinMaterialPeladoTest"`

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/recre/app/feature/historico/HistoricoDetalleScreen.kt \
        android/app/src/test/java/com/recre/app/arch/SinMaterialPeladoTest.kt
git commit -m "feat(android): HistoricoDetalle con estética de ticket (TicketRecibo) (rediseño F2a)"
```

---

## Cierre de Fase 2a

- [ ] **Suite:** `... :app:testDebugUnitTest --tests "com.recre.app.feature.*" --tests "com.recre.app.ui.*" --tests "com.recre.app.arch.*"` → PASS.
- [ ] **`assembleDebug`** → BUILD SUCCESSFUL.
- [ ] **Allowlist:** `LocalesScreen`, `LocalDetalleScreen`, `MaquinaCard`, `HistoricoDetalleScreen` retiradas. (Quedan en la allowlist las pantallas de Fases 3–4.)
- [ ] **PR(s)** a `main` (una por task o agrupadas <400 líneas; squash & merge). El usuario instala el APK para QA visual.

**Salida:** el núcleo diario con identidad propia — home con héroe "por recaudar", búsqueda y filtros; detalle de local con chrome propio, héroe de pendiente y máquinas que lideran con estado+acción (+overflow ⋮); y el detalle de histórico con estética de recibo térmico.

---

## Fase 2b (fuera de este plan) — Histórico v2 a escala (con backend)

Subsistema independiente (la **única excepción** de "no tocar backend"). Tendrá su **propio plan** porque cruza tres capas:

1. **Backend (Supabase):** migración aditiva con vista/RPC sobre `recaudacion` filtrable por `local_id`/`maquina_id`, **paginación por cursor (`fecha`+`id`)**, índices de soporte y **RBAC por rol** (técnico = las suyas; gestor = toda la empresa). pgTAP de la función. *(Requiere antes mapear el flujo actual: cómo se consultan hoy las recaudaciones "mis 200", la RLS vigente y el repositorio Android.)*
2. **Capa de datos Android:** repositorio/paginación por cursor (Paging 3 o cursor manual) + filtros server-side.
3. **`HistoricoScreen` (UI):** P4 + navegación **[Todo] · [Por local] · [Por máquina]** (`SegmentedControl`), filtros (`FilterChipRow`), `SearchField` server-side, **scroll infinito**, filas P3 (importe mono + local·máquina + fecha + `StatusChip`), `Skeleton`/`EmptyState`/`ErrorState`. Sacar `HistoricoScreen.kt` de la allowlist.

**Antes de planificar 2b:** decisión de diseño del contrato del RPC (forma del cursor, shape de respuesta, RBAC) — merece su propio paso de brainstorming/spec corto.
