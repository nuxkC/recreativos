package com.recre.app.feature.empresa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recre.app.R
import com.recre.app.ui.components.Pictograma
import com.recre.app.ui.components.PictogramaTono
import com.recre.app.ui.components.RecrePrimaryButton

@Composable
fun SinAccesoScreen(
    viewModel: SinAccesoViewModel = hiltViewModel(),
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Pictograma(Icons.Filled.Lock, tono = PictogramaTono.NEUTRO)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.empresa_sin_acceso_titulo),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.empresa_sin_acceso_descripcion),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            RecrePrimaryButton(
                text = stringResource(R.string.auth_signout),
                onClick = viewModel::cerrarSesion,
            )
        }
    }
}
