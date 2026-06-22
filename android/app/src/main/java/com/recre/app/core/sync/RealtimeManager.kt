package com.recre.app.core.sync

import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Suscripción Realtime a las tablas operacionales: DETECTA que algo cambió en el
 * servidor y refresca la UI en vivo. Dos mecanismos según dónde viva el dato:
 *
 *  - [TABLAS_SYNC] (datos que viven en Room vía el sync masivo): un cambio
 *    dispara [SyncManager.encolarSincronizacion] (política KEEP, coalesce
 *    ráfagas); el recálculo sigue server-side (SSOT) y la UI se refresca por los
 *    Flows de Room existentes (gestión, detalle de local, deudas, incidencias…).
 *  - [revision]: ante CUALQUIER cambio se incrementa un contador que los
 *    ViewModels que leen del servidor BAJO DEMANDA (vistas/tablas que no están en
 *    Room: agenda, histórico, deudas-ledger, averías, alertas) observan para
 *    refetch inmediato, sin esperar al ciclo de sync.
 *
 * Por qué re-sync/refetch y no aplicar deltas: muchas pantallas leen VISTAS
 * (`v_instalacion_actual`, `v_agenda_operario`, `v_recaudacion_historica`,
 * `v_credito_local_saldo`) y Realtime no emite sobre vistas; suscribimos sus
 * tablas BASE y reconsultamos, manteniendo el SSOT en el servidor.
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

    private val _revision = MutableStateFlow(0L)

    /**
     * Contador monótono que se incrementa ante cualquier cambio server-side de
     * las tablas suscritas. Señal "algo cambió, refresca" para los ViewModels
     * que leen del servidor bajo demanda. Es global (no por empresa); los
     * consumidores combinan con su propio `empresaId`/argumentos.
     */
    val revision: StateFlow<Long> = _revision.asStateFlow()

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
        val canal = supabase.channel("recre-operacional-$empresaId")

        TABLAS.forEach { tabla ->
            canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = tabla }
                .onEach {
                    // Señal "algo cambió" para los refetch bajo demanda…
                    _revision.update { it + 1 }
                    // …y, si el dato vive en Room, re-sync para refrescar sus Flows.
                    if (tabla in TABLAS_SYNC) {
                        syncManager.encolarSincronizacion(empresaId)
                    }
                    Timber.i("Realtime: cambio en %s (rev=%d, sync=%b)", tabla, _revision.value, tabla in TABLAS_SYNC)
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
         * Tablas cuyos datos viven en Room (vía el sync masivo): un cambio
         * dispara re-sync y las pantallas con Flows de Room se refrescan solas.
         * Alimentan también las vistas que el sync consume (v_instalacion_actual,
         * v_credito_local_saldo, v_instalacion_tolva) y la agenda.
         */
        val TABLAS_SYNC = setOf(
            "recaudacion",
            "cambio_placa",
            "instalacion",
            "maquina",
            "local",
            "licencia",
            "credito_local",
            "recuperacion",
            "lectura_no_recaudada",
        )

        /**
         * Tablas que la app lee del servidor BAJO DEMANDA (no Room): solo
         * incrementan [revision] para refetch directo de sus ViewModels.
         */
        val TABLAS_SOLO_REVISION = setOf(
            "alerta",
            "averia",
            "averia_recambio",
        )

        /** Todo lo que escuchamos (publicado en supabase_realtime). */
        val TABLAS = TABLAS_SYNC + TABLAS_SOLO_REVISION
    }
}
