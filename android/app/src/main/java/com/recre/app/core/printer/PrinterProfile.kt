package com.recre.app.core.printer

/**
 * Identificadores estables de los modelos de impresora térmica
 * soportados (T-105).
 *
 * El `name` de cada enum es la clave que se persiste en
 * [PrinterPreferences]; por eso NO se renombran una vez publicados (un
 * cambio rompería la selección guardada de los técnicos). Para añadir
 * un modelo nuevo basta con declarar la entrada aquí, su [PrinterProfile]
 * en [PrinterProfiles] y el string del nombre visible en `strings.xml`.
 */
enum class PrinterModelId {
    /** AGPTEK PT210 — modelo de referencia desde T-62, 58 mm sin cuter. */
    PT210,

    /** Térmica genérica de 58 mm (la mayoría de las BT chinas). */
    GENERICA_58,

    /** Térmica genérica de 80 mm con cuter automático. */
    GENERICA_80,

    /** Epson TM-T20 (80 mm) — usa cuter parcial `GS V`. */
    EPSON_TM_T20,

    /** Xprinter XP-58 (58 mm). */
    XPRINTER_58,
}

/**
 * Perfil de una impresora térmica ESC/POS (T-105).
 *
 * Captura SOLO los parámetros que varían entre modelos; los comandos
 * ESC/POS comunes (init, alineación, énfasis, raster) viven en [EscPos]
 * y son idénticos para todos. Así el subsistema soporta varios modelos
 * sin duplicar la lógica de formateo.
 *
 * @param id identificador estable que se persiste.
 * @param widthDots ancho útil de impresión en puntos. 58 mm ≈ 384,
 *        80 mm ≈ 576. Define el escalado de la firma raster.
 * @param cols columnas de texto en font A (12×24). 32 para 58 mm,
 *        48 para 80 mm. Define el ancho de los separadores y el
 *        alineado derecha de los importes.
 * @param tieneCuter `true` si el modelo tiene cuter mecánico; entonces
 *        cerramos el ticket con un corte ESC/POS (`GS V`). Los modelos
 *        sin cuter (como la PT210) avanzan papel para arrancarlo a mano.
 * @param lineasFinales líneas en blanco al cierre del ticket para que
 *        salga del cabezal antes del corte/arranque.
 */
data class PrinterProfile(
    val id: PrinterModelId,
    val widthDots: Int,
    val cols: Int,
    val tieneCuter: Boolean,
    val lineasFinales: Int,
)

/**
 * Catálogo de perfiles soportados (T-105).
 *
 * [POR_DEFECTO] es la PT210 para no romper el flujo existente (T-62):
 * cualquier técnico que ya tuviera vinculada su PT210 sigue imprimiendo
 * con el mismo formato exacto sin tocar nada.
 */
object PrinterProfiles {

    val PT210 = PrinterProfile(
        id = PrinterModelId.PT210,
        widthDots = 384,
        cols = 32,
        tieneCuter = false,
        lineasFinales = 4,
    )

    val GENERICA_58 = PrinterProfile(
        id = PrinterModelId.GENERICA_58,
        widthDots = 384,
        cols = 32,
        tieneCuter = false,
        lineasFinales = 4,
    )

    val GENERICA_80 = PrinterProfile(
        id = PrinterModelId.GENERICA_80,
        widthDots = 576,
        cols = 48,
        tieneCuter = true,
        lineasFinales = 2,
    )

    val EPSON_TM_T20 = PrinterProfile(
        id = PrinterModelId.EPSON_TM_T20,
        widthDots = 576,
        cols = 48,
        tieneCuter = true,
        lineasFinales = 2,
    )

    val XPRINTER_58 = PrinterProfile(
        id = PrinterModelId.XPRINTER_58,
        widthDots = 384,
        cols = 32,
        tieneCuter = false,
        lineasFinales = 4,
    )

    /** Perfil por defecto: PT210, compatible con T-62. */
    val POR_DEFECTO: PrinterProfile = PT210

    /** Todos los perfiles, en el orden en que se ofrecen en Ajustes. */
    val TODOS: List<PrinterProfile> = listOf(
        PT210,
        GENERICA_58,
        GENERICA_80,
        EPSON_TM_T20,
        XPRINTER_58,
    )

    /**
     * Resuelve un id persistido a su perfil. Devuelve `null` cuando el
     * id es desconocido (p. ej. una preferencia escrita por una versión
     * más nueva tras un downgrade), para que el llamador pueda mostrar
     * [PrinterError.ModeloNoSoportado] en lugar de imprimir con un
     * formato incorrecto silenciosamente.
     */
    fun resolver(id: String?): PrinterProfile? {
        if (id == null) return null
        val modelo = PrinterModelId.entries.firstOrNull { it.name == id } ?: return null
        return TODOS.first { it.id == modelo }
    }

    /**
     * Igual que [resolver] pero cae al perfil [POR_DEFECTO] cuando el id
     * es nulo o desconocido. Útil donde nunca queremos fallar el render
     * (p. ej. previsualización), reservando el error para el envío real.
     */
    fun resolverOPorDefecto(id: String?): PrinterProfile = resolver(id) ?: POR_DEFECTO
}
