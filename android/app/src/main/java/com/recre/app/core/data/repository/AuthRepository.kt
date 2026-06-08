package com.recre.app.core.data.repository

import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Operaciones de autenticación. La implementación concreta vive en `data/`
 * y solo se inyecta por interfaz para no acoplar la UI a Supabase.
 */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): DomainResult<Unit>
    suspend fun signOut(): DomainResult<Unit>
    fun observeIsLoggedIn(): Flow<Boolean>

    /**
     * UUID del usuario autenticado o `null` si no hay sesión activa.
     * Usado por el flujo de recaudación (T-57) para sellar el `tecnico_id`
     * en la cola offline antes de que el server lo reasigne tras subir.
     */
    fun currentUserId(): String?

    /**
     * Email del usuario autenticado o `null` si no hay sesión o
     * Supabase no lo ha devuelto (raro). Lo usa el ticket impreso por
     * T-62 para que el técnico quede identificado en el papel.
     */
    fun currentUserEmail(): String?
}

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): DomainResult<Unit> {
        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = { throwable ->
                val message = throwable.message
                val error = when {
                    message?.contains("invalid", ignoreCase = true) == true ->
                        DomainError.Auth(message)
                    message?.contains("network", ignoreCase = true) == true ->
                        DomainError.Network(message)
                    else -> DomainError.Unknown(message)
                }
                DomainResult.Failure(error)
            },
        )
    }

    override suspend fun signOut(): DomainResult<Unit> {
        return runCatching { supabase.auth.signOut() }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = { DomainResult.Failure(DomainError.Unknown(it.message)) },
        )
    }

    override fun observeIsLoggedIn(): Flow<Boolean> =
        supabase.auth.sessionStatus.map { status ->
            status is SessionStatus.Authenticated
        }

    override fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    override fun currentUserEmail(): String? = supabase.auth.currentUserOrNull()?.email
}
