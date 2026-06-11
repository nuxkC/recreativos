package com.recre.app.core.sync

import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Suscripción Realtime a las tablas que determinan el baseline (contadores) de
 * una recaudación. Su única responsabilidad es DETECTAR que algo cambió en el
 * servidor y disparar un re-sync; NO aplica deltas en local.
 *
 * Por qué re-sync y no delta: el baseline lo calcula el servidor
 * (`obtener_baseline` / `v_instalacion_actual`), que es una vista —Realtime no
 * emite sobre vistas—. Suscribimos las tablas BASE que la alimentan y, ante
 * cualquier cambio, reusamos [SyncManager.encolarSincronizacion] (política KEEP,
 * que coalesce ráfagas), de modo que el recálculo sigue siendo server-side
 * (SSOT) y la UI se refresca por los Flows de Room ya existentes.
 *
 * Multi-tenant: no filtramos por `empresa_id` a mano; postgres_changes aplica
 * las policies RLS `*_select` con el JWT del técnico, así que solo llegan filas
 * de su empresa.
 *
 * Ciclo de vida: mismo patrón que [SyncManager]. Singleton inyectado en
 * [com.recre.app.RecreApp]; [start] observa el [SessionState] y mantiene un
 * canal por empresa activa, recreándolo al cambiar y cerrándolo al desloguear.
 */
@Singleton
class RealtimeManager @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Canal de la empresa activa; se reemplaza en cada transición de empresa. */
    private var canalJob: Job? = null

    /**
     * Arranca la observación del [SessionState]. Llamar exactamente una vez
     * desde [com.recre.app.RecreApp.onCreate].
     */
    fun start() {
        scope.launch {
            sessionRepository.state
                .map { (it as? SessionState.Active)?.empresa?.id }
                .distinctUntilChanged()
                .collect { empresaId ->
                    canalJob?.cancelAndJoin()
                    canalJob = empresaId?.let { suscribir(it) }
                }
        }
    }

    private fun suscribir(empresaId: String): Job = scope.launch {
        // Idempotente; abre el WebSocket si aún no está conectado.
        supabase.realtime.connect()
        val canal = supabase.realtime.channel("recre-operacional-$empresaId")

        TABLAS_BASELINE.forEach { tabla ->
            canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = tabla }
                .onEach {
                    Timber.i("Realtime: cambio en %s → re-sync %s", tabla, empresaId)
                    syncManager.encolarSincronizacion(empresaId)
                }
                .launchIn(this)
        }

        canal.subscribe()

        try {
            // El canal vive mientras esta empresa siga activa; al cancelar el
            // job (cambio de empresa / logout) desuscribimos limpiamente.
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    canal.unsubscribe()
                    supabase.realtime.removeChannel(canal)
                }.onFailure { Timber.w(it, "Realtime: fallo cerrando canal %s", empresaId) }
            }
        }
    }

    private companion object {
        /**
         * Tablas base que alimentan el baseline (ver migración
         * 20260611140000). `recaudacion` y `cambio_placa` son las de mayor
         * riesgo (otro técnico recauda / cambia placa de la misma máquina).
         */
        val TABLAS_BASELINE = listOf(
            "recaudacion",
            "cambio_placa",
            "instalacion",
            "maquina",
        )
    }
}
