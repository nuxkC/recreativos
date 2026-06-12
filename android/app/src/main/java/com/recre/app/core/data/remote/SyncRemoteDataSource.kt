package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.CreditoLocalSaldoDto
import com.recre.app.core.data.remote.dto.EmpresaFullDto
import com.recre.app.core.data.remote.dto.InstalacionActivaDto
import com.recre.app.core.data.remote.dto.LicenciaDto
import com.recre.app.core.data.remote.dto.LocalDto
import com.recre.app.core.data.remote.dto.MaquinaDto
import com.recre.app.core.data.remote.dto.TolvaPendienteDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Único punto de acceso de red para la sincronización del inventario.
 *
 * Cada método ejecuta un `select` con filtro `empresa_id = X`. Aunque RLS
 * ya restringe la visibilidad por usuario, filtramos también explícitamente
 * para clarificar el query y permitir que el técnico (con acceso a varias
 * empresas) limite la descarga a la empresa activa.
 *
 * Mantiene cero lógica de negocio: solo hace red y entrega DTOs. La
 * frontera DTO -> entidad Room vive en `SyncRepository`.
 */
@Singleton
class SyncRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun fetchEmpresa(empresaId: String): EmpresaFullDto =
        supabase
            .from("empresa")
            .select {
                filter { eq("id", empresaId) }
            }
            .decodeSingle()

    suspend fun fetchLocales(empresaId: String): List<LocalDto> =
        supabase
            .from("local")
            .select {
                filter { eq("empresa_id", empresaId) }
            }
            .decodeList()

    suspend fun fetchMaquinas(empresaId: String): List<MaquinaDto> =
        supabase
            .from("maquina")
            .select {
                filter { eq("empresa_id", empresaId) }
            }
            .decodeList()

    suspend fun fetchLicencias(empresaId: String): List<LicenciaDto> =
        supabase
            .from("licencia")
            .select {
                filter { eq("empresa_id", empresaId) }
            }
            .decodeList()

    /**
     * Trae las instalaciones activas con maquina/local/licencia/baseline
     * pre-calculados por la vista `v_instalacion_actual` (ver migración
     * T-16). Una sola round-trip basta porque la vista hace los joins.
     */
    suspend fun fetchInstalacionesActivas(empresaId: String): List<InstalacionActivaDto> =
        supabase
            .from("v_instalacion_actual")
            .select {
                filter { eq("empresa_id", empresaId) }
            }
            .decodeList()

    /**
     * Deudas ABIERTAS de los locales de la empresa (T-215). Filtramos
     * `estado = 'abierto'` para no descargar el histórico de deudas saldadas
     * /condonadas: la app solo necesita las vivas para el preview offline de
     * recuperación y la ficha de deudas.
     */
    suspend fun fetchCreditosAbiertos(empresaId: String): List<CreditoLocalSaldoDto> =
        supabase
            .from("v_credito_local_saldo")
            .select {
                filter {
                    eq("empresa_id", empresaId)
                    eq("estado", "abierto")
                }
            }
            .decodeList()

    /** Merma de tolva pendiente por instalación activa (v_instalacion_tolva, §5.6). */
    suspend fun fetchTolvaPendientes(empresaId: String): List<TolvaPendienteDto> =
        supabase
            .from("v_instalacion_tolva")
            .select {
                filter {
                    eq("empresa_id", empresaId)
                    eq("estado", "activa")
                }
            }
            .decodeList()
}
