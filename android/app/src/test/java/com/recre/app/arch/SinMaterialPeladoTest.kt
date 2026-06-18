package com.recre.app.arch

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guardarraíl del rediseño (F0): ninguna pantalla de `feature/` debe consumir
 * Material 3 "pelado" (Card / Button / OutlinedButton / TextButton /
 * OutlinedTextField / TopAppBar) saltándose la librería propia (AppCard /
 * RecreButton / Field / SearchField / RecreShell). Cada fase del rediseño RETIRA
 * ficheros de la allowlist según los migra; este test impide que entren NUEVOS
 * infractores mientras tanto.
 *
 * Pure-JVM: no toca Android ni Compose, solo lee los `.kt` como texto. El cwd de
 * los unit tests es el directorio del módulo (`android/app`).
 */
class SinMaterialPeladoTest {
    private val prohibidos =
        listOf(
            "androidx.compose.material3.Card",
            "androidx.compose.material3.Button",
            "androidx.compose.material3.OutlinedButton",
            "androidx.compose.material3.TextButton",
            "androidx.compose.material3.OutlinedTextField",
            "androidx.compose.material3.TopAppBar",
        )

    // Infractores HEREDADOS a 2026-06-17 (arranque del rediseño F0). Cada fase los
    // retira al migrar la pantalla a la librería propia. Regla: NUNCA añadir
    // entradas nuevas; el sentido del test es que esta lista solo mengüe.
    private val allowlist =
        setOf(
            "feature/ajustes/AjustesScreen.kt",
            "feature/alertas/AlertasScreen.kt",
            "feature/historico/HistoricoScreen.kt",
            "feature/impresora/ImpresoraScreen.kt",
            "feature/incidencias/IncidenciasScreen.kt",
            // Migradas a chrome propio (PasoTopBar) + átomos en rediseño F1·Task 7;
            // fuera de la allowlist. Quedan ContadorOcrCapture/EscanerContadoresScreen
            // (cámara/OCR) pendientes de una fase posterior.
            "feature/recaudacion/contadores/ContadorOcrCapture.kt",
            "feature/recaudacion/contadores/EscanerContadoresScreen.kt",
            "feature/shell/ErroresSubidaDialog.kt",
        )

    // Match EXACTO por línea: una import-line es prohibida si es exactamente
    // `import <fqn>` o `import <fqn> as Alias`. Substring no vale: `CardDefaults`
    // contiene `Card` y `ButtonDefaults` contiene `Button` → falsos positivos.
    private fun importaProhibido(texto: String): Boolean =
        texto.lineSequence().any { linea ->
            val t = linea.trim()
            prohibidos.any { p -> t == "import $p" || t.startsWith("import $p ") }
        }

    @Test
    fun feature_no_usa_material_pelado_salvo_allowlist() {
        val featureDir = File("src/main/java/com/recre/app/feature")
        require(featureDir.isDirectory) {
            "No encuentro ${featureDir.absolutePath} (cwd=${File(".").absolutePath})"
        }

        val infractores =
            featureDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { importaProhibido(it.readText()) }
                .map { "feature/" + it.relativeTo(featureDir).path.replace('\\', '/') }
                .filter { it !in allowlist }
                .sorted()
                .toList()

        if (infractores.isNotEmpty()) {
            fail(
                "Pantallas con Material 3 pelado fuera de la allowlist " +
                    "(usa AppCard / RecreButton / Field / SearchField / RecreShell):\n" +
                    infractores.joinToString("\n") { "  - $it" },
            )
        }
    }
}
