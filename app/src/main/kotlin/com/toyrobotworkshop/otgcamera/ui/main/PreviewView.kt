package com.toyrobotworkshop.otgcamera.ui.main

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

/**
 * Camera preview surface backed by a TextureView.
 *
 * Exposes the [SurfaceTexture] so the camera backend can render frames into it.
 * Automatically pauses/resumes based on lifecycle events.
 */
@Composable
fun PreviewView(
    modifier: Modifier = Modifier,
    onSurfaceReady: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pause/resume camera on lifecycle changes
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
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        onSurfaceReady(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        // Size change — backend may need to adjust
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        onSurfaceDestroyed(surfaceTexture)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Frame arrived — no action needed
                    }
                }
            }
        },
        modifier = modifier,
    )
}
