package com.toyrobotworkshop.otgcamera.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.otgcamera.camera.CameraControls
import com.toyrobotworkshop.otgcamera.camera.CameraInterface
import com.toyrobotworkshop.otgcamera.camera.FocusMode
import com.toyrobotworkshop.otgcamera.camera.Size
import com.toyrobotworkshop.otgcamera.camera.WhiteBalanceMode

/**
 * Bottom sheet with camera manual controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsSheet(
    cameraInterface: CameraInterface?,
    onDismiss: () -> Unit,
) {
    val controls = cameraInterface?.controls ?: CameraControls()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Camera Controls",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Resolution selector
            var selectedResolution by remember { mutableStateOf(cameraInterface?.resolution?.toString() ?: "640x480") }
            ExposedDropdownMenuBox(
                expanded = false,
                onExpandedChange = { /* TODO: toggle dropdown */ },
            ) {
                OutlinedTextField(
                    value = selectedResolution,
                    onValueChange = {},
                    label = { Text("Resolution") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // TODO: ExposedDropdownMenu with supported resolutions
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Exposure time slider
            var exposureTime by remember { mutableStateOf(controls.exposureTimeNs ?: 0L) }
            Text("Exposure Time (µs)")
            Slider(
                value = exposureTime.toFloat(),
                onValueChange = { exposureTime = it.toLong() },
                valueRange = 0f..10000f,
            )

            // Gain slider
            var gain by remember { mutableStateOf(controls.gain ?: 1.0f) }
            Text("Gain (ISO)")
            Slider(
                value = gain,
                onValueChange = { gain = it },
                valueRange = 1.0f..16.0f,
            )

            // White balance selector
            var wbMode by remember { mutableStateOf(controls.whiteBalanceMode ?: WhiteBalanceMode.AUTO) }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                WhiteBalanceMode.values().forEach { mode ->
                    FilterChip(
                        selected = wbMode == mode,
                        onClick = { wbMode = mode },
                        label = { Text(mode.name) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Focus mode selector
            var focusMode by remember { mutableStateOf(controls.focusMode ?: FocusMode.CONTINUOUS_PICTURE) }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FocusMode.values().forEach { mode ->
                    FilterChip(
                        selected = focusMode == mode,
                        onClick = { focusMode = mode },
                        label = { Text(mode.name) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Image adjustments
            var brightness by remember { mutableStateOf(controls.brightness ?: 0) }
            Text("Brightness")
            Slider(
                value = brightness.toFloat(),
                onValueChange = { brightness = it.toInt() },
                valueRange = -128f..127f,
            )

            var contrast by remember { mutableStateOf(controls.contrast ?: 0) }
            Text("Contrast")
            Slider(
                value = contrast.toFloat(),
                onValueChange = { contrast = it.toInt() },
                valueRange = -128f..127f,
            )

            var saturation by remember { mutableStateOf(controls.saturation ?: 128) }
            Text("Saturation")
            Slider(
                value = saturation.toFloat(),
                onValueChange = { saturation = it.toInt() },
                valueRange = 0f..255f,
            )

            var sharpness by remember { mutableStateOf(controls.sharpness ?: 128) }
            Text("Sharpness")
            Slider(
                value = sharpness.toFloat(),
                onValueChange = { sharpness = it.toInt() },
                valueRange = 0f..255f,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Apply button
            Button(
                onClick = {
                    cameraInterface?.controls = controls.copy(
                        exposureTimeNs = if (exposureTime == 0L) null else exposureTime * 1000,
                        gain = if (gain == 1.0f) null else gain,
                        whiteBalanceMode = wbMode,
                        focusMode = focusMode,
                        brightness = if (brightness == 0) null else brightness,
                        contrast = if (contrast == 0) null else contrast,
                        saturation = if (saturation == 128) null else saturation,
                        sharpness = if (sharpness == 128) null else sharpness,
                    )
                    // TODO: trigger applyControls() coroutine
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
