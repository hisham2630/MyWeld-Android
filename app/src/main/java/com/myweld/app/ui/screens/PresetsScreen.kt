package com.myweld.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myweld.app.data.model.WeldParams
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.viewmodel.EditPresetState
import com.myweld.app.viewmodel.PresetsViewModel
import org.koin.androidx.compose.koinViewModel

private const val FIRST_CUSTOM = 7 // mirrors PresetsViewModel.FIRST_CUSTOM_INDEX

@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel = koinViewModel(),
) {
    val presets   by viewModel.presets.collectAsStateWithLifecycle()
    val status    by viewModel.status.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Weld Presets",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Tap to load · ✎ to edit custom presets · + to create new",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 80.dp), // space for FAB
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(presets, key = { it.index }) { preset ->
                    val isCustom = viewModel.isCustomPreset(preset.index)
                    PresetCard(
                        preset   = preset,
                        isActive = preset.index == status.activePreset,
                        isFactory = !isCustom,
                        onClick  = { viewModel.loadPreset(preset.index) },
                        onEdit   = if (isCustom) ({ viewModel.openEditDialog(preset) }) else null,
                    )
                }
            }
        }

        // FAB — "New Preset" — bottom-right
        FloatingActionButton(
            onClick = { viewModel.openCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New preset")
        }
    }

    // Edit / Create dialog
    editState?.let { state ->
        EditPresetDialog(
            state        = state,
            isCreating   = state.name.isEmpty() && state.index >= FIRST_CUSTOM,
            onDismiss    = { viewModel.dismissEditDialog() },
            onSave       = { viewModel.saveEditedPreset() },
            onNameChange = { viewModel.updateEditName(it) },
            onP1Change   = { viewModel.updateEditP1(it) },
            onTChange    = { viewModel.updateEditT(it) },
            onP2Change   = { viewModel.updateEditP2(it) },
            onP3Change   = { viewModel.updateEditP3(it) },
            onP4Change   = { viewModel.updateEditP4(it) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetCard(
    preset: WeldPreset,
    isActive: Boolean,
    isFactory: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        label = "border",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        tonalElevation = if (isActive) 4.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    isActive -> Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).padding(start = 2.dp),
                    )
                    onEdit != null -> Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit preset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onEdit() },
                    )
                    isFactory -> Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Factory preset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ParamLabel("P1", preset.p1Ms)
                ParamLabel("T", preset.tMs)
                ParamLabel("P2", preset.p2Ms)
            }

            // Show P3/P4 row only when they are active (> 0)
            if (preset.p3Ms > 0f || preset.p4Ms > 0f) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (preset.p3Ms > 0f) ParamLabel("P3", preset.p3Ms)
                    if (preset.p4Ms > 0f) ParamLabel("P4", preset.p4Ms)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditPresetDialog(
    state: EditPresetState,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onP1Change: (Float) -> Unit,
    onTChange: (Float) -> Unit,
    onP2Change: (Float) -> Unit,
    onP3Change: (Float) -> Unit,
    onP4Change: (Float) -> Unit,
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (isCreating) "New Preset" else "Edit Preset ${state.index + 1}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                // Name field
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Preset name") },
                    placeholder = { Text(if (isCreating) "My Custom Preset" else "") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor  = MaterialTheme.colorScheme.primary,
                    ),
                    supportingText = {
                        Text(
                            "${state.name.length}/${WeldPreset.NAME_MAX_LEN}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                // P1 slider
                ParamSlider(
                    label    = "P1 (Pulse 1)",
                    value    = state.p1Ms,
                    min      = WeldParams.PULSE_MIN,
                    max      = WeldParams.PULSE_MAX,
                    unit     = "ms",
                    onChange = onP1Change,
                )

                // T slider — 0 = single-pulse (disabled), active range = PAUSE_MIN..PAUSE_MAX
                ParamSlider(
                    label    = "T (Pause)",
                    value    = state.tMs,
                    min      = 0f,
                    max      = WeldParams.PAUSE_MAX,
                    unit     = "ms",
                    onChange = { raw ->
                        // Snap: below PAUSE_MIN → 0 (disabled), otherwise clamp to valid range
                        val clamped = if (raw < WeldParams.PAUSE_MIN) 0f
                                      else raw.coerceIn(WeldParams.PAUSE_MIN, WeldParams.PAUSE_MAX)
                        onTChange(clamped)
                    },
                )

                // P2 slider — 0 = disabled, active range = PULSE_MIN..PULSE_MAX
                ParamSlider(
                    label    = "P2 (Pulse 2)",
                    value    = state.p2Ms,
                    min      = 0f,
                    max      = WeldParams.PULSE_MAX,
                    unit     = "ms",
                    onChange = { raw ->
                        val clamped = if (raw < WeldParams.PULSE_MIN) 0f
                                      else raw.coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
                        onP2Change(clamped)
                    },
                )

                // P3 slider — only visible when T > 0 (multi-pulse mode)
                AnimatedVisibility(visible = state.tMs > 0f) {
                    ParamSlider(
                        label    = "P3 (Pulse 3)",
                        value    = state.p3Ms,
                        min      = 0f,
                        max      = WeldParams.PULSE_MAX,
                        unit     = "ms",
                        onChange = { raw ->
                            val clamped = if (raw < WeldParams.PULSE_MIN) 0f
                                          else raw.coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
                            onP3Change(clamped)
                        },
                    )
                }

                // P4 slider — only visible when P3 > 0
                AnimatedVisibility(visible = state.tMs > 0f && state.p3Ms > 0f) {
                    ParamSlider(
                        label    = "P4 (Pulse 4)",
                        value    = state.p4Ms,
                        min      = 0f,
                        max      = WeldParams.PULSE_MAX,
                        unit     = "ms",
                        onChange = { raw ->
                            val clamped = if (raw < WeldParams.PULSE_MIN) 0f
                                          else raw.coerceIn(WeldParams.PULSE_MIN, WeldParams.PULSE_MAX)
                            onP4Change(clamped)
                        },
                    )
                }

            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                // Require a non-blank name before saving
                enabled = state.name.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (isCreating) "Create Preset" else "Save to Welder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = "%.1f $unit".format(value),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value         = value,
            onValueChange = { onChange((it * 10).toInt() / 10f) }, // snap to 0.1 steps
            valueRange    = min..max,
            modifier      = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor        = MaterialTheme.colorScheme.primary,
                activeTrackColor  = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun ParamLabel(label: String, value: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text  = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text  = "%.1f".format(value),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
