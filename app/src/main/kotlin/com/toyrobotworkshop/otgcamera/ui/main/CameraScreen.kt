package com.toyrobotworkshop.otgcamera.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.toyrobotworkshop.otgcamera.util.FileSaver

/**
 * Main camera screen — preview, capture button, and controls toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onNoDevice: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showControls by remember { mutableStateOf(false) }

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Auto-detect camera on launch
    LaunchedEffect(Unit) {
        viewModel.detectAndInitialize(context)
    }

    // Handle no device case
    LaunchedEffect(uiState.status) {
        if (uiState.status == CameraStatus.NoCamera) {
            onNoDevice()
        }
    }

    // Show status message during initialization
    val statusMessage = uiState.message?.takeIf { it.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OTG Camera") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_preferences),
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showControls = !showControls },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(if (showControls) "✕" else "⚙")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Camera preview surface — only show when camera is actually ready or previewing
            val isCameraReady = uiState.status == CameraStatus.Ready ||
                    uiState.status == CameraStatus.Previewing
            if (isCameraReady) {
                PreviewView(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceReady = { surfaceTexture ->
                        viewModel.setPreviewSurface(surfaceTexture)
                    },
                    onSurfaceDestroyed = {
                        viewModel.clearPreviewSurface()
                    },
                )
            }

            // Initializing overlay
            if (uiState.status == CameraStatus.Initializing || uiState.status == CameraStatus.Detecting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusMessage ?: "Initializing camera...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Error overlay
            if (uiState.status is CameraStatus.Error) {
                val errorMsg = (uiState.status as CameraStatus.Error).message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            text = "Camera Error",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.detectAndInitialize(context) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Bottom controls bar
            if (uiState.status == CameraStatus.Ready || uiState.status == CameraStatus.Previewing) {
                CameraControlsBar(
                    isRecording = false, // TODO: track recording state
                    onCapturePhoto = {
                        val path = FileSaver.getPhotoPath(context.cacheDir)
                        viewModel.capturePhoto(path)
                    },
                    onStartRecording = {
                        val path = FileSaver.getVideoPath(context.cacheDir)
                        viewModel.startRecording(path)
                    },
                    onStopRecording = {
                        viewModel.stopRecording()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // Controls bottom sheet
            if (showControls) {
                ControlsSheet(
                    cameraInterface = null, // TODO: expose from ViewModel
                    onDismiss = { showControls = false },
                )
            }
        }
    }
}

@Composable
private fun CameraControlsBar(
    isRecording: Boolean,
    onCapturePhoto: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
    ) {
        // Gallery thumbnail (last captured photo)
        IconButton(onClick = { /* TODO: show gallery */ }) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = "Gallery",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Capture / Record button
        if (isRecording) {
            ExtendedFloatingActionButton(
                onClick = onStopRecording,
                containerColor = MaterialTheme.colorScheme.error,
            ) {
                Text("Stop", color = MaterialTheme.colorScheme.onError)
            }
        } else {
            ExtendedFloatingActionButton(
                onClick = onCapturePhoto,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Text("📷", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Video mode toggle
        IconButton(onClick = { /* TODO: toggle video mode */ }) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_camera),
                contentDescription = "Video mode",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
