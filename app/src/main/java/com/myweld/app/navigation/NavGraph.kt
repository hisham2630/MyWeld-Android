package com.myweld.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myweld.app.data.ble.AppEvent
import com.myweld.app.data.repository.WelderRepository
import com.myweld.app.ui.screens.AboutScreen
import com.myweld.app.ui.screens.DashboardScreen
import com.myweld.app.ui.screens.PresetsScreen
import com.myweld.app.ui.screens.ScanScreen
import com.myweld.app.ui.screens.SettingsScreen
import com.myweld.app.ui.screens.FirmwareUpdateScreen
import com.myweld.app.viewmodel.FirmwareViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomNavItem(Routes.PRESETS, "Presets", Icons.Filled.Tune, Icons.Outlined.Tune),
    BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun NavGraph(
    welderRepository: WelderRepository = koinInject(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    // Observe BLE events and show snackbar toasts app-wide
    // ── PIN auth dialog state ─────────────────────────────────────────────────────────
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    var lockoutSec by remember { mutableStateOf(0) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        welderRepository.events.collect { event ->
            when (event) {
                is AppEvent.AuthRequired -> {
                    showPinDialog = true
                    pinError = false
                }
                is AppEvent.AuthSuccess -> {
                    showPinDialog = false
                    pinError = false
                    lockoutSec = 0
                    snackbarScope.launch { snackbarHostState.showSnackbar("✅ Authenticated") }
                }
                is AppEvent.AuthFailed -> {
                    pinError = true
                    lockoutSec = 0
                    // Shake animation
                    snackbarScope.launch {
                        repeat(4) {
                            shakeOffset.animateTo(if (it % 2 == 0) 12f else -12f, tween(60))
                        }
                        shakeOffset.animateTo(0f, tween(60))
                    }
                }
                is AppEvent.AuthLocked -> {
                    pinError = false
                    lockoutSec = event.remainingSec.coerceAtLeast(1)
                    // Shake animation for lockout too
                    snackbarScope.launch {
                        repeat(4) {
                            shakeOffset.animateTo(if (it % 2 == 0) 12f else -12f, tween(60))
                        }
                        shakeOffset.animateTo(0f, tween(60))
                    }
                }
                is AppEvent.Connected -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("🔗 Connected to ${event.deviceName}")
                }
                is AppEvent.Disconnected -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("⚠️ Disconnected")
                }
                is AppEvent.Reconnecting -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("🔄 Reconnecting...")
                }
                is AppEvent.ReconnectFailed -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("❌ ${event.reason}")
                }
                is AppEvent.CommandSent -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("✅ ${event.label}")
                }
                is AppEvent.CommandFailed -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("❌ ${event.label}")
                }
            }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val isError = data.visuals.message.startsWith("❌") ||
                    data.visuals.message.startsWith("⚠️")
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.SCAN,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200))
            },
        ) {
            composable(Routes.SCAN) {
                ScanScreen(
                    onDeviceConnected = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen()
            }

            composable(Routes.PRESETS) {
                PresetsScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToAbout = {
                        navController.navigate(Routes.ABOUT)
                    },
                    onNavigateToFirmwareUpdate = {
                        navController.navigate(Routes.FIRMWARE_UPDATE)
                    },
                    onDisconnect = {
                        navController.navigate(Routes.SCAN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.FIRMWARE_UPDATE) {
                val firmwareViewModel: FirmwareViewModel = koinViewModel()
                FirmwareUpdateScreen(
                    viewModel = firmwareViewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }

    // ── PIN authentication dialog overlay ───────────────────────────────────────
    if (showPinDialog) {
        PinAuthDialog(
            error = pinError,
            lockoutSec = lockoutSec,
            shakeOffsetPx = shakeOffset.value,
            onPinComplete = { pin -> welderRepository.authenticate(pin) },
            onResetError = { pinError = false },
            onLockoutTick = { lockoutSec = it },
            onDisconnect = {
                welderRepository.disconnect()
                showPinDialog = false
                lockoutSec = 0
                navController.navigate(Routes.SCAN) { popUpTo(0) { inclusive = true } }
            },
        )
    }
}

@Composable
private fun PinAuthDialog(
    error: Boolean,
    lockoutSec: Int,
    shakeOffsetPx: Float,
    onPinComplete: (String) -> Unit,
    onResetError: () -> Unit,
    onLockoutTick: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val maxLen = 4
    val isLocked = lockoutSec > 0

    // Clear entered digits when the server rejects the PIN so user can immediately retype
    LaunchedEffect(error) {
        if (error) pin = ""
    }

    // Clear entered digits when lockout starts
    LaunchedEffect(isLocked) {
        if (isLocked) pin = ""
    }

    // Countdown timer — ticks every second while locked out
    LaunchedEffect(lockoutSec) {
        if (lockoutSec > 0) {
            kotlinx.coroutines.delay(1000L)
            onLockoutTick(lockoutSec - 1)
        }
    }

    // Auto-submit when 4 digits entered (only if not locked)
    LaunchedEffect(pin) {
        if (pin.length == maxLen && !isLocked) onPinComplete(pin)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.offset { IntOffset(shakeOffsetPx.toInt(), 0) },
        ) {
            Text(
                text = "⚡ MyWeld",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Enter connection PIN",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            if (isLocked) {
                Text(
                    text = "🔒 Locked for ${lockoutSec}s",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (error) {
                Text(
                    text = "Wrong PIN — try again",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(maxLen) { i ->
                    val filled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLocked) Color.White.copy(alpha = 0.15f)
                                else if (error) MaterialTheme.colorScheme.error
                                else if (filled) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.3f),
                            ),
                    )
                }
            }

            // Number pad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else if (key == "⌫") {
                                FilledIconButton(
                                    onClick = {
                                        if (pin.isNotEmpty() && !isLocked) {
                                            if (error) onResetError()
                                            pin = pin.dropLast(1)
                                        }
                                    },
                                    enabled = !isLocked,
                                    modifier = Modifier.size(72.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White.copy(alpha = if (isLocked) 0.05f else 0.1f),
                                        contentColor = Color.White.copy(alpha = if (isLocked) 0.3f else 1f),
                                    ),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                                }
                            } else {
                                Surface(
                                    onClick = {
                                        if (pin.length < maxLen && !isLocked) {
                                            if (error) onResetError()
                                            pin += key
                                        }
                                    },
                                    enabled = !isLocked,
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = if (isLocked) 0.04f else 0.12f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = key,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = if (isLocked) 0.3f else 1f),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TextButton(onClick = onDisconnect) {
                Text(
                    "Disconnect",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

