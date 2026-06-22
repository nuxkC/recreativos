package com.recre.app.core.data.local

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
 * Tamaño global de la interfaz elegido por el técnico.
 *
 * `escala` multiplica la densidad de toda la app (vía `LocalDensity` en la raíz),
 * de modo que se encoge/agranda TODO de forma uniforme —dp y sp por igual— sin
 * romper proporciones. El ajuste de tamaño de fuente del sistema sigue
 * aplicándose por encima (accesibilidad), este es un preferencia propia de la app.
 */
enum class TamanoUi(val escala: Float) {
    COMPACTO(0.88f),
    ESTANDAR(1.0f),
    GRANDE(1.12f),
}

/**
 * Persistencia ligera del tamaño de interfaz (preferencia de un solo valor).
 * Vive en el mismo DataStore de Preferences que [EmpresaPreferences] /
 * [com.recre.app.core.printer.PrinterPreferences].
 */
@Singleton
class UiPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Tamaño elegido; [TamanoUi.ESTANDAR] si el técnico nunca lo cambió. */
    val tamanoFlow: Flow<TamanoUi> = dataStore.data.map { prefs ->
        when (prefs[KEY_TAMANO]) {
            TamanoUi.COMPACTO.name -> TamanoUi.COMPACTO
            TamanoUi.GRANDE.name -> TamanoUi.GRANDE
            else -> TamanoUi.ESTANDAR
        }
    }

    /** Snapshot puntual. */
    suspend fun tamano(): TamanoUi = tamanoFlow.first()

    suspend fun setTamano(tamano: TamanoUi) {
        dataStore.edit { prefs -> prefs[KEY_TAMANO] = tamano.name }
    }

    private companion object {
        val KEY_TAMANO = stringPreferencesKey("ui_tamano")
    }
}
