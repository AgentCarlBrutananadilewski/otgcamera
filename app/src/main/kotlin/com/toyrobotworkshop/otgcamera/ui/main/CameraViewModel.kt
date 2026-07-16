package com.toyrobotworkshop.otgcamera.ui.main

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toyrobotworkshop.otgcamera.camera.CameraInterface
import com.toyrobotworkshop.otgcamera.camera.CameraManager
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

    // SurfaceTexture from PreviewView — used to create Surface for preview
    private var surfaceTexture: SurfaceTexture? = null

    /**
     * Called when the app starts or when USB device state changes.
     * Guards against re-initialization — if camera is already ready/previewing, skips.
     */
    fun detectAndInitialize(activityContext: android.content.Context) {
        // Don't re-initialize if already working
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
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Ready,
                backendType = CameraInterface.BackendType.CAMERA2,
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
     * Initialize UVC backend — always proceeds, letting the backend handle
     * permission requests asynchronously. Polls for state changes.
     *
     * @param activityContext Activity context required for USB permission dialog.
     */
    private suspend fun initializeUVC(device: UsbDevice, activityContext: android.content.Context) {
        try {
            _uiState.value = _uiState.value.copy(
                status = CameraStatus.Initializing,
                message = "Requesting USB permission..."
            )

            // Pass Activity context so the USB permission dialog can be shown
            camera = cameraManager.initializeUVC(device, activityContext)

            // Poll for the backend to transition to Ready (permission granted + camera opened)
            var attempts = 0
            val maxAttempts = 30 // 3 seconds total
            while (attempts < maxAttempts) {
                delay(100)
                attempts++

                when (val state = camera?.state) {
                    is CameraInterface.State.Ready -> {
                        _uiState.value = _uiState.value.copy(
                            status = CameraStatus.Ready,
                            backendType = CameraInterface.BackendType.UVCCAMERA,
                            message = "UVC camera ready"
                        )
                        Log.d(tag, "UVC backend ready after $attempts attempts")
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
                        // Still initializing — keep polling
                        if (attempts % 10 == 0) {
                            _uiState.value = _uiState.value.copy(
                                message = "Waiting for USB permission..."
                            )
                        }
                    }
                }
            }

            // Timeout
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
                // Pass SurfaceTexture directly — UVC backend uses setPreviewTexture(),
                // Camera2 backend wraps it in a Surface internally.
                cam.startPreview(st)
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Previewing,
                    message = null
                )
                Log.d(tag, "Preview started")
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
                _uiState.value = _uiState.value.copy(
                    status = CameraStatus.Ready,
                    message = null
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to stop preview", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        camera?.release()
        camera = null
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
    val resolution: com.toyrobotworkshop.otgcamera.camera.Size? = null,
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
