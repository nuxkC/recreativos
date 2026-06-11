package com.recre.app.core.ocr

/**
 * Extrae el valor de un contador a partir del texto crudo devuelto por el
 * OCR (T-100). Lógica **pura** (sin dependencias de Android ni ML Kit) para
 * poder testearla con tests unitarios en `src/test/`.
 *
 * Un contador físico muestra una secuencia de dígitos (electromecánico o
 * digital). El display puede incluir ruido: etiquetas ("IN", "OUT"), comas o
 * puntos de millar, ceros a la izquierda, etc. El parser:
 *  1. Aísla los grupos de dígitos del texto, uniendo los separados solo por
 *     separadores de millar (`.` `,` espacio) en un único número.
 *  2. Descarta grupos no plausibles como contador (longitud fuera de rango).
 *  3. Ordena los candidatos (más largos primero; a igual longitud, mayor valor)
 *     y estima una [Confianza] heurística para que la UI avise cuando la
 *     lectura es ambigua.
 *
 * La confianza es heurística y deliberadamente conservadora: ML Kit no expone
 * una confianza por carácter fiable, así que la derivamos de la estructura del
 * texto. Sirve para decidir si mostramos un aviso de "revisa el valor", nunca
 * para bloquear la edición manual.
 */
object ContadorOcrParser {

    /** Longitud mínima de dígitos para considerar un grupo como contador. */
    private const val MIN_DIGITOS = 1

    /** Longitud máxima (alineada con MAX_CONTADOR_DIGITS del flujo de recaudación). */
    private const val MAX_DIGITOS = 12

    /**
     * Ratio mínimo de retorno de la máquina: las salidas (lo que paga) son al
     * menos el 70 % de las entradas (lo que recauda). Es uno de los filtros que
     * usa [parseAmbos] para decidir, entre los números detectados, cuál es el
     * contador de salidas. Es un primer filtro heurístico; se añadirán más.
     */
    private const val RATIO_RETORNO_MIN = 0.70

    /**
     * Caracteres que actúan como separadores de millar dentro de un número.
     *
     * Solo puntuación de millar (`.` `,` apóstrofo): los espacios y saltos de
     * línea NO se tratan como separadores porque ML Kit los inserta entre
     * lecturas distintas del display, y unirlas produciría números falsos.
     */
    private val SEPARADORES_MILLAR = setOf('.', ',', '\'')

    /**
     * Analiza [textoCrudo] y devuelve los candidatos a valor de contador.
     *
     * @return un [ContadorOcrParseResult] con la lista ordenada de candidatos,
     *   el mejor candidato (o `null` si no hay ninguno) y la confianza estimada.
     */
    fun parse(textoCrudo: String): ContadorOcrParseResult {
        val candidatos = extraerCandidatos(textoCrudo)
            .filter { it.length in MIN_DIGITOS..MAX_DIGITOS }
            // Normaliza ceros a la izquierda sin perder el valor (un display
            // "007" representa el contador 7) pero conserva al menos un dígito.
            .mapNotNull { it.toLongOrNull() }
            .distinct()

        if (candidatos.isEmpty()) {
            return ContadorOcrParseResult(emptyList(), null, Confianza.NINGUNA)
        }

        // Ordena por número de dígitos (desc) y, a igualdad, por valor (desc):
        // el contador suele ser el número más largo del display.
        val ordenados = candidatos.sortedWith(
            compareByDescending<Long> { it.toString().length }.thenByDescending { it },
        )

        return ContadorOcrParseResult(
            candidatos = ordenados,
            mejor = ordenados.first(),
            confianza = estimarConfianza(ordenados),
        )
    }

