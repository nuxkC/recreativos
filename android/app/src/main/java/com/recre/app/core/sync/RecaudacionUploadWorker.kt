package com.recre.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Worker que vacía la cola de recaudaciones pendientes de una empresa.
 *
 * Trabaja en bucle hasta que el repository diga "no hay más" o devuelva
 * un error. Cada iteración sube **una** recaudación; si tiene éxito,
 * pide la siguiente; si falla por red, devuelve `Result.retry()` con
 * backoff (T-57: WorkManager reintenta cada 30s exp), y si falla por
 * validación/auth devuelve `Result.failure()` (la fila queda marcada
 * como `error` en Room y se reintentará la próxima vez que el manager
 * encole; el técnico ve "X recaudaciones bloqueadas" en T-63).
 */
@HiltWorker
class RecaudacionUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recaudacionRepository: RecaudacionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val empresaId = inputData.getString(KEY_EMPRESA_ID)
            ?: return Result.failure()

        var subidas = 0
        while (true) {
            when (val result = recaudacionRepository.subirSiguiente(empresaId)) {
                is DomainResult.Success -> {
                    val pendiente = result.value ?: return Result.success().also {
                        Timber.i("Cola %s vaciada (%d subidas)", empresaId, subidas)
                    }
                    subidas++
                    Timber.d("Subida OK: %s", pendiente.id)
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
        const val UNIQUE_WORK_PREFIX = "upload-recaudaciones-"
    }
}
