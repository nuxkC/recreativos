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
