package com.recre.app.core.data.repository

import java.time.Instant

/**
 * Modelos de dominio del inventario, pensados para alimentar la UI de
 * forma directa.
 *
 * Vienen de combinar las entidades Room (`LocalEntity`, `MaquinaEntity`,
 * `LicenciaEntity`, `InstalacionEntity`) en memoria. La cantidad de filas
 * por empresa es del orden de cientos, así que no merece la pena montar
 * vistas SQL ni `@Relation` anidadas; el combine + lookup map es más
 * legible y mantenible.
 */

/**
 * Resumen de un local para la lista principal: lo mínimo que necesita la
 * fila + el contador de máquinas activas.
 */
data class LocalResumen(
    val id: String,
    val nombre: String,
    val direccion: String?,
    val titularNombre: String?,
    val telefono: String?,
    val maquinasActivas: Int,
)

/**
 * Estado de agenda de un local, derivado en el servidor (vista
 * `v_agenda_operario`, Planificación P3a, anclado a semana+día objetivo).
 * El cálculo ("¿toca?") vive en el servidor (SSOT + zona horaria de la empresa);
 * el cliente solo lo muestra.
 *
 * "Por recaudar esta semana" ([esPendiente]) = PENDIENTE (es su semana, aún no
 * su día) ∪ TOCA_HOY (hoy es su día) ∪ ATRASADO (pasó su día sin recaudar).
 * AL_DIA y SIN_PLANIFICAR no entran en la cola de trabajo de la semana.
 */
enum class EstadoAgenda {
    SIN_PLANIFICAR,
    AL_DIA,
    PENDIENTE,
    TOCA_HOY,
    ATRASADO;

    val esPendiente: Boolean
        get() = this == PENDIENTE || this == TOCA_HOY || this == ATRASADO

    companion object {
        fun desde(valor: String?): EstadoAgenda = when (valor) {
            "atrasado" -> ATRASADO
            "toca_hoy" -> TOCA_HOY
            "pendiente" -> PENDIENTE
            "al_dia" -> AL_DIA
            else -> SIN_PLANIFICAR
        }
    }
}

/**
 * Una máquina vista desde el detalle de su local: incluye la instalación
 * activa que la conecta con el local y la licencia.
 *
 * `valorCredito`, `tasaSemanal` y `porcentajeLocal` se mantienen como
 * `String` para preservar precisión decimal hasta que la UI las formatee
 * con [java.math.BigDecimal].
 *
 * `baselineReferenciaId` apunta a la fila origen de la baseline:
 *   - Si `baselineOrigen = "recaudacion_anterior"` -> id de la `recaudacion`.
 *   - Si `baselineOrigen = "cambio_placa"`         -> id del `cambio_placa`.
 *   - Si `baselineOrigen = "instalacion_base"`     -> `null` (es el alta).
 *
 * Lo necesita el flujo de recaudación (T-57) para enviar `baseline_id`
 * en el payload y permitir al server detectar conflictos.
 */
data class MaquinaConInstalacion(
    val instalacionId: String,
    val maquinaId: String,
    val numeroSerie: String,
    val modelo: String?,
    val fabricante: String?,
    val estado: String,
    val valorCredito: String,
    val licenciaNumero: String,
    val tasaSemanal: String,
    val porcentajeLocal: String,
    val baselineEntradas: Long,
    val baselineSalidas: Long,
    val baselineFecha: Instant,
    val baselineOrigen: String,
    val baselineReferenciaId: String?,
    /**
     * Datos del local en el que está instalada la máquina. Se rellena
     * desde `LocalEntity` para evitar tener que hacer un segundo
     * flow-join en cada caller que necesite imprimir el ticket (T-62)
     * o pintar la cabecera del local en pantalla.
     */
    val localId: String,
    val localNombre: String,
    val localDireccion: String?,
    /**
     * Merma de tolva pendiente de reponer (de la instalación sincronizada, §5.6).
     * Default "0": solo el flujo de recaudación la necesita; otros usos
     * (p.ej. reimpresión de ticket) no recalculan, así que 0 es inocuo.
     */
    val pendienteTolva: String = "0",
)

/**
 * Detalle de un local: cabecera + máquinas activas. Si el local no
 * existe (por ejemplo, fue cerrado y borrado), el repositorio emite
 * `null` para que la pantalla muestre "no encontrado".
 */
data class LocalDetalle(
    val local: LocalResumen,
    val maquinas: List<MaquinaConInstalacion>,
)
