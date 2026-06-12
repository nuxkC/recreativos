package com.recre.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recre.app.core.data.repository.AveriaRepository
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Worker que vacía la cola de averías pendientes de una empresa (T-222).
 *
 * Gemelo de [RecaudacionUploadWorker]: sube de una en una hasta que la cola
 * quede vacía o haya un error. Fallo de red → `Result.retry()` (backoff
 * exponencial de WorkManager); cualquier otro fallo (validación/permiso) →
 * `Result.failure()` (la fila queda en `error` y se reintentará al re-encolar).
 * La subida es reanudable (ver [AveriaRepository.subirSiguiente]), así que un
 * reintento nunca duplica la avería ni sus recambios.
 */
@HiltWorker
class AveriaUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val averiaRepository: AveriaRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val empresaId = inputData.getString(KEY_EMPRESA_ID)
            ?: return Result.failure()

        var subidas = 0
        while (true) {
            when (val result = averiaRepository.subirSiguiente(empresaId)) {
                is DomainResult.Success -> {
                    val pendiente = result.value ?: return Result.success().also {
                        Timber.i("Cola de averías %s vaciada (%d subidas)", empresaId, subidas)
                    }
                    subidas++
                    Timber.d("Avería subida: %s", pendiente.id)
                }
                is DomainResult.Failure -> return when (result.error) {
                    is DomainError.Network -> Result.retry()
                    else -> Result.failure()
                }
            }
        }
    }

    companion object {
        const val KEY_EMPRESA_ID = "empresa_id"
        const val UNIQUE_WORK_PREFIX = "upload-averias-"
    }
}
