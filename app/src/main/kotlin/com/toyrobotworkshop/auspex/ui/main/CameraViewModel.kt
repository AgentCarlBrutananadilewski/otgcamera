package com.toyrobotworkshop.auspex.ui.main

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toyrobotworkshop.auspex.camera.CameraInterface
import com.toyrobotworkshop.auspex.camera.CameraManager
import com.toyrobotworkshop.auspex.util.UsbReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the camera preview screen.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val cameraManager: CameraManager,
) : AndroidViewModel(application) {

    private val tag = "CameraViewModel"

    // UI state
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // Camera interface (set after backend detection)
    private var camera: CameraInterface? = null

    // SurfaceTexture from PreviewView
    private var surfaceTexture: SurfaceTexture? = null

    // Broadcast receiver for USB plug/unplug events from UsbReceiver
    private val usbEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbReceiver.ACTION_USB_CAMERA_FOUND -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    Log.d(tag, "USB camera found broadcast: ${device?.deviceName}")
                    // Only re-initialize if we're not already running
                    val status = _uiState.value.status
                    if (status == CameraStatus.NoCamera || status == CameraStatus.Idle ||
                        status is CameraStatus.Error) {
                        Log.d(tag, "Camera reconnected — re-initializing")
                        // detectAndInitialize needs an Activity context for the permission dialog.
                        // We pass the app context here; the USB permission should already be
                        // granted for a reconnect so it won't need to show a dialog.
                        detectAndInitialize(context.applicationContext)
                    }
                }

                UsbReceiver.ACTION_USB_CAMERA_LOST -> {
                    Log.d(tag, "USB camera lost broadcast")
                    handleCameraDisconnected()
                }
            }
        }
    }

    init {
        // Register for USB plug/unplug events posted by UsbReceiver
        val filter = IntentFilter().apply {
            addAction(UsbReceiver.ACTION_USB_CAMERA_FOUND)
            addAction(UsbReceiver.ACTION_USB_CAMERA_LOST)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(usbEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(usbEventReceiver, filter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { getApplication<Application>().unregisterReceiver(usbEventReceiver) } catch (_: Exception) {}
        camera?.release()
        camera = null
    }

    /**
     * Called when the camera is physically disconnected.
     * Tears down the camera, resets state, and surfaces the disconnected status to the UI
     * so the user sees feedback and reconnection can be attempted.
     */
    private fun handleCameraDisconnected() {
        Log.d(tag, "Handling camera disconnection")
        // UVCBackend.onDisconnect() already calls destroy() and resets its own state to Idle.
        // We still call release() to clean up the USBMonitor and handler thread cleanly.
        camera?.release()
        camera = null
        surfaceTexture = null
        _uiState.value = CameraUiState(
            status = CameraStatus.NoCamera,
            message = "Camera disconnected. Reconnect the USB camera to continue.",
        )
    }

    /**
     * Called when the app starts or when USB device state changes.
     * Guards against re-initialization — if camera is already ready/previewing, skips.
     */
    fun detectAndInitialize(activityContext: Context) {
        val currentStatus = _uiState.value.status
        if (currentStatus == CameraStatus.Ready ||
            currentStatus == CameraStatus.Previewing ||
            currentStatus == CameraStatus.Initializing) {
            Log.d(tag, "Skipping detectAndInitialize — status is $currentStatus")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = CameraStatus.Detecting)

            val result = cameraManager.detectBackend()
            when (result) {
                is CameraManager.BackendResult.Camera2 -> {
                    Log.d(tag, "Camera2 backend available: ${result.cameraId}")
                    initializeCamera2(result.cameraId)
                }

                is CameraManager.BackendResult.UVCCamera -> {
                    Log.d(tag, "UVC backend available: ${result.device.deviceName}")
                    initializeUVC(result.device, activityContext)
                }

                CameraManager.BackendResult.None -> {
                    Log.d(tag, "No USB camera detected")
                    _uiState.value = _uiState.value.copy(
                        status = CameraStatus.NoCamera,
                        message = "No USB camera detected. Please connect a UVC camera."
                    )
                }
            }
        }
    }

    private suspend fun initializeCamera2(cameraId: String) {
        try {
            _uiState.value = _uiState.value.copy(status = CameraStatus.Initializing)
            camera = cameraManager.initializeCamera2(cameraId)
            val cam = camera!!
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Ready,
                backendType = CameraInterface.BackendType.CAMERA2,
                resolution = cam.resolution,
                message = "Camera2 backend initialized"
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Camera2 backend", e)
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Error(e.message ?: "Unknown error"),
                message = "Failed to initialize camera: ${e.message}"
            )
        }
    }

    /**
     * Initialize UVC backend asynchronously, polling for the backend to become ready.
     * The backend handles the USB permission dialog itself; we just wait for it.
     */
    private suspend fun initializeUVC(device: UsbDevice, activityContext: Context) {
        try {
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Initializing,
                message = "Requesting USB permission..."
            )

            camera = cameraManager.initializeUVC(device, activityContext)

            var attempts = 0
            val maxAttempts = 30 // 3 seconds
            while (attempts < maxAttempts) {
                delay(100)
                attempts++

                when (val state = camera?.state) {
                    is CameraInterface.State.Ready -> {
                        val cam = camera!!
                        _uiState.value = _uiState.value.copy(
                            status = CameraStatus.Ready,
                            backendType = CameraInterface.BackendType.UVCCAMERA,
                            // Expose the actual resolution so PreviewView can constrain its aspect ratio
                            resolution = cam.resolution,
                            message = "UVC camera ready"
                        )
                        Log.d(tag, "UVC backend ready after $attempts attempts, resolution=${cam.resolution}")
                        return
                    }

                    is CameraInterface.State.Error -> {
                        _uiState.value = _uiState.value.copy(
                            status = CameraStatus.Error(state.message),
                            message = state.message
                        )
                        return
                    }

                    else -> {
                        if (attempts % 10 == 0) {
                            _uiState.value = _uiState.value.copy(
                                message = "Waiting for USB permission..."
                            )
                        }
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Error("Timeout waiting for USB camera"),
                message = "Permission dialog may have been dismissed"
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize UVC backend", e)
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Error(e.message ?: "Unknown error"),
                message = "Failed to initialize camera: ${e.message}"
            )
        }
    }

    fun setPreviewSurface(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        startPreview()
    }

    fun clearPreviewSurface() {
        this.surfaceTexture = null
    }

    private fun startPreview() {
        val cam = camera ?: run {
            Log.w(tag, "No camera initialized")
            return
        }
        val st = surfaceTexture ?: run {
            Log.w(tag, "No surface texture available")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(status = CameraStatus.Starting)
                cam.startPreview(st)
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Previewing,
                    // Keep resolution in state so PreviewView keeps correct aspect ratio
                    resolution = cam.resolution,
                    message = null
                )
                Log.d(tag, "Preview started at ${cam.resolution}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start preview", e)
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Error(e.message ?: "Unknown error"),
                    message = "Failed to start preview: ${e.message}"
                )
            }
        }
    }

    fun stopPreview() {
        val cam = camera ?: return
        viewModelScope.launch {
            try {
                cam.stopPreview()
                _uiState.value = _uiState.value.copy(status = CameraStatus.Ready, message = null)
            } catch (e: Exception) {
                Log.e(tag, "Failed to stop preview", e)
            }
        }
    }

    fun capturePhoto(path: String) {
        val cam = camera ?: return
        viewModelScope.launch { cam.capturePhoto(path) }
    }

    fun startRecording(path: String) {
        val cam = camera ?: return
        viewModelScope.launch { cam.startRecording(path) }
    }

    fun stopRecording() {
        val cam = camera ?: return
        viewModelScope.launch { cam.stopRecording() }
    }
}

data class CameraUiState(
    val status: CameraStatus = CameraStatus.Idle,
    val backendType: CameraInterface.BackendType? = null,
    val message: String? = null,
    // Actual camera resolution — used by PreviewView to maintain correct aspect ratio.
    // Null until the camera is Ready (resolution is unknown before then).
    val resolution: com.toyrobotworkshop.auspex.camera.Size? = null,
)

sealed interface CameraStatus {
    object Idle : CameraStatus
    object Detecting : CameraStatus
    object Initializing : CameraStatus
    object Ready : CameraStatus
    object Starting : CameraStatus
    object Previewing : CameraStatus
    object NoCamera : CameraStatus
    data class Error(val message: String) : CameraStatus
}
