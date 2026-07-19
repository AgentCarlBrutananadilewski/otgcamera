package com.toyrobotworkshop.auspex.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.auspex.BuildConfig
import com.toyrobotworkshop.auspex.R
import com.toyrobotworkshop.auspex.camera.CameraControls
import com.toyrobotworkshop.auspex.camera.FocusMode
import com.toyrobotworkshop.auspex.camera.WhiteBalanceMode
import com.toyrobotworkshop.auspex.ui.main.CameraViewModel
import com.toyrobotworkshop.auspex.util.DiagnosticLogger

/**
 * Settings screen with camera controls, build info, and runtime diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: CameraViewModel,
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

    // Camera controls — wired to ViewModel
    val uiState by viewModel.uiState.collectAsState()
    var controls by remember { mutableStateOf(uiState.controls ?: CameraControls()) }

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
                .padding(padding),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Camera Controls (sticky header)
            stickyHeader {
                Text(
                    text = stringResource(R.string.section_camera_controls),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Exposure time slider with value label
                        Text(stringResource(R.string.exposure_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = (controls.exposureTimeNs?.div(1_000) ?: 0f).toFloat(),
                                onValueChange = {
                                    controls = controls.copy(exposureTimeNs = (it * 1_000).toLong())
                                },
                                valueRange = 0f..10_000f,
                                steps = 99,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${(controls.exposureTimeNs ?: 0L / 1_000)} µs",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Gain slider with value label
                        Text(stringResource(R.string.gain_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = controls.gain ?: 1.0f,
                                onValueChange = {
                                    controls = controls.copy(gain = it)
                                },
                                valueRange = 1.0f..16.0f,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "×${String.format("%.1f", controls.gain ?: 1.0f)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // White balance — ExposedDropdownMenuBox
                        Text(stringResource(R.string.white_balance_label))
                        var wbExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = wbExpanded,
                            onExpandedChange = { wbExpanded = !wbExpanded },
                        ) {
                            OutlinedTextField(
                                value = whiteBalanceDisplayName(controls.whiteBalanceMode),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.white_balance_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = wbExpanded,
                                onDismissRequest = { wbExpanded = false },
                            ) {
                                WhiteBalanceMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(whiteBalanceDisplayName(mode)) },
                                        onClick = {
                                            controls = controls.copy(whiteBalanceMode = mode)
                                            wbExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        // Focus mode — ExposedDropdownMenuBox
                        Text(stringResource(R.string.focus_mode_label))
                        var focusExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = focusExpanded,
                            onExpandedChange = { focusExpanded = !focusExpanded },
                        ) {
                            OutlinedTextField(
                                value = focusModeDisplayName(controls.focusMode),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.focus_mode_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = focusExpanded,
                                onDismissRequest = { focusExpanded = false },
                            ) {
                                FocusMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(focusModeDisplayName(mode)) },
                                        onClick = {
                                            controls = controls.copy(focusMode = mode)
                                            focusExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        // Brightness slider with value label
                        Text(stringResource(R.string.brightness_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = (controls.brightness ?: 0).toFloat(),
                                onValueChange = {
                                    controls = controls.copy(brightness = it.toInt())
                                },
                                valueRange = -128f..127f,
                                steps = 255,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${controls.brightness ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Contrast slider with value label
                        Text(stringResource(R.string.contrast_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = (controls.contrast ?: 0).toFloat(),
                                onValueChange = {
                                    controls = controls.copy(contrast = it.toInt())
                                },
                                valueRange = -128f..127f,
                                steps = 255,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${controls.contrast ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Saturation slider with value label
                        Text(stringResource(R.string.saturation_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = (controls.saturation ?: 128).toFloat(),
                                onValueChange = {
                                    controls = controls.copy(saturation = it.toInt())
                                },
                                valueRange = 0f..255f,
                                steps = 255,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${controls.saturation ?: 128}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Sharpness slider with value label
                        Text(stringResource(R.string.sharpness_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = (controls.sharpness ?: 128).toFloat(),
                                onValueChange = {
                                    controls = controls.copy(sharpness = it.toInt())
                                },
                                valueRange = 0f..255f,
                                steps = 255,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${controls.sharpness ?: 128}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

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

            // 2. Build Information (sticky header)
            stickyHeader {
                Text(
                    text = stringResource(R.string.section_build_info),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DiagnosticListItem(
                            label = stringResource(R.string.app_version),
                            value = BuildConfig.VERSION_NAME,
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.build_number),
                            value = BuildConfig.BUILD_NUMBER.toString(),
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.build_time),
                            value = BuildConfig.BUILD_TIME,
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.git_sha),
                            value = BuildConfig.GIT_SHA,
                        )
                    }
                }
            }

            // 3. Device Information (sticky header)
            stickyHeader {
                Text(
                    text = stringResource(R.string.section_device_info),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DiagnosticListItem(
                            label = stringResource(R.string.device_label),
                            value = "${Build.MANUFACTURER} ${Build.MODEL}",
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.android_version),
                            value = Build.VERSION.RELEASE,
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.sdk_int),
                            value = Build.VERSION.SDK_INT.toString(),
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.board),
                            value = Build.BOARD,
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.brand),
                            value = Build.BRAND,
                        )
                    }
                }
            }

            // 4. Package Information (sticky header)
            stickyHeader {
                Text(
                    text = stringResource(R.string.section_package_info),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DiagnosticListItem(
                            label = stringResource(R.string.package_name),
                            value = packageName,
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.first_install),
                            value = packageInfo?.firstInstallTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown",
                        )
                        DiagnosticListItem(
                            label = stringResource(R.string.last_updated),
                            value = packageInfo?.lastUpdateTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown",
                        )
                    }
                }
            }

            // 5. Runtime Diagnostics (sticky header)
            stickyHeader {
                Text(
                    text = stringResource(R.string.section_diagnostics),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (events.any { it.category == "ERROR" })
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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

                        if (events.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_events),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(events) { event ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = "[${event.category}] ${event.message}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = event.timestamp,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Display name for a white balance mode — pulled from string resources.
 */
