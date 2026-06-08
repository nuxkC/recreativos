package com.recre.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de login.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
) {
    val canSubmit: Boolean = email.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null) }
            val result = authRepository.signIn(current.email.trim(), current.password)
            when (result) {
                is DomainResult.Success -> _state.update {
                    it.copy(submitting = false, success = true)
                }
                is DomainResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = mapErrorMessage(result.error),
                    )
                }
            }
        }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun mapErrorMessage(error: DomainError): String = when (error) {
        is DomainError.Auth -> ERROR_INVALID_CREDENTIALS
        else -> ERROR_GENERIC
    }

    companion object {
        // Strings reales se resuelven en la pantalla via stringResource.
        // Aquí trabajamos con códigos para mantener el VM independiente de Android resources.
        const val ERROR_INVALID_CREDENTIALS = "auth_login_error_invalid"
        const val ERROR_GENERIC = "auth_login_error_generic"
    }
}
