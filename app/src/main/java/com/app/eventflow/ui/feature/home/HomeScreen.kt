package com.app.eventflow.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.ui.components.EfPrimaryButton

/** Placeholder del módulo 2 (catálogo): demuestra sesión persistida + logout. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        session?.let { user ->
            Text(stringResource(R.string.home_greeting, user.fullName))
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
        }
        EfPrimaryButton(text = stringResource(R.string.home_logout), onClick = viewModel::onLogout)
    }
}