@Composable
fun whiteBalanceDisplayName(mode: WhiteBalanceMode?): String {
    return when (mode) {
        WhiteBalanceMode.AUTO -> stringResource(R.string.wb_auto)
        WhiteBalanceMode.INCANDESCENT -> stringResource(R.string.wb_incandescent)
        WhiteBalanceMode.FLUORESCENT -> stringResource(R.string.wb_fluorescent)
        WhiteBalanceMode.DAYLIGHT -> stringResource(R.string.wb_daylight)
        WhiteBalanceMode.CLOUDY_DAYLIGHT -> stringResource(R.string.wb_cloudy)
        WhiteBalanceMode.TWILIGHT -> stringResource(R.string.wb_twilight)
        WhiteBalanceMode.SHADE -> stringResource(R.string.wb_shade)
        WhiteBalanceMode.MANUAL -> stringResource(R.string.wb_manual)
        null -> stringResource(R.string.mode_auto)
    }
}

/**
 * Display name for a focus mode — pulled from string resources.
 */
@Composable
fun focusModeDisplayName(mode: FocusMode?): String {
    return when (mode) {
        FocusMode.CONTINUOUS_PICTURE -> stringResource(R.string.focus_continuous)
        FocusMode.AUTO -> stringResource(R.string.focus_auto)
        FocusMode.FIXED -> stringResource(R.string.focus_fixed)
        FocusMode.MANUAL -> stringResource(R.string.focus_manual)
        FocusMode.MACRO -> stringResource(R.string.focus_macro)
        FocusMode.INFINITY -> stringResource(R.string.focus_infinity)
        FocusMode.EDGE -> stringResource(R.string.focus_edge)
        null -> stringResource(R.string.mode_auto)
    }
}

/**
 * Diagnostic row using ListItem with headlineContent + supportingContent.
 */
@Composable
private fun DiagnosticListItem(
    label: String,
    value: String,
) {
    ListItem(
        headlineContent = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
