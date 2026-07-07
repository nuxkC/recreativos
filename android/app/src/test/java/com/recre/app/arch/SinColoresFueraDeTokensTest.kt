package com.recre.app.arch

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guardarraíl del re-skin «Neón de sala» (N5): ninguna pantalla de `feature/`
 * debe declarar colores literales (`Color(0x…)`). Todo color sale de
 * MaterialTheme, RecreColors o los tokens nombrados de `ui/theme`. Cada fase del
 * re-skin RETIRA ficheros de la allowlist según los migra; este test impide que
 * entren NUEVOS infractores mientras tanto.
 *
 * Pure-JVM: no toca Android ni Compose, solo lee los `.kt` como texto. El cwd de
 * los unit tests es el directorio del módulo (`android/app`).
 */
class SinColoresFueraDeTokensTest {
    // Literal de color hex ARGB de Compose: `Color(0xAARRGGBB)`.
    private val colorHex = Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")

    // Infractores HEREDADOS al arrancar N5; nunca añadir, solo retirar. Cada fase
    // del re-skin migra la pantalla a tokens y la saca de aquí; el sentido del
    // test es que esta lista solo mengüe.
    private val allowlist =
        setOf(
            // Lienzo de firma: dibuja trazo/guías con Color(0x…) en Canvas nativo.
            // Pendiente de migrar a tokens en una fase posterior del re-skin.
            "feature/recaudacion/components/SignaturePad.kt",
        )

    @Test
    fun feature_sin_color_hex_literal() {
        val featureDir = File("src/main/java/com/recre/app/feature")
        require(featureDir.isDirectory) {
            "No encuentro ${featureDir.absolutePath} (cwd=${File(".").absolutePath})"
        }

        val infractores =
            featureDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains(colorHex) }
                .map { "feature/" + it.relativeTo(featureDir).path.replace('\\', '/') }
                .filter { it !in allowlist }
                .sorted()
                .toList()

        if (infractores.isNotEmpty()) {
            fail(
                "Colores hex fuera de tokens en feature/ " +
                    "(usa RecreColors / MaterialTheme / tokens de ui/theme):\n" +
                    infractores.joinToString("\n") { "  - $it" },
            )
        }
    }
}
