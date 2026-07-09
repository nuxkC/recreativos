package com.recre.app.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
private val CON_HORA = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", ES)
private val SIN_HORA = DateTimeFormatter.ofPattern("d MMM yyyy", ES)

/** Fecha humanizada del mockup: «mié 1 jul, 12:48» / «1 jul 2026». TZ del dispositivo. */
fun formatFechaHumana(instant: Instant, incluirHora: Boolean = true): String {
    val z = instant.atZone(ZoneId.systemDefault())
    val f = (if (incluirHora) CON_HORA else SIN_HORA).format(z)
    // es-ES ya da día/mes abreviados en minúscula; normalizamos por si el locale del
    // dispositivo capitaliza (p. ej. «Mié.» → «mié»), y quitamos el punto abreviador.
    return f.replace(".", "").lowercase(ES)
}
