package com.recre.app.core.locks

/**
 * Estado del lock optimista de una instalación (T-58).
 *
 * Drive del diálogo "continuar de todos modos" en la pantalla de
 * contadores: si el lock está [Ocupado], el botón principal queda
 * deshabilitado y aparece un AlertDialog ofreciendo `forzar`.
 */
sealed interface LockState {
    /** Aún no se ha intentado adquirir (cargando o sin red). */
    data object Inactivo : LockState

    /** Lock adquirido a nuestro nombre. */
    data class Adquirido(val expiresAt: String?) : LockState

    /** Otro técnico lo tiene. La UI ofrece `forzar=true`. */
    data class Ocupado(
        val tecnicoId: String?,
        val expiresAt: String?,
    ) : LockState

    /** No hay red o falló la llamada al backend. Se permite continuar offline. */
    data class Indisponible(val codigoError: String) : LockState
}
