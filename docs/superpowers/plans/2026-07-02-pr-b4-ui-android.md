# PR-B4 · UI Android de catálogo (autocomplete-con-alta) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir en el formulario de máquina de Android los dos `GestionTextField` de texto libre de **fabricante** y **modelo** por un combo editable con autocompletado sobre el catálogo global (con alta al vuelo) y cascada fabricante→modelo — el equivalente del PR-B3 web ya mergeado.

**Architecture:** El widget sigue enviando el **NOMBRE en texto**; la RPC `crear_maquina`/`actualizar_maquina` (PR-B2) ya resuelve nombre→FK (find-or-create) al guardar. Por eso **no cambian** el DTO, la RPC ni el contrato: `MaquinaInput.fabricante/modelo` siguen siendo `String?` de punta a punta. El catálogo se lee **on-demand** (nunca a Room, no entra en `TABLAS_SYNC`) mediante un repositorio nuevo de **solo lectura**. "Crear «X»" fija el texto tecleado como valor del campo; **no** hay escritura eager al catálogo (mejora sobre spec §6.7, idéntica a la decisión de B3 web). Se construye un componente editable `FieldAutocomplete` (design system) + su adaptador `GestionAutocomplete` (capa gestión), siguiendo el patrón existente `GestionDropdown→FieldSelect`. La lógica de cascada se extrae a funciones puras JVM-testeables.

**Tech Stack:** Kotlin · Jetpack Compose (Material3 `ExposedDropdownMenuBox`) · Hilt · supabase-kt (postgrest) · JUnit4 (unit test JVM). Clean Architecture `data → domain ← ui`.

## Global Constraints

- **La UI manda NOMBRES en texto.** `MaquinaInput.fabricante/modelo: String?`, `CrearMaquinaParams.p_fabricante/p_modelo`, la RPC y `MaquinaFormViewModel.guardar()` (que ya hace `s.fabricante.normalizarOpcional()`) NO cambian. Prohibido tocar `MaquinasRemoteDataSource`, `GestionDtos`, `MaquinasGestorRepository` (salvo que el catálogo es un repo nuevo aparte), la RPC o cualquier guardarraíl.
- **Catálogo GLOBAL solo-lectura, on-demand.** `fetch*` hacen `select` sobre `public.fabricante`/`public.modelo` **sin filtro `empresa_id`** (RLS permite SELECT a `authenticated`). **Prohibido** añadir `fabricante`/`modelo` a `TABLAS_SYNC`/`TABLAS`/`RealtimeManager` o a Room. Sin escritura eager (`crear_fabricante`/`crear_modelo` NO se llaman aquí).
- **Cascada modelo⊂fabricante.** El modelo se filtra por el fabricante seleccionado (por id, en el ViewModel); cambiar de fabricante limpia el modelo; el combo de modelo se deshabilita si no hay fabricante. En **edición** el modelo heredado NO se borra al cargar.
- **Extensión aditiva del design system.** `ComboboxCcaa` (lista cerrada CCAA) NO se toca; se añade un `FieldAutocomplete` nuevo. Sin `createLabel` un `FieldAutocomplete` es una lista cerrada.
- **Layering.** `ui/components` (Field.kt) no importa tipos de `feature/*`. Por eso `FieldAutocomplete` opera sobre `List<String>` (etiquetas); `FkOption` (id+label) vive en `feature/gestion` y lo usa el adaptador `GestionAutocomplete` + el ViewModel. El repositorio (capa data) devuelve tipos de datos propios (`FabricanteCatalogo`/`ModeloCatalogo`), no `FkOption`.
- **Kotlin sin `!!`** salvo patrón local ya usado (`createLabel!!` tras comprobar `!= null`, como el código existente hace con `valor!!`). Comentarios/UI en español; identificadores en inglés salvo dominio (`fabricante`, `modelo`, `maquina`, `catalogo`). Textos por `res/values/strings.xml`.

## Entorno de build (obligatorio en cada comando gradle)

Desde `android/`. El JDK solo está en el JBR de Android Studio y no se hereda; expórtalo **en el comando**. Los unit tests requieren locale UTF-8 (si no, mangla nombres/acentos). Usa `compileDebugKotlin` como gate de compilación (más rápido que `assembleDebug` y ya resuelve `R.string.*` porque depende de `processDebugResources`):

