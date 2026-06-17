package com.recre.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// =====================================================================
// Design System "Confianza Industrial" — Grupo Forma (rediseño F0 · spec §3).
// Radios 12/16/20. M3 mapea las formas por tamaño de componente:
//   small  = controles (botón, chip, field)  → 12dp
//   medium = cards                            → 16dp
//   large  = hojas / diálogos                 → 20dp
// La marca es FIJA: no hay esquinas a 0 ni a 28dp; el redondeo es el del spec.
// =====================================================================

val RecreShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(12.dp), // controles: botón, chip, field
        medium = RoundedCornerShape(16.dp), // cards
        large = RoundedCornerShape(20.dp), // hojas, diálogos
        extraLarge = RoundedCornerShape(20.dp),
    )

/** Píldora: chip/badge totalmente redondeado (50%). No es un slot de [Shapes]. */
val PillShape = RoundedCornerShape(percent = 50)
