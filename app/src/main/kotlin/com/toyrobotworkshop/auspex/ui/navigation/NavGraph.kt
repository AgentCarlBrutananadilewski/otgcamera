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
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations.
 *
 * Using sealed classes with @Serializable routes eliminates string typos at compile time
 * and gives us IDE autocomplete for all destinations.
 */
sealed class Screen {
    @Serializable data object Camera : Screen()
    @Serializable data object NoDevice : Screen()
    @Serializable data object Settings : Screen()
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
                if (!navController.currentBackStackEntry?.destination?.route.isNullOrEmpty()) {
                    navController.navigate(Screen.NoDevice) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
            // Auto-navigate: no_device → camera when camera reconnects and becomes ready
            CameraStatus.Ready, CameraStatus.Previewing -> {
                if (!navController.currentBackStackEntry?.destination?.route.isNullOrEmpty()) {
                    navController.navigate(Screen.Camera) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Camera,
    ) {
        composable<Screen.Camera> {
            CameraScreen(
                viewModel = viewModel,
                onSettingsClick = { navController.navigate(Screen.Settings) },
            )
        }
        composable<Screen.NoDevice> {
            NoDeviceScreen(
                onRetry = { viewModel.detectAndInitialize(navController.context) },
            )
        }
        composable<Screen.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
