package com.toyrobotworkshop.otgcamera.camera.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size as AndroidSize
import android.view.Surface
import com.toyrobotworkshop.otgcamera.camera.CameraControls
import com.toyrobotworkshop.otgcamera.camera.CameraInterface
import com.toyrobotworkshop.otgcamera.camera.FocusMode
import com.toyrobotworkshop.otgcamera.camera.Size
import com.toyrobotworkshop.otgcamera.camera.WhiteBalanceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera2 API backend for external USB cameras.
 *
 * This backend works on devices where the OEM has enabled external camera support
 * via the ExternalCameraProvider HAL. The camera appears as a standard Camera2 device
 * with LENS_FACING_EXTERNAL.
 */
@Singleton
class Camera2Backend @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraInterface {

    private val tag = "Camera2Backend"

    // Camera state
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var imageReader: ImageReader? = null

    // Preview surface — set by UI layer
    private var previewSurface: Surface? = null

    // Pending preview surface (set before camera is opened)
    private var pendingSurface: Surface? = null

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

    /**
     * Initialize the backend with a specific camera ID.
     */
    suspend fun initialize(cameraId: String) {
        this.cameraId = cameraId
        _state = CameraInterface.State.Initializing

        handlerThread = HandlerThread("Camera2").also { it.start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        // Discover supported resolutions
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map != null) {
            _supportedResolutions.clear()
            val sizes = map.getOutputSizes(ImageFormat.JPEG).toList()
            _supportedResolutions.addAll(sizes.map { Size(it.width, it.height) })
            // Pick the largest resolution by default
            if (_supportedResolutions.isNotEmpty()) {
                resolution = _supportedResolutions.maxByOrNull { it.width * it.height }
                    ?: Size(640, 480)
            }
        }

        Log.d(tag, "Initialized camera $cameraId with ${_supportedResolutions.size} resolutions")
        _state = CameraInterface.State.Ready(CameraInterface.BackendType.CAMERA2)
    }

    @SuppressLint("MissingPermission")
    override suspend fun startPreview(surface: Surface) {
        val cameraId = cameraId ?: throw IllegalStateException("Camera not initialized")

        // If camera isn't open yet, store the surface and open it
        if (cameraDevice == null) {
            pendingSurface = surface
            openCameraAndStartSession(cameraId)
            return
        }

        // Camera is already open — just create the session
        this.previewSurface = surface
        createCaptureSession(surface)
    }

