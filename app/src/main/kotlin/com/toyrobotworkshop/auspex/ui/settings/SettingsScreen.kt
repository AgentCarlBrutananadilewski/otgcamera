package com.toyrobotworkshop.auspex.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.auspex.BuildConfig
import com.toyrobotworkshop.auspex.R
import com.toyrobotworkshop.auspex.camera.FocusMode
import com.toyrobotworkshop.auspex.camera.WhiteBalanceMode
import com.toyrobotworkshop.auspex.util.DiagnosticLogger

/**
 * Settings screen with camera controls, build info, and runtime diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val packageName = context.packageName

    // Package info for version
    val packageInfo = try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: Exception) {
        null
    }

    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCodeStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "unknown"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "unknown"
    }

    // Collect diagnostic events
    val events = DiagnosticLogger.events

    // Build the full log text for copying
    val logText = remember(events.size) {
        events.joinToString("\n") { "${it.timestamp} [${it.category}] ${it.message}" }
    }

    // Copy to clipboard
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Camera Controls
            item { CameraControlsSection() }

            // 2. Build Information
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.build_info_title), style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        DiagnosticRow(label = stringResource(R.string.app_version), value = versionName)
                        DiagnosticRow(label = stringResource(R.string.build_number), value = versionCodeStr)
                        DiagnosticRow(label = stringResource(R.string.buildconfig_version), value = BuildConfig.VERSION_NAME)
                        DiagnosticRow(label = stringResource(R.string.buildconfig_code), value = BuildConfig.BUILD_NUMBER.toString())
                        DiagnosticRow(label = stringResource(R.string.build_time), value = BuildConfig.BUILD_TIME)
                        DiagnosticRow(label = stringResource(R.string.git_sha), value = BuildConfig.GIT_SHA)
                    }
                }
            }

            // 3. Device Information
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.device_info_title), style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        DiagnosticRow(label = stringResource(R.string.device_label), value = "${Build.MANUFACTURER} ${Build.MODEL}")
                        DiagnosticRow(label = stringResource(R.string.android_version), value = Build.VERSION.RELEASE)
                        DiagnosticRow(label = stringResource(R.string.sdk_int), value = Build.VERSION.SDK_INT.toString())
                        DiagnosticRow(label = stringResource(R.string.board), value = Build.BOARD)
                        DiagnosticRow(label = stringResource(R.string.brand), value = Build.BRAND)
                    }
                }
            }

            // 4. Package Information
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.package_info_title), style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        DiagnosticRow(label = stringResource(R.string.package_name), value = packageName)
                        DiagnosticRow(label = stringResource(R.string.first_install), value = packageInfo?.firstInstallTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown")
                        DiagnosticRow(label = stringResource(R.string.last_updated), value = packageInfo?.lastUpdateTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown")
                    }
                }
            }

            // 5. Runtime Diagnostics (moved to bottom)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (events.any { it.category == "ERROR" })
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.runtime_diagnostics), style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { DiagnosticLogger.clear() }) {
                                    Text(stringResource(R.string.clear))
                                }
                                TextButton(onClick = {
                                    if (logText.isNotBlank()) {
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics", logText))
                                        copied = true
                                    }
                                }) {
                                    Text(if (copied) stringResource(R.string.copied) else stringResource(R.string.copy))
                                }
                            }
                        }
                        HorizontalDivider()

                        if (events.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_events),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .padding(8.dp),
                            ) {
                                BasicTextField(
                                    value = logText,
                                    onValueChange = {}, // read-only
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraControlsSection() {
    var exposureTime by remember { mutableStateOf(0L) }
    var gain by remember { mutableStateOf(1.0f) }
    var wbMode by remember { mutableStateOf(WhiteBalanceMode.AUTO) }
    var focusMode by remember { mutableStateOf(FocusMode.CONTINUOUS_PICTURE) }
    var brightness by remember { mutableStateOf(0) }
    var contrast by remember { mutableStateOf(0) }
    var saturation by remember { mutableStateOf(128) }
    var sharpness by remember { mutableStateOf(128) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.controls_title), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Text(stringResource(R.string.exposure_label))
            Slider(
                value = exposureTime.toFloat(),
                onValueChange = { exposureTime = it.toLong() },
                valueRange = 0f..10000f,
            )

            Text(stringResource(R.string.gain_label))
            Slider(
                value = gain,
                onValueChange = { gain = it },
                valueRange = 1.0f..16.0f,
            )

            Text(stringResource(R.string.white_balance_label))
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

            Text(stringResource(R.string.focus_mode_label))
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

            Text(stringResource(R.string.brightness_label))
            Slider(
                value = brightness.toFloat(),
                onValueChange = { brightness = it.toInt() },
                valueRange = -128f..127f,
            )

            Text(stringResource(R.string.contrast_label))
            Slider(
                value = contrast.toFloat(),
                onValueChange = { contrast = it.toInt() },
                valueRange = -128f..127f,
            )

            Text(stringResource(R.string.saturation_label))
            Slider(
                value = saturation.toFloat(),
                onValueChange = { saturation = it.toInt() },
                valueRange = 0f..255f,
            )

            Text(stringResource(R.string.sharpness_label))
            Slider(
                value = sharpness.toFloat(),
                onValueChange = { sharpness = it.toInt() },
                valueRange = 0f..255f,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { /* TODO: apply controls to camera */ },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}
