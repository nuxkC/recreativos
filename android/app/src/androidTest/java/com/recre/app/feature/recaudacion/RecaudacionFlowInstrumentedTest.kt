package com.recre.app.feature.recaudacion

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recre.app.R
import com.recre.app.core.auth.Rol
import com.recre.app.core.data.local.dao.CreditoLocalDao
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.MaquinaConInstalacion
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.locks.LockManager
import com.recre.app.core.locks.LockState
import com.recre.app.core.printer.PrinterRepository
import com.recre.app.core.session.EmpresaResumen
import com.recre.app.core.session.Membresia
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.RecaudacionUploadManager
import com.recre.app.core.sync.SyncManager
import com.recre.app.feature.recaudacion.confirmacion.ConfirmacionScreen
import com.recre.app.feature.recaudacion.contadores.ContadoresScreen
import com.recre.app.feature.recaudacion.denominaciones.DenominacionesScreen
import com.recre.app.feature.recaudacion.denominaciones.ModoDenominaciones
import com.recre.app.ui.theme.RecreTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas del flujo de recaudación (T-81).
 *
 * Cubren los criterios de la HU-7 que dependen de la UI:
 *  - Navegación contadores → denominaciones (total) → denominaciones
 *    (local) → confirmación.
 *  - El avance vive en la tecla ok del keypad (D.3-3, neón N7): en
 *    denominaciones solo navega al paso siguiente cuando la suma exacta
 *    coincide con el objetivo (bruto / parte local); si no cuadra, la tecla
 *    ok salta a la siguiente denominación y NO cambia de pantalla. El gate se
 *    verifica por navegación (aparece / no aparece el título del paso
 *    siguiente), no por `enabled`, porque la tecla ok está siempre habilitada.
 *  - Bruto < tasa: se oculta "Continuar" y se ofrece "Saltar a la
 *    siguiente" sin pedir denominaciones.
 *
 * Las dependencias remotas (Supabase, WorkManager, impresora, locks) se
 * aíslan con MockK; el [RecaudacionFlowViewModel] real ejerce la lógica de
 * cálculo y validación, que es lo que queremos verificar.
 *
 * Validates: HU-7 (Recaudación de una máquina)
 */