    @SuppressLint("MissingPermission")
    private fun openCameraAndStartSession(cameraId: String) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Create ImageReader for photo capture
        imageReader = ImageReader.newInstance(
            resolution.width,
            resolution.height,
            ImageFormat.JPEG,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                // TODO: handle captured JPEG image
            }, backgroundHandler)
        }

        cameraManager.openCamera(cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    Log.d(tag, "Camera $cameraId opened")
                    cameraDevice = device
                    _state = CameraInterface.State.Previewing

                    // Now create the capture session with the pending surface
                    val surface = pendingSurface ?: previewSurface
                        ?: throw IllegalStateException("No preview surface available")
                    createCaptureSession(surface)
                }

                override fun onDisconnected(device: CameraDevice) {
                    Log.w(tag, "Camera $cameraId disconnected")
                    cameraDevice = null
                    captureSession?.close()
                    captureSession = null
                    _state = CameraInterface.State.Idle
                }

                override fun onError(device: CameraDevice, error: Int) {
                    Log.e(tag, "Camera error on $cameraId: $error")
                    cameraDevice = null
                    captureSession?.close()
                    captureSession = null
                    _state = CameraInterface.State.Error("Camera error: $error")
                }
            },
            backgroundHandler
        )
    }

    private fun createCaptureSession(surface: Surface) {
        val imageReaderSurface = imageReader?.surface
            ?: throw IllegalStateException("ImageReader not initialized")

        val surfaces = listOf(surface, imageReaderSurface)

        cameraDevice?.createCaptureSession(surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(tag, "Capture session configured")
                    captureSession = session
                    startPreviewRepeating(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(tag, "Session configuration failed")
                    _state = CameraInterface.State.Error("Failed to configure camera session")
                }
            },
            backgroundHandler
        )
    }

    private fun startPreviewRepeating(session: CameraCaptureSession) {
        val builder = cameraDevice?.createCaptureRequest(CameraCaptureSession.TEMPLATE_PREVIEW)
            ?: return

        builder.addTarget(previewSurface!!)
        builder.addTarget(imageReader!!.surface)

        // Apply current controls
        applyControlsToRequest(builder)

        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
            Log.d(tag, "Preview repeating started")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start preview", e)
            _state = CameraInterface.State.Error("Failed to start preview: ${e.message}")
        }
    }

    override suspend fun stopPreview() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        previewSurface = null
        pendingSurface = null
        _state = CameraInterface.State.Ready(CameraInterface.BackendType.CAMERA2)
    }

    override suspend fun setPreviewSurface(surface: Surface?) {
        if (surface == null) {
            stopPreview()
            return
        }

        if (cameraDevice != null && captureSession != null) {
            // Camera is already open — rebuild session with new surface
            previewSurface = surface
            createCaptureSession(surface)
        } else {
            // Store for later when camera opens
            pendingSurface = surface
        }
    }

    override suspend fun capturePhoto(path: String): Result<Unit> = runCatching {
        Log.d(tag, "Capturing photo to $path")
        _state = CameraInterface.State.Capturing

        // TODO: Create a one-shot capture request targeting ImageReader
        // For now, we'll use the existing ImageReader surface which is already
        // part of the repeating request. We need to switch to a burst capture
        // or a one-shot request with JPEG output.

        _state = CameraInterface.State.Previewing
    }

    override suspend fun startRecording(path: String): Result<Unit> = runCatching {
        Log.d(tag, "Starting recording to $path")
        _state = CameraInterface.State.Recording

        // TODO: Create MediaCodec encoder surface and rebuild capture session
        // with the encoder surface as target. Use YUV_420_888 format for preview
        // + encoder surface for recording.

        _state = CameraInterface.State.Previewing
    }

    override suspend fun stopRecording(): Result<Unit> = runCatching {
        Log.d(tag, "Stopping recording")
        // TODO: Stop MediaCodec encoder and finalize MP4 file
        _state = CameraInterface.State.Previewing
    }

    override suspend fun applyControls() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return

        val builder = device.createCaptureRequest(CameraCaptureSession.TEMPLATE_PREVIEW)
        builder.addTarget(previewSurface!!)
        builder.addTarget(imageReader!!.surface)
        applyControlsToRequest(builder)

        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
            Log.d(tag, "Controls applied")
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply controls", e)
        }
    }

    private fun applyControlsToRequest(builder: CaptureRequest.Builder) {
        with(controls) {
            exposureTimeNs?.let {
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
            }
            gain?.let {
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, (it * 100).toInt())
            }
            whiteBalanceMode?.let { mode ->
                val awbMode = when (mode) {
                    WhiteBalanceMode.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
                    WhiteBalanceMode.INCANDESCENT -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                    WhiteBalanceMode.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                    WhiteBalanceMode.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                    WhiteBalanceMode.CLOUDY_DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    WhiteBalanceMode.TWILIGHT -> CaptureRequest.CONTROL_AWB_MODE_TWILIGHT
                    WhiteBalanceMode.SHADE -> CaptureRequest.CONTROL_AWB_MODE_SHADE
                    WhiteBalanceMode.MANUAL -> CaptureRequest.CONTROL_AWB_MODE_OFF
                }
                builder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode)
            }
            focusMode?.let { mode ->
                val afMode = when (mode) {
                    FocusMode.CONTINUOUS_PICTURE -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    FocusMode.AUTO -> CaptureRequest.CONTROL_AF_MODE_AUTO
                    FocusMode.FIXED -> CaptureRequest.CONTROL_AF_MODE_OFF
                    FocusMode.MANUAL -> CaptureRequest.CONTROL_AF_MODE_OFF
                    else -> null
                }
                afMode?.let { builder.set(CaptureRequest.CONTROL_AF_MODE, it) }
            }
        }
    }

    override fun release() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        backgroundHandler?.looper?.quitSafely()
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        previewSurface = null
        pendingSurface = null
        _state = CameraInterface.State.Idle
    }
}
