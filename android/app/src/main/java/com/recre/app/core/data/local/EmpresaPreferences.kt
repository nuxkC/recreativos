package com.recre.app.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistencia de la empresa activa entre arranques.
 *
 * Equivalente Android de la cookie httpOnly `recre_empresa_id` que usa
 * la web. La elección no es secreta (RLS ya cubre el acceso real) pero
 * se persiste para que el técnico no tenga que reseleccionar tras
 * reabrir la app.
 *
 * El valor solo se considera válido si coincide con una de las membresías
 * activas que devuelve [com.recre.app.core.data.repository.EmpresaRepository];
 * la verificación vive en [com.recre.app.core.session.SessionRepository].
 */
@Singleton
class EmpresaPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val empresaActivaIdFlow: Flow<String?> = dataStore.data.map { it[EMPRESA_ACTIVA_ID_KEY] }

    suspend fun setEmpresaActivaId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(EMPRESA_ACTIVA_ID_KEY)
            } else {
                prefs[EMPRESA_ACTIVA_ID_KEY] = id
            }
        }
    }

    private companion object {
        val EMPRESA_ACTIVA_ID_KEY = stringPreferencesKey("empresa_activa_id")
    }
}