    /**
     * Identifica **ambos** contadores (entradas y salidas) en una sola foto del
     * display (HU-14 fase 2). El técnico ya no escanea cada campo por separado:
     * una única captura contiene los dos números y aquí decidimos cuál es cuál
     * aplicando filtros de dominio sobre los candidatos detectados.
     *
     * Filtros (en orden):
     *  1. **Entradas ≥ última lectura** ([baselineEntradas]): el contador físico
     *     es monótono creciente, nunca baja respecto a la recaudación anterior.
     *     De los candidatos válidos, entradas es el **mayor** (la máquina recauda
     *     más de lo que paga).
     *  2. **Salidas ≥ última lectura** ([baselineSalidas]) y **≤ entradas**.
     *  3. **Salidas ≥ 70 % de entradas** ([RATIO_RETORNO_MIN]): la máquina
     *     devuelve el 70 % o más, así que un número muy por debajo de ese umbral
     *     no es el contador de salidas (será ruido del display).
     *
     * El OCR sigue siendo una ayuda: si algún filtro no encuentra candidato,
     * devolvemos lo que sí se pudo identificar (entradas) con confianza baja para
     * que la UI invite a revisar, y el técnico siempre corrige a mano.
     *
     * @return entradas/salidas detectadas (`null` cada una si no se identificó)
     *   y la [Confianza] heurística del conjunto.
     */
    fun parseAmbos(
        textoCrudo: String,
        baselineEntradas: Long,
        baselineSalidas: Long,
    ): ContadorOcrAmbosResult {
        val candidatos = extraerCandidatos(textoCrudo)
            .filter { it.length in MIN_DIGITOS..MAX_DIGITOS }
            .mapNotNull { it.toLongOrNull() }
            .distinct()
            .sortedDescending()

        // Entradas: el mayor candidato no inferior a su última lectura.
        val entradasElegibles = candidatos.filter { it >= baselineEntradas }
        val entradas = entradasElegibles.firstOrNull()
            ?: return ContadorOcrAmbosResult(null, null, Confianza.NINGUNA)

        // Salidas: candidato distinto que respeta los filtros respecto a entradas.
        val salidasMinimo = entradas.toDouble() * RATIO_RETORNO_MIN
        val salidasElegibles = candidatos.filter { c ->
            c != entradas &&
                c <= entradas &&
                c >= baselineSalidas &&
                c.toDouble() >= salidasMinimo
        }
        val salidas = salidasElegibles.firstOrNull()

        // Alta solo si salidas queda determinada de forma única; si hay varios
        // candidatos plausibles o no se pudo aislar, pedimos revisión.
        val confianza = when {
            salidas == null -> Confianza.BAJA
            salidasElegibles.size == 1 -> Confianza.ALTA
            else -> Confianza.BAJA
        }

        return ContadorOcrAmbosResult(entradas, salidas, confianza)
    }

    /**
     * Aísla los grupos de dígitos del texto. Une dígitos separados únicamente
     * por separadores de millar (p. ej. "12.345" -> "12345"); cualquier otro
     * carácter rompe el grupo.
     */
    private fun extraerCandidatos(texto: String): List<String> {
        val grupos = mutableListOf<String>()
        val actual = StringBuilder()
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            when {
                c.isDigit() -> actual.append(c)
                c in SEPARADORES_MILLAR && actual.isNotEmpty() && siguienteEsDigito(texto, i) -> {
                    // Separador de millar entre dígitos: lo ignoramos para unir.
                }
                else -> {
                    if (actual.isNotEmpty()) {
                        grupos.add(actual.toString())
                        actual.clear()
                    }
                }
            }
            i++
        }
        if (actual.isNotEmpty()) grupos.add(actual.toString())
        return grupos
    }

    private fun siguienteEsDigito(texto: String, indice: Int): Boolean {
        val siguiente = indice + 1
        return siguiente < texto.length && texto[siguiente].isDigit()
    }

    /**
     * Estima la confianza a partir de la estructura de los candidatos:
     *  - [Confianza.ALTA]: hay un único candidato, o el más largo es
     *    estrictamente más largo que el resto (ganador claro) y tiene al menos
     *    2 dígitos.
     *  - [Confianza.BAJA]: hay varios candidatos con la misma longitud máxima
     *    (ambiguo), o el mejor candidato tiene un solo dígito.
     */
    private fun estimarConfianza(ordenados: List<Long>): Confianza {
        if (ordenados.size == 1) {
            return if (ordenados.first().toString().length >= 2) Confianza.ALTA else Confianza.BAJA
        }
        val longitudes = ordenados.map { it.toString().length }
        val maxLongitud = longitudes.first()
        val empatadosEnMax = longitudes.count { it == maxLongitud }
        return when {
            empatadosEnMax > 1 -> Confianza.BAJA
            maxLongitud < 2 -> Confianza.BAJA
            else -> Confianza.ALTA
        }
    }
}

/**
 * Resultado del parseo del texto OCR.
 *
 * @property candidatos valores plausibles ordenados (mejor primero).
 * @property mejor mejor candidato o `null` si no se detectó ningún número.
 * @property confianza confianza heurística del [mejor] candidato.
 */
data class ContadorOcrParseResult(
    val candidatos: List<Long>,
    val mejor: Long?,
    val confianza: Confianza,
)

/**
 * Resultado de identificar ambos contadores en una sola foto ([ContadorOcrParser.parseAmbos]).
 *
 * @property entradas valor del contador de entradas o `null` si no se identificó.
 * @property salidas valor del contador de salidas o `null` si no se identificó.
 * @property confianza confianza heurística del conjunto detectado.
 */
data class ContadorOcrAmbosResult(
    val entradas: Long?,
    val salidas: Long?,
    val confianza: Confianza,
)

/** Confianza heurística de una lectura OCR. */
enum class Confianza {
    /** Lectura clara: un único número plausible o ganador inequívoco. */
    ALTA,

    /** Lectura ambigua: varios candidatos o número demasiado corto. */
    BAJA,

    /** No se detectó ningún número plausible. */
    NINGUNA,
}
