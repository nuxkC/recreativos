package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.PushTokenRemoteDataSource
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Registro del token de notificaciones push (FCM) del dispositivo (T-101).
 *
 * Une el remote data source ([PushTokenRemoteDataSource]) con la empresa
 * activa de la sesión. La obtención del token la hace FCM en
 * `RecreMessagingService` o el `PushTokenManager`; aquí solo lo persistimos
 * en el backend para la empresa activa.
 */
interface PushTokenRepository {

    /**
     * Registra/actualiza [token] para la empresa activa. Si no hay empresa
     * activa todavía (sesión a medias), devuelve [DomainError.Auth] sin
     * lanzar: el token se reintentará al activarse una empresa.
     */
    suspend fun registrarToken(token: String): DomainResult<Unit>
}

@Singleton
class PushTokenRepositoryImpl @Inject constructor(
    private val remote: PushTokenRemoteDataSource,
    private val sessionRepository: SessionRepository,
) : PushTokenRepository {

    override suspend fun registrarToken(token: String): DomainResult<Unit> {
        val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
            ?: return DomainResult.Failure(DomainError.Auth("Sin empresa activa"))

        return runCatching { remote.registrar(empresaId, token) }.fold(
            onSuccess = {
                // No loggeamos el token: identificador sensible del dispositivo.
                Timber.tag(TAG).i("Token push registrado para empresa activa")
                DomainResult.Success(Unit)
            },
            onFailure = { err ->
                Timber.tag(TAG).w(err, "No se pudo registrar el token push")
                DomainResult.Failure(DomainError.Network(err.message))
            },
        )
    }

    private companion object {
        const val TAG = "PushToken"
    }
}
