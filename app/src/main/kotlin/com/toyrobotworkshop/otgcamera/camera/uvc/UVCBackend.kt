package com.toyrobotworkshop.otgcamera.camera.uvc

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.*
import com.toyrobotworkshop.otgcamera.camera.CameraControls
import com.toyrobotworkshop.otgcamera.camera.CameraInterface
import com.toyrobotworkshop.otgcamera.camera.FocusMode
import com.toyrobotworkshop.otgcamera.camera.Size
import com.toyrobotworkshop.otgcamera.camera.WhiteBalanceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UVCCamera backend for USB UVC cameras.
 *
 * Uses alexey-pelykh's UVCCamera library (fork of saki4510t/UVCCamera) which provides
 * direct USB access to UVC devices without requiring OEM Camera2 external camera support.
 *
 * This is the fallback path for most Android devices — it works on any device that
 * supports USB host mode, regardless of whether the OEM exposes external cameras via Camera2.
 */
@Singleton
class UVCBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraInterface {

    private val tag = "UVCBackend"

    // UVCCamera library objects
    private var usbMonitor: USBMonitor? = null
    private var uvccamera: com.serenegiant.usb.UVCCamera? = null

    // State
    private var _state: CameraInterface.State = CameraInterface.State.Idle
    override val state: CameraInterface.State
        get() = _state

    override var resolution: Size = Size(640, 480)
        set(value) {
            field = value
        }

    private val _supportedResolutions = mutableListOf<Size>()
    override val supportedResolutions: List<Size>
        get() = _supportedResolutions.toList()

    override var controls: CameraControls = CameraControls()
    override val isCapturingPhoto: Boolean
        get() = _state is CameraInterface.State.Capturing
    override val isRecording: Boolean
        get() = _state is CameraInterface.State.Recording

    // USB device we're connected to
    private var connectedDevice: UsbDevice? = null

    /**
     * Initialize the UVC backend with a specific USB device.
     */
    fun initialize(device: UsbDevice) {
        connectedDevice = device
        _state = CameraInterface.State.Initializing

        // Create USBMonitor for permission handling and device management
        usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice): Boolean {
                Log.d(tag, "UVC device attached: ${device.deviceName}")
                return false // We'll handle connection manually
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: USBMonitor.UsbControlBlock
            ): Boolean {
                Log.d(tag, "UVC device connected: ${device.deviceName}")
                if (connectedDevice == device) {
                    openCamera(ctrlBlock)
                    return true
                }
                return false
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
                Log.d(tag, "UVC device disconnected: ${device.deviceName}")
                ctrlBlock.release()
                uvccamera?.release()
                uvccamera = null
                connectedDevice = null
                _state = CameraInterface.State.Idle
            }

