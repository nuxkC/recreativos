package com.recre.app.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.recre.app.core.data.repository.PushTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Servicio FCM (T-101): recibe el token de registro y los mensajes push.
 *
 * Responsabilidades:
 *  - [onNewToken]: registra el token en el backend (`registrar-device-token`)
 *    para la empresa activa. Best-effort: si aún no hay empresa activa, el
 *    [PushTokenRepository] lo descarta y el `PushTokenManager` reintentará
 *    al activarse una empresa.
 *  - [onMessageReceived]: muestra la notificación de resolución de conflicto
 *    cuando la app está en primer plano (en background el sistema pinta la
 *    `notification` del payload automáticamente). El `data` trae el
 *    `recaudacion_id` para el deep-link al detalle (T-63/T-64).
 *
 * Diseño extensible: el enrutado por `data.tipo` permite añadir más eventos
 * (anulaciones, licencias por caducar…) sin tocar el transporte.
 */
@AndroidEntryPoint
class RecreMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenRepository: PushTokenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // No loggeamos el token (identificador sensible del dispositivo).
        Timber.tag(TAG).i("Nuevo token FCM recibido")
        scope.launch {
            pushTokenRepository.registrarToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val tipo = data[PushNotifier.DATA_TIPO]
        val recaudacionId = data[PushNotifier.DATA_RECAUDACION_ID]

        // Preferimos el bloque `notification` (título/cuerpo localizados por
        // el backend) y caemos a `data` si llegara un mensaje data-only.
        val title = message.notification?.title ?: data[KEY_TITLE].orEmpty()
        val body = message.notification?.body ?: data[KEY_BODY].orEmpty()

        when (tipo) {
            // Por ahora solo conflictos; el `when` deja sitio a más eventos.
            TIPO_CONFLICTO, null -> PushNotifier.mostrarConflicto(
                context = this,
                title = title.ifBlank { getString(com.recre.app.R.string.alertas_tipo_conflicto) },
                body = body,
                recaudacionId = recaudacionId,
                tipo = tipo,
            )

            else -> Timber.tag(TAG).d("Tipo de push no manejado: %s", tipo)
        }
    }

    private companion object {
        const val TAG = "Fcm"
        const val TIPO_CONFLICTO = "recaudacion_conflicto"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}
