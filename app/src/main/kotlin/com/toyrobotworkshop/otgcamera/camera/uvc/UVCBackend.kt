package com.toyrobotworkshop.otgcamera.camera.uvc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.serenegiant.usb.USBMonitor
import com.toyrobotworkshop.otgcamera.camera.CameraControls
import com.toyrobotworkshop.otgcamera.camera.CameraInterface
import com.toyrobotworkshop.otgcamera.camera.FocusMode
import com.toyrobotworkshop.otgcamera.camera.Size
import com.toyrobotworkshop.otgcamera.camera.WhiteBalanceMode
import com.toyrobotworkshop.otgcamera.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UVCCamera backend for USB UVC cameras.
 *
 * Uses the serenegiant UVCCamera library (org.uvccamera:lib) which provides
 * direct USB access to UVC devices without requiring OEM Camera2 external camera support.
 */
@Singleton
class UVCBackend @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : CameraInterface {

    private val tag = "UVCBackend"
    private val USB_PERMISSION_ACTION = "android.hardware.usb.action.USB_PERMISSION"

    // UVCCamera library objects
    private var usbMonitor: USBMonitor? = null
    private var uvccamera: com.serenegiant.usb.UVCCamera? = null
    private var ctrlBlock: USBMonitor.UsbControlBlock? = null

    // UVCCamera requires all operations on the same thread
    private var cameraHandlerThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

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
        get() = false // TODO: track capture state
    override val isRecording: Boolean
        get() = _state is CameraInterface.State.Recording

    // USB device we're connected to
    private var connectedDevice: UsbDevice? = null

    // Permission receiver — handles the result of our manual permission request
    private var permissionReceiver: BroadcastReceiver? = null
    private var permissionActivityContext: Context? = null

