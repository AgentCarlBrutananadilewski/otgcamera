package com.toyrobotworkshop.auspex.util

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
        const val ACTION_USB_CAMERA_FOUND = "com.toyrobotworkshop.auspex.USB_CAMERA_FOUND"
        const val ACTION_USB_CAMERA_LOST = "com.toyrobotworkshop.auspex.USB_CAMERA_LOST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (isUvcDevice(device)) {
                    Log.d(tag, "UVC camera attached: ${device?.deviceName}")
                    context.sendBroadcast(Intent(ACTION_USB_CAMERA_FOUND).apply {
                        putExtra(UsbManager.EXTRA_DEVICE, device)
                        setPackage(context.packageName)
                    })
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (isUvcDevice(device)) {
                    Log.d(tag, "UVC camera detached: ${device?.deviceName}")
                    context.sendBroadcast(Intent(ACTION_USB_CAMERA_LOST).apply {
                        setPackage(context.packageName)
                    })
                }
            }
        }
    }

    /**
     * Check if a USB device is a UVC camera.
     *
     * UVC cameras can report their class at either the device level (class=0x0E)
     * or at the interface level via an IAD (Interface Association Descriptor,
     * class=239/bInterfaceClass=0x0E). We check both patterns.
     */
    private fun isUvcDevice(device: UsbDevice?): Boolean {
        if (device == null) return false

        // Direct UVC device (class at device level)
        if (device.deviceClass == 0x0E && device.deviceSubclass == 0x03) {
            return true
        }

        // IAD-based UVC device (common for composite devices)
        if (device.deviceClass == 239 && device.deviceSubclass == 2) {
            return true
        }

        // Some cheap cameras report class=0 at device level — check interfaces
        if (device.deviceClass == 0) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.getInterfaceClass() == 0x0E && iface.getInterfaceSubclass() == 0x03) {
                    return true
                }
            }
        }

        return false
    }
}
