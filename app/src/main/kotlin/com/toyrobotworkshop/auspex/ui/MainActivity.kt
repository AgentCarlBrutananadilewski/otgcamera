package com.toyrobotworkshop.auspex.ui

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.toyrobotworkshop.auspex.ui.navigation.NavGraph
import com.toyrobotworkshop.auspex.ui.theme.AuspexTheme
import com.toyrobotworkshop.auspex.util.UsbReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleUsbIntent(intent)
        setContent {
            AuspexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (device != null) {
                // Forward to local receivers (like the one in CameraViewModel)
                sendBroadcast(Intent(UsbReceiver.ACTION_USB_CAMERA_FOUND).apply {
                    putExtra(UsbManager.EXTRA_DEVICE, device)
                    setPackage(packageName)
                })
            }
        }
    }
}
