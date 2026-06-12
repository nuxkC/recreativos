package com.recre.app.core.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Encola y observa el [AveriaUploadWorker] por empresa (T-222).
 *
 * Patrón gemelo del [RecaudacionUploadManager]: un Worker UNIQUE por empresa,
 * `KEEP` para no acumular runs, `REPLACE` para forzar. Se arranca en
 * [com.recre.app.RecreApp.onCreate] y, además, el técnico lo dispara tras
 * reportar una avería para empezar a subir cuanto antes.
 */
@Singleton
class AveriaUploadManager @Inject constructor(
    private val workManager: WorkManager,
    private val sessionRepository: SessionRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Arranca la observación: cada cambio de empresa activa encola un upload. */
    fun start() {
        scope.launch {
            sessionRepository.state
                .map { (it as? SessionState.Active)?.empresa?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { empresaId -> encolar(empresaId) }
        }
    }

    /** Encola un upload UNIQUE para [empresaId] (respeta el que ya esté en curso). */
    fun encolar(empresaId: String) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(empresaId),
            ExistingWorkPolicy.KEEP,
            buildRequest(empresaId),
        )
    }

    private fun buildRequest(empresaId: String) = OneTimeWorkRequestBuilder<AveriaUploadWorker>()
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
                .putString(AveriaUploadWorker.KEY_EMPRESA_ID, empresaId)
                .build(),
        )
        .addTag(TAG)
        .build()

    private fun uniqueWorkName(empresaId: String): String =
        "${AveriaUploadWorker.UNIQUE_WORK_PREFIX}$empresaId"

    private companion object {
        const val TAG = "recre.averia.upload"
        const val BACKOFF_INITIAL_SECONDS = 30L
    }
}