```bash
# Compilación (gate de tipos + recursos R):
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin
# Unit test JVM:
LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.CatalogoCascadaTest"
```

Nota: los builds de Android son largos y pueden cortar la conexión del sandbox; si un comando gradle no devuelve, reléelo/reintenta y reporta. No uses `assembleDebug` salvo que necesites empaquetar.

---

## File Structure

| Fichero | Responsabilidad |
|---------|-----------------|
| `feature/gestion/FkOption.kt` (nuevo) | `data class FkOption(id, label)` promovido desde instalaciones a común (feature layer). |
| `feature/gestion/instalaciones/InstalacionFormViewModel.kt` (modificar) | Quitar la declaración local de `FkOption`; importar la común. |
| `feature/gestion/instalaciones/InstalacionFormScreen.kt` (modificar) | Importar `FkOption` común. |
| `core/data/remote/dto/CatalogoDtos.kt` (nuevo) | `FabricanteDto`, `ModeloDto` (serialización PostgREST). |
| `core/data/remote/CatalogoRemoteDataSource.kt` (nuevo) | `fetchFabricantes()`/`fetchModelos()`: SELECT global (solo lectura). |
| `core/data/repository/CatalogoRepository.kt` (nuevo) | Tipos `FabricanteCatalogo`/`ModeloCatalogo`/`CatalogoMaquinas` + interfaz + impl (`runCatching…fold`). |
| `di/RepositoryModule.kt` (modificar) | `@Binds` de `CatalogoRepository`. |
| `feature/gestion/CatalogoCascada.kt` (nuevo) | Funciones puras `fabricantesComoOpciones`/`modelosDeFabricante` (cascada, JVM-testeable). |
| `test/.../feature/gestion/CatalogoCascadaTest.kt` (nuevo) | Unit tests de la cascada. |
| `ui/components/Field.kt` (modificar) | Añadir `FieldAutocomplete` (editable + alta, sobre `List<String>`), a partir de `ComboboxCcaa`. |
| `feature/gestion/components/FormFields.kt` (modificar) | Añadir `GestionAutocomplete(options: List<FkOption>)` → delega en `FieldAutocomplete`. |
| `feature/gestion/maquinas/MaquinaFormViewModel.kt` (modificar) | Inyectar `CatalogoRepository`; estado `fabricantesDisponibles`/`modelosDisponibles`; `cargarCatalogo()`; cascada en `onFabricanteChange`. |
| `feature/gestion/maquinas/MaquinaFormScreen.kt` (modificar) | Los 2 `GestionTextField` → 2 `GestionAutocomplete` en cascada. |
| `res/values/strings.xml` (modificar) | Strings `gestion_catalogo_crear`, `gestion_catalogo_sin_coincidencias`, `gestion_maquina_modelo_sin_fabricante`. |

---

## Task 1: Promover `FkOption` a común

**Files:**
- Create: `android/app/src/main/java/com/recre/app/feature/gestion/FkOption.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/gestion/instalaciones/InstalacionFormViewModel.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/gestion/instalaciones/InstalacionFormScreen.kt`

**Interfaces:**
- Produces: `data class FkOption(val id: String, val label: String)` en el paquete `com.recre.app.feature.gestion`.

**Contexto:** hoy `FkOption` se declara como top-level dentro de `InstalacionFormViewModel.kt` (paquete `…feature.gestion.instalaciones`) y lo usan ese ViewModel y `InstalacionFormScreen.kt`. Se mueve a `…feature.gestion` para que también lo usen `maquinas` y `components`. Localiza símbolos con `mcp__serena__find_symbol`/grep; edita con `Read` acotado.

- [ ] **Step 1: Crear el fichero común** — `feature/gestion/FkOption.kt`

```kotlin
package com.recre.app.feature.gestion

/**
 * Opción de un desplegable de clave foránea: `id` estable para casar/filtrar +
 * `label` visible. Común a los formularios del gestor (instalaciones, máquinas…).
 */
data class FkOption(val id: String, val label: String)
```

- [ ] **Step 2: Quitar la declaración local en `InstalacionFormViewModel.kt`**