@RunWith(AndroidJUnit4::class)
class RecaudacionFlowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // Test data
    // -------------------------------------------------------------------------

    private val empresa = EmpresaResumen(
        id = "emp-1",
        nombre = "Recre Test SL",
        zonaHoraria = "Europe/Madrid",
    )

    private val empresaParams = EmpresaParamsEntity(
        empresaId = "emp-1",
        nombre = "Recre Test SL",
        cif = null,
        direccion = null,
        telefono = null,
        email = null,
        logoUrl = null,
        zonaHoraria = "Europe/Madrid",
        ticketCabecera = null,
        ticketPie = null,
        redondeoRecaudacion = 0,
        porcentajeRecuperacion = 0,
        updatedAt = Instant.now(),
    )

    /**
     * Máquina cuya recaudación procede: con baseline de "esta semana"
     * (0 semanas de tasa) el bruto siempre cubre la tasa total (0,00 €).
     * Con `valorCredito = 0,20` y 100 créditos → bruto = 20,00 €,
     * parte local (50 %) = 10,00 €.
     */
    private fun maquinaQueProcede() = maquinaBase(
        tasaSemanal = "10.00",
        baselineFecha = Instant.now(),
    )

    /**
     * Máquina cuya recaudación NO procede: baseline de hace 30 días
     * (≥ 4 semanas) con tasa semanal alta hace que la tasa total
     * (≥ 200,00 €) supere el bruto (20,00 €).
     */
    private fun maquinaQueNoProcede() = maquinaBase(
        tasaSemanal = "50.00",
        baselineFecha = Instant.now().minus(30, ChronoUnit.DAYS),
    )

    private fun maquinaBase(tasaSemanal: String, baselineFecha: Instant) = MaquinaConInstalacion(
        instalacionId = INSTALACION_ID,
        maquinaId = "maq-1",
        numeroSerie = "SN-001",
        modelo = "Modelo X",
        fabricante = "Fabricante Y",
        estado = "activa",
        valorCredito = "0.20",
        licenciaNumero = "LIC-001",
        tasaSemanal = tasaSemanal,
        porcentajeLocal = "50.00",
        baselineEntradas = 0L,
        baselineSalidas = 0L,
        baselineFecha = baselineFecha,
        baselineOrigen = "instalacion_base",
        baselineReferenciaId = null,
        localId = "local-1",
        localNombre = "Bar Pepe",
        localDireccion = "Calle Mayor 1",
        pendienteTolva = "0",
    )

    // -------------------------------------------------------------------------
    // ViewModel real con dependencias mockeadas
    // -------------------------------------------------------------------------

    private fun crearViewModel(maquina: MaquinaConInstalacion): RecaudacionFlowViewModel {
        val membresia = Membresia(empresa = empresa, rol = Rol.TECNICO)
        val sessionRepository = mockk<SessionRepository>(relaxed = true)
        every { sessionRepository.state } returns
            MutableStateFlow<SessionState>(SessionState.Active(membresia, listOf(membresia)))

        val empresaParamsDao = mockk<EmpresaParamsDao>(relaxed = true)
        every { empresaParamsDao.observe(any()) } returns flowOf(empresaParams)

        // T-215: el flujo observa deudas + % del local; sin deudas en el test.
        val creditoLocalDao = mockk<CreditoLocalDao>(relaxed = true)
        every { creditoLocalDao.observarPorLocal(any()) } returns flowOf(emptyList())
        val localDao = mockk<LocalDao>(relaxed = true)
        every { localDao.observe(any()) } returns flowOf(null)

        val inventoryRepository = mockk<InventoryRepository>(relaxed = true)
        every { inventoryRepository.observarMaquinaPorInstalacion(any()) } returns flowOf(maquina)

        val syncManager = mockk<SyncManager>(relaxed = true)
        every { syncManager.observarSyncStale(any()) } returns flowOf(false)

        val lockManager = mockk<LockManager>(relaxed = true)
        coEvery { lockManager.adquirir(any(), any(), any()) } returns LockState.Adquirido(null)

        return RecaudacionFlowViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(RecaudacionFlowViewModel.ARG_INSTALACION_ID to INSTALACION_ID),
            ),
            inventoryRepository = inventoryRepository,
            empresaParamsDao = empresaParamsDao,
            creditoLocalDao = creditoLocalDao,
            localDao = localDao,
            sessionRepository = sessionRepository,
            authRepository = mockk<AuthRepository>(relaxed = true),
            recaudacionRepository = mockk<RecaudacionRepository>(relaxed = true),
            uploadManager = mockk<RecaudacionUploadManager>(relaxed = true),
            lockManager = lockManager,
            printerRepository = mockk<PrinterRepository>(relaxed = true),
            syncManager = syncManager,
        )
    }

    /**
     * Host de pruebas que reproduce la navegación del sub-grafo de
     * recaudación con un único [RecaudacionFlowViewModel] compartido, igual
     * que el NavGraph real, pero sin depender de NavHost/Hilt.
     */
    @Composable
    private fun FlujoTestHost(viewModel: RecaudacionFlowViewModel) {
        var paso by remember { mutableStateOf(PasoRecaudacion.Contadores) }
        RecreTheme {
            when (paso) {
                PasoRecaudacion.Contadores -> ContadoresScreen(
                    viewModel = viewModel,
                    onContinuar = { paso = PasoRecaudacion.DenominacionesTotal },
                    onLecturaNoRecaudada = {},
                    onBack = {},
                )

                PasoRecaudacion.DenominacionesTotal -> DenominacionesScreen(
                    viewModel = viewModel,
                    modo = ModoDenominaciones.Total,
                    onContinuar = { paso = PasoRecaudacion.DenominacionesLocal },
                    onBack = { paso = PasoRecaudacion.Contadores },
                )

                PasoRecaudacion.DenominacionesLocal -> DenominacionesScreen(
                    viewModel = viewModel,
                    modo = ModoDenominaciones.Local,
                    onContinuar = { paso = PasoRecaudacion.Confirmacion },
                    onBack = { paso = PasoRecaudacion.DenominacionesTotal },
                )

                PasoRecaudacion.Confirmacion -> ConfirmacionScreen(
                    viewModel = viewModel,
                    onFinalizar = {},
                    onBack = { paso = PasoRecaudacion.DenominacionesLocal },
                )
            }
        }
    }

    private fun esperarContadores() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule
                .onAllNodesWithTag(RecaudacionTestTags.CONTADOR_ENTRADAS)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Teclea [valor] en la denominación [key] con el keypad in-app de T-232
     * (la celda ya no acepta IME): activa la fila, limpia y pulsa los dígitos.
     */
    private fun tecleaCantidad(key: String, valor: String) {
        composeRule.onNodeWithTag(RecaudacionTestTags.denominacionCantidad(key)).performClick()
        repeat(7) { composeRule.onNodeWithTag("keypad-backspace").performClick() } // limpia la celda
        valor.forEach { c -> composeRule.onNodeWithTag("keypad-$c").performClick() }
    }

    /**
     * Teclea el contador [testTag] (entradas o salidas) con el keypad in-app
     * (neón N7): la celda es tappable, no un TextField, así que el IME del
     * sistema jamás aparece. Activa la celda y pulsa los dígitos uno a uno.
     */
    private fun tecleaContador(testTag: String, valor: String) {
        composeRule.onNodeWithTag(testTag).performScrollTo().performClick()
        valor.forEach { c -> composeRule.onNodeWithTag("keypad-$c").performClick() }
    }

    @Test
    fun flujoCompleto_contadores_denominaciones_confirmacion() {
        val viewModel = crearViewModel(maquinaQueProcede())
        composeRule.setContent { FlujoTestHost(viewModel) }
        esperarContadores()

        // Paso 1 — contadores: 100 créditos => bruto 20,00 €, procede. Se teclean
        // por el keypad in-app; la tecla ok avanza al estar en salidas con cifras
        // válidas (D.3-3), ya no hay botón "Continuar".
        tecleaContador(RecaudacionTestTags.CONTADOR_ENTRADAS, "100")
        tecleaContador(RecaudacionTestTags.CONTADOR_SALIDAS, "0")
        composeRule.onNodeWithTag("keypad-next").performClick()

        // Paso 2 — denominaciones del total (objetivo 20,00 €): 0,20 € × 100. Al
        // cuadrar, la tecla ok navega al paso local.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_total_titulo))
            .assertIsDisplayed()
        tecleaCantidad("0.20", "100")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).performClick()

        // Paso 3 — denominaciones de la parte local (objetivo 10,00 €): 0,20 € × 50.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_local_titulo))
            .assertIsDisplayed()
        tecleaCantidad("0.20", "50")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).performClick()

        // Paso 4 — confirmación.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_paso_confirmacion)).assertIsDisplayed()
    }

    @Test
    fun denominaciones_okSoloAvanzaConSumaExacta() {
        val viewModel = crearViewModel(maquinaQueProcede())
        composeRule.setContent { FlujoTestHost(viewModel) }
        esperarContadores()

        // Avanza a denominaciones del total (objetivo 20,00 €) con la tecla ok.
        tecleaContador(RecaudacionTestTags.CONTADOR_ENTRADAS, "100")
        tecleaContador(RecaudacionTestTags.CONTADOR_SALIDAS, "0")
        composeRule.onNodeWithTag("keypad-next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_total_titulo))
            .assertIsDisplayed()

        // El gate ya NO se comprueba por `enabled` (la tecla ok está siempre
        // habilitada): se comprueba por NAVEGACIÓN. Sin cuadrar, pulsar la tecla
        // ok salta a la siguiente denominación y NO cambia de pantalla.

        // Sin desglose (diferencia ≠ 0): la tecla ok no navega al paso local.
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_local_titulo))
            .assertDoesNotExist()

        // Suma insuficiente (0,20 € × 50 = 10,00 €) → sigue sin navegar.
        tecleaCantidad("0.20", "50")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_local_titulo))
            .assertDoesNotExist()

        // Suma exacta (0,20 € × 100 = 20,00 €) → la tecla ok navega al paso local.
        tecleaCantidad("0.20", "100")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_local_titulo))
            .assertIsDisplayed()
    }

    @Test
    fun brutoMenorQueTasa_ofreceSaltarSinPedirDenominaciones() {
        val viewModel = crearViewModel(maquinaQueNoProcede())
        composeRule.setContent { FlujoTestHost(viewModel) }
        esperarContadores()

        // 100 créditos => bruto 20,00 € < tasa total (≥ 200,00 €): no procede.
        tecleaContador(RecaudacionTestTags.CONTADOR_ENTRADAS, "100")
        tecleaContador(RecaudacionTestTags.CONTADOR_SALIDAS, "0")
        composeRule.waitForIdle()

        // Se ofrece "Saltar a la siguiente" y NO el botón de "Continuar".
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADORES_LECTURA_NO_RECAUDADA)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADORES_CONTINUAR).assertDoesNotExist()
    }

    private fun textoDe(resId: Int): String = composeRule.activity.getString(resId)

    private companion object {
        const val INSTALACION_ID = "inst-1"
    }
}
