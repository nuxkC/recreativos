package com.recre.app.feature.auth

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.theme.RecreShapes

/**
 * Pantalla de acceso (HU-1). Diseño "Confianza Industrial": marca centrada
 * (tile petróleo + nombre + claim), campos limpios y un CTA **anclado al
 * fondo** que permanece visible por encima del teclado.
 *
 * El contenido superior es desplazable y el contenedor aplica `imePadding()`,
 * de modo que al enfocar la contraseña el área se encoge y el botón "Entrar"
 * nunca queda tapado por el IME (era el bug de la versión anterior).
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorInvalid = stringResource(R.string.auth_login_error_invalid)
    val errorGeneric = stringResource(R.string.auth_login_error_generic)

    LaunchedEffect(state.errorMessage) {
        val key = state.errorMessage ?: return@LaunchedEffect
        val message = when (key) {
            LoginViewModel.ERROR_INVALID_CREDENTIALS -> errorInvalid
            else -> errorGeneric
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    // Entrada sutil: marca y formulario aparecen con un fundido + leve ascenso,
    // una sola vez. Con reduce-motion aparecen al instante.
    val animaciones = rememberAnimationsEnabled()
    var aparecido by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { aparecido = true }
    val entrada by animateFloatAsState(
        targetValue = if (aparecido) 1f else 0f,
        animationSpec =
            if (animaciones) tween(durationMillis = 520, easing = FastOutSlowInEasing) else snap(),
        label = "login-entrada",
    )

    Scaffold(
        snackbarHost = { RecreSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        alpha = entrada
                        translationY = (1f - entrada) * 20.dp.toPx()
                    }
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                MarcaRecre()
                Spacer(Modifier.height(40.dp))

                FieldText(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = stringResource(R.string.auth_login_email),
                    placeholder = stringResource(R.string.auth_login_email_placeholder),
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(12.dp))
                FieldText(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.auth_login_password),
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(24.dp))
            }

            // CTA anclado: queda SIEMPRE visible por encima del teclado.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = entrada }
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                RecrePrimaryButton(
                    text = stringResource(R.string.auth_login_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit && !state.submitting,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Identidad de marca: tile petróleo con glifo de fichas + nombre + claim. */
@Composable
private fun MarcaRecre() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RecreShapes.large,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Toll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.auth_login_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale != 0f
    }
}
