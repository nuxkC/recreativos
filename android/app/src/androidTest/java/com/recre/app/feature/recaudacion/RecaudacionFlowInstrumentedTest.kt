package com.recre.app.feature.recaudacion

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recre.app.R
import com.recre.app.core.auth.Rol
import com.recre.app.core.data.local.dao.EmpresaParamsDao
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
 *  - "Continuar" en denominaciones solo se habilita cuando la suma exacta
 *    coincide con el objetivo (bruto / parte local).
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

    @Test
    fun flujoCompleto_contadores_denominaciones_confirmacion() {
        val viewModel = crearViewModel(maquinaQueProcede())
        composeRule.setContent { FlujoTestHost(viewModel) }
        esperarContadores()

        // Paso 1 — contadores: 100 créditos => bruto 20,00 €, procede.
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADOR_ENTRADAS).performTextInput("100")
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADOR_SALIDAS).performTextInput("0")
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADORES_CONTINUAR)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        // Paso 2 — denominaciones del total (objetivo 20,00 €): 0,20 € × 100.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_total_titulo))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RecaudacionTestTags.denominacionCantidad("0.20"))
            .performTextInput("100")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR)
            .assertIsEnabled()
            .performClick()

        // Paso 3 — denominaciones de la parte local (objetivo 10,00 €): 0,20 € × 50.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_denominaciones_local_titulo))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RecaudacionTestTags.denominacionCantidad("0.20"))
            .performTextInput("50")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR)
            .assertIsEnabled()
            .performClick()

        // Paso 4 — confirmación.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(textoDe(R.string.recaudacion_paso_confirmacion)).assertIsDisplayed()
    }

    @Test
    fun denominaciones_continuarSoloSeHabilitaConSumaExacta() {
        val viewModel = crearViewModel(maquinaQueProcede())
        composeRule.setContent { FlujoTestHost(viewModel) }
        esperarContadores()

        // Avanza a denominaciones del total (objetivo 20,00 €).
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADOR_ENTRADAS).performTextInput("100")
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADOR_SALIDAS).performTextInput("0")
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADORES_CONTINUAR)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Sin desglose: la diferencia no es 0 → "Continuar" deshabilitado.
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).assertIsNotEnabled()

        // Suma insuficiente (0,20 € × 50 = 10,00 €) → sigue deshabilitado.
        composeRule.onNodeWithTag(RecaudacionTestTags.denominacionCantidad("0.20"))
            .performTextInput("50")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).assertIsNotEnabled()

        // Suma exacta (0,20 € × 100 = 20,00 €) → habilitado.
        composeRule.onNodeWithTag(RecaudacionTestTags.denominacionCantidad("0.20"))
            .performTextReplacement("100")
        composeRule.onNodeWithTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR).assertIsEnabled()
    }

    @Test
    fun brutoMenorQueTasa_ofreceSaltarSinPedirDenominaciones() {
        val viewModel = crearViewModel(maquinaQueNoProcede())
        composeRule.setContent { FlujoTestHost(viewModel) }
        esperarContadores()

        // 100 créditos => bruto 20,00 € < tasa total (≥ 200,00 €): no procede.
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADOR_ENTRADAS).performTextInput("100")
        composeRule.onNodeWithTag(RecaudacionTestTags.CONTADOR_SALIDAS).performTextInput("0")
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
