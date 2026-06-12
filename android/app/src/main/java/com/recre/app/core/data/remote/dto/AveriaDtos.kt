package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs del sistema de averías (T-222).
 *
 * - Lectura del historial por máquina: [AveriaConRecambiosDto] (tabla `averia`
 *   con `averia_recambio` y `local` joinados vía PostgREST embedding, RLS
 *   solo-lectura para `authenticated`).
 * - Escritura vía RPCs `SECURITY DEFINER` (la directa está revocada): los
 *   nombres serializados son los argumentos SQL `p_*`; `numeric` viaja como
 *   número vía [NumericStringSerializer]; el rol/tenant y `reportada_por`
 *   (= auth.uid()) los valida/deriva la propia función.
 *
 * Convención (igual que [CrearMaquinaParams]): los opcionales se declaran
 * `String?` SIN default Kotlin para que kotlinx serialice SIEMPRE el campo.
 */

// ----------------------------------------------------------- escritura (RPC)

/** Parámetros de `crear_averia`. Devuelve el id (uuid) de la avería creada. */
@Serializable
data class CrearAveriaParams(
    @SerialName("p_empresa_id")
    val empresaId: String,
    @SerialName("p_maquina_id")
    val maquinaId: String,
    @SerialName("p_categoria")
    val categoria: String,
    @SerialName("p_descripcion")
    val descripcion: String?,
    @SerialName("p_pone_maquina_fuera_servicio")
    val poneMaquinaFueraServicio: Boolean,
    @SerialName("p_notas")
    val notas: String?,
    /** §5.6: la avería pagó premio de la tolva → la RPC inserta la `merma`. */
    @SerialName("p_afecta_tolva")
    val afectaTolva: Boolean,
    /** Importe pagado de la tolva (numeric); `null` (→ 0 server-side) si no aplica. */
    @SerialName("p_importe_tolva")
    @Serializable(with = NumericStringSerializer::class)
    val importeTolva: String?,
)

/** Parámetros de `crear_recambio`. Devuelve el id (uuid) del recambio creado. */
@Serializable
data class CrearRecambioParams(
    @SerialName("p_averia_id")
    val averiaId: String,
    @SerialName("p_pieza")
    val pieza: String,
    @SerialName("p_cantidad")
    val cantidad: Int,
    @SerialName("p_coste")
    @Serializable(with = NumericStringSerializer::class)
    val coste: String?,
    @SerialName("p_notas")
    val notas: String?,
)

/** Parámetros de `resolver_averia` (cierre de la avería). */
@Serializable
data class ResolverAveriaParams(
    @SerialName("p_id")
    val id: String,
    @SerialName("p_notas_resolucion")
    val notasResolucion: String?,
)

// ----------------------------------------------------------- lectura (embed)

/**
 * Fila del historial de averías de una máquina. Se hidrata de `public.averia`
 * con los recambios y el nombre del local (snapshot) embebidos en una sola
 * round-trip. `coste` de recambio viaja como número; el resto de importes no
 * aplican aquí (la avería es trazabilidad, no dinero — Fase 1).
 */
@Serializable
data class AveriaConRecambiosDto(
    val id: String,
    @SerialName("maquina_id")
    val maquinaId: String,
    @SerialName("instalacion_id")
    val instalacionId: String? = null,
    @SerialName("local_id")
    val localId: String? = null,
    val categoria: String,
    val descripcion: String? = null,
    val estado: String,
    @SerialName("pone_maquina_fuera_servicio")
    val poneMaquinaFueraServicio: Boolean,
    @SerialName("fecha_reporte")
    val fechaReporte: String,
    @SerialName("fecha_resolucion")
    val fechaResolucion: String? = null,
    val notas: String? = null,
    @SerialName("recambios")
    val recambios: List<RecambioDto> = emptyList(),
    @SerialName("local")
    val local: LocalNombreDto? = null,
)

@Serializable
data class RecambioDto(
    val id: String,
    val pieza: String,
    val cantidad: Int,
    @Serializable(with = NumericStringSerializer::class)
    val coste: String? = null,
    val notas: String? = null,
)

/** Embed mínimo de `local` para el snapshot del nombre. */
@Serializable
data class LocalNombreDto(
    val nombre: String,
)
