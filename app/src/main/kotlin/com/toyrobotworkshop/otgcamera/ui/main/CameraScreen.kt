package com.toyrobotworkshop.otgcamera.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
) {
    val context = LocalContext.current
    val backendResult by viewModel.backendResult.collectAsState()
    val cameraInterface by viewModel.cameraInterface.collectAsState()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var isRecording by derivedStateOf { state == CameraState.Recording }

    // Camera permission check
    val cameraPermissionLauncher = rememberLauncherForPermission(
        permission = Manifest.permission.CAMERA,
        onGranted = { /* permission granted */ },
        onDenied = { /* handle denial */ },
    )

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Auto-open camera when backend is detected and permission is granted
    LaunchedEffect(backendResult, hasCameraPermission) {
        if (backendResult !is CameraManager.BackendResult.None && hasCameraPermission) {
            viewModel.openCamera()
        }
    }

    // Handle no device case
    LaunchedEffect(backendResult) {
        if (backendResult is CameraManager.BackendResult.None) {
            onNoDevice()
        }
    }

    Scaffold(
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
            // Camera preview surface
            if (cameraInterface != null || state == CameraState.Opening) {
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

            // Error overlay
            error?.let { errorMsg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { /* dismiss */ }) {
                            Text("Dismiss")
                        }
                    },
                ) {
                    Text(errorMsg)
                }
            }

            // Bottom controls bar
            if (cameraInterface != null) {
                CameraControlsBar(
                    isRecording = isRecording,
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

            // Recording indicator
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = "Recording",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("00:00", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Controls bottom sheet
            if (showControls) {
                ControlsSheet(
                    cameraInterface = cameraInterface,
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
