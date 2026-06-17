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
 * Gemelo de [RecaudacionUploadWorker] (T-63): sube de una en una hasta vaciar la
 * cola. Cada iteración:
 *  - éxito → pide la siguiente.
 *  - fallo de **red** (transitorio) → `Result.retry()` con backoff: la fila sigue
 *    `error` (reintentable) y WorkManager reintenta toda la cola.
 *  - fallo **permanente** (validación/permiso/…): el repo marca la fila `fallida`
 *    (terminal, fuera del drenado) y el worker **NO aborta** — sigue con la
 *    siguiente. Así una avería corrupta deja de bloquear a las averías VÁLIDAS que
 *    tiene detrás. El técnico ve las `fallida` en el Centro de Incidencias con
 *    Reintentar/Descartar.
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

        // Recupera filas colgadas en 'subiendo' de una ejecución anterior abortada
        // (proceso muerto a mitad de subida) para que vuelvan a drenarse.
        averiaRepository.recuperarColgadas(empresaId)

        var subidas = 0
        var bloqueadas = 0
        while (true) {
            when (val result = averiaRepository.subirSiguiente(empresaId)) {
                is DomainResult.Success -> {
                    val pendiente = result.value ?: return Result.success().also {
                        Timber.i(
                            "Cola de averías %s vaciada (%d subidas, %d bloqueadas)",
                            empresaId, subidas, bloqueadas,
                        )
                    }
                    subidas++
                    Timber.d("Avería subida: %s", pendiente.id)
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
                            "Avería fallida (terminal), se omite y se sigue: %s",
                            result.error,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_EMPRESA_ID = "empresa_id"
        const val UNIQUE_WORK_PREFIX = "upload-averias-"
    }
}
