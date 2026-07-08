package com.recre.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Héroe desnudo (S11, mockup hero-conteo/hero-dinero): eyebrow + cifra
 * protagonista + sub, SOBRE EL FONDO — sin card contenedora. El valor lo pone
 * el llamador (OdometroText para dinero, Text displayHero para conteos) para
 * que el héroe no conozca formatos.
 */
@Composable
fun HeroSection(
    eyebrow: String,
    modifier: Modifier = Modifier,
    sub: (@Composable () -> Unit)? = null,
    valor: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(6.dp))
        valor()
        if (sub != null) {
            Spacer(Modifier.height(4.dp))
            sub()
        }
    }
}
