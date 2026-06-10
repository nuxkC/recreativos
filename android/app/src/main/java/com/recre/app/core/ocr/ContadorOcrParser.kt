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

/** Confianza heurística de una lectura OCR. */
enum class Confianza {
    /** Lectura clara: un único número plausible o ganador inequívoco. */
    ALTA,

    /** Lectura ambigua: varios candidatos o número demasiado corto. */
    BAJA,

    /** No se detectó ningún número plausible. */
    NINGUNA,
}
