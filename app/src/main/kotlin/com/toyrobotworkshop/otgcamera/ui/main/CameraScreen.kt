package com.toyrobotworkshop.otgcamera.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.otgcamera.R
import com.toyrobotworkshop.otgcamera.util.FileSaver

/**
 * Main camera screen — preview and bottom action bar.
 *
 * Receives a shared [viewModel] from NavGraph so ViewModel state (and the USB
 * reconnect broadcast receiver) survives navigation to/from NoDeviceScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Auto-detect camera on launch
    LaunchedEffect(Unit) {
        viewModel.detectAndInitialize(context)
    }

    val statusMessage = uiState.message?.takeIf { it.isNotEmpty() }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isCameraReady = uiState.status == CameraStatus.Ready ||
                    uiState.status == CameraStatus.Previewing

            if (isCameraReady) {
                PreviewView(
                    modifier = Modifier.fillMaxSize(),
                    cameraResolution = uiState.resolution,
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

            // Bottom action bar — 4 round icon buttons
            if (isCameraReady) {
                BottomActionBar(
                    onGalleryClick = { /* TODO: show gallery */ },
                    onCapturePhoto = {
                        val path = FileSaver.getPhotoPath(context.cacheDir)
                        viewModel.capturePhoto(path)
                    },
                    onStartRecording = {
                        val path = FileSaver.getVideoPath(context.cacheDir)
                        viewModel.startRecording(path)
                    },
                    onStopRecording = viewModel::stopRecording,
                    onSettingsClick = onSettingsClick,
                    isRecording = false, // TODO: track recording state
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private val buttonSize = 64.dp
private val buttonColor = Color(0xFF424242)

@Composable
private fun BottomActionBar(
    onGalleryClick: () -> Unit,
    onCapturePhoto: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSettingsClick: () -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    val navBarHeight = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .padding(bottom = navBarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomAction(
            icon = { Icon(painterResource(R.drawable.ic_photo_library), "Gallery", tint = Color.White) },
            onClick = onGalleryClick,
        )
        BottomAction(
            icon = { Icon(painterResource(R.drawable.ic_camera), "Capture Photo", tint = Color.White) },
            onClick = onCapturePhoto,
        )
        BottomAction(
            icon = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.error, shape = CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fiber_manual_record),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            onClick = if (isRecording) onStopRecording else onStartRecording,
        )
        BottomAction(
            icon = { Icon(painterResource(R.drawable.ic_settings), "Settings", tint = Color.White) },
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun BottomAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = buttonColor,
        modifier = Modifier.size(buttonSize),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}