Elimina la línea `data class FkOption(val id: String, val label: String)` (top-level en ese fichero) y añade el import `import com.recre.app.feature.gestion.FkOption` en la zona de imports.

- [ ] **Step 3: Importar la común en `InstalacionFormScreen.kt`**

Añade `import com.recre.app.feature.gestion.FkOption` en la zona de imports (antes referenciaba `FkOption` del mismo paquete `instalaciones`).

- [ ] **Step 4: Verificar que compila**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (instalaciones sigue compilando con el `FkOption` común).

- [ ] **Step 5: Commit**

```bash
git add "android/app/src/main/java/com/recre/app/feature/gestion/FkOption.kt" "android/app/src/main/java/com/recre/app/feature/gestion/instalaciones/InstalacionFormViewModel.kt" "android/app/src/main/java/com/recre/app/feature/gestion/instalaciones/InstalacionFormScreen.kt"
git commit -m "refactor(android): promueve FkOption a feature/gestion común (T-271)"
```

---

## Task 2: Capa de datos del catálogo (solo lectura) + cascada pura

**Files:**
- Create: `android/app/src/main/java/com/recre/app/core/data/remote/dto/CatalogoDtos.kt`
- Create: `android/app/src/main/java/com/recre/app/core/data/remote/CatalogoRemoteDataSource.kt`
- Create: `android/app/src/main/java/com/recre/app/core/data/repository/CatalogoRepository.kt`
- Modify: `android/app/src/main/java/com/recre/app/di/RepositoryModule.kt`
- Create: `android/app/src/main/java/com/recre/app/feature/gestion/CatalogoCascada.kt`
- Test: `android/app/src/test/java/com/recre/app/feature/gestion/CatalogoCascadaTest.kt`

**Interfaces:**
- Consumes: `FkOption` (Task 1); `SupabaseClient`; `clasificarErrorGestion`; `GestionResult`.
- Produces:
  - `data class FabricanteCatalogo(id, nombre)`, `data class ModeloCatalogo(id, nombre, fabricanteId)`, `data class CatalogoMaquinas(fabricantes, modelos)`.
  - `interface CatalogoRepository { suspend fun cargar(): GestionResult<CatalogoMaquinas> }`.
  - `fun fabricantesComoOpciones(List<FabricanteCatalogo>): List<FkOption>`
  - `fun modelosDeFabricante(fabricanteNombre: String, fabricantes: List<FabricanteCatalogo>, modelos: List<ModeloCatalogo>): List<FkOption>`

- [ ] **Step 1: Escribir el test que falla** — `test/.../feature/gestion/CatalogoCascadaTest.kt`

```kotlin
package com.recre.app.feature.gestion

import com.recre.app.core.data.repository.FabricanteCatalogo
import com.recre.app.core.data.repository.ModeloCatalogo
import org.junit.Assert.assertEquals
import org.junit.Test

// Nombres de test en ASCII a propósito: el runner mangla acentos con locale no-UTF8.
class CatalogoCascadaTest {

    private val fabricantes = listOf(
        FabricanteCatalogo("fab-cirsa", "Cirsa"),
        FabricanteCatalogo("fab-unidesa", "Unidesa"),
    )
    private val modelos = listOf(
        ModeloCatalogo("m1", "Diplomat", "fab-cirsa"),
        ModeloCatalogo("m2", "Super", "fab-cirsa"),
        ModeloCatalogo("m3", "Gallo", "fab-unidesa"),
    )

    @Test
    fun fabricantes_como_opciones_mapea_id_y_label() {
        assertEquals(
            listOf(FkOption("fab-cirsa", "Cirsa"), FkOption("fab-unidesa", "Unidesa")),
            fabricantesComoOpciones(fabricantes),
        )
    }

    @Test
    fun modelos_del_fabricante_por_nombre_laxo() {
        assertEquals(
            listOf(FkOption("m1", "Diplomat"), FkOption("m2", "Super")),
            modelosDeFabricante("  cirsa ", fabricantes, modelos),
        )
    }

    @Test
    fun modelos_vacio_si_fabricante_nuevo_o_vacio() {
        assertEquals(emptyList<FkOption>(), modelosDeFabricante("Nueva SL", fabricantes, modelos))
        assertEquals(emptyList<FkOption>(), modelosDeFabricante("", fabricantes, modelos))
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.CatalogoCascadaTest"`
Expected: FALLA de compilación (símbolos `FabricanteCatalogo`/`ModeloCatalogo`/`fabricantesComoOpciones`/`modelosDeFabricante` no existen).

