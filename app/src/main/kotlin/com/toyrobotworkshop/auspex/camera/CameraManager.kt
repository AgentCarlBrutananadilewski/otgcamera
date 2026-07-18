package com.toyrobotworkshop.auspex.camera

import android.content.Context
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.toyrobotworkshop.auspex.camera.camera2.Camera2Backend
import com.toyrobotworkshop.auspex.camera.uvc.UVCBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory that determines which backend to use and creates the appropriate [CameraInterface].
 *
 * Priority:
 * 1. Camera2 API — if the OEM has enabled external camera support (LENS_FACING_EXTERNAL)
 * 2. UVCCamera — direct USB access via alexey-pelykh's library
 */
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val camera2Backend: Camera2Backend,
    private val uvcBackend: UVCBackend,
) {

    private val tag = "CameraManager"

    /**
     * Check if a Camera2-compatible external camera is available.
     * Returns the camera ID or null.
     */
    fun findExternalCameraId(): String? {
        val cameraManager = context.getSystemService(AndroidCameraManager::class.java)
        for (id in cameraManager.cameraIdList) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val lensFacing = characteristics.get(
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING
                )
                if (lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    Log.d(tag, "Found external camera via Camera2: $id")
                    return id
                }
            } catch (e: Exception) {
                Log.w(tag, "Error checking camera $id", e)
            }
        }
        return null
    }

    /**
     * Check if any UVC-compatible USB device is connected.
     *
     * Handles both direct UVC devices (class=0x0E at device level) and
     * IAD-based composite devices (class=239 with UVC interfaces).
     */
    fun findUvcDevice(): UsbDevice? {
        val usbManager = context.getSystemService(UsbManager::class.java)
        for (device in usbManager.deviceList.values) {
            if (isUvcDevice(device)) {
                Log.d(tag, "Found UVC device: ${device.deviceName}")
                return device
            }
        }
        return null
    }

    /**
     * Check if a USB device is a UVC camera.
     */
    private fun isUvcDevice(device: UsbDevice): Boolean {
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

    /**
     * Determine which backend should be used.
     */
    fun detectBackend(): BackendResult {
        val camera2Id = findExternalCameraId()
        if (camera2Id != null) {
            return BackendResult.Camera2(camera2Id)
        }

        val uvcDevice = findUvcDevice()
        if (uvcDevice != null) {
            return BackendResult.UVCCamera(uvcDevice)
        }

        Log.d(tag, "No USB camera detected")
        return BackendResult.None
    }

    /**
     * Initialize and return the Camera2 backend for a specific camera ID.
     */
    suspend fun initializeCamera2(cameraId: String): CameraInterface {
        withContext(Dispatchers.IO) {
            camera2Backend.initialize(cameraId)
        }
        return camera2Backend
    }

    /**
     * Initialize and return the UVC backend for a specific USB device.
     * NOTE: Must be called on the main thread — USB permission dialogs require it.
     */
    suspend fun initializeUVC(device: UsbDevice, activityContext: Context? = null): CameraInterface {
        // DO NOT wrap in Dispatchers.IO — USB permission dialog must be shown on main thread
        uvcBackend.initialize(device, activityContext)
        return uvcBackend
    }

    /**
     * Request USB permission for a UVC device.
     * Returns true if permission was already granted or the user approved it.
     */
    fun hasUsbPermission(device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(UsbManager::class.java)
        return usbManager.hasPermission(device)
    }

    sealed interface BackendResult {
        data class Camera2(val cameraId: String) : BackendResult
        data class UVCCamera(val device: UsbDevice) : BackendResult
        object None : BackendResult
    }
}
