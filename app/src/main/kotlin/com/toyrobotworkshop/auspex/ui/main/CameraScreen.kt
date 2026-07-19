package com.toyrobotworkshop.auspex.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    // Keep screen on only when camera is ready/previewing
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(uiState.status) {
        val shouldKeepOn = uiState.status == CameraStatus.Ready || uiState.status == CameraStatus.Previewing
        if (shouldKeepOn) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    val isCameraReady = uiState.status == CameraStatus.Ready ||
            uiState.status == CameraStatus.Previewing
    val isRecording = uiState.isRecording

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isCameraReady) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    actions = {
                        // Gallery
                        IconButton(onClick = { /* TODO: show gallery */ }) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.action_gallery),
                            )
                        }

                        // Capture photo
                        IconButton(onClick = {
                            val path = com.toyrobotworkshop.auspex.util.FileSaver.getPhotoPath(context.cacheDir)
                            viewModel.capturePhoto(path)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = stringResource(R.string.capture_photo),
                            )
                        }

                        // Settings
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    },
                    floatingActionButton = {
                        // Primary capture action — LargeFloatingActionButton
                        LargeFloatingActionButton(
                            onClick = {
                                if (isRecording) {
                                    viewModel.stopRecording()
                                } else {
                                    val path = com.toyrobotworkshop.auspex.util.FileSaver.getVideoPath(context.cacheDir)
                                    viewModel.startRecording(path)
                                }
                            },
                            containerColor = if (isRecording)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isRecording)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = if (isRecording)
                                    Icons.Rounded.StopCircle
                                else
                                    Icons.Rounded.CameraAlt,
                                contentDescription = stringResource(
                                    if (isRecording) R.string.stop_recording else R.string.start_recording
                                ),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                            text = uiState.message ?: stringResource(R.string.initializing_camera),
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
                            text = stringResource(R.string.camera_error),
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
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}
