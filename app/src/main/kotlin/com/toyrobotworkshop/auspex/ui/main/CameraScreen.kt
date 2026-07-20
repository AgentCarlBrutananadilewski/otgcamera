package com.toyrobotworkshop.auspex.ui.main

import android.Manifest
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    // Runtime permissions
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            viewModel.detectAndInitialize(context)
        }
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    // Keep screen on while the camera screen is being viewed
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hide status bar and force black navigation bar while camera screen is active
    val activity = (context as? ComponentActivity)
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val controller = activity?.window?.let {
                    WindowCompat.getInsetsController(it, it.decorView)
                }

                // Force navigation bar to be dark (black background, light icons)
                activity?.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                )

                // Configure the system bars to stay hidden until a swipe
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                // On cold start, the system may ignore the hide call if it happens too early.
                // Re-triggering on RESUME and using post ensure it's applied when the window is ready.
                view.post {
                    controller?.hide(WindowInsetsCompat.Type.statusBars())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            
            val controller = activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
            }
            controller?.show(WindowInsetsCompat.Type.statusBars())
            
            // Restore theme-dependent system bar styling when leaving the screen
            activity?.enableEdgeToEdge(
                statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) 
                                 else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                     else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            )
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
                    containerColor = Color.Black,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    // ── Left: navigation ──────────────────────────────
                    CameraIconButton(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.action_gallery),
                        onClick = { /* TODO: gallery */ },
                    )
                    Spacer(Modifier.width(8.dp))
                    CameraIconButton(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        onClick = onSettingsClick,
                    )

                    Spacer(Modifier.weight(1f))

                    // ── Right: camera actions ─────────────────────────
                    // Record / Stop — red when active
                    CameraIconButton(
                        imageVector = if (isRecording) Icons.Rounded.Stop
                                      else             Icons.Rounded.FiberManualRecord,
                        contentDescription = stringResource(
                            if (isRecording) R.string.stop_recording else R.string.start_recording
                        ),
                        onClick = {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording(FileSaver.getVideoPath(context.cacheDir))
                            }
                        },
                        containerColor = if (isRecording) Color(0xFFB71C1C) else Color(0xFF2A2A2A),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Shutter — always tappable, even while recording
                    CameraIconButton(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.capture_photo),
                        onClick = { viewModel.capturePhoto(FileSaver.getPhotoPath(context.cacheDir)) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
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

/** Fixed-colour circular icon button for use over a camera preview. */
@Composable
private fun CameraIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF2A2A2A),
    contentColor: Color = Color.White,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}
