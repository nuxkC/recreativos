package com.recre.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recre.app.core.data.repository.SyncRepository
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Worker que ejecuta una sincronización del inventario.
 *
 * - Recibe el `empresaId` por inputData.
 * - Devuelve `Result.success()` si OK, `Result.retry()` si fue un error de
 *   red (WorkManager reintenta con backoff exponencial), o `Result.failure()`
 *   para cualquier otro error que no se vaya a resolver con un retry
 *   (auth, validación, etc.) — el usuario tendrá que actuar.
 */
@HiltWorker
class RecreSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val empresaId = inputData.getString(KEY_EMPRESA_ID)
            ?: return Result.failure()

        return when (val result = syncRepository.sincronizar(empresaId)) {
            is DomainResult.Success -> {
                val summary = result.value
                Timber.i(
                    "Sync OK %s — locales=%d maquinas=%d licencias=%d instalaciones=%d",
                    summary.empresaId,
                    summary.locales,
                    summary.maquinas,
                    summary.licencias,
                    summary.instalacionesActivas,
                )
                Result.success()
            }
            is DomainResult.Failure -> when (result.error) {
                is DomainError.Network -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val KEY_EMPRESA_ID = "empresa_id"
        const val UNIQUE_WORK_PREFIX = "sync-empresa-"
    }
}