            override fun onDetach(device: UsbDevice) {
                Log.d(tag, "UVC device detached: ${device.deviceName}")
            }
        })

        // Request permission for the device
        usbMonitor?.requestPermission(device)

        _state = CameraInterface.State.Ready(CameraInterface.BackendType.UVC)
    }

    /**
     * Open the camera with the given UsbControlBlock.
     */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        try {
            uvccamera = com.serenegiant.usb.UVCCamera(ctrlBlock).apply {
                // Discover supported preview sizes
                val displaySizes = getSupportedPreviewSizes()
                _supportedResolutions.clear()
                displaySizes.forEach { size ->
                    _supportedResolutions.add(Size(size.width, size.height))
                }

                if (_supportedResolutions.isNotEmpty()) {
                    resolution = _supportedResolutions.maxByOrNull { it.width * it.height }
                        ?: Size(640, 480)
                }

                Log.d(tag, "UVC camera opened, ${_supportedResolutions.size} resolutions available")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to open UVC camera", e)
            _state = CameraInterface.State.Error("Failed to open UVC camera: ${e.message}")
            return
        }

        _state = CameraInterface.State.Ready(CameraInterface.BackendType.UVC)
    }

    override suspend fun startPreview(surface: Surface) {
        val camera = uvccamera ?: throw IllegalStateException("UVC camera not opened")

        try {
            // Set preview size
            val uvcSize = com.serenegiant.usb.Size(resolution.width, resolution.height)
            camera.setPreviewSize(uvcSize.width, uvcSize.height)

            // Set the preview surface (SurfaceTexture from TextureView)
            camera.setPreviewDisplay(surface)

            // Start preview — format YUV420_888 for compatibility
            camera.startPreview { _, _, _, _ ->
                // Frame callback — used for recording
                // TODO: pipe frames to MediaCodec encoder when recording
            }

            _state = CameraInterface.State.Previewing
            Log.d(tag, "UVC preview started at ${resolution.width}x${resolution.height}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start UVC preview", e)
            _state = CameraInterface.State.Error("Failed to start preview: ${e.message}")
        }
    }

    override suspend fun stopPreview() {
        uvccamera?.stopPreview()
        _state = CameraInterface.State.Ready(CameraInterface.BackendType.UVC)
    }

    override suspend fun setPreviewSurface(surface: Surface?) {
        val camera = uvccamera ?: return

        if (surface == null) {
            stopPreview()
            return
        }

        try {
            // If preview is running, we need to stop and restart with new surface
            val wasPreviewing = _state is CameraInterface.State.Previewing

            camera.setPreviewDisplay(surface)

            if (wasPreviewing) {
                camera.startPreview { _, _, _, _ ->
                    // Frame callback for recording
                }
            }

            Log.d(tag, "Preview surface updated")
        } catch (e: Exception) {
            Log.e(tag, "Failed to set preview surface", e)
        }
    }

    override suspend fun capturePhoto(path: String): Result<Unit> = runCatching {
        Log.d(tag, "Capturing photo to $path")
        _state = CameraInterface.State.Capturing

        // Capture a single frame via the IFrameCallback
        // TODO: Use UVCCamera's snapshot capability or grab a frame from the callback,
        // then encode to JPEG using ImageDecoder/Bitmap and save to path.

        _state = CameraInterface.State.Previewing
    }

    override suspend fun startRecording(path: String): Result<Unit> = runCatching {
        Log.d(tag, "Starting recording to $path")
        _state = CameraInterface.State.Recording

        // TODO: Create MediaCodec H.264 encoder and pipe YUV frames from IFrameCallback
        // into the encoder input surface/buffer. Write output to MediaMuxer at path.

        _state = CameraInterface.State.Previewing
    }

    override suspend fun stopRecording(): Result<Unit> = runCatching {
        Log.d(tag, "Stopping recording")
        // TODO: Stop MediaCodec encoder, release MediaMuxer, finalize MP4 file
        _state = CameraInterface.State.Previewing
    }

    override suspend fun applyControls() {
        val camera = uvccamera ?: return

        with(controls) {
            // UVC camera controls via device-specific extension units
            // The UVCCamera library exposes these through setParameter methods

            exposureTimeNs?.let { exposure ->
                // Map nanoseconds to UVC exposure value
                try {
                    camera.setExposure(exposure.toInt())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set exposure", e)
                }
            }

            gain?.let { gainValue ->
                try {
                    camera.setGain(gainValue.toInt())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set gain", e)
                }
            }

            focusMode?.let { mode ->
                when (mode) {
                    FocusMode.AUTO -> {
                        try { camera.setAutoFocus(true) } catch (e: Exception) {
                            Log.w(tag, "Failed to set auto focus", e)
                        }
                    }
                    FocusMode.FIXED, FocusMode.MANUAL -> {
                        try { camera.setAutoFocus(false) } catch (e: Exception) {
                            Log.w(tag, "Failed to disable auto focus", e)
                        }
                    }
                    else -> {}
                }
            }

            focusDistance?.let { distance ->
                try {
                    camera.setFocus(distance.toInt())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set focus distance", e)
                }
            }

            whiteBalanceMode?.let { wbMode ->
                when (wbMode) {
                    WhiteBalanceMode.AUTO -> {
                        try { camera.setAutoWhiteBalance(true) } catch (e: Exception) {
                            Log.w(tag, "Failed to set auto WB", e)
                        }
                    }
                    WhiteBalanceMode.MANUAL -> {
                        try { camera.setAutoWhiteBalance(false) } catch (e: Exception) {
                            Log.w(tag, "Failed to disable auto WB", e)
                        }
                    }
                    // For preset WB modes, we'd need to set manual temperature
                    // which varies by device — skip for now
                    else -> {}
                }
            }

            whiteBalanceTemp?.let { temp ->
                try {
                    camera.setWhiteBalance(temp.toInt())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set WB temperature", e)
                }
            }
        }

        Log.d(tag, "Controls applied to UVC camera")
    }

    override fun release() {
        uvccamera?.run {
            stopPreview()
            release()
        }
        uvccamera = null
        usbMonitor?.run {
            unregister()
            destroy()
        }
        usbMonitor = null
        connectedDevice = null
        _state = CameraInterface.State.Idle
        Log.d(tag, "UVC backend released")
    }
}
