package com.toyrobotworkshop.auspex.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.auspex.R
import com.toyrobotworkshop.auspex.util.FileSaver

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

    // Runtime CAMERA permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.detectAndInitialize(context)
        }
    }

    // Request permission on launch
    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val statusMessage = uiState.message?.takeIf { it.isNotEmpty() }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val isCameraReady = uiState.status == CameraStatus.Ready ||
                uiState.status == CameraStatus.Previewing

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (isCameraReady) Modifier.keepScreenOn else Modifier),
        ) {

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
                            text = statusMessage ?: stringResource(R.string.initializing_camera),
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
    // M3 surface token with alpha for a scrim-like overlay on the camera preview
    val buttonColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .padding(bottom = navBarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomAction(
            icon = { Icon(Icons.Rounded.PhotoLibrary, "Gallery", tint = Color.White) },
            onClick = onGalleryClick,
            buttonColor = buttonColor,
        )
        BottomAction(
            icon = { Icon(Icons.Rounded.CameraAlt, "Capture Photo", tint = Color.White) },
            onClick = onCapturePhoto,
            buttonColor = buttonColor,
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
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            onClick = if (isRecording) onStopRecording else onStartRecording,
            buttonColor = buttonColor,
        )
        BottomAction(
            icon = { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) },
            onClick = onSettingsClick,
            buttonColor = buttonColor,
        )
    }
}

@Composable
private fun BottomAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    buttonColor: Color,
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
