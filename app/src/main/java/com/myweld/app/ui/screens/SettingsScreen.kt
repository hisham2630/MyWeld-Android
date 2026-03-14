package com.myweld.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myweld.app.BuildConfig
import com.myweld.app.data.model.WeldParams
import com.myweld.app.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToFirmwareUpdate: () -> Unit,
    onDisconnect: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinMismatch by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Reboot Device",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    "The welder will restart with a 3-second countdown. Any active BLE connection will be lost.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rebootDevice()
                        showRebootDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Reboot")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Factory Reset Device",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently erase ALL device settings:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "• Weld parameters & presets\n• Device name\n• Connection PIN (reset to 1234)\n• Weld counters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.factoryReset()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect") },
            text = { Text("Disconnect from the welder?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectDialog = false
                        onDisconnect()
                    },
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Rename Machine",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set the BLE advertising name (max ${WeldParams.BLE_NAME_MAX_LEN} chars). " +
                            "The new name appears immediately on the next scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it.take(WeldParams.BLE_NAME_MAX_LEN) },
                        label = { Text("Machine name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        supportingText = {
                            Text(
                                "${renameInput.length}/${WeldParams.BLE_NAME_MAX_LEN}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameMachine(renameInput)
                        showRenameDialog = false
                    },
                    enabled = renameInput.trim().isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    // ── Change PIN dialog ──────────────────────────────────────────────────────
    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Change Connection PIN",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a new 4-digit numeric PIN. All future connections will require this PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = {
                            newPinInput = it.filter { c -> c.isDigit() }.take(4)
                            pinMismatch = false
                        },
                        label = { Text("New PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next,
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        isError = pinMismatch,
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = {
                            confirmPinInput = it.filter { c -> c.isDigit() }.take(4)
                            pinMismatch = false
                        },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                        ),
                        isError = pinMismatch,
                        supportingText = if (pinMismatch) ({
                            Text("PINs do not match", color = MaterialTheme.colorScheme.error)
                        }) else null,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length == 4 && newPinInput == confirmPinInput) {
                            viewModel.changePin(newPinInput)
                            showChangePinDialog = false
                            newPinInput = ""; confirmPinInput = ""
                        } else {
                            pinMismatch = true
                        }
                    },
                    enabled = newPinInput.length == 4,
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Change PIN") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangePinDialog = false
                    newPinInput = ""; confirmPinInput = ""; pinMismatch = false
                }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
    }

    // ── Calibration dialog (debug builds only) ────────────────────────────────
    if (BuildConfig.DEBUG && showCalibrationDialog) {
        CalibrationDialog(
            currentVoltageMv = status.supercapVoltageMv,
            currentProtectionMv = status.protectionVoltageMv,
            onCalibrate = { channel, volts ->
                viewModel.calibrateAdc(channel, volts)
            },
            onDismiss = { showCalibrationDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // =============================================================
        // App Settings Section
        // =============================================================
        SectionHeader("App Settings")

        SettingsCard {
            SettingsRow(
                icon = {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = "Theme",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "Dark Mode",
                trailing = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                title = "Auto Reconnect",
                subtitle = "Reconnect to last device on app start",
                trailing = {
                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = { viewModel.setAutoReconnect(it) },
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Change PIN",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "Connection PIN",
                subtitle = "Change the 4-digit connection PIN",
                trailing = {
                    IconButton(onClick = {
                        newPinInput = ""; confirmPinInput = ""; pinMismatch = false
                        showChangePinDialog = true
                    }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Change PIN",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =============================================================
        // Device Settings Section
        // =============================================================
        SectionHeader("Device Settings")

        SettingsCard {
            SettingsRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Router,
                        contentDescription = "Machine Name",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "Machine Name",
                subtitle = status.bleName.ifBlank { "MyWeld" },
                trailing = {
                    IconButton(
                        onClick = {
                            renameInput = status.bleName.ifBlank { "MyWeld" }
                            showRenameDialog = true
                        },
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Brightness Slider ─────────────────────────────────────
            var brightnessSlider by remember(status.brightness) {
                mutableFloatStateOf(status.brightness.toFloat())
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LightMode,
                            contentDescription = "Brightness",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.padding(end = 16.dp))
                        Text(
                            text = "Brightness",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "${brightnessSlider.roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = brightnessSlider,
                    onValueChange = { brightnessSlider = it },
                    onValueChangeFinished = {
                        viewModel.setBrightness(brightnessSlider.roundToInt())
                    },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Volume Slider ─────────────────────────────────────────
            var volumeSlider by remember(status.volume) {
                mutableFloatStateOf(status.volume.toFloat())
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.padding(end = 16.dp))
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "${volumeSlider.roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = volumeSlider,
                    onValueChange = { volumeSlider = it },
                    onValueChangeFinished = {
                        viewModel.setVolume(volumeSlider.roundToInt())
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Sound Toggle ─────────────────────────────────────────
            SettingsRow(
                icon = {
                    Icon(
                        imageVector = if (status.soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Sound",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "Sound",
                subtitle = if (status.soundOn) "On" else "Off",
                trailing = {
                    Switch(
                        checked = status.soundOn,
                        onCheckedChange = { viewModel.toggleSound(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                title = "Firmware Version",
                subtitle = status.firmwareVersion,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = "Firmware Update",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "Firmware Update",
                subtitle = "Update firmware over Bluetooth",
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onNavigateToFirmwareUpdate,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =============================================================
        // ADC Calibration Section (debug builds only)
        // =============================================================
        if (BuildConfig.DEBUG) {
            SectionHeader("ADC Calibration")

            // Live calibration data card
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ── Supercap ADC ──────────────────────────────────
                    Text(
                        "Supercap ADC (CH4)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AdcDebugRow("Raw ADC", "${"%.3f".format(status.rawSupercapVoltage)} V")
                            AdcDebugRow("Cal Factor", "${"%.4f".format(status.calFactorVoltage)}")
                            AdcDebugRow(
                                "Calibrated",
                                "${"%.3f".format(status.supercapVoltage)} V",
                                valueColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // ── Protection ADC ────────────────────────────────
                    Text(
                        "Protection ADC (CH5)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AdcDebugRow("Raw ADC", "${"%.3f".format(status.rawProtectionVoltage)} V")
                            AdcDebugRow("Cal Factor", "${"%.4f".format(status.calFactorProtection)}")
                            AdcDebugRow(
                                "Calibrated",
                                "${"%.3f".format(status.protectionVoltage)} V",
                                valueColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Status badge
                    Surface(
                        color = if (status.isCalibrated) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = if (status.isCalibrated) "✓ Calibrated" else "⚠ Not Calibrated (factors = 1.0)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (status.isCalibrated) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Calibrate button
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Calibration",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    title = "Calibrate Voltage",
                    subtitle = "Use multimeter to calibrate ADC channels",
                    trailing = {
                        Button(
                            onClick = { showCalibrationDialog = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Calibrate")
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // =============================================================
        // Saved Devices Section
        // =============================================================
        if (savedDevices.isNotEmpty()) {
            SectionHeader("Saved Devices")
            SettingsCard {
                savedDevices.forEachIndexed { index, device ->
                    SettingsRow(
                        title = device.name,
                        subtitle = device.macAddress,
                        trailing = {
                            IconButton(onClick = { viewModel.forgetDevice(device.macAddress) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Forget",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    if (index < savedDevices.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // =============================================================
        // Danger Zone
        // =============================================================
        SectionHeader("Danger Zone")

        SettingsCard {
            SettingsRow(
                icon = {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = "Reboot",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "Reboot Device",
                subtitle = "Restart the welder (3-second countdown)",
                onClick = { showRebootDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = "Factory Reset Device",
                subtitle = "Reset all settings to defaults",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showResetDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About
        SettingsCard {
            SettingsRow(
                icon = {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "About",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = "About",
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onNavigateToAbout,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Disconnect button
        Button(
            onClick = { showDisconnectDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.error,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Disconnect", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.padding(end = 16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun AdcDebugRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            color = valueColor,
        )
    }
}

@Composable
private fun CalibrationDialog(
    currentVoltageMv: Int,
    currentProtectionMv: Int,
    onCalibrate: (channel: Int, referenceVolts: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var supercapInput by remember { mutableStateOf("") }
    var protectionInput by remember { mutableStateOf("") }
    var supercapDone by remember { mutableStateOf(false) }
    var protectionDone by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "ADC Calibration",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Instructions
                Text(
                    text = "Measure each rail with a multimeter and enter the exact " +
                        "reading to calibrate the ADC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ── Supercap Channel ─────────────────────────────────
                Text(
                    "Supercap Voltage",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Current ESP32 reading
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "ESP32 reads:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${"%.2f".format(currentVoltageMv / 1000f)} V",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Supercap voltage input
                OutlinedTextField(
                    value = supercapInput,
                    onValueChange = { input ->
                        supercapInput = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Multimeter voltage (V)") },
                    placeholder = { Text("e.g. 4.73") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    enabled = !supercapDone,
                    supportingText = if (supercapDone) ({
                        Text("✓ Supercap calibrated", color = MaterialTheme.colorScheme.primary)
                    }) else null,
                )

                // Calibrate supercap button
                if (!supercapDone) {
                    val volts = supercapInput.toFloatOrNull()
                    Button(
                        onClick = {
                            volts?.let {
                                onCalibrate(0, it)
                                supercapDone = true
                            }
                        },
                        enabled = volts != null && volts in 0.1f..30f,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Calibrate Supercap")
                    }
                }

                // ── Protection Rail Channel ──────────────────────────
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "Protection Rail (Gate Drive)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Current protection reading
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "ESP32 reads:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${"%.2f".format(currentProtectionMv / 1000f)} V",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                OutlinedTextField(
                    value = protectionInput,
                    onValueChange = { input ->
                        protectionInput = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Multimeter voltage (V)") },
                    placeholder = { Text("e.g. 13.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    enabled = !protectionDone,
                    supportingText = if (protectionDone) ({
                        Text("✓ Protection calibrated", color = MaterialTheme.colorScheme.primary)
                    }) else null,
                )

                if (!protectionDone) {
                    val protVolts = protectionInput.toFloatOrNull()
                    Button(
                        onClick = {
                            protVolts?.let {
                                onCalibrate(1, it)
                                protectionDone = true
                            }
                        },
                        enabled = protVolts != null && protVolts in 0.1f..30f,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Calibrate Protection")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (supercapDone || protectionDone) "Done" else "Cancel",
                    color = if (supercapDone || protectionDone) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = null,
    )
}
