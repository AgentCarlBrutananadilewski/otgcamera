package com.toyrobotworkshop.auspex.ui.main

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.toyrobotworkshop.auspex.camera.Size

/**
 * Camera preview surface backed by a TextureView.
 *
 * Accepts [cameraResolution] so it can apply a corrective transform to the TextureView
 * that keeps the preview letterboxed/pillarboxed at the camera's native aspect ratio,
 * rather than stretching to fill the available space.
 *
 * The transform works in two steps:
 *  1. Scale the texture down uniformly so its longer dimension fits the view.
 *  2. Translate it to centre it within the view.
 *
 * This is done in [onSurfaceTextureSizeChanged] (view resized) and whenever
 * [cameraResolution] changes (new camera connected with different resolution).
 * Both cases cause [updateTransform] to run.
 */
@Composable
fun PreviewView(
    modifier: Modifier = Modifier,
    cameraResolution: Size?,
    onSurfaceReady: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Hold a reference to the TextureView so we can update its transform when
    // cameraResolution arrives (which happens after the view is already laid out).
    val textureViewRef = remember { androidx.compose.runtime.mutableStateOf<TextureView?>(null) }

    // Re-apply the transform whenever the known resolution changes
    androidx.compose.runtime.LaunchedEffect(cameraResolution) {
        textureViewRef.value?.let { tv ->
            if (tv.width > 0 && tv.height > 0) {
                updateTransform(tv, tv.width, tv.height, cameraResolution)
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
                        updateTransform(this@apply, width, height, cameraResolution)
                        onSurfaceReady(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        // View was resized (orientation change, window resize) — recompute transform
                        updateTransform(this@apply, width, height, cameraResolution)
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
 * The TextureView draws its SurfaceTexture by mapping the texture rectangle to the
 * view rectangle, which stretches it when the aspect ratios differ. We correct this
 * by applying an inverse scale + centering translation via [TextureView.setTransform].
 *
 * If [cameraResolution] is null (camera not yet ready), the identity matrix is applied
 * so the view renders normally until the resolution is known.
 */
private fun updateTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    cameraResolution: Size?,
) {
    if (cameraResolution == null || viewWidth == 0 || viewHeight == 0) {
        textureView.setTransform(Matrix())
        return
    }

    val camW = cameraResolution.width.toFloat()
    val camH = cameraResolution.height.toFloat()

    // Scale factors to fit the camera frame inside the view
    val scaleX = camW / viewWidth
    val scaleY = camH / viewHeight

    // Use the larger scale factor so the frame fits entirely within the view (letterbox/pillarbox)
    val scale = maxOf(scaleX, scaleY)

    // Scaled dimensions of the camera frame in view-space
    val scaledW = camW / scale
    val scaledH = camH / scale

    // TextureView stretches its SurfaceTexture to fill the view by default.
    // setTransform() applies a matrix in view-pixel space to undo that stretch.
    // We scale each axis independently so the camera frame fits inside the view,
    // using the view centre as the pivot so the result is automatically centred
    // (no extra translation needed — the pivot handles it exactly).
    val matrix = Matrix()
    matrix.setScale(
        scaledW / viewWidth,   // x scale: fraction of view width the frame should occupy
        scaledH / viewHeight,  // y scale: fraction of view height the frame should occupy
        viewWidth  / 2f,       // pivot x: view centre
        viewHeight / 2f,       // pivot y: view centre
    )
    textureView.setTransform(matrix)
}
