package com.myweld.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.myweld.app.data.ble.BleConnectionState
import com.myweld.app.data.ble.BleUuids
import com.myweld.app.ui.theme.MyWeldColors
import com.myweld.app.viewmodel.ScanViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    onDeviceConnected: () -> Unit,
    viewModel: ScanViewModel = koinViewModel(),
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Navigate on successful connection
    LaunchedEffect(connectionState) {
        if (connectionState is BleConnectionState.Connected) {
            onDeviceConnected()
        }
    }

    // BLE Permissions
    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
    val permissionState = rememberMultiplePermissionsState(blePermissions)

    // Bluetooth adapter
    val bluetoothAdapter = remember {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        manager?.adapter
    }

    // Track Bluetooth enabled state reactively
    var isBluetoothEnabled by remember {
        mutableStateOf(bluetoothAdapter?.isEnabled == true)
    }

    // Listen for Bluetooth on/off changes via BroadcastReceiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    isBluetoothEnabled = state == BluetoothAdapter.STATE_ON
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Launcher to prompt user to enable Bluetooth
    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // State is updated by the BroadcastReceiver, no need to handle result here
    }

    // Scanner — derive from live adapter state so it's non-null after BT is turned on
    val scanner = remember(isBluetoothEnabled) { bluetoothAdapter?.bluetoothLeScanner }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                viewModel.onScanResult(result)
            }
        }
    }

    fun startScan() {
        val currentScanner = scanner ?: return
        if (!permissionState.allPermissionsGranted) return
        if (!isBluetoothEnabled) return
        viewModel.clearDiscoveredDevices()
        viewModel.setScanningState(true)

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleUuids.SERVICE))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        currentScanner.startScan(filters, settings, scanCallback)
    }

    fun stopScan() {
        val currentScanner = scanner ?: return
        try {
            currentScanner.stopScan(scanCallback)
        } catch (_: Exception) {}
        viewModel.setScanningState(false)
    }

    // Stop scan on dispose
    DisposableEffect(Unit) {
        onDispose { stopScan() }
    }

    // Stop scan if Bluetooth gets turned off while scanning
    LaunchedEffect(isBluetoothEnabled) {
        if (!isBluetoothEnabled && isScanning) {
            viewModel.setScanningState(false)
        }
    }

    // Auto-stop scan after 10 seconds
    LaunchedEffect(isScanning) {
        if (isScanning) {
            kotlinx.coroutines.delay(10_000)
            stopScan()
        }
    }

    // Animated background elements
    val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // App title
        Text(
            text = "⚡ MyWeld",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Spot Welder Control",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // State priority: Permissions → Bluetooth Off → Connecting → Scan
        if (!permissionState.allPermissionsGranted) {
            // 1) Missing permissions
            Button(
                onClick = { permissionState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Grant Bluetooth Permission")
            }
        } else if (!isBluetoothEnabled) {
            // 2) Bluetooth is disabled — prompt user to enable
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = "Bluetooth Off",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Bluetooth is Off",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Turn on Bluetooth to scan for\nyour MyWeld welder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBtLauncher.launch(enableBtIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enable Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else if (connectionState is BleConnectionState.Connecting || connectionState is BleConnectionState.Authenticating) {
            // 3) Currently connecting or waiting for PIN auth
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (connectionState is BleConnectionState.Authenticating) "Authenticating…" else "Connecting…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            // 4) Ready to scan
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp),
            ) {
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                }
                Icon(
                    imageVector = if (isScanning) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                    contentDescription = "Bluetooth",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { if (isScanning) stopScan() else startScan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isScanning) "Stop Scanning" else "Scan for Welders",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device lists
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Discovered devices
            if (discoveredDevices.isNotEmpty()) {
                item {
                    Text(
                        text = "NEARBY DEVICES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(discoveredDevices, key = { "disc_${it.macAddress}" }) { device ->
                    DeviceCard(
                        name = device.name,
                        macAddress = device.macAddress,
                        rssi = device.rssi,
                        onClick = {
                            stopScan()
                            viewModel.connectToDevice(device.device)
                        },
                    )
                }
            }

            // Saved devices
            if (savedDevices.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SAVED DEVICES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(savedDevices, key = { "saved_${it.macAddress}" }) { device ->
                    SavedDeviceCard(
                        name = device.name,
                        macAddress = device.macAddress,
                        onConnect = {
                            bluetoothAdapter?.getRemoteDevice(device.macAddress)?.let { btDevice ->
                                viewModel.connectToDevice(btDevice)
                            }
                        },
                        onForget = { viewModel.forgetDevice(device.macAddress) },
                    )
                }
            }

            // Empty state
            if (discoveredDevices.isEmpty() && !isScanning) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "No devices found.\nMake sure your welder is powered on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    name: String,
    macAddress: String,
    rssi: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Signal strength
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SignalCellularAlt,
                    contentDescription = "Signal",
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        rssi > -60 -> MyWeldColors.StateReady
                        rssi > -80 -> MyWeldColors.VoltageYellow
                        else -> MyWeldColors.VoltageRed
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${rssi}dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SavedDeviceCard(
    name: String,
    macAddress: String,
    onConnect: () -> Unit,
    onForget: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onForget) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Forget device",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
