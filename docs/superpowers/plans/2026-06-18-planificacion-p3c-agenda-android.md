# Planificación de recaudación — P3c: agenda del técnico (Android) · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans.

**Goal:** El home de Locales (Android) muestra, para el técnico, su **agenda**: héroe "X por recaudar" (nº de locales pendientes), un `StatusChip` de estado por local (atrasado/toca hoy/al día/sin planificar), orden pendientes-primero y filtros pendientes/al día. Cierra el diferido del héroe de Fase 2a.

**Architecture:** La agenda es un dato **derivado en el servidor** (vista `v_agenda_operario`, P3a; usa `now()` en la TZ de la empresa) → se trae **online** vía un `AgendaRemoteDataSource` (PostgREST, patrón del repo) y se **superpone** el `estado` sobre la lista de locales de Room por `id`. **Offline o sin agenda cargada = comportamiento actual** (lista plana, sin héroe/chips): así no se rompe el offline-first.

**Tech Stack:** Kotlin, Compose, Hilt, supabase-kt (Postgrest). Sin cambios de dinero.

**Spec:** §4.2 (estados), §6.2 (home Android). Vista: `v_agenda_operario` (PR #83).

## File Structure

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `core/data/repository/InventoryModels.kt` | enum `EstadoAgenda` | **Modificar** (añadir enum) |
| `core/data/remote/AgendaRemoteDataSource.kt` | GET a `v_agenda_operario` → `Map<id, EstadoAgenda>` | **Crear** |
| `feature/locales/LocalesViewModel.kt` | inyecta el data source; `agendaFlow`; campos de UiState; filtros | **Modificar** |
| `feature/locales/components/LocalCard.kt` | param opcional `estado` → `StatusChip` | **Modificar** |
| `feature/locales/LocalesScreen.kt` | héroe + `FilterChipRow` + usar items con estado | **Modificar** |
| `app/src/main/res/values/strings.xml` | textos (héroe, estados, filtros) | **Modificar** |

## Decisiones
- **Online overlay, no Room.** La vista es server-derived (TZ "hoy"); no se sincroniza a Room. Si la llamada falla (offline) → `agendaDisponible=false` → home actual sin agenda.
- **Filtros con `FilterChipRow` (multi-select) como exclusivo-suave:** chips `pendientes` / `al_dia`; vacío = todos; ambos = pendientes∪al_día (sin_planificar solo en "todos").

---

### Task 1: enum `EstadoAgenda`
**Files:** Modify `core/data/repository/InventoryModels.kt`
- [ ] Añade:
```kotlin
/** Estado de agenda de un local (derivado en v_agenda_operario, P3a). */
enum class EstadoAgenda { SIN_PLANIFICAR, AL_DIA, TOCA_HOY, ATRASADO;
    val esPendiente: Boolean get() = this == TOCA_HOY || this == ATRASADO
    companion object {
        fun desde(valor: String?): EstadoAgenda = when (valor) {
            "atrasado" -> ATRASADO; "toca_hoy" -> TOCA_HOY; "al_dia" -> AL_DIA; else -> SIN_PLANIFICAR
        }
    }
}
```

### Task 2: `AgendaRemoteDataSource`
**Files:** Create `core/data/remote/AgendaRemoteDataSource.kt`
- [ ] Patrón `DeudasRemoteDataSource` (supabase-kt). DTO `@Serializable` privado con `@SerialName`. Devuelve `Map<localId, EstadoAgenda>`. Lanza en error (la VM captura).
```kotlin
@Singleton
class AgendaRemoteDataSource @Inject constructor(private val supabase: SupabaseClient) {
    @Serializable
    private data class AgendaRow(
        @SerialName("local_id") val localId: String,
        val estado: String,
    )
    suspend fun obtenerEstados(empresaId: String): Map<String, EstadoAgenda> =
        supabase.from("v_agenda_operario")
            .select(Columns.list("local_id", "estado")) { filter { eq("empresa_id", empresaId) } }
            .decodeList<AgendaRow>()
            .associate { it.localId to EstadoAgenda.desde(it.estado) }
}
```
- [ ] Verifica el import correcto de `Columns` (en RecaudacionHistoricaRemoteDataSource se usa `Columns.raw(...)`; usar `Columns.list(...)` o `Columns.raw("local_id,estado")` según la API instalada).

### Task 3: ViewModel — agenda + filtros
**Files:** Modify `feature/locales/LocalesViewModel.kt`
- [ ] Inyecta `private val agendaRemoteDataSource: AgendaRemoteDataSource`.
- [ ] `filtroFlow = MutableStateFlow<Set<String>>(emptySet())`; `onFiltroToggle(key, now)`, `onFiltroClear()`.
- [ ] `agendaFlow`: re-fetch al cambiar empresa o tras sync. `emit` `AgendaCarga(disponible, mapa)`; en `runCatching` fallo → `AgendaCarga(false, emptyMap())`.
- [ ] UiState: añade `agenda: Map<String, EstadoAgenda> = emptyMap()`, `agendaDisponible: Boolean = false`, `filtro: Set<String> = emptySet()`. Añade `localesPendientes: Int` (de `agenda.values.count{it.esPendiente}`). NO reutilizar `pendientes` (es la cola de subida).
- [ ] Computed `itemsVisibles: List<LocalAgendaItem>` (data class `LocalAgendaItem(val local: LocalResumen, val estado: EstadoAgenda)`): filtro texto (como hoy) → map con `agenda[id] ?: SIN_PLANIFICAR` → filtro chip → orden ATRASADO(0)→TOCA_HOY(1)→AL_DIA(2)→SIN_PLANIFICAR(3) luego nombre.
- [ ] Reestructura el `combine`: anida (combine de los 5 actuales → Partial) y un `combine(partial, agendaFlow, filtroFlow)` externo.

### Task 4: LocalCard — chip de estado
**Files:** Modify `feature/locales/components/LocalCard.kt`
- [ ] Añade `estado: EstadoAgenda? = null`. Si `!= null && != SIN_PLANIFICAR` (o siempre que agenda disponible) pinta `StatusChip(role, label, icon, size=SM)` con mapeo: ATRASADO→DANGER (icon `Icons.Filled.Warning`), TOCA_HOY→WARNING (`Icons.Filled.Schedule`/`Today`), AL_DIA→SUCCESS (`Icons.Filled.CheckCircle`), SIN_PLANIFICAR→NEUTRAL (`Icons.Filled.HelpOutline`). Label vía `stringResource`. Mantiene el resto de la card.

### Task 5: LocalesScreen — héroe + filtros
**Files:** Modify `feature/locales/LocalesScreen.kt`
- [ ] Si `state.agendaDisponible`: encima del `SearchField`, un **héroe** (AppCard) "X por recaudar" = `state.localesPendientes` (texto + número grande, NO MoneyText: es un conteo). Debajo, `FilterChipRow(chips = [pendientes(count), al_dia(count)], selectedKeys = state.filtro, onToggle = vm::onFiltroToggle, onClear = vm::onFiltroClear, clearLabel)`.
- [ ] Render de la lista: usar `state.itemsVisibles` (en vez de `localesFiltrados`); `items(itemsVisibles, key = { it.local.id }) { item -> LocalCard(local = item.local, estado = if (state.agendaDisponible) item.estado else null, onClick = ...) }`.
- [ ] Si NO `agendaDisponible`: sin héroe ni chips (lista plana actual).

### Task 6: strings
**Files:** Modify `app/src/main/res/values/strings.xml`
- [ ] `locales_por_recaudar` ("Por recaudar"), `locales_por_recaudar_count` (plural), `agenda_estado_atrasado`/`_toca_hoy`/`_al_dia`/`_sin_planificar`, `agenda_filtro_pendientes`/`_al_dia`, `action_clear` (si no existe ya).

---

## Cierre de P3c
- [ ] `JAVA_HOME=/snap/android-studio/current/jbr LC_ALL=C.UTF-8 LANG=C.UTF-8 ./gradlew assembleDebug` (desde `android/`) → verde.
- [ ] Guardrail `SinMaterialPeladoTest` verde (no importar Material3 pelado en feature; usar AppCard/StatusChip/FilterChipRow de la librería).
- [ ] PR a `main`. T-266 (parte Android) → P3 completo en tasks.md.

**Salida:** el técnico abre la app y ve su ruta del día (pendientes primero, héroe real, estado por local). Con P3a (cálculo) + P3b (gestor web) + P3c (técnico Android), la Planificación de recaudación queda completa.