    /**
     * Initialize the UVC backend with a specific USB device.
     * This handles permission requests asynchronously — the state will transition
     * to Ready only after the user grants permission and onConnect fires.
     *
     * CRITICAL: Must be called on the main thread — USB permission dialogs require it.
     *
     * @param activityContext Activity context required for showing the USB permission dialog.
     *                        If null, falls back to Application context (dialog won't show).
     */
    fun initialize(device: UsbDevice, activityContext: android.content.Context? = null) {
        DiagnosticLogger.clear()
        DiagnosticLogger.init("initialize() called for device ${device.deviceName}")
        DiagnosticLogger.perm("Activity context provided: ${activityContext != null}")
        connectedDevice = device
        _state = CameraInterface.State.Initializing

        Log.d(tag, "Initializing UVC backend for ${device.deviceName}, activityContext=${activityContext != null}")

        // Use Activity context if provided — required for USB permission dialog
        val monitorContext = activityContext ?: appContext

        // Create USBMonitor for device management (but we handle permission ourselves)
        usbMonitor = USBMonitor(monitorContext, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                Log.d(tag, "UVC device attached: ${device.deviceName}")
            }

            override fun onDettach(device: UsbDevice) {
                Log.d(tag, "UVC device detached: ${device.deviceName}")
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: USBMonitor.UsbControlBlock,
                createNew: Boolean
            ) {
                Log.d(tag, "UVC onConnect: ${device.deviceName}, createNew=$createNew")
                if (connectedDevice == device) {
                    openCamera(ctrlBlock)
                }
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
                Log.d(tag, "UVC device disconnected: ${device.deviceName}")
                ctrlBlock.close()
                uvccamera?.destroy()
                uvccamera = null
                this@UVCBackend.ctrlBlock = null
                connectedDevice = null
                _state = CameraInterface.State.Idle
            }

            override fun onCancel(device: UsbDevice) {
                // This fires when USBMonitor's own permission request fails.
                // We handle permission ourselves, so ignore unless it's our device.
                if (device == connectedDevice && !usbPermissionRequested) {
                    Log.d(tag, "UVC onCancel from USBMonitor (permission not yet requested by us): ${device.deviceName}")
                    DiagnosticLogger.perm("USBMonitor.onCancel fired — we haven't requested permission yet, treating as error")
                    _state = CameraInterface.State.Error("USB permission denied for camera")
                } else {
                    Log.d(tag, "UVC onCancel (ignored — we handle permission ourselves): ${device.deviceName}")
                    DiagnosticLogger.perm("USBMonitor.onCancel fired — ignored (usbPermissionRequested=$usbPermissionRequested)")
                }
            }
        })

        // Check if we already have permission — if so, register and open directly
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val hasPerm = usbManager.hasPermission(device)
        DiagnosticLogger.perm("hasPermission(${device.deviceName}) = $hasPerm")
        if (hasPerm) {
            Log.d(tag, "Already have USB permission, registering monitor and opening directly")
            DiagnosticLogger.perm("Already have permission — registering and opening")
            usbMonitor?.register()
            val cb = usbMonitor?.openDevice(device)
            if (cb != null) {
                openCamera(cb)
            } else {
                _state = CameraInterface.State.Error("Failed to open USB device")
            }
        } else {
            // Request permission FIRST — before registering USBMonitor.
            // USBMonitor.register() internally iterates connected devices and calls its own
            // requestPermission() which creates a PendingIntent without FLAG_MUTABLE, crashing
            // silently on Android 12+. That causes onCancel to fire immediately, and Android
            // records the denial so our subsequent request never shows a dialog.
            // By requesting permission first with proper flags, we get the dialog shown before
            // USBMonitor can interfere.
            Log.d(tag, "Requesting USB permission for ${device.deviceName} BEFORE register()")
            DiagnosticLogger.perm("No permission — requesting BEFORE register() to avoid USBMonitor bug")
            usbPermissionRequested = true
            requestUsbPermission(device, activityContext)
        }
    }

    /** Tracks whether we've already requested permission ourselves (to ignore USBMonitor's onCancel). */
    private var usbPermissionRequested = false

    /**
     * Request USB permission using a properly-constructed PendingIntent.
     * USBMonitor.requestPermission() fails on Android 12+ because it doesn't set
     * FLAG_MUTABLE/FLAG_IMMUTABLE on the PendingIntent.
     */
    private fun requestUsbPermission(device: UsbDevice, activityContext: Context?) {
        val context = activityContext ?: appContext
        permissionActivityContext = context

        // Log detailed context info for debugging
        val isActivity = context is android.app.Activity
        Log.d(tag, "requestUsbPermission: context=${context::class.simpleName}, isActivity=$isActivity, packageName=${context.packageName}")
        DiagnosticLogger.perm("requestUsbPermission: context=${context::class.simpleName}, isActivity=$isActivity")
        Log.d(tag, "Device: name=${device.deviceName}, vid=${Integer.toHexString(device.vendorId)}, pid=${Integer.toHexString(device.productId)}")
        DiagnosticLogger.usb("Device: ${device.deviceName} VID=${Integer.toHexString(device.vendorId)} PID=${Integer.toHexString(device.productId)}")

        // Create PendingIntent with proper flags for Android 12+
        val action = "${context.packageName}.USB_PERMISSION"
        val intent = Intent(action).apply {
            `package` = context.packageName
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        Log.d(tag, "Creating PendingIntent with flags=0x${Integer.toHexString(flags)}")
        DiagnosticLogger.perm("PendingIntent flags=0x${Integer.toHexString(flags)} (SDK ${android.os.Build.VERSION.SDK_INT})")
        val permissionIntent = try {
            PendingIntent.getBroadcast(context, 0, intent, flags).also {
                Log.d(tag, "PendingIntent created successfully")
                DiagnosticLogger.perm("PendingIntent created OK")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to create PendingIntent", e)
            DiagnosticLogger.error("PendingIntent creation failed: ${e.message}")
            _state = CameraInterface.State.Error("Failed to create permission intent: ${e.message}")
            return
        }

        // Register receiver for the permission result
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (USB_PERMISSION_ACTION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    Log.d(tag, "USB permission result: granted=$granted, device=${dev?.deviceName}")
                    DiagnosticLogger.perm("Receiver fired: granted=$granted, device=${dev?.deviceName}")

                    // Unregister receiver
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (_: Exception) {
                        // Already unregistered
                    }
                    permissionReceiver = null

                    if (granted && dev == connectedDevice) {
                        // Permission granted — register monitor then open the camera
                        Log.d(tag, "Permission granted — registering USBMonitor and opening device")
                        DiagnosticLogger.perm("GRANTED — registering USBMonitor and opening device")
                        usbMonitor?.register()
                        val cb = usbMonitor?.openDevice(dev)
                        if (cb != null) {
                            DiagnosticLogger.camera("openDevice succeeded, calling openCamera()")
                            openCamera(cb)
                        } else {
                            DiagnosticLogger.error("openDevice returned null after permission granted")
                            _state = CameraInterface.State.Error("Failed to open USB device after permission granted")
                        }
                    } else {
                        // Permission denied
                        DiagnosticLogger.perm("DENIED — user rejected or wrong device (dev=$dev, connected=$connectedDevice)")
                        _state = CameraInterface.State.Error("USB permission denied for camera")
                    }
                }
            }
        }

        // Register the receiver
        val filter = IntentFilter(USB_PERMISSION_ACTION)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(permissionReceiver, filter)
            }
            Log.d(tag, "BroadcastReceiver registered successfully")
            DiagnosticLogger.perm("BroadcastReceiver registered (action=$USB_PERMISSION_ACTION)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to register BroadcastReceiver", e)
            DiagnosticLogger.error("BroadcastReceiver registration failed: ${e.message}")
            _state = CameraInterface.State.Error("Failed to register permission receiver: ${e.message}")
            return
        }

        // Request the permission — this shows the system dialog
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        try {
            Log.d(tag, "Calling usbManager.requestPermission()...")
            DiagnosticLogger.perm("Calling usbManager.requestPermission() — dialog should appear now")
            usbManager.requestPermission(device, permissionIntent)
            Log.d(tag, "usbManager.requestPermission() returned — waiting for user response")
            DiagnosticLogger.perm("requestPermission() returned — waiting for BroadcastReceiver callback")
        } catch (e: Exception) {
            Log.e(tag, "Failed to request USB permission", e)
            DiagnosticLogger.error("requestPermission() threw: ${e.message}")
            _state = CameraInterface.State.Error("Failed to request USB permission: ${e.message}")
            // Clean up receiver
            try {
                context.unregisterReceiver(permissionReceiver)
            } catch (_: Exception) {}
            permissionReceiver = null
        }
    }

    /**
     * Open the camera with the given UsbControlBlock.
     * All UVCCamera operations MUST run on the same thread — we use a HandlerThread for this.
     */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        DiagnosticLogger.camera("openCamera() called — creating UVCCamera instance")

        // Create dedicated handler thread — UVCCamera requires all ops on the same thread
        cameraHandlerThread = HandlerThread("UVC-Camera").also { it.start() }
        cameraHandler = Handler(cameraHandlerThread!!.looper)

        DiagnosticLogger.camera("HandlerThread created: ${cameraHandlerThread?.name}")
        DiagnosticLogger.camera("Thread ID: ${Thread.currentThread().id} (${Thread.currentThread().name})")

        cameraHandler!!.post {
            val threadName = Thread.currentThread().name
            DiagnosticLogger.camera("openCamera on handler thread: $threadName (ID=${Thread.currentThread().id})")

            try {
                this@UVCBackend.ctrlBlock = ctrlBlock
                DiagnosticLogger.camera("ctrlBlock assigned, calling UVCCamera.open()")

                uvccamera = com.serenegiant.usb.UVCCamera().apply {
                    open(ctrlBlock)
                    DiagnosticLogger.camera("UVCCamera.open() succeeded")

                    // Discover supported preview sizes
                    val displaySizes = getSupportedSizeList()
                    DiagnosticLogger.camera("getSupportedSizeList() returned ${displaySizes.size} sizes")
                    if (displaySizes.isEmpty()) {
                        DiagnosticLogger.camera("WARNING: Camera reports ZERO supported sizes — this is suspicious")
                        DiagnosticLogger.camera("Camera device name: ${ctrlBlock.deviceName}")
                        connectedDevice?.let { dev ->
                            DiagnosticLogger.camera("Camera vendorId: ${Integer.toHexString(dev.vendorId)}")
                            DiagnosticLogger.camera("Camera productId: ${Integer.toHexString(dev.productId)}")
                        }
                    }

                    _supportedResolutions.clear()
                    displaySizes.forEach { size ->
                        _supportedResolutions.add(Size(size.width, size.height))
                    }

                    DiagnosticLogger.camera("Supported resolutions: ${_supportedResolutions}")

                    if (_supportedResolutions.isNotEmpty()) {
                        // Pick highest resolution that's reasonable (cap at 1080p)
                        val reasonable = _supportedResolutions.filter { it.width <= 1920 && it.height <= 1080 }
                        resolution = reasonable.maxByOrNull { it.width * it.height }
                            ?: Size(640, 480)
                    } else {
                        // Fallback — try common sizes
                        DiagnosticLogger.camera("No supported sizes found, falling back to 640x480")
                        resolution = Size(640, 480)
                    }

                    DiagnosticLogger.camera("Selected resolution: ${resolution.width}x${resolution.height}")
                    Log.d(tag, "UVC camera opened on $threadName, ${_supportedResolutions.size} resolutions available: ${_supportedResolutions}, selected: ${resolution.width}x${resolution.height}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to open UVC camera", e)
                _state = CameraInterface.State.Error("Failed to open UVC camera: ${e.message}")
                DiagnosticLogger.error("openCamera failed: ${e.javaClass.simpleName}: ${e.message}")
                return@post
            }

            _state = CameraInterface.State.Ready(CameraInterface.BackendType.UVCCAMERA)
            Log.d(tag, "UVC backend ready")
        }
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        DiagnosticLogger.camera("startPreview() called — state=$_state")
        val camera = uvccamera ?: throw IllegalStateException("UVC camera not opened — state=$_state")

        // All UVCCamera operations must run on the same handler thread as openCamera
        val handler = cameraHandler ?: throw IllegalStateException("Camera handler thread not initialized")

        DiagnosticLogger.camera("Posting startPreview to handler thread: ${cameraHandlerThread?.name}")

        // Use CountDownLatch to wait for the handler thread to finish (can't use suspend Channel in post {})
        val latch = CountDownLatch(1)
        var error: Throwable? = null

        handler.post {
            val threadName = Thread.currentThread().name
            DiagnosticLogger.camera("startPreview executing on handler thread: $threadName (ID=${Thread.currentThread().id})")

            try {
                // Step 1: Set preview size first
                DiagnosticLogger.camera("Step 1: setPreviewSize(${resolution.width}x${resolution.height})")
                camera.setPreviewSize(resolution.width, resolution.height)
                DiagnosticLogger.camera("Step 1 OK — setPreviewSize succeeded")

                // Step 2: Use setPreviewTexture() instead of setPreviewDisplay(Surface) —
                // this is the correct API for TextureView-backed previews with UVC cameras.
                DiagnosticLogger.camera("Step 2: setPreviewTexture(surfaceTexture)")
                camera.setPreviewTexture(surfaceTexture)
                DiagnosticLogger.camera("Step 2 OK — setPreviewTexture succeeded")

                // Step 3: Start preview
                DiagnosticLogger.camera("Step 3: startPreview() on UVCCamera")
                camera.startPreview()
                DiagnosticLogger.camera("Step 3 OK — startPreview succeeded")

                _state = CameraInterface.State.Previewing
                Log.d(tag, "UVC preview started at ${resolution.width}x${resolution.height}")
            } catch (e: Exception) {
                error = e
                Log.e(tag, "Failed to start UVC preview", e)
                _state = CameraInterface.State.Error("Failed to start preview: ${e.message}")
                DiagnosticLogger.error("startPreview failed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                latch.countDown()
            }
        }

        // Wait for the handler thread to finish (with timeout)
        val waited = latch.await(10, TimeUnit.SECONDS)
        if (!waited) {
            throw TimeoutException("startPreview timed out waiting for handler thread")
        }
        error?.let { throw it }
    }

    override suspend fun stopPreview() {
        uvccamera?.stopPreview()
        if (_state is CameraInterface.State.Previewing || _state is CameraInterface.State.Recording) {
            _state = CameraInterface.State.Ready(CameraInterface.BackendType.UVCCAMERA)
        }
    }

    override suspend fun capturePhoto(path: String): Result<Unit> = runCatching {
        Log.d(tag, "Capturing photo to $path")
        // TODO: Use IFrameCallback to grab a frame, encode to JPEG, save to path.
    }

    override suspend fun startRecording(path: String): Result<Unit> = runCatching {
        Log.d(tag, "Starting recording to $path")
        _state = CameraInterface.State.Recording
        // TODO: Create MediaCodec H.264 encoder and pipe YUV frames from IFrameCallback
    }

    override suspend fun stopRecording(): Result<Unit> = runCatching {
        Log.d(tag, "Stopping recording")
        _state = CameraInterface.State.Previewing
        // TODO: Stop MediaCodec encoder, release MediaMuxer, finalize MP4 file
    }

    override suspend fun applyControls() {
        val camera = uvccamera ?: return

        with(controls) {
            gain?.let { gainValue ->
                try {
                    camera.setGain((gainValue * 16).toInt())
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set gain", e)
                }
            }

            focusMode?.let { mode ->
                when (mode) {
                    FocusMode.AUTO, FocusMode.CONTINUOUS_PICTURE -> {
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

            whiteBalanceMode?.let { mode ->
                when (mode) {
                    WhiteBalanceMode.AUTO -> {
                        try { camera.setAutoWhiteBlance(true) } catch (e: Exception) {
                            Log.w(tag, "Failed to set auto WB", e)
                        }
                    }
                    WhiteBalanceMode.MANUAL -> {
                        try {
                            camera.setAutoWhiteBlance(false)
                            colorTemperatureK?.let { tempK ->
                                camera.setWhiteBlance(tempK.coerceIn(2000, 8000))
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to set manual WB", e)
                        }
                    }
                    else -> {
                        try { camera.setAutoWhiteBlance(true) } catch (e: Exception) {
                            Log.w(tag, "Failed to set WB mode", e)
                        }
                    }
                }
            }

            brightness?.let { b ->
                try { camera.setBrightness((b + 128).coerceIn(0, 255)) } catch (e: Exception) {
                    Log.w(tag, "Failed to set brightness", e)
                }
            }

            contrast?.let { c ->
                try { camera.setContrast((c + 128).coerceIn(0, 255)) } catch (e: Exception) {
                    Log.w(tag, "Failed to set contrast", e)
                }
            }

            saturation?.let { s ->
                try { camera.setSaturation(s.coerceIn(0, 255)) } catch (e: Exception) {
                    Log.w(tag, "Failed to set saturation", e)
                }
            }

            sharpness?.let { sh ->
                try { camera.setSharpness(sh.coerceIn(0, 255)) } catch (e: Exception) {
                    Log.w(tag, "Failed to set sharpness", e)
                }
            }
        }

        Log.d(tag, "Controls applied to UVC camera")
    }

    override fun release() {
        // Clean up permission receiver
        try {
            permissionActivityContext?.unregisterReceiver(permissionReceiver)
        } catch (_: Exception) {}
        permissionReceiver = null
        permissionActivityContext = null

        uvccamera?.run {
            stopPreview()
            destroy()
        }
        uvccamera = null
        ctrlBlock?.close()
        ctrlBlock = null
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
