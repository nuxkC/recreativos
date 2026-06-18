package com.recre.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost

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

    Scaffold(
        snackbarHost = { RecreSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_login_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))

            FieldText(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.auth_login_email),
                placeholder = stringResource(R.string.auth_login_email_placeholder),
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(12.dp))
            FieldText(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.auth_login_password),
                keyboardType = KeyboardType.Password,
                isPassword = true,
            )
            Spacer(Modifier.height(24.dp))
            RecrePrimaryButton(
                text = stringResource(R.string.auth_login_submit),
                onClick = viewModel::submit,
                enabled = state.canSubmit && !state.submitting,
                loading = state.submitting,
            )
        }
    }
}
