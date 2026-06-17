package com.recre.app.ui.icons

import androidx.annotation.DrawableRes
import com.recre.app.R

/**
 * Set de iconos propios de dominio (rediseño F0). Indirección sobre `res/drawable`
 * para que las pantallas referencien `RecreIcons.Recaudar` en vez de `Icons.Filled.*`
 * sueltos de Material: al sustituir el arte por el set definitivo no se tocan los
 * sitios de uso. Andamiaje con los primeros iconos; cada fase amplía el set al migrar
 * sus pantallas.
 */
object RecreIcons {
    @DrawableRes val Recaudar = R.drawable.ic_recaudar
    @DrawableRes val Averia = R.drawable.ic_averia
    @DrawableRes val Local = R.drawable.ic_local
    @DrawableRes val Maquina = R.drawable.ic_maquina
}
