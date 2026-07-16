package com.toyrobotworkshop.otgcamera.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.toyrobotworkshop.otgcamera.BuildConfig
import com.toyrobotworkshop.otgcamera.util.DiagnosticLogger

/**
 * Settings screen with build diagnostics and runtime event log.
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

    // Collect diagnostic events — DiagnosticLogger.events is a mutableStateListOf,
    // so reading it directly triggers recomposition when new events arrive
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back"
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
            // Runtime diagnostics card — FIRST, most important
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
                            Text(
                                text = "Runtime Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { DiagnosticLogger.clear() }) {
                                    Text("Clear")
                                }
                                TextButton(onClick = {
                                    if (logText.isNotBlank()) {
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics", logText))
                                        copied = true
                                    }
                                }) {
                                    Text(if (copied) "✓ Copied" else "Copy")
                                }
                            }
                        }
                        HorizontalDivider()

                        if (events.isEmpty()) {
                            Text(
                                text = "No events yet. Try initializing the camera.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Selectable, scrollable text block — user can long-press to select/copy
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

            // Version info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Build Information",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HorizontalDivider()
                        DiagnosticRow(label = "App Version", value = versionName)
                        DiagnosticRow(label = "Build Number", value = versionCodeStr)
                        DiagnosticRow(label = "BuildConfig Version", value = BuildConfig.VERSION_NAME)
                        DiagnosticRow(label = "BuildConfig Code", value = BuildConfig.BUILD_NUMBER.toString())
                        DiagnosticRow(label = "Build Time", value = BuildConfig.BUILD_TIME)
                        DiagnosticRow(label = "Git SHA", value = BuildConfig.GIT_SHA)
                    }
                }
            }

            // Device info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Device Information",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HorizontalDivider()
                        DiagnosticRow(label = "Device", value = "${Build.MANUFACTURER} ${Build.MODEL}")
                        DiagnosticRow(label = "Android Version", value = Build.VERSION.RELEASE)
                        DiagnosticRow(label = "SDK Int", value = Build.VERSION.SDK_INT.toString())
                        DiagnosticRow(label = "Board", value = Build.BOARD)
                        DiagnosticRow(label = "Brand", value = Build.BRAND)
                    }
                }
            }

            // Package info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Package Information",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HorizontalDivider()
                        DiagnosticRow(label = "Package Name", value = packageName)
                        DiagnosticRow(label = "First Install", value = packageInfo?.firstInstallTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown")
                        DiagnosticRow(label = "Last Updated", value = packageInfo?.lastUpdateTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it) } ?: "unknown")
                    }
                }
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
