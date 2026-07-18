package com.toyrobotworkshop.auspex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.toyrobotworkshop.auspex.ui.main.CameraScreen
import com.toyrobotworkshop.auspex.ui.main.CameraStatus
import com.toyrobotworkshop.auspex.ui.main.CameraViewModel
import com.toyrobotworkshop.auspex.ui.main.NoDeviceScreen
import com.toyrobotworkshop.auspex.ui.settings.SettingsScreen

/**
 * Routes in the app.
 */
object Routes {
    const val CAMERA = "camera"
    const val NO_DEVICE = "no_device"
    const val SETTINGS = "settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
) {
    // Hoist the ViewModel to NavGraph scope so it is shared across all destinations
    // and survives navigation between camera ↔ no_device screens.
    // This lets the USB reconnect broadcast handler in the ViewModel keep running even
    // when the user is on NoDeviceScreen, and automatically navigate back on plug-in.
    val viewModel: CameraViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Auto-navigate: camera → no_device when camera is lost
    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            CameraStatus.NoCamera -> {
                val current = navController.currentBackStackEntry?.destination?.route
                if (current != Routes.NO_DEVICE) {
                    navController.navigate(Routes.NO_DEVICE) {
                        popUpTo(Routes.CAMERA) { inclusive = true }
                    }
                }
            }
            // Auto-navigate: no_device → camera when camera reconnects and becomes ready
            CameraStatus.Ready, CameraStatus.Previewing -> {
                val current = navController.currentBackStackEntry?.destination?.route
                if (current == Routes.NO_DEVICE) {
                    navController.navigate(Routes.CAMERA) {
                        popUpTo(Routes.NO_DEVICE) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.CAMERA,
    ) {
        composable(Routes.CAMERA) {
            CameraScreen(
                viewModel = viewModel,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.NO_DEVICE) {
            NoDeviceScreen(
                onRetry = { viewModel.detectAndInitialize(navController.context) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