- [ ] **Step 3: DTOs** — `core/data/remote/dto/CatalogoDtos.kt`

```kotlin
package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de `public.fabricante` (catálogo global, sin empresa_id). */
@Serializable
data class FabricanteDto(
    val id: String,
    val nombre: String,
)

/** Fila de `public.modelo`; `fabricante_id` = FK a su fabricante. */
@Serializable
data class ModeloDto(
    val id: String,
    val nombre: String,
    @SerialName("fabricante_id") val fabricanteId: String,
)
```

- [ ] **Step 4: Remote data source** — `core/data/remote/CatalogoRemoteDataSource.kt`

```kotlin
package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.FabricanteDto
import com.recre.app.core.data.remote.dto.ModeloDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP del catálogo global de máquinas (fabricante/modelo). SOLO
 * LECTURA: la RLS permite SELECT a `authenticated`; el alta ocurre dentro de la
 * RPC `crear_maquina` al guardar (find-or-create), no desde aquí. Global → sin
 * filtro `empresa_id`.
 */
@Singleton
class CatalogoRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun fetchFabricantes(): List<FabricanteDto> =
        supabase.from("fabricante").select().decodeList()

    suspend fun fetchModelos(): List<ModeloDto> =
        supabase.from("modelo").select().decodeList()
}
```

> Si `decodeList()` no infiere el tipo, anótalo: `.decodeList<FabricanteDto>()`. Imports de `select`/`decodeList`: los mismos que ya resuelve el módulo para `DeudasRemoteDataSource` (postgrest). No añadas `filter {}` (catálogo global).

- [ ] **Step 5: Repositorio (tipos + interfaz + impl)** — `core/data/repository/CatalogoRepository.kt`

```kotlin
package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.CatalogoRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Fabricante del catálogo (capa data; el ViewModel lo mapea a FkOption). */
data class FabricanteCatalogo(val id: String, val nombre: String)

/** Modelo del catálogo con su fabricante (para la cascada en cliente). */
data class ModeloCatalogo(val id: String, val nombre: String, val fabricanteId: String)

/** Catálogo global completo, leído on-demand al abrir el formulario. */
data class CatalogoMaquinas(
    val fabricantes: List<FabricanteCatalogo>,
    val modelos: List<ModeloCatalogo>,
)

interface CatalogoRepository {
    /**
     * Lee el catálogo global (solo lectura). El alta de fabricante/modelo la
     * hace la RPC `crear_maquina` al guardar la máquina; aquí no se escribe.
     */
    suspend fun cargar(): GestionResult<CatalogoMaquinas>
}

@Singleton
class CatalogoRepositoryImpl @Inject constructor(
    private val remote: CatalogoRemoteDataSource,
) : CatalogoRepository {

    override suspend fun cargar(): GestionResult<CatalogoMaquinas> =
        runCatching {
            val fabricantes = remote.fetchFabricantes().map { FabricanteCatalogo(it.id, it.nombre) }
            val modelos = remote.fetchModelos().map { ModeloCatalogo(it.id, it.nombre, it.fabricanteId) }
            CatalogoMaquinas(fabricantes, modelos)
        }.fold(
            onSuccess = { GestionResult.Success(it) },
            onFailure = { throwable ->
                val (error, code) = clasificarErrorGestion(throwable)
                Timber.w(throwable, "Cargar catálogo falló: %s", code)
                GestionResult.Failure(error, code)
            },
        )
}
```

- [ ] **Step 6: Binding Hilt** — `di/RepositoryModule.kt`

