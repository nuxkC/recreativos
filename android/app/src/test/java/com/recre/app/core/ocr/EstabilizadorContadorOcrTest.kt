package com.recre.app.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de la estabilización temporal del OCR en vivo de contadores (T-100).
 *
 * Verifica que el parpadeo del reconocedor (lecturas que varían fotograma a
 * fotograma con el display borroso) se resuelve por consenso dentro de una
 * ventana temporal, con histéresis para no perder una lectura ya fijada.
 *
 * Las marcas de tiempo se pasan explícitas para que el test sea determinista
 * (la lógica es pura y no consulta ningún reloj).
 */
class EstabilizadorContadorOcrTest {

    private fun lectura(entradas: Long?, salidas: Long?, confianza: Confianza = Confianza.ALTA) =
        ContadorOcrAmbosResult(entradas, salidas, confianza)

    private val ninguna = ContadorOcrAmbosResult(null, null, Confianza.NINGUNA)

    @Test
    fun `sin alcanzar el minimo de muestras no fija nada`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)

        assertNull(est.estabilizar(a, instanteMs = 0))
        assertNull(est.estabilizar(a, instanteMs = 100))
        assertNull(est.estabilizar(a, instanteMs = 200))
    }

    @Test
    fun `cuatro lecturas iguales fijan esa pareja por consenso`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)

        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(a, 200)
        val estable = est.estabilizar(a, 300)

        assertEquals(a, estable)
    }

    @Test
    fun `la lectura mayoritaria gana y la minoritaria del parpadeo se descarta`() {
        val est = EstabilizadorContadorOcr()
        val buena = lectura(12345, 10000)
        val parpadeo = lectura(12348, 10000) // misreconocimiento de un dígito

        est.estabilizar(buena, 0)
        est.estabilizar(buena, 100)
        est.estabilizar(parpadeo, 200)
        est.estabilizar(buena, 300)
        val estable = est.estabilizar(buena, 400) // 4 de 5 = 80 %

        assertEquals(buena, estable)
    }

    @Test
    fun `una pareja ambigua que no alcanza la mayoria no fija nada`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)
        val b = lectura(12346, 10000)

        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(b, 200)
        // 2 de 4 = 50 % < 60 %: ningún ganador claro.
        val estable = est.estabilizar(b, 300)

        assertNull(estable)
    }

    @Test
    fun `histeresis un fotograma borroso aislado no tumba la lectura fijada`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)
        val borroso = lectura(12399, 10000)

        // Fija A.
        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(a, 200)
        assertEquals(a, est.estabilizar(a, 300))

        // Un fotograma distinto suelto: sigue mandando A (4 de 5).
        assertEquals(a, est.estabilizar(borroso, 400))
    }

    @Test
    fun `cuando otra pareja gana el consenso la lectura estable se actualiza`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)
        val b = lectura(20000, 16000) // el técnico apunta a otra máquina

        // Fija A.
        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(a, 200)
        assertEquals(a, est.estabilizar(a, 300))

        // Tras la ventana, las muestras de A caducan y B se impone.
        est.estabilizar(b, 2000)
        est.estabilizar(b, 2100)
        est.estabilizar(b, 2200)
        val estable = est.estabilizar(b, 2300)

        assertEquals(b, estable)
    }

    @Test
    fun `los fotogramas sin deteccion se ignoran y conservan la ultima lectura estable`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)

        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(a, 200)
        assertEquals(a, est.estabilizar(a, 300))

        // El display se pierde un momento: lecturas vacías no resetean ni
        // compiten; la lectura fijada se mantiene para poder confirmarla.
        est.estabilizar(ninguna, 2000)
        val estable = est.estabilizar(ninguna, 2100)

        assertEquals(a, estable)
    }

    @Test
    fun `las muestras fuera de la ventana no cuentan para el consenso`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)

        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(a, 200)
        // Salto temporal mayor que la ventana: las tres anteriores caducan.
        val estable = est.estabilizar(a, 2000)

        // Solo queda una muestra dentro de la ventana: insuficiente.
        assertNull(estable)
    }

    @Test
    fun `reiniciar olvida el historial y la lectura estable`() {
        val est = EstabilizadorContadorOcr()
        val a = lectura(12345, 10000)

        est.estabilizar(a, 0)
        est.estabilizar(a, 100)
        est.estabilizar(a, 200)
        assertEquals(a, est.estabilizar(a, 300))

        est.reiniciar()

        // Tras reiniciar arranca de cero: una sola lectura no fija nada.
        assertNull(est.estabilizar(a, 400))
    }
}
