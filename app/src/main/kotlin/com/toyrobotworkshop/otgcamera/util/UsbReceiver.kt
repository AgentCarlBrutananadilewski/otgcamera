package com.toyrobotworkshop.otgcamera.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Broadcast receiver for USB device attach/detach events.
 *
 * Posts local broadcasts that the UI layer can observe to react to
 * camera plug/unplug events in real time.
 */
class UsbReceiver : BroadcastReceiver() {

    private val tag = "UsbReceiver"

    companion object {
        const val ACTION_USB_CAMERA_FOUND = "com.toyrobotworkshop.otgcamera.USB_CAMERA_FOUND"
        const val ACTION_USB_CAMERA_LOST = "com.toyrobotworkshop.otgcamera.USB_CAMERA_LOST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (isUvcDevice(device)) {
                    Log.d(tag, "UVC camera attached: ${device?.deviceName}")
                    context.sendBroadcast(Intent(ACTION_USB_CAMERA_FOUND).apply {
                        putExtra(UsbManager.EXTRA_DEVICE, device)
                    })
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (isUvcDevice(device)) {
                    Log.d(tag, "UVC camera detached: ${device?.deviceName}")
                    context.sendBroadcast(Intent(ACTION_USB_CAMERA_LOST))
                }
            }
        }
    }

    /**
     * Check if a USB device is a UVC camera.
     */
    private fun isUvcDevice(device: UsbDevice?): Boolean {
        return device != null && device.classType == 0x0E && device.subclass == 0x01
    }
}
