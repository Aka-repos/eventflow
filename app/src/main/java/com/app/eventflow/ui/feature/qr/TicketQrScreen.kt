package com.app.eventflow.ui.feature.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.ui.components.EfPrimaryButton

@Composable
fun TicketQrRoute(onNavigateBack: () -> Unit, viewModel: TicketQrViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect {
            when (it) {
                TicketQrUiEffect.NavigateBack -> onNavigateBack()
            }
        }
    }
    // refresco automático del QR dinámico antes de que expire
    LaunchedEffect(state.qr?.qrToken) {
        state.qr?.let {
            kotlinx.coroutines.delay(viewModel.millisUntilRefresh(it.refreshAfter))
            viewModel.refresh()
        }
    }
    TicketQrScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketQrScreen(state: TicketQrUiState, onEvent: (TicketQrUiEvent) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(TicketQrUiEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.notYetVisible -> Text(
                    stringResource(R.string.qr_not_yet_visible),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(state.error, textAlign = TextAlign.Center)
                    EfPrimaryButton(text = stringResource(R.string.catalog_retry),
                        onClick = { onEvent(TicketQrUiEvent.Retry) })
                }
                state.qr != null -> {
                    val bitmap = remember(state.qr.qrToken) { encodeQrBitmap(state.qr.qrToken) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_title),
                            modifier = Modifier.size(280.dp).background(Color.White).padding(12.dp),
                        )
                        Text(
                            stringResource(R.string.qr_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
        }
    }
}
