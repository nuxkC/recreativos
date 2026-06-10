package com.recre.app.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Observador de conectividad de red para el bloqueo offline (T-70) en
 * las pantallas del CRUD gestor (T-66..T-69).
 *
 * Devuelve un `Flow<Boolean>` que emite `true` cuando hay al menos una
 * red **validada** con capacidad de internet, y `false` en cuanto se
 * pierde la última. Se apoya en `ConnectivityManager.NetworkCallback`,
 * que es la API recomendada en SDK 24+ y mucho más fiable que el
 * deprecado `BroadcastReceiver` de `CONNECTIVITY_ACTION`.
 *
 * **Por qué `NetworkCapabilities.NET_CAPABILITY_VALIDATED`**: nos
 * importa que el dispositivo realmente tenga acceso a internet (no que
 * esté conectado a un AP sin ruta). Esto hace que el estado refleje
 * lo que el técnico vería en la barra de notificaciones.
 *
 * El flujo se comparte con `WhileSubscribed` para no instalar el
 * callback hasta que algún consumidor está activo.
 */
@Singleton
class ConnectivityRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * `true` mientras haya conexión a internet con red validada. Se
     * inicializa en `false` para que las pantallas pinten el banner
     * antes de que llegue la primera notificación del callback.
     */
    val online: Flow<Boolean> = callbackFlow<Boolean> {
        if (cm == null) {
            trySend(false)
            awaitClose { /* no-op */ }
            return@callbackFlow
        }

        val activas = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activas.add(network)
                trySend(true)
            }

            override fun onLost(network: Network) {
                activas.remove(network)
                trySend(activas.isNotEmpty())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val ok = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (ok) activas.add(network) else activas.remove(network)
                trySend(activas.isNotEmpty())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // Estado inicial síncrono basado en la red activa actual; los
        // updates posteriores llegan por el callback.
        val activeNet = cm.activeNetwork
        val activeCaps = activeNet?.let(cm::getNetworkCapabilities)
        trySend(
            activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )

        cm.registerNetworkCallback(request, callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0),
            replay = 1,
        )
}
