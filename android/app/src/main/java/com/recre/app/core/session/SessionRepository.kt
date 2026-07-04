package com.recre.app.core.session

import com.recre.app.core.data.local.EmpresaPreferences
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.data.repository.EmpresaRepository
import com.recre.app.core.util.DomainResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Singleton que centraliza la sesión: auth + empresa activa + membresías.
 *
 * Se mantiene fuera del ViewModel para sobrevivir a recreaciones y para
 * que cualquier feature pueda inyectarlo y leer la empresa activa en O(1)
 * sin volver a consultar Supabase.
 *
 * Reglas:
 * - Al iniciar sesión, dispara automáticamente `refreshMembresias()`.
 * - Al cerrar sesión, limpia membresías y `empresa_activa_id`.
 * - Si la `empresa_activa_id` persistida no coincide con ninguna membresía
 *   válida, se descarta y el estado pasa a [SessionState.NeedsEmpresaSelection]
 *   o auto-selecciona si solo hay una.
 */
@Singleton
class SessionRepository internal constructor(
    private val authRepository: AuthRepository,
    private val empresaRepository: EmpresaRepository,
    private val empresaPreferences: EmpresaPreferences,
    /**
     * Scope propio del singleton: vive todo el proceso. Solo los tests lo
     * inyectan (constructor internal) para volver determinista el orden de
     * las corrutinas; producción entra por el secundario @Inject con IO.
     */
    private val scope: CoroutineScope,
) {

    @Inject
    constructor(
        authRepository: AuthRepository,
        empresaRepository: EmpresaRepository,
        empresaPreferences: EmpresaPreferences,
    ) : this(
        authRepository,
        empresaRepository,
        empresaPreferences,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    /**
     * Cache reactiva de membresías. Se actualiza tras cada refresh o
     * al cerrar sesión.
     */
    private val membresiasState = MutableStateFlow<MembresiasResult>(MembresiasResult.Loading)

    val state: StateFlow<SessionState> = combine(
        authRepository.observeIsLoggedIn(),
        membresiasState,
        empresaPreferences.empresaActivaIdFlow,
    ) { isLoggedIn, membresias, empresaActivaId ->
        when {
            !isLoggedIn -> SessionState.NotAuthenticated
            membresias is MembresiasResult.Loading -> SessionState.Loading
            membresias is MembresiasResult.Failure -> SessionState.Loading
            membresias is MembresiasResult.Success -> resolveActive(membresias.value, empresaActivaId)
            else -> SessionState.Loading
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SessionState.Loading,
        )

    init {
        // Refresca membresías cada vez que cambie el estado de auth.
        scope.launch {
            authRepository.observeIsLoggedIn().collect { isLoggedIn ->
                if (isLoggedIn) {
                    refreshMembresias()
                } else {
                    // Loading y no `Success(emptyList())`: si quedara la lista
                    // vacía cacheada, el combine del próximo login podría
                    // re-emitirla antes de que refreshMembresias() marcara
                    // Loading → flash de SinAcceso (NoMemberships espurio).
                    membresiasState.value = MembresiasResult.Loading
                    empresaPreferences.setEmpresaActivaId(null)
                }
            }
        }
    }

    /**
     * Vuelve a leer las membresías del usuario desde el backend. Útil tras
     * un login, una invitación aceptada o un pull-to-refresh manual.
     */
    suspend fun refreshMembresias(): DomainResult<List<Membresia>> {
        membresiasState.value = MembresiasResult.Loading
        val result = empresaRepository.listarMembresiasActivas()
        membresiasState.value = when (result) {
            is DomainResult.Success -> MembresiasResult.Success(result.value)
            is DomainResult.Failure -> MembresiasResult.Failure(result.error.message)
        }
        return result
    }

    /**
     * Persiste la elección de empresa. Si el [empresaId] no se corresponde
     * con una membresía válida, no toca preferencias y devuelve `false`.
     */
    suspend fun seleccionarEmpresa(empresaId: String): Boolean {
        val membresias = (membresiasState.value as? MembresiasResult.Success)?.value
            ?: empresaRepository.listarMembresiasActivas().let { result ->
                when (result) {
                    is DomainResult.Success -> {
                        membresiasState.value = MembresiasResult.Success(result.value)
                        result.value
                    }
                    is DomainResult.Failure -> return false
                }
            }
        if (membresias.none { it.empresa.id == empresaId }) return false
        empresaPreferences.setEmpresaActivaId(empresaId)
        return true
    }

    /**
     * Borra la empresa activa. La UI volverá a [SessionState.NeedsEmpresaSelection]
     * (o auto-selección si solo queda una).
     */
    suspend fun limpiarEmpresaActiva() {
        empresaPreferences.setEmpresaActivaId(null)
    }

    /**
     * Cierra sesión completamente. Delega el signOut en [AuthRepository];
     * el collector interno del `init {}` se encarga de limpiar la empresa
     * activa y las membresías cuando observe que la sesión terminó.
     *
     * Con supabase-kt el cambio a `NotAuthenticated` es síncrono local, así
     * que la transición es atómica desde el punto de vista de la UI.
     */
    suspend fun cerrarSesion(): DomainResult<Unit> = authRepository.signOut()

    /**
     * Versión one-shot del estado: espera al primer valor estable distinto
     * de Loading. Útil en tests y splash screens.
     */
    suspend fun awaitFirstResolved(): SessionState =
        state.first { it !is SessionState.Loading }

    private fun resolveActive(
        membresias: List<Membresia>,
        empresaActivaId: String?,
    ): SessionState {
        if (membresias.isEmpty()) return SessionState.NoMemberships

        val activa = empresaActivaId?.let { id ->
            membresias.firstOrNull { it.empresa.id == id }
        }
        if (activa != null) {
            return SessionState.Active(membresia = activa, membresias = membresias)
        }
        // Cookie inválida o no presente: si solo hay una, autoselect implícito al
        // entrar en /seleccionarEmpresa. Aquí solo informamos del estado.
        return SessionState.NeedsEmpresaSelection(membresias = membresias)
    }

    private sealed interface MembresiasResult {
        data object Loading : MembresiasResult
        data class Success(val value: List<Membresia>) : MembresiasResult
        data class Failure(val message: String?) : MembresiasResult
    }
}
