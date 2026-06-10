package com.recre.app.core.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Encola y observa el [RecaudacionUploadWorker] por empresa.
 *
 * Patrón gemelo del [SyncManager] (T-51): un Worker UNIQUE por empresa,
 * `KEEP` para no acumular runs cuando ya hay uno en curso, `REPLACE`
 * para reintentos forzados.
 *
 * El manager se invoca:
 *   - Tras `RecaudacionRepository.encolar(...)` desde el flujo de
 *     recaudación, para empezar a subir cuanto antes.
 *   - Periódicamente al recuperar conectividad (WorkManager constraint
 *     `NetworkType.CONNECTED` ya cubre este caso).
 *   - Manualmente desde Ajustes (T-65 "Sincronizar ahora").
 */
@Singleton
class RecaudacionUploadManager @Inject constructor(
    private val workManager: WorkManager,
    private val sessionRepository: SessionRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Arranca la observación: cada vez que cambie la empresa activa,
     * encola un upload. Llamado desde [com.recre.app.RecreApp.onCreate].
     */
    fun start() {
        scope.launch {
            sessionRepository.state
                .map { (it as? SessionState.Active)?.empresa?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { empresaId -> encolar(empresaId) }
        }
    }

    /**
     * Encola un upload UNIQUE para [empresaId]. Si ya hay uno corriendo,
     * lo respeta (`KEEP`) — la cola se vaciará al terminar el actual.
     */
    fun encolar(empresaId: String) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(empresaId),
            ExistingWorkPolicy.KEEP,
            buildRequest(empresaId),
        )
    }

    /** Igual que [encolar] pero reemplaza la corrida en curso. */
    fun forzar(empresaId: String) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(empresaId),
            ExistingWorkPolicy.REPLACE,
            buildRequest(empresaId),
        )
    }

    /**
     * Espera con [timeout] a que el upload activo termine. Devuelve el
     * estado final (SUCCEEDED / FAILED / CANCELLED) o `null` si vence el
     * timeout. Lo usa el flujo de recaudación: tras encolar, espera unos
     * segundos por si hay red para mostrar "subido" en lugar de
     * "pendiente".
     */
    suspend fun esperarFinalizacion(
        empresaId: String,
        timeout: Duration = 5.seconds,
    ): WorkInfo.State? = withTimeoutOrNull(timeout) {
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(empresaId))
            .map { it.firstOrNull()?.state }
            .first { state ->
                state == WorkInfo.State.SUCCEEDED ||
                    state == WorkInfo.State.FAILED ||
                    state == WorkInfo.State.CANCELLED
            }
    }

    fun observarEstado(empresaId: String): Flow<UploadStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(empresaId))
            .map { infos ->
                val state = infos.firstOrNull()?.state
                when (state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED,
                    -> UploadStatus.Subiendo
                    WorkInfo.State.FAILED -> UploadStatus.ConErrores
                    else -> UploadStatus.Idle
                }
            }
            .distinctUntilChanged()

    private fun buildRequest(empresaId: String) = OneTimeWorkRequestBuilder<RecaudacionUploadWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            BACKOFF_INITIAL_SECONDS,
            TimeUnit.SECONDS,
        )
        .setInputData(
            Data.Builder()
                .putString(RecaudacionUploadWorker.KEY_EMPRESA_ID, empresaId)
                .build(),
        )
        .addTag(TAG)
        .build()

    private fun uniqueWorkName(empresaId: String): String =
        "${RecaudacionUploadWorker.UNIQUE_WORK_PREFIX}$empresaId"

    private companion object {
        const val TAG = "recre.recaudacion.upload"
        const val BACKOFF_INITIAL_SECONDS = 30L
    }
}

/** Estado simplificado para la UI. */
sealed interface UploadStatus {
    data object Idle : UploadStatus
    data object Subiendo : UploadStatus
    data object ConErrores : UploadStatus
}
