package com.recre.app.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.recre.app.core.auth.Rol
import com.recre.app.core.session.EmpresaResumen
import com.recre.app.core.session.Membresia
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Última lista de membresías confirmada por el backend, persistida para que
 * el arranque sin red no quede detrás de una puerta online: con sesión válida
 * en disco y esta cache, la app llega a `Active` aunque el fetch falle y el
 * técnico sigue trabajando con los datos de Room.
 *
 * No es fuente de verdad: cada refresh con éxito la sobreescribe y el logout
 * la limpia (otro usuario no debe heredarla). RLS sigue cubriendo el acceso
 * real; aquí solo hay nombres y roles que el propio usuario ya vio.
 */
@Singleton
class MembresiasCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** La última lista confirmada, o `null` si no hay cache utilizable. */
    suspend fun leer(): List<Membresia>? =
        dataStore.data.first()[MEMBRESIAS_KEY]?.let(::membresiasFromJson)

    suspend fun guardar(membresias: List<Membresia>) {
        dataStore.edit { prefs -> prefs[MEMBRESIAS_KEY] = membresiasToJson(membresias) }
    }

    suspend fun limpiar() {
        dataStore.edit { prefs -> prefs.remove(MEMBRESIAS_KEY) }
    }

    private companion object {
        val MEMBRESIAS_KEY = stringPreferencesKey("membresias_cache")
    }
}

/** Forma persistida: plana y con el rol en crudo para sobrevivir a renombrados. */
@Serializable
internal data class MembresiaCacheDto(
    val empresaId: String,
    val empresaNombre: String,
    val zonaHoraria: String,
    val rol: String,
)

private val json = Json { ignoreUnknownKeys = true }

internal fun membresiasToJson(membresias: List<Membresia>): String =
    json.encodeToString(
        membresias.map { membresia ->
            MembresiaCacheDto(
                empresaId = membresia.empresa.id,
                empresaNombre = membresia.empresa.nombre,
                zonaHoraria = membresia.empresa.zonaHoraria,
                rol = membresia.rol.raw,
            )
        },
    )

/**
 * `null` = cache ilegible (equivale a no tener). Un dato viejo nunca debe
 * romper el arranque: los roles desconocidos se descartan entrada a entrada.
 */
internal fun membresiasFromJson(raw: String): List<Membresia>? = runCatching {
    json.decodeFromString<List<MembresiaCacheDto>>(raw).mapNotNull { dto ->
        Rol.fromRaw(dto.rol)?.let { rol ->
            Membresia(
                empresa = EmpresaResumen(
                    id = dto.empresaId,
                    nombre = dto.empresaNombre,
                    zonaHoraria = dto.zonaHoraria,
                ),
                rol = rol,
            )
        }
    }
}.getOrNull()
