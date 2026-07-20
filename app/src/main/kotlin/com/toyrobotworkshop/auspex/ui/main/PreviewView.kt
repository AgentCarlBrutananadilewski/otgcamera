package com.toyrobotworkshop.auspex.ui.main

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.toyrobotworkshop.auspex.camera.Size

/**
 * Camera preview surface backed by a TextureView.
 *
 * Accepts [cameraResolution] so it can apply a corrective transform to the TextureView
 * that keeps the preview letterboxed/pillarboxed at the camera's native aspect ratio,
 * rather than stretching to fill the available space.
 *
 * This is done in [onSurfaceTextureSizeChanged] (view resized) and whenever
 * [cameraResolution] or [deviceRotation] changes.
 * Both cases cause [updateTransform] to run.
 */
@Composable
fun PreviewView(
    modifier: Modifier = Modifier,
    cameraResolution: Size?,
    deviceRotation: Int = 0,
    onSurfaceReady: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Hold a reference to the TextureView so we can update its transform when
    // cameraResolution arrives (which happens after the view is already laid out).
    val textureViewRef = remember { androidx.compose.runtime.mutableStateOf<TextureView?>(null) }

    // Re-apply the transform whenever the known resolution or device rotation changes
    androidx.compose.runtime.LaunchedEffect(cameraResolution, deviceRotation) {
        textureViewRef.value?.let { tv ->
            if (tv.width > 0 && tv.height > 0) {
                updateTransform(tv, tv.width, tv.height, cameraResolution, deviceRotation)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // TODO: signal camera to resume preview
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // TODO: signal camera to pause preview
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                textureViewRef.value = this
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        updateTransform(this@apply, width, height, cameraResolution, deviceRotation)
                        onSurfaceReady(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        // View was resized (orientation change, window resize) — recompute transform
                        updateTransform(this@apply, width, height, cameraResolution, deviceRotation)
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        textureViewRef.value = null
                        onSurfaceDestroyed(surfaceTexture)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Frame arrived — no action needed
                    }
                }
            }
        },
        // Update the listener's closure when resolution changes so the next
        // onSurfaceTextureSizeChanged call uses the latest value.
        update = { tv ->
            textureViewRef.value = tv
        },
        modifier = modifier,
    )
}

/**
 * Compute and apply an aspect-ratio-preserving transform to [textureView].
 *
 * This version implements "Width-First" scaling:
 * - Portrait (0/180°): Scale is based on video width.
 * - Landscape (90/270°): Scale is based on video height.
 * This ensures the image always spans the full width of the device screen
 * without squashing or stretching.
 */
private fun updateTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    cameraResolution: Size?,
    deviceRotation: Int = 0,
) {
    if (cameraResolution == null || viewWidth == 0 || viewHeight == 0) {
        textureView.setTransform(Matrix())
        return
    }

    val matrix = Matrix()

    // 1. Physical dimensions of the camera buffer
    val camW = cameraResolution.width.toFloat()
    val camH = cameraResolution.height.toFloat()

    // 2. Determine which camera dimension becomes the visual "width"
    val isRotated = deviceRotation == 90 || deviceRotation == 270
    val visualContentWidth = if (isRotated) camH else camW

    // 3. View center
    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f

    // 4. Step-by-step Matrix assembly:
    
    // A. Undo the TextureView default stretch.
    // It maps buffer(camW, camH) -> view(viewWidth, viewHeight).
    // We scale by the inverse to get back to a 1:1 pixel representation.
    matrix.setScale(camW / viewWidth, camH / viewHeight, centerX, centerY)

    // B. Apply rotation to keep the image upright.
    if (deviceRotation != 0) {
        matrix.postRotate(deviceRotation.toFloat(), centerX, centerY)
    }

    // C. Apply uniform "Width-First" scale.
    // We want visualContentWidth to match viewWidth.
    val finalScale = viewWidth / visualContentWidth
    matrix.postScale(finalScale, finalScale, centerX, centerY)

    textureView.setTransform(matrix)
}
