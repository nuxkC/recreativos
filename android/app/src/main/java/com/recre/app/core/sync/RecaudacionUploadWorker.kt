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
 * Trabaja en bucle hasta que el repository diga "no hay más". Cada iteración
 * sube **una** recaudación:
 *  - éxito → pide la siguiente.
 *  - fallo de **red** (transitorio) → `Result.retry()` con backoff (T-57): la
 *    fila sigue `error` (reintentable) y WorkManager reintenta toda la cola.
 *  - fallo **permanente** (validación/auth/…): el repo marca la fila `fallida`
 *    (terminal, fuera del drenado) y el worker **NO aborta** — sigue con la
 *    siguiente. Así una recaudación corrupta deja de bloquear a las recaudaciones
 *    VÁLIDAS que tiene detrás (antes un error de validación en la cabeza de la
 *    cola mataba el drenado entero y las válidas no se subían nunca). El técnico
 *    ve las `fallida` en el panel de subidas con Reintentar/Descartar (T-63).
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

        // Recupera filas colgadas en 'subiendo' de una ejecución anterior abortada
        // (proceso muerto a mitad de subida) para que vuelvan a drenarse.
        recaudacionRepository.recuperarColgadas(empresaId)

        var subidas = 0
        var bloqueadas = 0
        while (true) {
            when (val result = recaudacionRepository.subirSiguiente(empresaId)) {
                is DomainResult.Success -> {
                    val pendiente = result.value ?: return Result.success().also {
                        Timber.i(
                            "Cola %s vaciada (%d subidas, %d bloqueadas)",
                            empresaId, subidas, bloqueadas,
                        )
                    }
                    subidas++
                    Timber.d("Subida OK: %s", pendiente.id)
                }
                is DomainResult.Failure -> when (result.error) {
                    // Red caída: cortamos y dejamos que WorkManager reintente toda
                    // la cola con backoff. La fila sigue 'error' (reintentable).
                    is DomainError.Network -> return Result.retry()
                    // Error permanente: el repo ya marcó la fila 'fallida' (fuera
                    // del drenado). NO abortamos: seguimos drenando el resto.
                    else -> {
                        bloqueadas++
                        Timber.w(
                            "Recaudación fallida (terminal), se omite y se sigue: %s",
                            result.error,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_EMPRESA_ID = "empresa_id"
        const val UNIQUE_WORK_PREFIX = "upload-recaudaciones-"
    }
}
