package com.recre.app.core.util

/**
 * Resultado de una operación de dominio.
 *
 * Convenciones:
 * - Éxito → [Success].
 * - Error de dominio (red, validación, auth, conflicto…) → [Failure] con
 *   un [DomainError] tipado, NUNCA con `Throwable` directo.
 *
 * Los repositorios capturan las excepciones técnicas y las mapean a
 * [DomainError] antes de devolver.
 */
sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

/**
 * Errores de dominio comunes a toda la app.
 * Cada feature puede crear códigos más específicos como subclases.
 */
sealed interface DomainError {
    val message: String?

    data class Network(override val message: String? = null) : DomainError
    data class Validation(override val message: String? = null) : DomainError
    data class Auth(override val message: String? = null) : DomainError
    data class Conflict(override val message: String? = null) : DomainError
    data class NotFound(override val message: String? = null) : DomainError
    data class Unknown(override val message: String? = null) : DomainError
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

inline fun <T> DomainResult<T>.onSuccess(action: (T) -> Unit): DomainResult<T> {
    if (this is DomainResult.Success) action(value)
    return this
}

inline fun <T> DomainResult<T>.onFailure(action: (DomainError) -> Unit): DomainResult<T> {
    if (this is DomainResult.Failure) action(error)
    return this
}
