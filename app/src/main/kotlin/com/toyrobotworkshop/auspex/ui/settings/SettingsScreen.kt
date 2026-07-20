package com.toyrobotworkshop.auspex.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(uiState.controls ?: CameraControls()) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Camera Controls
            item {
                SettingsGroup(title = stringResource(R.string.section_camera_controls)) {
                    Column {
                        // Exposure time
                        SettingsSliderItem(
                            label = stringResource(R.string.exposure_label),
                            value = (controls.exposureTimeNs?.div(1_000) ?: 0f).toFloat(),
                            onValueChange = {
                                controls = controls.copy(exposureTimeNs = (it * 1_000).toLong())
                            },
                            valueRange = 0f..10_000f,
                            steps = 99,
                            valueDisplay = "${((controls.exposureTimeNs ?: 0L) / 1_000)} µs"
                        )

                        // Gain
                        SettingsSliderItem(
                            label = stringResource(R.string.gain_label),
                            value = controls.gain ?: 1.0f,
                            onValueChange = {
                                controls = controls.copy(gain = it)
                            },
                            valueRange = 1.0f..16.0f,
                            valueDisplay = "×${"%.1f".format(controls.gain ?: 1.0f)}"
                        )

                        // White balance
                        var wbExpanded by remember { mutableStateOf(false) }
                        SettingsDropdownItem(
                            label = stringResource(R.string.white_balance_label),
                            value = whiteBalanceDisplayName(controls.whiteBalanceMode),
                            expanded = wbExpanded,
                            onExpandedChange = { wbExpanded = it },
                            options = WhiteBalanceMode.entries,
                            optionLabel = { whiteBalanceDisplayName(it) },
                            onOptionClick = {
                                controls = controls.copy(whiteBalanceMode = it)
                                wbExpanded = false
                            }
                        )

                        // Focus mode
                        var focusExpanded by remember { mutableStateOf(false) }
                        SettingsDropdownItem(
                            label = stringResource(R.string.focus_mode_label),
                            value = focusModeDisplayName(controls.focusMode),
                            expanded = focusExpanded,
                            onExpandedChange = { focusExpanded = it },
                            options = FocusMode.entries,
                            optionLabel = { focusModeDisplayName(it) },
                            onOptionClick = {
                                controls = controls.copy(focusMode = it)
                                focusExpanded = false
                            }
                        )

                        // Brightness
                        SettingsSliderItem(
                            label = stringResource(R.string.brightness_label),
                            value = (controls.brightness ?: 0).toFloat(),
                            onValueChange = {
                                controls = controls.copy(brightness = it.toInt())
                            },
                            valueRange = -128f..127f,
                            steps = 255,
                            valueDisplay = "${controls.brightness ?: 0}"
                        )

                        // Contrast
                        SettingsSliderItem(
                            label = stringResource(R.string.contrast_label),
                            value = (controls.contrast ?: 0).toFloat(),
                            onValueChange = {
                                controls = controls.copy(contrast = it.toInt())
                            },
                            valueRange = -128f..127f,
                            steps = 255,
                            valueDisplay = "${controls.contrast ?: 0}"
                        )

                        // Saturation
                        SettingsSliderItem(
                            label = stringResource(R.string.saturation_label),
                            value = (controls.saturation ?: 128).toFloat(),
                            onValueChange = {
                                controls = controls.copy(saturation = it.toInt())
                            },
                            valueRange = 0f..255f,
                            steps = 255,
                            valueDisplay = "${controls.saturation ?: 128}"
                        )

                        // Sharpness
                        SettingsSliderItem(
                            label = stringResource(R.string.sharpness_label),
                            value = (controls.sharpness ?: 128).toFloat(),
                            onValueChange = {
                                controls = controls.copy(sharpness = it.toInt())
                            },
                            valueRange = 0f..255f,
                            steps = 255,
                            valueDisplay = "${controls.sharpness ?: 128}"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { /* TODO: apply controls to camera */ },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.apply))
                            }
                        }
                    }
                }
            }

            // 2. Build Information
            item {
                SettingsGroup(title = stringResource(R.string.section_build_info)) {
                    Column {
                        SettingsInfoItem(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
                        SettingsInfoItem(stringResource(R.string.build_number), BuildConfig.BUILD_NUMBER.toString())
                        SettingsInfoItem(stringResource(R.string.build_time), BuildConfig.BUILD_TIME)
                        SettingsInfoItem(stringResource(R.string.git_sha), BuildConfig.GIT_SHA)
                    }
                }
            }

            // 3. Device Information
            item {
                SettingsGroup(title = stringResource(R.string.section_device_info)) {
                    Column {
                        SettingsInfoItem(stringResource(R.string.device_label), "${Build.MANUFACTURER} ${Build.MODEL}")
                        SettingsInfoItem(stringResource(R.string.android_version), Build.VERSION.RELEASE)
                        SettingsInfoItem(stringResource(R.string.sdk_int), Build.VERSION.SDK_INT.toString())
                        SettingsInfoItem(stringResource(R.string.board), Build.BOARD)
                        SettingsInfoItem(stringResource(R.string.brand), Build.BRAND)
                    }
                }
            }

            // 4. Package Information
            item {
                SettingsGroup(title = stringResource(R.string.section_package_info)) {
                    Column {
                        SettingsInfoItem(stringResource(R.string.package_name), packageName)
                        SettingsInfoItem(
                            stringResource(R.string.first_install),
                            packageInfo?.firstInstallTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown"
                        )
                        SettingsInfoItem(
                            stringResource(R.string.last_updated),
                            packageInfo?.lastUpdateTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown"
                        )
                    }
                }
            }

            // 5. Runtime Diagnostics
            item {
                SettingsGroup(title = stringResource(R.string.section_diagnostics)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Column(
                                modifier = Modifier.heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f)
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
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun SettingsInfoItem(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.labelLarge) },
        supportingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueDisplay: String
) {
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = valueDisplay,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdownItem(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onOptionClick: (T) -> Unit
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = { onOptionClick(option) },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
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
