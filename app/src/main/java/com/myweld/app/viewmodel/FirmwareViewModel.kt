package com.myweld.app.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myweld.app.data.ble.MyWeldBleManager
import com.myweld.app.data.ble.OtaProgress
import com.myweld.app.data.ble.OtaService
import com.myweld.app.data.ble.OtaState
import com.myweld.app.data.ble.VersionInfo
import com.myweld.app.data.repository.GitHubReleaseChecker
import com.myweld.app.data.repository.GitHubReleaseChecker.FirmwareRelease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for the Firmware Update screen.
 *
 * Manages:
 *  - Current device firmware version (from BLE status)
 *  - GitHub release checking (auto-detect new versions)
 *  - File picker OTA flow
 *  - GitHub download + OTA flow
 *  - Progress and state management
 */
class FirmwareViewModel(
    private val bleManager: MyWeldBleManager,
) : ViewModel() {

    companion object {
        private const val TAG = "FirmwareVM"
    }

    private val otaService = OtaService(bleManager)
    private val releaseChecker = GitHubReleaseChecker()

    // ── State ────────────────────────────────────────────────────────────────

    val otaProgress: StateFlow<OtaProgress> = otaService.progress

    private val _latestRelease = MutableStateFlow<FirmwareRelease?>(null)
    val latestRelease: StateFlow<FirmwareRelease?> = _latestRelease.asStateFlow()

    private val _isCheckingRelease = MutableStateFlow(false)
    val isCheckingRelease: StateFlow<Boolean> = _isCheckingRelease.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _checkResultMessage = MutableStateFlow<String?>(null)
    val checkResultMessage: StateFlow<String?> = _checkResultMessage.asStateFlow()

    /** Whether the firmware supports OTA (has the OTA characteristic). */
    val isOtaSupported: Boolean get() = bleManager.isOtaSupported

    /** Decoded version info from the device (includes hwCompatId, variantSlug). */
    val versionInfo: StateFlow<VersionInfo?> = bleManager.versionInfo

    /**
     * Current device firmware version string.
     * Uses VersionInfo when available (includes patch), falls back to status.
     */
    val currentVersion: String
        get() {
            val vi = bleManager.versionInfo.value
            if (vi != null) return vi.versionString
            val s = bleManager.status.value
            return "${s.fwMajor}.${s.fwMinor}"
        }

    /** Device firmware major/minor/patch for version comparison. */
    val deviceFwMajor: Int get() = bleManager.versionInfo.value?.major ?: bleManager.status.value.fwMajor
    val deviceFwMinor: Int get() = bleManager.versionInfo.value?.minor ?: bleManager.status.value.fwMinor
    val deviceFwPatch: Int get() = bleManager.versionInfo.value?.patch ?: 0

    /** Human-readable variant name for display. */
    val deviceVariantName: String
        get() {
            val vi = bleManager.versionInfo.value ?: return "Unknown"
            return when (vi.variantSlug) {
                "jc3248w535" -> "JC3248W535 (QSPI TFT)"
                "devkit-nextion" -> "DevKit + Nextion"
                "devkit-lcd2004" -> "DevKit + LCD 2004"
                "goouuu-nextion" -> "GOOUUU CAM + Nextion"
                "goouuu-lcd2004" -> "GOOUUU CAM + LCD 2004"
                else -> "Unknown (0x${vi.hwCompatId.toString(16)})"
            }
        }

    // ── GitHub Release Checking ──────────────────────────────────────────────

    /** Check GitHub for latest firmware release — variant-aware when possible. */
    fun checkForUpdates() {
        if (_isCheckingRelease.value) return

        viewModelScope.launch {
            _isCheckingRelease.value = true
            _errorMessage.value = null
            _checkResultMessage.value = null

            try {
                val vi = bleManager.versionInfo.value

                // Use variant-aware check when we know the device's hardware
                val release = if (vi != null && vi.hwCompatId != 0L) {
                    Log.i(TAG, "Variant-aware update check: ${vi.variantSlug} (hw=0x${vi.hwCompatId.toString(16)})")
                    releaseChecker.getLatestReleaseForVariant(vi)
                } else {
                    Log.i(TAG, "Generic update check (no variant info from device)")
                    releaseChecker.getLatestReleaseWithBinary()
                }

                _latestRelease.value = release

                if (release != null) {
                    Log.i(TAG, "Latest release: ${release.versionString()} (${release.tagName})")
                    if (release.downloadUrl != null) {
                        Log.i(TAG, "Binary available: ${release.downloadUrl}")
                        val isNewer = release.isNewerThan(deviceFwMajor, deviceFwMinor, deviceFwPatch)
                        val variantNote = if (release.variantSlug != null) {
                            " [${release.variantSlug}]"
                        } else ""

                        _checkResultMessage.value = if (isNewer) {
                            "New firmware v${release.versionString()}$variantNote available!"
                        } else {
                            "Firmware is up to date (v${release.versionString()})$variantNote"
                        }
                    } else {
                        Log.w(TAG, "No .bin asset found in release")
                        _checkResultMessage.value = "Release found but no firmware binary attached"
                    }
                } else {
                    if (vi != null && vi.hwCompatId != 0L) {
                        _checkResultMessage.value = "No compatible firmware found for ${vi.variantSlug}"
                    } else {
                        _checkResultMessage.value = "No releases found on GitHub"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                _errorMessage.value = "Failed to check for updates: ${e.message}"
            } finally {
                _isCheckingRelease.value = false
            }
        }
    }

    // ── OTA from File Picker ─────────────────────────────────────────────────

    /** Start OTA from a user-selected .bin file. */
    fun startOtaFromFile(context: Context, uri: Uri) {
        if (otaProgress.value.state != OtaState.IDLE) {
            _errorMessage.value = "OTA already in progress"
            return
        }

        viewModelScope.launch {
            _errorMessage.value = null

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")

                // Get file size
                val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw Exception("Cannot read file")
                val totalSize = fileDescriptor.statSize.toInt()
                fileDescriptor.close()

                // Pass device's hwCompatId so ESP32 can validate
                val hwId = bleManager.versionInfo.value?.hwCompatId ?: 0L

                Log.i(TAG, "Starting OTA from file: $totalSize bytes (hwCompatId=0x${hwId.toString(16)})")
                otaService.startOta(
                    scope = viewModelScope,
                    inputStream = inputStream,
                    totalSize = totalSize,
                    hwCompatId = hwId,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start OTA from file", e)
                _errorMessage.value = "Failed: ${e.message}"
            }
        }
    }

    // ── OTA from GitHub Release ──────────────────────────────────────────────

    /** Download firmware from GitHub and start OTA. */
    fun startOtaFromGitHub() {
        val release = _latestRelease.value
        val downloadUrl = release?.downloadUrl

        if (downloadUrl == null) {
            _errorMessage.value = "No firmware download URL available"
            return
        }

        if (otaProgress.value.state != OtaState.IDLE) {
            _errorMessage.value = "OTA already in progress"
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            _errorMessage.value = null

            try {
                // Pass device's hwCompatId so ESP32 can do final validation
                val hwId = bleManager.versionInfo.value?.hwCompatId ?: 0L

                Log.i(TAG, "Downloading firmware from: $downloadUrl (hwCompatId=0x${hwId.toString(16)})")
                val firmwareData = releaseChecker.downloadFirmware(downloadUrl)
                    ?: throw Exception("Download failed")

                Log.i(TAG, "Downloaded ${firmwareData.size} bytes, starting OTA")
                otaService.startOta(
                    scope = viewModelScope,
                    inputStream = firmwareData.inputStream(),
                    totalSize = firmwareData.size,
                    fwMajor = release.major,
                    fwMinor = release.minor,
                    fwPatch = release.patch,
                    hwCompatId = hwId,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download and flash firmware", e)
                _errorMessage.value = "Failed: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    // ── Controls ─────────────────────────────────────────────────────────────

    /** Abort the current OTA transfer. */
    fun abortOta() {
        otaService.abort()
    }

    /** Reset OTA state to idle. */
    fun resetOta() {
        otaService.reset()
        _errorMessage.value = null
    }

    /** Clear the error message. */
    fun clearError() {
        _errorMessage.value = null
    }

    /** Clear the check result message (after Snackbar shown). */
    fun clearCheckResult() {
        _checkResultMessage.value = null
    }
}