Añade los imports de `CatalogoRepository`/`CatalogoRepositoryImpl` (junto a los demás `com.recre.app.core.data.repository.*`) y, dentro de la clase, un binding nuevo (p. ej. tras `bindMaquinasGestorRepository`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindCatalogoRepository(impl: CatalogoRepositoryImpl): CatalogoRepository
```

- [ ] **Step 7: Cascada pura** — `feature/gestion/CatalogoCascada.kt`

```kotlin
package com.recre.app.feature.gestion

import com.recre.app.core.data.repository.FabricanteCatalogo
import com.recre.app.core.data.repository.ModeloCatalogo

/** Fabricantes del catálogo como opciones (id + etiqueta) para el autocomplete. */
fun fabricantesComoOpciones(fabricantes: List<FabricanteCatalogo>): List<FkOption> =
    fabricantes.map { FkOption(it.id, it.nombre) }

/**
 * Modelos del fabricante cuyo nombre coincide (laxo: trim + ignore-case), como
 * opciones. Si el fabricante es nuevo (no catalogado) o el nombre está vacío,
 * lista vacía: el usuario podrá teclear un modelo nuevo que la RPC creará bajo
 * ese fabricante al guardar.
 */
fun modelosDeFabricante(
    fabricanteNombre: String,
    fabricantes: List<FabricanteCatalogo>,
    modelos: List<ModeloCatalogo>,
): List<FkOption> {
    val objetivo = fabricanteNombre.trim()
    if (objetivo.isEmpty()) return emptyList()
    val fabId = fabricantes.firstOrNull { it.nombre.trim().equals(objetivo, ignoreCase = true) }?.id
        ?: return emptyList()
    return modelos.filter { it.fabricanteId == fabId }.map { FkOption(it.id, it.nombre) }
}
```

- [ ] **Step 8: Ejecutar el test y verificar que pasa**

Run: `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest --tests "com.recre.app.feature.gestion.CatalogoCascadaTest"`
Expected: BUILD SUCCESSFUL, 3 tests PASS.

- [ ] **Step 9: Verificar que todo compila** (incluye el binding Hilt)

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add "android/app/src/main/java/com/recre/app/core/data/remote/dto/CatalogoDtos.kt" "android/app/src/main/java/com/recre/app/core/data/remote/CatalogoRemoteDataSource.kt" "android/app/src/main/java/com/recre/app/core/data/repository/CatalogoRepository.kt" "android/app/src/main/java/com/recre/app/di/RepositoryModule.kt" "android/app/src/main/java/com/recre/app/feature/gestion/CatalogoCascada.kt" "android/app/src/test/java/com/recre/app/feature/gestion/CatalogoCascadaTest.kt"
git commit -m "feat(android): repositorio de catálogo global (solo lectura) + cascada pura (T-271)"
```

---

## Task 3: Componente de autocomplete editable con alta

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/ui/components/Field.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/gestion/components/FormFields.kt`

**Interfaces:**
- Consumes: `FkOption` (Task 1).
- Produces:
  - `FieldAutocomplete(value, onValueChange, options: List<String>, label, emptyText, modifier, createLabel: ((String)->String)?, enabled, placeholder)` en `ui/components`.
  - `GestionAutocomplete(label, value, options: List<FkOption>, onValueChange, emptyText, createLabel, modifier, enabled, placeholder)` en `feature/gestion/components`.

**Contexto:** `Field.kt` ya tiene `ComboboxCcaa` (combo editable filtrable sobre `List<String>`, `MenuAnchorType.PrimaryEditable`, "Sin coincidencias") con todos los imports Material necesarios. `FieldAutocomplete` es ese patrón + un ítem "Crear «X»" opcional. `FormFields.kt` son adaptadores finos (`GestionDropdown→FieldSelect`); `GestionAutocomplete` sigue ese estilo. El combo solo muestra/emite **etiquetas** (String); la cascada por id la resuelve el ViewModel (Task 4).

- [ ] **Step 1: Añadir `FieldAutocomplete`** — al final de `ui/components/Field.kt` (tras `ComboboxCcaa`, antes de las Previews)

Añade el import que falta (los demás ya están por `ComboboxCcaa`): `import androidx.compose.material.icons.filled.Add`.

```kotlin
/**
 * Combobox editable con filtro y ALTA opcional. Extiende el patrón de
 * [ComboboxCcaa] (input de búsqueda + menú filtrado + "Sin coincidencias") con
 * un ítem "crear" cuando `createLabel != null` y lo tecleado no casa ninguna
 * opción: el valor emitido es el propio texto (el back-end lo resuelve/crea al
 * guardar). Editable: `MenuAnchorType.PrimaryEditable`.
 *
 * @param value valor confirmado (texto). Cambia SOLO al elegir/crear, no al teclear.
 * @param onValueChange se invoca con la etiqueta elegida o el texto a crear.
 * @param options etiquetas visibles a filtrar.
 * @param createLabel construye el texto del ítem de alta a partir de lo tecleado;
 *   `null` desactiva el alta (lista cerrada, como [ComboboxCcaa]).
 * @param emptyText mensaje cuando no hay coincidencias NI alta que ofrecer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    createLabel: ((String) -> String)? = null,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    val colors = RecreColors.current
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(query, options) {
            if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
        }
    val queryTrim = query.trim()
    val hayExacta =
        remember(query, options) {
            options.any { it.trim().equals(queryTrim, ignoreCase = true) }
        }
    val ofrecerCrear = createLabel != null && queryTrim.isNotEmpty() && !hayExacta

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (expanded) query else value,
            onValueChange = { query = it },
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = recreFieldColors(),
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filtered.forEach { opt ->
                val selected = opt == value
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onValueChange(opt)
                        query = ""
                        expanded = false
                    },
                    trailingIcon =
                        if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.ring,
                                )
                            }
                        } else {
                            null
                        },
                )
            }
            if (ofrecerCrear) {
                DropdownMenuItem(
                    text = { Text(createLabel!!(queryTrim)) },
                    onClick = {
                        onValueChange(queryTrim)
                        query = ""
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = colors.ring)
                    },
                )
            } else if (filtered.isEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text(
                            emptyText,
                            color = colors.mutedStrong,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    },
                    onClick = {},
                )
            }
        }
    }
}
```

- [ ] **Step 2: Añadir el adaptador `GestionAutocomplete`** — al final de `feature/gestion/components/FormFields.kt`

Añade imports: `import com.recre.app.feature.gestion.FkOption` y `import com.recre.app.ui.components.FieldAutocomplete`.

```kotlin
/**
 * Autocomplete editable con alta al vuelo, sobre opciones de FK (`FkOption`).
 * Muestra/emite la **etiqueta** (texto); el `id` lo usa el ViewModel para la
 * cascada. Delega en `FieldAutocomplete` del design system. Con `createLabel`
 * ofrece "Crear «lo tecleado»" cuando no casa ninguna opción.
 */
@Composable
fun GestionAutocomplete(
    label: String,
    value: String,
    options: List<FkOption>,
    onValueChange: (String) -> Unit,
    emptyText: String,
    createLabel: (String) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    FieldAutocomplete(
        value = value,
        onValueChange = onValueChange,
        options = options.map { it.label },
        label = label,
        emptyText = emptyText,
        createLabel = createLabel,
        enabled = enabled,
        placeholder = placeholder,
        modifier = modifier,
    )
}
```

- [ ] **Step 3: Verificar que compila**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add "android/app/src/main/java/com/recre/app/ui/components/Field.kt" "android/app/src/main/java/com/recre/app/feature/gestion/components/FormFields.kt"
git commit -m "feat(android): FieldAutocomplete editable + GestionAutocomplete con alta (T-271)"
```

---

## Task 4: Cablear el ViewModel, la Screen y los strings

**Files:**
- Modify: `android/app/src/main/java/com/recre/app/feature/gestion/maquinas/MaquinaFormViewModel.kt`
- Modify: `android/app/src/main/java/com/recre/app/feature/gestion/maquinas/MaquinaFormScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `CatalogoRepository` (Task 2), `fabricantesComoOpciones`/`modelosDeFabricante` (Task 2), `FkOption` (Task 1), `GestionAutocomplete` (Task 3).

**Contexto:** con el nuevo `GestionAutocomplete`, `onValueChange` se dispara SOLO al elegir/crear (no al teclear: la búsqueda es interna al combo). Por eso `onFabricanteChange` puede actuar como **commit**: fija el fabricante, limpia el modelo y recalcula las opciones de modelo. En **edición**, `init` precarga `fabricante`/`modelo` directamente (sin pasar por `onFabricanteChange`), así que el modelo heredado no se borra. Los campos siguen siendo `String` y `guardar()` no cambia.

- [ ] **Step 1: Añadir los strings** — `res/values/strings.xml`

Inserta tras `<string name="gestion_maquina_fabricante">Fabricante</string>`:

```xml
    <string name="gestion_catalogo_crear">Crear «%1$s»</string>
    <string name="gestion_catalogo_sin_coincidencias">Sin coincidencias</string>
    <string name="gestion_maquina_modelo_sin_fabricante">Elige primero un fabricante</string>
```

- [ ] **Step 2: `MaquinaFormViewModel.kt` — imports, constructor y estado**

Añade imports:

```kotlin
import com.recre.app.core.data.repository.CatalogoRepository
import com.recre.app.core.data.repository.FabricanteCatalogo
import com.recre.app.core.data.repository.ModeloCatalogo
import com.recre.app.feature.gestion.FkOption
import com.recre.app.feature.gestion.fabricantesComoOpciones
import com.recre.app.feature.gestion.modelosDeFabricante
import timber.log.Timber
```

Añade el repositorio al constructor (junto a los demás `@Inject`):

```kotlin
    private val catalogoRepository: CatalogoRepository,
```

Añade dos campos a `MaquinaFormUiState` (tras `online`):

```kotlin
    val fabricantesDisponibles: List<FkOption> = emptyList(),
    val modelosDisponibles: List<FkOption> = emptyList(),
```

- [ ] **Step 3: `MaquinaFormViewModel.kt` — cache privada, carga on-demand y cascada**

Añade dos campos privados (tras `private val maquinaId`):

```kotlin
    // Catálogo crudo cacheado para recalcular la cascada sin re-consultar.
    private var catalogoFabricantes: List<FabricanteCatalogo> = emptyList()
    private var catalogoModelos: List<ModeloCatalogo> = emptyList()
```

Dentro de `init { … }`, añade un lanzamiento para cargar el catálogo (p. ej. tras el `launch` de conectividad):

```kotlin
        viewModelScope.launch { cargarCatalogo() }
```

Añade el método de carga (métodos privados del ViewModel):

```kotlin
    private suspend fun cargarCatalogo() {
        when (val r = catalogoRepository.cargar()) {
            is GestionResult.Success -> {
                catalogoFabricantes = r.value.fabricantes
                catalogoModelos = r.value.modelos
                _state.update {
                    it.copy(
                        fabricantesDisponibles = fabricantesComoOpciones(r.value.fabricantes),
                        modelosDisponibles =
                            modelosDeFabricante(it.fabricante, r.value.fabricantes, r.value.modelos),
                    )
                }
            }
            is GestionResult.Failure ->
                // Silencioso: sin catálogo el usuario aún teclea libre; la RPC crea al guardar.
                Timber.w("Catálogo de máquinas no disponible: %s", r.code)
        }
    }
```

Reemplaza `onFabricanteChange` (deja `onModeloChange` igual):

```kotlin
    fun onFabricanteChange(v: String) = _state.update { s ->
        // Con el autocomplete, esto es un COMMIT (elegir/crear), no cada tecla.
        val cambiaFabricante = v.trim() != s.fabricante.trim()
        s.copy(
            fabricante = v,
            // Cambiar de fabricante invalida el modelo (pertenece a un fabricante).
            modelo = if (cambiaFabricante) "" else s.modelo,
            modelosDisponibles = modelosDeFabricante(v, catalogoFabricantes, catalogoModelos),
        )
    }
```

- [ ] **Step 4: `MaquinaFormScreen.kt` — sustituir los dos campos**

Añade el import `import com.recre.app.feature.gestion.components.GestionAutocomplete` (el paquete `components` ya se importa para `GestionTextField`/`GestionDropdown`).

En `MaquinaIdentificacion`, reemplaza el bloque `Row { GestionTextField(fabricante) … GestionTextField(modelo) }` (los dos campos) por:

```kotlin
    val crearFmt = stringResource(R.string.gestion_catalogo_crear)
    val sinCoincidencias = stringResource(R.string.gestion_catalogo_sin_coincidencias)
    val modeloSinFabricante = stringResource(R.string.gestion_maquina_modelo_sin_fabricante)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GestionAutocomplete(
            label = stringResource(R.string.gestion_maquina_fabricante),
            value = state.fabricante,
            options = state.fabricantesDisponibles,
            onValueChange = viewModel::onFabricanteChange,
            emptyText = sinCoincidencias,
            createLabel = { crearFmt.format(it) },
            modifier = Modifier.weight(1f),
        )
        GestionAutocomplete(
            label = stringResource(R.string.gestion_maquina_modelo),
            value = state.modelo,
            options = state.modelosDisponibles,
            onValueChange = viewModel::onModeloChange,
            emptyText = sinCoincidencias,
            createLabel = { crearFmt.format(it) },
            enabled = state.fabricante.isNotBlank(),
            placeholder = if (state.fabricante.isBlank()) modeloSinFabricante else null,
            modifier = Modifier.weight(1f),
        )
    }
