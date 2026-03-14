package com.myweld.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.ui.components.ConnectionBadge
import com.myweld.app.ui.components.ParamCard
import com.myweld.app.ui.components.StatusIndicator
import com.myweld.app.ui.components.VoltageBar
import com.myweld.app.ui.components.WeldCounter
import com.myweld.app.ui.theme.MyWeldColors
import com.myweld.app.ui.theme.ValueSmallTextStyle
import com.myweld.app.viewmodel.DashboardControlMode
import com.myweld.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isLegacyFirmware by viewModel.isLegacyFirmware.collectAsStateWithLifecycle()
    val activePresetName by viewModel.activePresetName.collectAsStateWithLifecycle()
    val controlMode by viewModel.controlMode.collectAsStateWithLifecycle()
    val paramsLocked by viewModel.paramsLocked.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    // Dropdown state for preset picker
    var presetDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.recordVoltage(status.supercapVoltage)
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚡ MyWeld",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            ConnectionBadge(state = connectionState)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Control mode selector ─────────────────────────────────────────────
        ControlModeSelector(
            mode = controlMode,
            onSelect = { viewModel.setControlMode(it) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Legacy firmware warning ───────────────────────────────────────────
        AnimatedVisibility(visible = isLegacyFirmware, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "⚠️ Firmware update required. Binary protocol not detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── Voltage bar ───────────────────────────────────────────────────────
        VoltageBar(
            voltageMv = status.supercapVoltageMv,
            chargePercent = status.chargePercent,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Gate drive rail ───────────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Gate Drive Rail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.2f V".format(status.protectionVoltage),
                    style = ValueSmallTextStyle,
                    color = if (status.protectionVoltage in 10f..18f) MyWeldColors.StateReady
                    else MyWeldColors.StateError,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Active preset info (PRESET mode only) ─────────────────────────────
        AnimatedVisibility(
            visible = controlMode == DashboardControlMode.PRESET,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
        ) {
            Column {
                Box {
                    Surface(
                        color = if (paramsLocked)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { presetDropdownExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Preset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = activePresetName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (paramsLocked) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Locked",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "Factory",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                } else {
                                    Text(
                                        text = "Custom ✎",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Change preset",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // ── Preset picker dropdown ────────────────────────────────
                    DropdownMenu(
                        expanded = presetDropdownExpanded,
                        onDismissRequest = { presetDropdownExpanded = false },
                        modifier = Modifier.width(280.dp),
                    ) {
                        presets.forEachIndexed { idx, preset ->
                            val isActive = idx == status.activePreset
                            val displayName = preset.name.ifEmpty { "Preset ${idx + 1}" }

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Active",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(18.dp))
                                        }
                                        Column {
                                            Text(
                                                text = displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface,
                                            )
                                            // Show P1/P2 summary
                                            Text(
                                                text = "P1=${preset.p1Ms.toInt()}ms  P2=${preset.p2Ms.toInt()}ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    presetDropdownExpanded = false
                                    viewModel.selectPreset(idx)
                                },
                            )
                            // Divider between factory and custom presets
                            if (idx == 6 && presets.size > 7) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── Weld trigger mode (AUTO sense / manual button) ────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Weld Trigger",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (status.autoMode) "Auto sense" else "Physical button",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAN",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (!status.autoMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!status.autoMode) FontWeight.Bold else FontWeight.Normal,
                    )
                    Switch(
                        checked = status.autoMode,
                        onCheckedChange = { viewModel.toggleMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "AUTO",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (status.autoMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (status.autoMode) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Locked hint bar ───────────────────────────────────────────────────
        AnimatedVisibility(visible = paramsLocked, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Factory preset — switch to User Defined to edit freely, or load a Custom preset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // ── Parameter cards P1, T, P2 ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ParamCard(
                label = "P1",
                value = status.p1Ms,
                unit = "ms",
                step = WeldParams.PULSE_STEP,
                onIncrement = { viewModel.adjustP1(WeldParams.PULSE_STEP) },
                onDecrement = { viewModel.adjustP1(-WeldParams.PULSE_STEP) },
                enabled = !paramsLocked,
                modifier = Modifier.weight(1f),
            )
            ParamCard(
                label = "T",
                value = status.tMs,
                unit = "ms",
                step = WeldParams.PAUSE_STEP,
                onIncrement = { viewModel.adjustT(WeldParams.PAUSE_STEP) },
                onDecrement = { viewModel.adjustT(-WeldParams.PAUSE_STEP) },
                enabled = !paramsLocked,
                modifier = Modifier.weight(1f),
            )
            ParamCard(
                label = "P2",
                value = status.p2Ms,
                unit = "ms",
                step = WeldParams.PULSE_STEP,
                onIncrement = { viewModel.adjustP2(WeldParams.PULSE_STEP) },
                onDecrement = { viewModel.adjustP2(-WeldParams.PULSE_STEP) },
                enabled = !paramsLocked,
                modifier = Modifier.weight(1f),
            )
        }

        // ── P3 / P4 cards — progressive unlock ───────────────────────────────
        // Only visible when T > 0 (multi-pulse mode is active).
        // P3 appears first (at 0 = disabled). P4 unlocks only when P3 > 0.
        AnimatedVisibility(visible = status.tMs > 0f) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ParamCard(
                        label = "P3",
                        value = status.p3Ms,
                        unit = "ms",
                        step = WeldParams.PULSE_STEP,
                        onIncrement = { viewModel.adjustP3(WeldParams.PULSE_STEP) },
                        onDecrement = { viewModel.adjustP3(-WeldParams.PULSE_STEP) },
                        enabled = !paramsLocked,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedVisibility(
                        visible = status.p3Ms > 0f,
                        modifier = Modifier.weight(1f),
                    ) {
                        ParamCard(
                            label = "P4",
                            value = status.p4Ms,
                            unit = "ms",
                            step = WeldParams.PULSE_STEP,
                            onIncrement = { viewModel.adjustP4(WeldParams.PULSE_STEP) },
                            onDecrement = { viewModel.adjustP4(-WeldParams.PULSE_STEP) },
                            enabled = !paramsLocked,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // ── S card (AUTO mode only) ───────────────────────────────────────────
        AnimatedVisibility(visible = status.autoMode) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                ParamCard(
                    label = "S (Contact Delay)",
                    value = status.sSeconds,
                    unit = "s",
                    step = WeldParams.S_STEP,
                    onIncrement = { viewModel.adjustS(WeldParams.S_STEP) },
                    onDecrement = { viewModel.adjustS(-WeldParams.S_STEP) },
                    enabled = !paramsLocked,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Status + preset badge ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIndicator(state = status.state, modifier = Modifier.weight(1f))

            // In USER_DEFINED mode still show a subtle preset badge
            AnimatedContent(
                targetState = controlMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "presetBadge",
            ) { mode ->
                if (mode == DashboardControlMode.PRESET) {
                    Text(
                        text = activePresetName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "User Defined",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        WeldCounter(sessionWelds = status.sessionWelds, totalWelds = status.totalWelds)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Control mode pill selector ────────────────────────────────────────────────

@Composable
private fun ControlModeSelector(
    mode: DashboardControlMode,
    onSelect: (DashboardControlMode) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DashboardControlMode.entries.forEach { entry ->
                val selected = mode == entry
                val label = when (entry) {
                    DashboardControlMode.PRESET -> "Preset"
                    DashboardControlMode.USER_DEFINED -> "User Defined"
                }
                val bgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    animationSpec = tween(200),
                    label = "modeTabBg",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "modeTabText",
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    color = bgColor,
                    shape = RoundedCornerShape(9.dp),
                    onClick = { onSelect(entry) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = textColor,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
