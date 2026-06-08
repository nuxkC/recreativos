package com.recre.app.core.printer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistencia ligera de la impresora seleccionada (T-62).
 *
 * El usuario empareja la AGPTEK PT210 desde los ajustes del sistema y
 * desde la app elige cuál de los dispositivos emparejados es la
 * impresora de tickets. Solo guardamos su MAC + nombre legible para no
 * tener que reabrir el selector cada vez.
 *
 * No es información sensible — la app necesita BLUETOOTH_CONNECT para
 * usarla — así que vive como Preferences planas dentro del mismo
 * DataStore de [com.recre.app.core.data.local.EmpresaPreferences].
 */
@Singleton
class PrinterPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Emite la impresora seleccionada (`null` si el técnico aún no ha
     * vinculado ninguna). Cualquier consumidor de UI puede usar este
     * Flow para refrescar al instante cuando se cambia de impresora.
     */
    val seleccionadaFlow: Flow<PrinterDevice?> = dataStore.data.map { prefs ->
        val mac = prefs[KEY_MAC] ?: return@map null
        val name = prefs[KEY_NAME] ?: mac
        PrinterDevice(mac = mac, name = name)
    }

    /** Snapshot puntual; los call-sites suspendidos prefieren esto. */
    suspend fun seleccionada(): PrinterDevice? = seleccionadaFlow.first()

    suspend fun setSeleccionada(device: PrinterDevice?) {
        dataStore.edit { prefs ->
            if (device == null) {
                prefs.remove(KEY_MAC)
                prefs.remove(KEY_NAME)
            } else {
                prefs[KEY_MAC] = device.mac
                prefs[KEY_NAME] = device.name
            }
        }
    }

    private companion object {
        val KEY_MAC = stringPreferencesKey("impresora_mac")
        val KEY_NAME = stringPreferencesKey("impresora_nombre")
    }
}