```

- [ ] **Step 5: Compilar (valida wiring, Hilt y strings)**

Run: `JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Resuelve `R.string.gestion_catalogo_*`, la inyección de `CatalogoRepository` en el VM y el uso de `GestionAutocomplete`.)

- [ ] **Step 6: Suite unitaria completa (no regresión)**

Run: `LC_ALL=es_ES.utf8 JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (incluye `CatalogoCascadaTest` y los tests previos del módulo).

- [ ] **Step 7: Commit**

```bash
git add "android/app/src/main/java/com/recre/app/feature/gestion/maquinas/MaquinaFormViewModel.kt" "android/app/src/main/java/com/recre/app/feature/gestion/maquinas/MaquinaFormScreen.kt" "android/app/src/main/res/values/strings.xml"
git commit -m "feat(android): cascada fabricante→modelo con autocomplete en el form de máquina (T-271)"
```

- [ ] **Step 8: Prueba manual (anotar, no automatizada)**

En dispositivo/emulador con sesión de gestión, alta/edición de máquina: Fabricante es un combo editable; teclear filtra; elegir un fabricante habilita Modelo con solo sus modelos; teclear un nombre nuevo ofrece "Crear «…»"; guardar crea la máquina (la RPC da de alta el catálogo). En edición se muestran el fabricante/modelo actuales (aunque no estén catalogados).

---

## Notas de diseño (decisiones cerradas)

- **Solo lectura + texto en guardar** (mejora sobre spec §6.7, igual que B3 web): no se llama `crear_fabricante`/`crear_modelo` eager; "Crear «X»" fija el texto y la RPC `crear_maquina` resuelve/crea al guardar. Menos superficie, sin filas de catálogo huérfanas si se abandona el formulario.
- **Layering:** `FieldAutocomplete` (ui) es String-based (no conoce `FkOption`); `GestionAutocomplete` (feature) y el ViewModel manejan `FkOption`; el repositorio (data) devuelve tipos propios. Nadie invierte la dependencia.
- **Cascada en commit, no por tecla:** el combo confirma el valor solo al elegir/crear, así que limpiar el modelo al cambiar de fabricante no molesta al teclear. En edición el modelo heredado se preserva (init no pasa por el commit).
- **`onFabricanteChange` cambia de semántica** (antes: cada tecla; ahora: commit). Es seguro porque su único llamador pasa a ser `GestionAutocomplete`, que solo emite al elegir/crear.
- **Fallo de catálogo = silencioso:** sin catálogo el combo queda sin opciones pero el usuario teclea libre; la RPC resuelve al guardar. No bloquea el formulario.

## Self-review (writing-plans)

- **Cobertura spec §6.7:** combo editable nuevo (`FieldAutocomplete`/`GestionAutocomplete`, `PrimaryEditable`, item "Crear") ✓; cascada `fabricanteId` ✓; `FkOption` promovido a común ✓; on-demand fuera de `TABLAS_SYNC` ✓; repo de catálogo ✓ (solo lectura, refinamiento documentado). Escritura eager de catálogo: deliberadamente omitida (alineado con B3).
- **Sin placeholders:** código literal en todos los pasos; ediciones a ficheros existentes localizadas por símbolo/anchor. Rutas absolutas de paquete.
- **Consistencia de tipos:** `FabricanteCatalogo`/`ModeloCatalogo` (data) definidos en Task 2, consumidos por la cascada (Task 2) y el VM (Task 4); `FkOption` (Task 1) fluye VM→`GestionAutocomplete`→`FieldAutocomplete(String)`. `MaquinaInput`/DTO/RPC intactos.
