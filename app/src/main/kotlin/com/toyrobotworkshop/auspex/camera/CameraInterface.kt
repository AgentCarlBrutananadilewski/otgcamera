package com.toyrobotworkshop.auspex.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import kotlin.math.roundToInt

/**
 * Unified interface abstracting over Camera2 and UVCCamera backends.
 * The UI layer only knows about this interface — it doesn't care which backend is active.
 */
interface CameraInterface {

    /** Current state of the camera. */
    val state: State

    /** Flow of camera state changes — use instead of polling [state]. */
    val stateFlow: kotlinx.coroutines.flow.StateFlow<State>

    /** Currently selected resolution. */
    var resolution: Size

    /** List of resolutions supported by the current device. */
    val supportedResolutions: List<Size>

    /** Whether a photo is currently being captured. */
    val isCapturingPhoto: Boolean

    /** Whether video recording is in progress. */
    val isRecording: Boolean

    /** Current camera controls state. */
    var controls: CameraControls

    /**
     * Start preview rendering to the given [SurfaceTexture].
     * Using SurfaceTexture directly is more reliable than wrapping in a Surface,
     * especially for UVC cameras where setPreviewTexture() is the preferred API.
     */
    suspend fun startPreview(surfaceTexture: SurfaceTexture)

    /** Stop the preview. */
    suspend fun stopPreview()

    /**
     * Capture a single photo and save it to [path].
     */
    suspend fun capturePhoto(path: String): Result<Unit>

    /**
     * Start video recording, encoding frames to [path].
     */
    suspend fun startRecording(path: String): Result<Unit>
    /**
     * Stop video recording and finalize the output file.
     */
    suspend fun stopRecording(): Result<Unit>

    /**
     * Apply the current [controls] to the camera device.
     */
    suspend fun applyControls()

    /**
     * Release all camera resources. Call when the camera is no longer needed.
     */
    fun release()

    /** Possible states of the camera interface. */
    sealed interface State {
        object Idle : State
        object Initializing : State
        data class Ready(val backend: BackendType) : State
        object Previewing : State
        object Recording : State
        data class Error(val message: String) : State
    }

    /** Which backend is active. */
    enum class BackendType {
        CAMERA2,
        UVCCAMERA
    }
}

/**
 * Resolution size for camera output.
 */
data class Size(
    val width: Int,
    val height: Int
) {
    override fun toString(): String = "${width}x${height}"
}

/**
 * Camera control parameters. Each property is nullable — null means "use device default / auto".
 */
data class CameraControls(
    /** Exposure time in nanoseconds. null = auto. */
    val exposureTimeNs: Long? = null,

    /** Gain multiplier (ISO). null = auto. Range typically 1.0–16.0. */
    val gain: Float? = null,

    /** White balance mode. null = auto. */
    val whiteBalanceMode: WhiteBalanceMode? = null,

    /** Manual color temperature in Kelvin (only used when whiteBalanceMode == MANUAL). */
    val colorTemperatureK: Int? = null,

    /** Focus mode. null = device default. */
    val focusMode: FocusMode? = null,

    /** Manual focus position 0–1000 (only used when focusMode == MANUAL). */
    val focusPosition: Int? = null,

    /** Brightness adjustment -128 to 127. null = default. */
    val brightness: Int? = null,

    /** Contrast adjustment -128 to 127. null = default. */
    val contrast: Int? = null,

    /** Saturation adjustment 0–255 (128 = normal). null = default. */
    val saturation: Int? = null,

    /** Sharpness adjustment 0–255 (128 = normal). null = default. */
    val sharpness: Int? = null,
)

/** White balance modes. */
enum class WhiteBalanceMode {
    AUTO,
    INCANDESCENT,
    FLUORESCENT,
    DAYLIGHT,
    CLOUDY_DAYLIGHT,
    TWILIGHT,
    SHADE,
    MANUAL,
}

/** Focus modes. */
enum class FocusMode {
    CONTINUOUS_PICTURE,
    AUTO,
    FIXED,
    MANUAL,
    MACRO,
    INFINITY,
    EDGE,
}
