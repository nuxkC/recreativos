package com.recre.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.recre.app.core.push.PushNotifier
import com.recre.app.core.session.SessionState
import com.recre.app.core.data.repository.TipoAlerta
import com.recre.app.feature.ajustes.AjustesScreen
import com.recre.app.feature.alertas.AlertasScreen
import com.recre.app.feature.auth.LoginScreen
import com.recre.app.feature.auth.LoginViewModel
import com.recre.app.feature.averias.AveriasMaquinaScreen
import com.recre.app.feature.averias.AveriasMaquinaViewModel
import com.recre.app.feature.averias.ReportarAveriaScreen
import com.recre.app.feature.averias.ReportarAveriaViewModel
import com.recre.app.feature.cambio_placa.CambioPlacaScreen
import com.recre.app.feature.cambio_placa.CambioPlacaViewModel
import com.recre.app.feature.deudas.DeudasGestorScreen
import com.recre.app.feature.deudas.DeudasLocalScreen
import com.recre.app.feature.deudas.DeudasLocalViewModel
import com.recre.app.feature.empresa.SeleccionarEmpresaScreen
import com.recre.app.feature.empresa.SeleccionarEmpresaViewModel
import com.recre.app.feature.empresa.SinAccesoScreen
import com.recre.app.feature.gestion.GestionScreen
import com.recre.app.ui.components.navigateTab
import com.recre.app.feature.gestion.instalaciones.InstalacionFormScreen
import com.recre.app.feature.gestion.instalaciones.InstalacionFormViewModel
import com.recre.app.feature.gestion.instalaciones.InstalacionesGestorScreen
import com.recre.app.feature.gestion.licencias.LicenciaFormScreen
import com.recre.app.feature.gestion.licencias.LicenciaFormViewModel
import com.recre.app.feature.gestion.licencias.LicenciasGestorScreen
import com.recre.app.feature.gestion.locales.LocalFormScreen
import com.recre.app.feature.gestion.locales.LocalFormViewModel
import com.recre.app.feature.gestion.locales.LocalesGestorScreen
import com.recre.app.feature.gestion.maquinas.MaquinaFormScreen
import com.recre.app.feature.gestion.maquinas.MaquinaFormViewModel
import com.recre.app.feature.gestion.maquinas.MaquinasGestorScreen
import com.recre.app.feature.historico.HistoricoDetalleScreen
import com.recre.app.feature.historico.HistoricoDetalleViewModel
import com.recre.app.feature.historico.HistoricoScreen
import com.recre.app.feature.impresora.ImpresoraScreen
import com.recre.app.feature.locales.LocalDetalleScreen
import com.recre.app.feature.locales.LocalDetalleViewModel
import com.recre.app.feature.locales.LocalesScreen
import com.recre.app.feature.locales.LocalesViewModel
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.confirmacion.ConfirmacionScreen
import com.recre.app.feature.recaudacion.contadores.ContadoresScreen
import com.recre.app.feature.recaudacion.denominaciones.DenominacionesScreen
import com.recre.app.feature.recaudacion.denominaciones.ModoDenominaciones
import com.recre.app.ui.theme.RecreTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Deep-link pendiente desde una notificación push (T-101): id de la
    // recaudación cuyo conflicto se resolvió. Se consume al navegar.
    private val deepLinkRecaudacionId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        leerDeepLink(intent)
        setContent {
            RecreTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RecreApp(
                        deepLinkRecaudacionId = deepLinkRecaudacionId,
                        onDeepLinkConsumido = { deepLinkRecaudacionId.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leerDeepLink(intent)
    }

    /** Extrae el `recaudacion_id` que [PushNotifier] adjunta al tocar la push. */
    private fun leerDeepLink(intent: Intent?) {
        val recaudacionId = intent?.getStringExtra(PushNotifier.EXTRA_RECAUDACION_ID)
        if (!recaudacionId.isNullOrBlank()) {
            deepLinkRecaudacionId.value = recaudacionId
        }
    }
}

@Composable
private fun RecreApp(
    deepLinkRecaudacionId: StateFlow<String?> = MutableStateFlow(null),
    onDeepLinkConsumido: () -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val sessionState by rootViewModel.sessionState.collectAsStateWithLifecycle()

    // Sincroniza el destino raíz con el estado de sesión. Solo se ejecuta
    // cuando el SessionState cambia de clase, NO cuando estamos navegando
    // dentro de la sesión activa (p. ej. locales -> detalle -> recaudación).
    LaunchedEffect(sessionState::class) {
        navigateForState(navController, sessionState)
    }

    // Permiso de notificaciones (Android 13+): se pide una vez al entrar en
    // sesión activa. Si se deniega, las push no se muestran pero la app
    // sigue plenamente funcional (T-101).
    PedirPermisoNotificaciones(sessionState)

    // Deep-link de notificación push: cuando hay sesión activa y un
    // recaudacion_id pendiente, navega al detalle del histórico (T-63/T-64)
    // y consume el id para no repetir la navegación.
    val pendingDeepLink by deepLinkRecaudacionId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLink, sessionState::class) {
        val recaudacionId = pendingDeepLink
        if (recaudacionId != null && sessionState is SessionState.Active) {
            navController.navigate(Routes.historicoDetalle(recaudacionId))
            onDeepLinkConsumido()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen()
        }
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = vm,
                onLoginSuccess = {
                    // No navegamos manualmente: el SessionState observará la
                    // sesión nueva y el LaunchedEffect de arriba moverá al
                    // destino correcto (selector u home).
                },
            )
        }
        composable(Routes.SELECCIONAR_EMPRESA) {
            val vm: SeleccionarEmpresaViewModel = hiltViewModel()
            SeleccionarEmpresaScreen(viewModel = vm)
        }
        composable(Routes.SIN_ACCESO) {
            SinAccesoScreen()
        }
        composable(Routes.LOCALES) {
            val vm: LocalesViewModel = hiltViewModel()
            LocalesScreen(
                viewModel = vm,
                onLocalClick = { localId ->
                    navController.navigate(Routes.localDetalle(localId))
                },
                onAlertasClick = {
                    navController.navigate(Routes.ALERTAS)
                },
                onSelectTab = { dest -> navController.navigateTab(dest) },
            )
        }
        composable(Routes.IMPRESORA) {
            ImpresoraScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AJUSTES) {
            AjustesScreen(
                onSelectTab = { dest -> navController.navigateTab(dest) },
                onImpresoraClick = { navController.navigate(Routes.IMPRESORA) },
                onHistoricoClick = { navController.navigate(Routes.HISTORICO) },
                onAlertasClick = { navController.navigate(Routes.ALERTAS) },
            )
        }
        composable(Routes.HISTORICO) {
            HistoricoScreen(
                onSelectTab = { dest -> navController.navigateTab(dest) },
                onAlertasClick = { navController.navigate(Routes.ALERTAS) },
                onRecaudacionClick = { id ->
                    navController.navigate(Routes.historicoDetalle(id))
                },
            )
        }
        composable(
            route = Routes.HISTORICO_DETALLE,
            arguments = listOf(
                navArgument(HistoricoDetalleViewModel.ARG_RECAUDACION_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            HistoricoDetalleScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ALERTAS) {
            AlertasScreen(
                onBack = { navController.popBackStack() },
                onAlertaClick = { alerta ->
                    // Si la alerta tiene referencia a una recaudación,
                    // saltamos al detalle directamente para que el técnico
                    // vea cifras, motivo de anulación, etc.
                    val refId = alerta.referenciaId
                    if (refId != null && esRecaudacionRelevante(alerta.tipo)) {
                        navController.navigate(Routes.historicoDetalle(refId))
                    }
                },
            )
        }
        composable(
            route = Routes.LOCAL_DETALLE,
            arguments = listOf(
                navArgument(LocalDetalleViewModel.ARG_LOCAL_ID) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val vm: LocalDetalleViewModel = hiltViewModel()
            val localId = backStackEntry.arguments
                ?.getString(LocalDetalleViewModel.ARG_LOCAL_ID).orEmpty()
            LocalDetalleScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onRecaudarMaquina = { instalacionId ->
                    navController.navigate(Routes.recaudacion(instalacionId))
                },
                onCambioPlaca = { instalacionId ->
                    navController.navigate(Routes.cambioPlaca(instalacionId))
                },
                onReportarAveria = { maquinaId ->
                    navController.navigate(Routes.reportarAveria(maquinaId))
                },
                onRecaudarTodas = { primeraInstalacionId ->
                    navController.navigate(
                        Routes.recaudacion(primeraInstalacionId, cadenaLocalId = localId),
                    )
                },
                onVerDeudas = { navController.navigate(Routes.localDeudas(localId)) },
            )
        }
        composable(
            route = Routes.LOCAL_DEUDAS,
            arguments = listOf(
                navArgument(DeudasLocalViewModel.ARG_LOCAL_ID) { type = NavType.StringType },
            ),
        ) {
            DeudasLocalScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CAMBIO_PLACA,
            arguments = listOf(
                navArgument(CambioPlacaViewModel.ARG_INSTALACION_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            val vm: CambioPlacaViewModel = hiltViewModel()
            CambioPlacaScreen(
                viewModel = vm,
                onFinalizar = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.REPORTAR_AVERIA,
            arguments = listOf(
                navArgument(ReportarAveriaViewModel.ARG_MAQUINA_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            ReportarAveriaScreen(
                onGuardado = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        recaudacionGraph(navController)
        gestionRoutes(navController)
    }
}

/**
 * Sub-rutas del CRUD de Gestión (T-66..T-69).
 *
 * Estructuralmente sigue el mismo patrón que la web: hub + listado por
 * entidad + form de alta y form de edición. El form de alta y el de
 * edición comparten composable: la edición se discrimina por la
 * presencia del id en `SavedStateHandle`. Cada éxito del form vuelve
 * automáticamente a la lista (popUpTo).
 */
private fun androidx.navigation.NavGraphBuilder.gestionRoutes(
    navController: NavHostController,
) {
    composable(Routes.GESTION) {
        GestionScreen(
            onSelectTab = { dest -> navController.navigateTab(dest) },
            onAlertasClick = { navController.navigate(Routes.ALERTAS) },
            onLicenciasClick = { navController.navigate(Routes.GESTION_LICENCIAS) },
            onMaquinasClick = { navController.navigate(Routes.GESTION_MAQUINAS) },
            onLocalesClick = { navController.navigate(Routes.GESTION_LOCALES) },
            onInstalacionesClick = {
                navController.navigate(Routes.GESTION_INSTALACIONES)
            },
            onDeudasClick = { navController.navigate(Routes.GESTION_DEUDAS) },
        )
    }

    // Deudas: tolva y préstamos (T-219)
    composable(Routes.GESTION_DEUDAS) {
        DeudasGestorScreen(
            onBack = { navController.popBackStack() },
            onLocalClick = { localId -> navController.navigate(Routes.localDeudas(localId)) },
        )
    }

    // Licencias (T-66)
    composable(Routes.GESTION_LICENCIAS) {
        LicenciasGestorScreen(
            onBack = { navController.popBackStack() },
            onAlta = { navController.navigate(Routes.GESTION_LICENCIA_NUEVA) },
            onEditar = { id ->
                navController.navigate(Routes.gestionLicenciaEditar(id))
            },
        )
    }
    composable(Routes.GESTION_LICENCIA_NUEVA) {
        LicenciaFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_LICENCIAS, inclusive = false)
            },
        )
    }
    composable(
        route = Routes.GESTION_LICENCIA_EDITAR,
        arguments = listOf(
            navArgument(LicenciaFormViewModel.ARG_LICENCIA_ID) { type = NavType.StringType },
        ),
    ) {
        LicenciaFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_LICENCIAS, inclusive = false)
            },
        )
    }

    // Máquinas (T-67)
    composable(Routes.GESTION_MAQUINAS) {
        MaquinasGestorScreen(
            onBack = { navController.popBackStack() },
            onAlta = { navController.navigate(Routes.GESTION_MAQUINA_NUEVA) },
            onEditar = { id ->
                navController.navigate(Routes.gestionMaquinaEditar(id))
            },
            onVerAverias = { id ->
                navController.navigate(Routes.gestionMaquinaAverias(id))
            },
        )
    }
    composable(
        route = Routes.GESTION_MAQUINA_AVERIAS,
        arguments = listOf(
            navArgument(AveriasMaquinaViewModel.ARG_MAQUINA_ID) { type = NavType.StringType },
        ),
    ) {
        AveriasMaquinaScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.GESTION_MAQUINA_NUEVA) {
        MaquinaFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_MAQUINAS, inclusive = false)
            },
        )
    }
    composable(
        route = Routes.GESTION_MAQUINA_EDITAR,
        arguments = listOf(
            navArgument(MaquinaFormViewModel.ARG_MAQUINA_ID) { type = NavType.StringType },
        ),
    ) {
        MaquinaFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_MAQUINAS, inclusive = false)
            },
        )
    }

    // Locales (T-68)
    composable(Routes.GESTION_LOCALES) {
        LocalesGestorScreen(
            onBack = { navController.popBackStack() },
            onAlta = { navController.navigate(Routes.GESTION_LOCAL_NUEVO) },
            onEditar = { id ->
                navController.navigate(Routes.gestionLocalEditar(id))
            },
        )
    }
    composable(Routes.GESTION_LOCAL_NUEVO) {
        LocalFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_LOCALES, inclusive = false)
            },
        )
    }
    composable(
        route = Routes.GESTION_LOCAL_EDITAR,
        arguments = listOf(
            navArgument(LocalFormViewModel.ARG_LOCAL_ID) { type = NavType.StringType },
        ),
    ) {
        LocalFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_LOCALES, inclusive = false)
            },
        )
    }

    // Instalaciones (T-69)
    composable(Routes.GESTION_INSTALACIONES) {
        InstalacionesGestorScreen(
            onBack = { navController.popBackStack() },
            onAlta = { navController.navigate(Routes.GESTION_INSTALACION_NUEVA) },
            onEditar = { id ->
                navController.navigate(Routes.gestionInstalacionEditar(id))
            },
        )
    }
    composable(Routes.GESTION_INSTALACION_NUEVA) {
        InstalacionFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_INSTALACIONES, inclusive = false)
            },
        )
    }
    composable(
        route = Routes.GESTION_INSTALACION_EDITAR,
        arguments = listOf(
            navArgument(InstalacionFormViewModel.ARG_INSTALACION_ID) {
                type = NavType.StringType
            },
        ),
    ) {
        InstalacionFormScreen(
            onBack = { navController.popBackStack() },
            onGuardado = {
                navController.popBackStack(Routes.GESTION_INSTALACIONES, inclusive = false)
            },
        )
    }
}

