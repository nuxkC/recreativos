package com.recre.app.core.data.repository

import com.recre.app.core.util.DomainError

/**
 * Resultado tipado para los CRUDs del gestor (T-66..T-69).
 *
 * A diferencia de [com.recre.app.core.util.DomainResult], esta variante
 * preserva el **código i18n** discriminado por
 * [com.recre.app.core.data.remote.clasificarErrorGestion] (`network`,
 * `auth`, `numero_serie_duplicado`, `instalacion_activa_maquina`...).
 * El ViewModel lo usa directamente para resolver el `R.string.gestor_error_*`
 * sin un segundo switch fuera del repositorio.
 */
sealed interface GestionResult<out T> {
    data class Success<T>(val value: T) : GestionResult<T>
    data class Failure(
        val error: DomainError,
        val code: String,
    ) : GestionResult<Nothing>
}
