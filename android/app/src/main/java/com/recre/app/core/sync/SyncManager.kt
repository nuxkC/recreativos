package com.recre.app.core.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.recre.app.core.data.repository.SyncRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Orquestador de sincronización: encola Work, observa estado y dispara
 * automáticamente al cambiar de empresa activa.
 *
 * Singleton inyectado en [com.recre.app.RecreApp] para garantizar que la
 * observación arranca al iniciar el proceso. Sin esa inyección, Hilt no
 * crearía el singleton hasta que un ViewModel lo pidiera y perderíamos
 * el primer Active del cold-start.
 */
@Singleton
class SyncManager @Inject constructor(
    private val workManager: WorkManager,
    private val sessionRepository: SessionRepository,
    private val syncRepository: SyncRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Arranca la observación del [SessionState]. Llamar exactamente una
     * vez desde [com.recre.app.RecreApp.onCreate].
     */
    fun start() {
        scope.launch {
            sessionRepository.state
                .map { state ->
                    (state as? SessionState.Active)?.empresa?.id
                }
                .distinctUntilChanged()
                .collect { empresaId ->
                    if (empresaId != null) {
                        encolarSincronizacion(empresaId)
                    }
                }
        }
    }

    /**
     * Encola una sincronización para la empresa indicada. Usa
     * `ExistingWorkPolicy.KEEP` para que múltiples llamadas seguidas
     * (cambio rápido de empresa, pull-to-refresh, etc.) no se acumulen:
     * la corrida en curso se respeta y la nueva se descarta.
     *
     * Si quieres forzar (p. ej. botón "sincronizar ahora" en T-65) usa
     * [forzarSincronizacion].
     */
    fun encolarSincronizacion(empresaId: String) {
        val request = OneTimeWorkRequestBuilder<RecreSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_INITIAL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            .setInputData(
                Data.Builder()
                    .putString(RecreSyncWorker.KEY_EMPRESA_ID, empresaId)
                    .build(),
            )
            .addTag(TAG_SYNC)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(empresaId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Igual que [encolarSincronizacion] pero reemplaza la corrida existente
     * (`ExistingWorkPolicy.REPLACE`). Usado por "forzar sincronización".
     */
    fun forzarSincronizacion(empresaId: String) {
        val request = OneTimeWorkRequestBuilder<RecreSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(RecreSyncWorker.KEY_EMPRESA_ID, empresaId)
                    .build(),
            )
            .addTag(TAG_SYNC)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(empresaId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Estado del Work activo de una empresa. La UI lo usa para mostrar un
     * spinner mientras la sync está en marcha.
     */
    fun observarEstado(empresaId: String): Flow<SyncStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(empresaId))
            .map { infos ->
                val info = infos.firstOrNull()
                when (info?.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED,
                    -> SyncStatus.Running
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED,
                    null,
                    -> SyncStatus.Idle
                }
            }
            .distinctUntilChanged()

    /**
     * Fecha de la última sync exitosa para [empresaId].
     */
    fun observarUltimaSync(empresaId: String): Flow<Instant?> =
        syncRepository.observarUltimaSync(empresaId)

    /**
     * `true` si han pasado más de 48 h desde la última sync exitosa, o si
     * nunca se ha sincronizado. Lo usará T-59 para bloquear recaudaciones.
     */
    fun observarSyncStale(empresaId: String): Flow<Boolean> =
        observarUltimaSync(empresaId).map { last ->
            last == null || Duration.between(last, Instant.now()) > MAX_SYNC_AGE
        }

    private fun uniqueWorkName(empresaId: String): String =
        "${RecreSyncWorker.UNIQUE_WORK_PREFIX}$empresaId"

    private companion object {
        const val TAG_SYNC = "recre.sync"
        const val BACKOFF_INITIAL_SECONDS = 30L
        val MAX_SYNC_AGE: Duration = Duration.ofHours(48)
    }
}
