package com.toyrobotworkshop.otgcamera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.toyrobotworkshop.otgcamera.ui.main.CameraScreen
import com.toyrobotworkshop.otgcamera.ui.main.NoDeviceScreen

/**
 * Routes in the app.
 */
object Routes {
    const val CAMERA = "camera"
    const val NO_DEVICE = "no_device"
}

@Composable
fun NavGraph(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CAMERA,
    ) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onNoDevice = { navController.navigate(Routes.NO_DEVICE) { popUpTo(Routes.CAMERA) { inclusive = true } } },
            )
        }
        composable(Routes.NO_DEVICE) {
            NoDeviceScreen(
                onRetry = { navController.navigate(Routes.CAMERA) { popUpTo(Routes.NO_DEVICE) { inclusive = true } } },
            )
        }
    }
}
