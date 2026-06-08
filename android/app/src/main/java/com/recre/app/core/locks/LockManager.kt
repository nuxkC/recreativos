package com.recre.app.core.locks

import com.recre.app.core.data.remote.AdquirirLockResult
import com.recre.app.core.data.remote.RecaudacionRemoteDataSource
import com.recre.app.core.data.remote.RecaudacionRemoteError
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Adquisición y liberación del lock optimista de recaudación (T-58).
 *
 * Diseño:
 * - **Best-effort**: si no hay red, devolvemos [LockState.Indisponible]
 *   y permitimos al técnico continuar offline. Cualquier conflicto que
 *   surja por dos técnicos recaudando la misma máquina sin red será
 *   detectado por el server al subir (baseline distinta -> alerta de
 *   conflicto, T-21).
 * - **Forzar**: si otro técnico tiene el lock pero el actual decide
 *   continuar igualmente, se vuelve a llamar con `forzar=true`. El
 *   server registra el cambio de owner.
 * - **Liberación**: al cerrar el flujo (cancelar o guardar), se libera
 *   el lock con `liberar-lock`. Si falla, no pasa nada — el TTL de 30
 *   min lo expira solo (T-24).
 */
@Singleton
class LockManager @Inject constructor(
    private val remote: RecaudacionRemoteDataSource,
) {

    /** Adquiere el lock. Nunca lanza: empaqueta cualquier error en [LockState]. */
    suspend fun adquirir(
        instalacionId: String,
        dispositivoId: String?,
        forzar: Boolean,
    ): LockState {
        return runCatching {
            remote.adquirirLock(instalacionId, dispositivoId, forzar)
        }.fold(
            onSuccess = { result ->
                when (result) {
                    is AdquirirLockResult.Adquirido -> LockState.Adquirido(result.expiresAt)
                    is AdquirirLockResult.Ocupado -> LockState.Ocupado(
                        tecnicoId = result.tecnicoId,
                        expiresAt = result.expiresAt,
                    )
                }
            },
            onFailure = { throwable ->
                val code = when (throwable) {
                    is RecaudacionRemoteError -> throwable.code ?: "unknown"
                    else -> "network"
                }
                Timber.w(throwable, "Lock no adquirido (%s); siguiendo offline", code)
                LockState.Indisponible(codigoError = code)
            },
        )
    }

    /** Libera el lock al salir del flujo. Best-effort, ignora errores. */
    suspend fun liberar(instalacionId: String) {
        remote.liberarLock(instalacionId)
    }
}
