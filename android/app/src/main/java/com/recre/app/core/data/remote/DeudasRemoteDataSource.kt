package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.CondonarCreditoParams
import com.recre.app.core.data.remote.dto.CrearPrestamoParams
import com.recre.app.core.data.remote.dto.RecuperacionRowDto
import com.recre.app.core.data.remote.dto.RegistrarRecuperacionEfectivoParams
import com.recre.app.core.data.remote.dto.SetPorcentajeRecuperacionLocalParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP de la ficha de deudas del local (T-215).
 *
 * - Lectura del libro mayor de abonos directa sobre la tabla `recuperacion`
 *   (RLS solo-lectura). El orden/recorte se hace en el repositorio.
 * - Altas/abonos/condonación/% vía RPC `SECURITY DEFINER`: la escritura directa
 *   está revocada y cada función valida rol (gestor/admin) + tenant.
 */
@Singleton
class DeudasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun fetchLedger(empresaId: String, localId: String): List<RecuperacionRowDto> =
        supabase
            .from("recuperacion")
            .select {
                filter {
                    eq("empresa_id", empresaId)
                    eq("local_id", localId)
                }
            }
            .decodeList()

    suspend fun crearPrestamo(params: CrearPrestamoParams): String =
        supabase.postgrest.rpc("crear_prestamo", params).decodeAs<String>()

    suspend fun registrarRecuperacionEfectivo(
        params: RegistrarRecuperacionEfectivoParams,
    ): String =
        supabase.postgrest.rpc("registrar_recuperacion_efectivo", params).decodeAs<String>()

    suspend fun condonarCredito(params: CondonarCreditoParams) {
        supabase.postgrest.rpc("condonar_credito", params)
    }

    suspend fun setPorcentajeRecuperacionLocal(params: SetPorcentajeRecuperacionLocalParams) {
        supabase.postgrest.rpc("set_porcentaje_recuperacion_local", params)
    }
}
