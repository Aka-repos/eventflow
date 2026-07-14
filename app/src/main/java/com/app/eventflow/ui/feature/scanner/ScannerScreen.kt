package com.app.eventflow.ui.feature.scanner

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.app.eventflow.R
import com.app.eventflow.domain.model.checkin.CheckInOutcome
import com.app.eventflow.ui.components.EfPrimaryButton
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerRoute(onNavigateBack: () -> Unit, viewModel: ScannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        viewModel.effects.collect {
            when (it) {
                ScannerUiEffect.NavigateBack -> onNavigateBack()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(ScannerUiEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (cameraPermission.status.isGranted) {
                CameraPreview(
                    active = state.cameraActive,
                    onQr = { viewModel.onEvent(ScannerUiEvent.QrDetected(it)) },
                )
                OutcomeOverlay(state = state, onScanNext = { viewModel.onEvent(ScannerUiEvent.ScanNext) })
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.scanner_permission_needed), textAlign = TextAlign.Center)
                    EfPrimaryButton(text = stringResource(R.string.scanner_grant_permission),
                        onClick = { cameraPermission.launchPermissionRequest() })
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(active: Boolean, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, QrCameraAnalyzer(onQr)) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@Composable
private fun OutcomeOverlay(state: ScannerUiState, onScanNext: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        when {
            state.isProcessing -> CircularProgressIndicator(Modifier.padding(48.dp))
            state.networkError -> ResultCard(
                background = MaterialTheme.colorScheme.errorContainer,
                title = stringResource(R.string.error_network),
                subtitle = null,
                onScanNext = onScanNext,
            )
            state.lastOutcome is CheckInOutcome.Granted -> ResultCard(
                background = MaterialTheme.colorScheme.primaryContainer,
                title = stringResource(R.string.scanner_granted),
                subtitle = listOfNotNull(
                    (state.lastOutcome).attendeeName,
                    (state.lastOutcome).ticketTypeName,
                    (state.lastOutcome).zoneName,
                ).joinToString(" · "),
                onScanNext = onScanNext,
            )
            state.lastOutcome is CheckInOutcome.Denied -> ResultCard(
                background = MaterialTheme.colorScheme.errorContainer,
                title = stringResource(R.string.scanner_denied),
                subtitle = (state.lastOutcome).message,
                onScanNext = onScanNext,
            )
        }
    }
}

@Composable
private fun ResultCard(background: Color, title: String, subtitle: String?, onScanNext: () -> Unit) {
    androidx.compose.material3.Surface(
        color = background,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Button(onClick = onScanNext) { Text(stringResource(R.string.scanner_scan_next)) }
        }
    }
}