/**
 * Sub-NavGraph del flujo de recaudación.
 *
 * Las 4 pantallas (`contadores`, `denominacionesTotal`,
 * `denominacionesLocal`, `confirmacion`) comparten un único
 * [RecaudacionFlowViewModel] scoped al backStackEntry del propio graph.
 * Esto permite que el ViewModel sobreviva a las navegaciones internas y
 * muera cuando el usuario sale del flujo (popUpTo del graph).
 *
 * Modo cadena (T-60): la ruta del graph acepta un query arg opcional
 * `cadenaLocalId`. Si está presente, las pantallas saben que están en
 * cadena y, tras guardar/saltar, navegan a la siguiente máquina con
 * `popUpTo(graph)` para no acumular back stack infinito.
 */
private fun androidx.navigation.NavGraphBuilder.recaudacionGraph(
    navController: NavHostController,
) {
    navigation(
        startDestination = Routes.RECAUDACION_CONTADORES,
        route = Routes.RECAUDACION_GRAPH,
    ) {
        composable(Routes.RECAUDACION_CONTADORES) { backStackEntry ->
            val flowVm = flowViewModel(navController, backStackEntry)
            ContadoresScreen(
                viewModel = flowVm,
                onContinuar = { navController.navigate(Routes.RECAUDACION_DENOMINACIONES_TOTAL) },
                onLecturaNoRecaudada = {
                    saltarOTerminarCadena(navController, flowVm)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RECAUDACION_DENOMINACIONES_TOTAL) { backStackEntry ->
            val flowVm = flowViewModel(navController, backStackEntry)
            DenominacionesScreen(
                viewModel = flowVm,
                modo = ModoDenominaciones.Total,
                onContinuar = { navController.navigate(Routes.RECAUDACION_DENOMINACIONES_LOCAL) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RECAUDACION_DENOMINACIONES_LOCAL) { backStackEntry ->
            val flowVm = flowViewModel(navController, backStackEntry)
            DenominacionesScreen(
                viewModel = flowVm,
                modo = ModoDenominaciones.Local,
                onContinuar = { navController.navigate(Routes.RECAUDACION_CONFIRMACION) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RECAUDACION_CONFIRMACION) { backStackEntry ->
            val flowVm = flowViewModel(navController, backStackEntry)
            ConfirmacionScreen(
                viewModel = flowVm,
                onFinalizar = {
                    saltarOTerminarCadena(navController, flowVm)
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Cierre del flujo: si estamos en cadena, navega al siguiente
 * `recaudacion/{id}?cadenaLocalId=...` con `popUpTo(graph, inclusive)`
 * para que el ViewModel actual muera. Si no hay siguiente o no estamos
 * en cadena, simplemente popea el graph.
 */
private fun saltarOTerminarCadena(
    navController: NavHostController,
    flowVm: RecaudacionFlowViewModel,
) {
    val cadena = flowVm.state.value.cadena
    if (cadena != null) {
        val siguiente = cadena.siguienteInstalacionId
        if (siguiente != null) {
            navController.navigate(
                Routes.recaudacion(siguiente, cadenaLocalId = cadena.localId),
            ) {
                popUpTo(Routes.RECAUDACION_GRAPH) { inclusive = true }
            }
            return
        }
    }
    navController.popBackStack(Routes.RECAUDACION_GRAPH, inclusive = true)
}

/**
 * Resuelve el [RecaudacionFlowViewModel] scoped al sub-graph, no al
 * destino concreto. `getBackStackEntry(GRAPH_ROUTE)` devuelve el
 * NavBackStackEntry del nav graph, cuyo ViewModelStoreOwner persiste
 * mientras el graph esté en el back stack.
 */
@Composable
private fun flowViewModel(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
): RecaudacionFlowViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(Routes.RECAUDACION_GRAPH)
    }
    return hiltViewModel(parentEntry)
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Solicita el permiso POST_NOTIFICATIONS en Android 13+ una sola vez, al
 * entrar en sesión activa (T-101). En versiones anteriores el permiso es
 * implícito y no se pide nada.
 */
@Composable
private fun PedirPermisoNotificaciones(sessionState: SessionState) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* concedido o no: la app funciona igual, solo cambia si se ven push */ }

    var solicitado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionState is SessionState.Active) {
        if (sessionState is SessionState.Active && !solicitado) {
            solicitado = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun navigateForState(
    navController: NavHostController,
    state: SessionState,
) {
    val target = when (state) {
        SessionState.Loading -> Routes.SPLASH
        SessionState.NotAuthenticated -> Routes.LOGIN
        SessionState.NoMemberships -> Routes.SIN_ACCESO
        is SessionState.NeedsEmpresaSelection -> Routes.SELECCIONAR_EMPRESA
        is SessionState.Active -> Routes.LOCALES
    }
    val current = navController.currentBackStackEntry?.destination?.route
    if (current == target) return
    navController.navigate(target) {
        popUpTo(navController.graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}

private object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SELECCIONAR_EMPRESA = "seleccionarEmpresa"
    const val SIN_ACCESO = "sinAcceso"
    const val LOCALES = "locales"
    const val LOCAL_DETALLE = "local/{${LocalDetalleViewModel.ARG_LOCAL_ID}}"
    const val LOCAL_DEUDAS = "local/{${DeudasLocalViewModel.ARG_LOCAL_ID}}/deudas"
    const val CAMBIO_PLACA = "cambio-placa/{${CambioPlacaViewModel.ARG_INSTALACION_ID}}"
    const val REPORTAR_AVERIA = "averia/{${ReportarAveriaViewModel.ARG_MAQUINA_ID}}"
    const val IMPRESORA = "impresora"
    const val AJUSTES = "ajustes"
    const val HISTORICO = "historico"
    const val ALERTAS = "alertas"
    const val HISTORICO_DETALLE =
        "historico/{${HistoricoDetalleViewModel.ARG_RECAUDACION_ID}}"

    // Recaudación (sub-graph)
    const val RECAUDACION_GRAPH =
        "recaudacion/{${RecaudacionFlowViewModel.ARG_INSTALACION_ID}}" +
            "?${RecaudacionFlowViewModel.ARG_CADENA_LOCAL_ID}={${RecaudacionFlowViewModel.ARG_CADENA_LOCAL_ID}}"
    const val RECAUDACION_CONTADORES = "recaudacion-contadores"
    const val RECAUDACION_DENOMINACIONES_TOTAL = "recaudacion-denominaciones-total"
    const val RECAUDACION_DENOMINACIONES_LOCAL = "recaudacion-denominaciones-local"
    const val RECAUDACION_CONFIRMACION = "recaudacion-confirmacion"

    fun localDetalle(localId: String): String = "local/$localId"
    fun localDeudas(localId: String): String = "local/$localId/deudas"
    fun cambioPlaca(instalacionId: String): String = "cambio-placa/$instalacionId"
    fun reportarAveria(maquinaId: String): String = "averia/$maquinaId"
    fun historicoDetalle(recaudacionId: String): String = "historico/$recaudacionId"

    fun recaudacion(instalacionId: String, cadenaLocalId: String? = null): String {
        val base = "recaudacion/$instalacionId"
        return if (cadenaLocalId == null) {
            base
        } else {
            "$base?${RecaudacionFlowViewModel.ARG_CADENA_LOCAL_ID}=$cadenaLocalId"
        }
    }

    // Gestión (T-66..T-69)
    const val GESTION = "gestion"
    const val GESTION_LICENCIAS = "gestion/licencias"
    const val GESTION_LICENCIA_NUEVA = "gestion/licencias/nueva"
    const val GESTION_LICENCIA_EDITAR =
        "gestion/licencias/{${LicenciaFormViewModel.ARG_LICENCIA_ID}}"
    const val GESTION_MAQUINAS = "gestion/maquinas"
    const val GESTION_MAQUINA_NUEVA = "gestion/maquinas/nueva"
    const val GESTION_MAQUINA_EDITAR =
        "gestion/maquinas/{${MaquinaFormViewModel.ARG_MAQUINA_ID}}"
    const val GESTION_MAQUINA_AVERIAS =
        "gestion/maquinas/{${AveriasMaquinaViewModel.ARG_MAQUINA_ID}}/averias"
    const val GESTION_LOCALES = "gestion/locales"
    const val GESTION_LOCAL_NUEVO = "gestion/locales/nuevo"
    const val GESTION_LOCAL_EDITAR =
        "gestion/locales/{${LocalFormViewModel.ARG_LOCAL_ID}}"
    const val GESTION_DEUDAS = "gestion/deudas"
    const val GESTION_INSTALACIONES = "gestion/instalaciones"
    const val GESTION_INSTALACION_NUEVA = "gestion/instalaciones/nueva"
    const val GESTION_INSTALACION_EDITAR =
        "gestion/instalaciones/{${InstalacionFormViewModel.ARG_INSTALACION_ID}}"

    fun gestionLicenciaEditar(id: String): String = "gestion/licencias/$id"
    fun gestionMaquinaEditar(id: String): String = "gestion/maquinas/$id"
    fun gestionMaquinaAverias(id: String): String = "gestion/maquinas/$id/averias"
    fun gestionLocalEditar(id: String): String = "gestion/locales/$id"
    fun gestionInstalacionEditar(id: String): String = "gestion/instalaciones/$id"
}

/**
 * `true` si el tipo de alerta enlaza a una recaudación concreta.
 * Cuando es `false` (p. ej. licencia caducada, local sin recaudar) el
 * `referencia_id` apunta a otra entidad para la que aún no hay detalle
 * en la app del técnico, así que ignoramos el tap.
 */
private fun esRecaudacionRelevante(tipo: TipoAlerta): Boolean = when (tipo) {
    TipoAlerta.RecaudacionConflicto, TipoAlerta.RecaudacionAnulada -> true
    else -> false
}

