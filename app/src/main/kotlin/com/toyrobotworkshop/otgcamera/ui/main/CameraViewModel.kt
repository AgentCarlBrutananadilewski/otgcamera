package com.toyrobotworkshop.otgcamera.ui.main

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toyrobotworkshop.otgcamera.camera.CameraInterface
import com.toyrobotworkshop.otgcamera.camera.CameraManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the camera preview screen.
 *
 * Manages camera lifecycle: detection, initialization, preview start/stop,
 * and state changes. Uses CameraManager to determine which backend is available.
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

    // SurfaceTexture from PreviewView — used to create Surface for preview
    private var surfaceTexture: SurfaceTexture? = null

    /**
     * Called when the app starts or when USB device state changes.
     * Detects available backends and initializes the appropriate one.
     */
    fun detectAndInitialize() {
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
                    initializeUVC(result.device)
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

    /**
     * Initialize the Camera2 backend with a specific camera ID.
     */
    private suspend fun initializeCamera2(cameraId: String) {
        try {
            _uiState.value = _uiState.value.copy(status = CameraStatus.Initializing)
            camera = cameraManager.initializeCamera2(cameraId)
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Ready,
                backendType = CameraInterface.BackendType.CAMERA2,
                message = "Camera2 backend initialized"
            )
            Log.d(tag, "Camera2 backend initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Camera2 backend", e)
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Error,
                message = "Failed to initialize camera: ${e.message}"
            )
        }
    }

    /**
     * Initialize the UVC backend with a specific USB device.
     */
    private suspend fun initializeUVC(device: UsbDevice) {
        try {
            _uiState.value = _uiState.value.copy(status = CameraStatus.Initializing)

            // Check if we already have permission
            if (!cameraManager.hasUsbPermission(device)) {
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.PermissionRequired,
                    message = "USB permission required for ${device.deviceName}"
                )
                return
            }

            camera = cameraManager.initializeUVC(device)
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Ready,
                backendType = CameraInterface.BackendType.UVC,
                message = "UVC backend initialized"
            )
            Log.d(tag, "UVC backend initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize UVC backend", e)
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Error,
                message = "Failed to initialize camera: ${e.message}"
            )
        }
    }

    /**
     * Set the SurfaceTexture from PreviewView. This is called when the TextureView
     * becomes available and creates a new SurfaceTexture on surfaceCreated().
     */
    fun setSurfaceTexture(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
    }

    /**
     * Start the camera preview using the current SurfaceTexture.
     */
    fun startPreview() {
        val camera = camera ?: run {
            Log.w(tag, "No camera initialized")
            return
        }

        val surfaceTexture = surfaceTexture ?: run {
            Log.w(tag, "No surface texture available")
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Error,
                message = "Preview surface not ready"
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(status = CameraStatus.Starting)

                // Create a Surface from the SurfaceTexture
                val surface = Surface(surfaceTexture)
                camera.startPreview(surface)

                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Previewing,
                    message = null
                )
                Log.d(tag, "Preview started")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start preview", e)
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Error,
                    message = "Failed to start preview: ${e.message}"
                )
            }
        }
    }

    /**
     * Stop the camera preview.
     */
    fun stopPreview() {
        val camera = camera ?: return

        viewModelScope.launch {
            try {
                camera.stopPreview()
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Ready,
                    message = null
                )
                Log.d(tag, "Preview stopped")
            } catch (e: Exception) {
                Log.e(tag, "Failed to stop preview", e)
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Error,
                    message = "Failed to stop preview: ${e.message}"
                )
            }
        }
    }

    /**
     * Release camera resources. Called when the ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        camera?.release()
        camera = null
        Log.d(tag, "Camera released")
    }
}

/**
 * Camera UI state exposed to Compose.
 */
data class CameraUiState(
    val status: CameraStatus = CameraStatus.Idle,
    val backendType: CameraInterface.BackendType? = null,
    val message: String? = null,
    val resolution: com.toyrobotworkshop.otgcamera.camera.Size? = null,
)

/**
 * Camera lifecycle states.
 */
sealed interface CameraStatus {
    object Idle : CameraStatus
    object Detecting : CameraStatus
    object Initializing : CameraStatus
    object Ready : CameraStatus
    object Starting : CameraStatus
    object Previewing : CameraStatus
    object NoCamera : CameraStatus
    object PermissionRequired : CameraStatus
    data class Error(val message: String) : CameraStatus
}
