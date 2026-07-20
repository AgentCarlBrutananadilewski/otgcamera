package com.toyrobotworkshop.auspex.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.auspex.R

/**
 * Shown when no USB camera is detected.
 */
@Composable
fun NoDeviceScreen(
    onRetry: () -> Unit,
) {
    val deviceRotationDegrees by rememberDeviceRotation()
    val animatedRotation by animateFloatAsState(
        targetValue = deviceRotationDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "NoDeviceRotation"
    )

    Scaffold { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val isRotated = deviceRotationDegrees == 90f || deviceRotationDegrees == 270f
            
            // If the device is horizontal, the 'width' of our content area 
            // should match the device's vertical height (maxHeight) to avoid squashing.
            val contentWidth = if (isRotated) maxHeight else maxWidth
            val contentHeight = if (isRotated) maxWidth else maxHeight

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .requiredSize(width = contentWidth, height = contentHeight)
                    .rotate(animatedRotation)
                    .padding(horizontal = 32.dp),
            ) {
                // Decorative icon — 48dp per M3 empty state guidance
                Icon(
                    imageVector = Icons.Rounded.VideocamOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.no_device_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.no_device_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                // Content-sized OutlinedButton with leading icon
                OutlinedButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_check_again))
                }
            }
        }
    }
}
