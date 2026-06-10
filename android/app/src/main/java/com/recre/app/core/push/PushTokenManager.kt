package com.recre.app.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.recre.app.core.data.repository.PushTokenRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Arranca el registro del token FCM cuando hay una empresa activa (T-101).
 *
 * `RecreMessagingService.onNewToken` cubre el caso de token rotado, pero
 * cuando el técnico cambia de empresa activa (o reabre la app con un token
 * ya emitido y sin rotación) necesitamos re-registrar el token actual para
 * la nueva `empresa_id`. Este manager observa [SessionState.Active] y lo
 * hace, de forma análoga a `SyncManager` (T-51).
 *
 * Robustez: si Firebase no está inicializado (falta `google-services.json`
 * en el build), `FirebaseApp.getApps` está vacío y no hacemos nada. Así el
 * resto de la app funciona sin proyecto Firebase configurado.
 */
@Singleton
class PushTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val pushTokenRepository: PushTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!firebaseDisponible()) {
            Timber.tag(TAG).i("Firebase no inicializado; push deshabilitado (falta google-services.json)")
            return
        }
        // Crea el canal cuanto antes para que las notificaciones en
        // background tengan dónde aterrizar.
        PushNotifier.asegurarCanales(context)

        scope.launch {
            sessionRepository.state
                .filterIsInstance<SessionState.Active>()
                .map { it.empresa.id }
                .distinctUntilChanged()
                .collect { registrarTokenActual() }
        }
    }

    private fun registrarTokenActual() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                // TODO: quitar antes de publicar en producción
                Timber.tag(TAG).d("TOKEN FCM (solo dev): %s", token)
                scope.launch { pushTokenRepository.registrarToken(token) }
            }
            .addOnFailureListener { err ->
                Timber.tag(TAG).w(err, "No se pudo obtener el token FCM")
            }
    }

    private fun firebaseDisponible(): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private companion object {
        const val TAG = "PushToken"
    }
}
