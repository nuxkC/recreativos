package com.recre.app.core.session

/**
 * Estado global de la sesión del usuario.
 *
 * Drive del NavHost raíz: cada destino se mapea 1:1 a uno de estos valores.
 * No tiene UI específica; las pantallas concretas viven en `feature/` y
 * solo reaccionan al estado.
 */
sealed interface SessionState {

    /** Estado inicial: aún no sabemos si hay sesión ni membresías. */
    data object Loading : SessionState

    /** El usuario no ha iniciado sesión. */
    data object NotAuthenticated : SessionState

    /**
     * Sesión iniciada pero sin membresías activas: el usuario fue
     * desactivado en todas las empresas o nunca aceptó una invitación.
     */
    data object NoMemberships : SessionState

    /**
     * Sesión válida pero las membresías no se pudieron cargar y no hay cache
     * de respaldo (p. ej. primer login sin red). La UI ofrece reintentar;
     * sin este estado el arranque se quedaba en un spinner sin salida.
     */
    data class LoadError(val message: String?) : SessionState

    /**
     * Sesión iniciada con varias membresías y ninguna seleccionada todavía
     * (o la cookie persistida apunta a una que ya no es válida).
     */
    data class NeedsEmpresaSelection(
        val membresias: List<Membresia>,
    ) : SessionState

    /** Sesión iniciada y empresa activa resuelta. La app es navegable. */
    data class Active(
        val membresia: Membresia,
        val membresias: List<Membresia>,
    ) : SessionState {
        val empresa: EmpresaResumen get() = membresia.empresa
    }
}
