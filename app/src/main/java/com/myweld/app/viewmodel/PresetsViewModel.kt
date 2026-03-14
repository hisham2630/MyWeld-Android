package com.myweld.app.viewmodel

import androidx.lifecycle.ViewModel
import com.myweld.app.data.model.WeldPreset
import com.myweld.app.data.repository.WelderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimum custom preset index — presets 0–6 are factory read-only. */
private const val FIRST_CUSTOM_INDEX = 7

data class EditPresetState(
    val index: Int,
    val name: String,
    val p1Ms: Float,
    val tMs: Float,
    val p2Ms: Float,
    val p3Ms: Float,
    val p4Ms: Float,
)

class PresetsViewModel(
    private val repository: WelderRepository,
) : ViewModel() {

    val status = repository.status

    /**
     * Live preset list — full WeldPreset objects from firmware via BLE.
     * No local fallback: firmware is the single source of truth.
     */
    val presets: StateFlow<List<WeldPreset>> = repository.presets

    // Edit dialog state — null = dialog closed
    private val _editState = MutableStateFlow<EditPresetState?>(null)
    val editState: StateFlow<EditPresetState?> = _editState.asStateFlow()

    fun loadPreset(index: Int) {
        if (index in 0 until WeldPreset.MAX_PRESETS) {
            repository.loadPreset(index)
        }
    }

    /**
     * Open the edit dialog pre-filled with current preset values.
     * Only allowed for custom presets (index >= FIRST_CUSTOM_INDEX).
     */
    fun openEditDialog(preset: WeldPreset) {
        if (preset.index < FIRST_CUSTOM_INDEX) return
        _editState.value = EditPresetState(
            index    = preset.index,
            name     = preset.name,
            p1Ms     = preset.p1Ms,
            tMs      = preset.tMs,
            p2Ms     = preset.p2Ms,
            p3Ms     = preset.p3Ms,
            p4Ms     = preset.p4Ms,
        )
    }

    /**
     * Open the edit dialog to CREATE a new custom preset.
     * Finds the next available custom slot (prefers slots still on the default name).
     * Pre-fills with the current live device parameters so the user captures
     * exactly what the welder is currently set to.
     */
    fun openCreateDialog() {
        val currentPresets = presets.value
        val liveStatus   = status.value

        // Prefer a slot whose name still matches the default "Custom N" pattern
        val defaultNames = (FIRST_CUSTOM_INDEX until WeldPreset.MAX_PRESETS)
            .map { "Custom ${it - FIRST_CUSTOM_INDEX + 1}" }
        val targetIndex = currentPresets
            .filter { it.index >= FIRST_CUSTOM_INDEX }
            .firstOrNull { it.name in defaultNames || it.name.isBlank() }
            ?.index
            ?: FIRST_CUSTOM_INDEX   // fall back to slot 7 if all are customised

        _editState.value = EditPresetState(
            index    = targetIndex,
            name     = "",
            p1Ms     = liveStatus.p1Ms,
            tMs      = liveStatus.tMs,
            p2Ms     = liveStatus.p2Ms,
            p3Ms     = liveStatus.p3Ms,
            p4Ms     = liveStatus.p4Ms,
        )
    }

    fun dismissEditDialog() {
        _editState.value = null
    }

    /** Switch the target slot while in the create-preset dialog. */
    fun updateEditSlot(index: Int) {
        if (index < FIRST_CUSTOM_INDEX || index >= WeldPreset.MAX_PRESETS) return
        _editState.value = _editState.value?.copy(index = index)
    }

    fun updateEditName(name: String) {
        _editState.value = _editState.value?.copy(name = name.take(WeldPreset.NAME_MAX_LEN))
    }

    fun updateEditP1(value: Float) {
        _editState.value = _editState.value?.copy(p1Ms = value)
    }

    fun updateEditT(value: Float) {
        _editState.value = _editState.value?.copy(tMs = value)
    }

    fun updateEditP2(value: Float) {
        _editState.value = _editState.value?.copy(p2Ms = value)
    }

    fun updateEditP3(value: Float) {
        _editState.value = _editState.value?.copy(p3Ms = value)
    }

    fun updateEditP4(value: Float) {
        _editState.value = _editState.value?.copy(p4Ms = value)
    }

    /** Save the preset to firmware — the live presets flow will sync back. */
    fun saveEditedPreset() {
        val state = _editState.value ?: return
        val trimmedName = state.name.trim().ifEmpty { "Custom ${state.index - FIRST_CUSTOM_INDEX + 1}" }

        repository.savePreset(
            index    = state.index,
            name     = trimmedName,
            p1Ms     = state.p1Ms,
            tMs      = state.tMs,
            p2Ms     = state.p2Ms,
            p3Ms     = state.p3Ms,
            p4Ms     = state.p4Ms,
        )

        _editState.value = null
    }

    fun isCustomPreset(index: Int) = index >= FIRST_CUSTOM_INDEX
}
