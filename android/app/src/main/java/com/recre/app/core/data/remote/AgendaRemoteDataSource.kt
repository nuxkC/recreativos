package com.recre.app.core.data.remote

import com.recre.app.core.data.repository.EstadoAgenda
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Frontera HTTP de la agenda del técnico (Planificación P3c).
 *
 * Lee la vista `v_agenda_operario` (P3a), que deriva en el servidor el estado de
 * cada local ("¿toca?") usando la zona horaria de la empresa. La RLS estricta
 * (P2) filtra: un técnico solo recibe sus locales. El estado es solo de lectura
 * y se superpone sobre la lista de locales del home; no se persiste en Room.
 */
@Singleton
class AgendaRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    @Serializable
    private data class AgendaRow(
        @SerialName("local_id") val localId: String,
        val estado: String,
    )

    /** Mapa local_id → estado de agenda de los locales visibles para el usuario. */
    suspend fun obtenerEstados(empresaId: String): Map<String, EstadoAgenda> =
        supabase
            .from("v_agenda_operario")
            .select {
                filter { eq("empresa_id", empresaId) }
            }
            .decodeList<AgendaRow>()
            .associate { it.localId to EstadoAgenda.desde(it.estado) }
}
