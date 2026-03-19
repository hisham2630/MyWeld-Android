package com.myweld.app.data.repository

import android.util.Log
import com.myweld.app.data.ble.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub releases for the MyWeld firmware repo.
 * Uses the public GitHub REST API (no API key needed for public repos).
 */
class GitHubReleaseChecker {

    data class FirmwareRelease(
        val tagName: String,         // e.g., "v1.0.1"
        val major: Int,
        val minor: Int,
        val patch: Int,
        val name: String,            // Release title
        val body: String,            // Changelog / description (markdown)
        val downloadUrl: String?,    // .bin asset URL (variant-matched or generic)
        val publishedAt: String,     // ISO date
        val variantSlug: String? = null,  // Which variant this binary is for (null = generic/unknown)
        val allAssets: Map<String, String> = emptyMap(),  // slug → downloadUrl for all variants
    ) {
        fun versionString() = "$major.$minor.$patch"

        fun isNewerThan(currentMajor: Int, currentMinor: Int, currentPatch: Int): Boolean {
            if (major != currentMajor) return major > currentMajor
            if (minor != currentMinor) return minor > currentMinor
            return patch > currentPatch
        }

        /** True if this release has multiple variant-specific binaries. */
        val isMultiVariant: Boolean get() = allAssets.size > 1
    }

    /** Fetch the latest release from GitHub. Returns null on error or no releases. */
    suspend fun getLatestRelease(): FirmwareRelease? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_URL/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${conn.responseCode}")
                return@withContext null
            }

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            parseRelease(org.json.JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch latest release", e)
            null
        }
    }

    /** Fetch all releases and return the latest with a .bin asset. */
    suspend fun getLatestReleaseWithBinary(): FirmwareRelease? = withContext(Dispatchers.IO) {
        try {
            val allReleases = fetchAllReleases() ?: return@withContext null
            allReleases.firstOrNull { it.downloadUrl != null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch releases", e)
            null
        }
    }

    /**
     * Fetch the latest release that has a .bin asset matching the connected device's variant.
     *
     * Matching strategy (ordered by priority):
     * 1. Exact variant slug match: `myweld-{slug}-v{ver}.bin` (e.g., myweld-lcd2004-v1.0.1.bin)
     * 2. If only one .bin exists in the release, use it (backward compat for single-variant repos)
     * 3. If multiple .bin files exist but none match the variant, return null (safety!)
     *
     * @param versionInfo The connected device's version info (provides variantSlug and hwCompatId)
     * @return A FirmwareRelease with the correct downloadUrl for this device, or null
     */
    suspend fun getLatestReleaseForVariant(versionInfo: VersionInfo): FirmwareRelease? = withContext(Dispatchers.IO) {
        try {
            val allReleases = fetchAllReleases() ?: return@withContext null
            val deviceSlug = versionInfo.variantSlug
            Log.i(TAG, "Looking for firmware matching variant: $deviceSlug (hwCompatId=0x${versionInfo.hwCompatId.toString(16)})")

            for (release in allReleases) {
                // Strategy 1: Check for exact variant match
                val variantUrl = release.allAssets[deviceSlug]
                if (variantUrl != null) {
                    Log.i(TAG, "Found variant-matched binary: $deviceSlug")
                    return@withContext release.copy(
                        downloadUrl = variantUrl,
                        variantSlug = deviceSlug,
                    )
                }

                // Strategy 2: Single .bin fallback (backward compat)
                if (release.allAssets.size == 1 && release.downloadUrl != null) {
                    Log.w(TAG, "No variant-specific binary found, using single .bin fallback")
                    return@withContext release.copy(variantSlug = null)
                }

                // Strategy 3: Multiple .bin files but none match — skip this release (SAFETY)
                if (release.allAssets.isNotEmpty()) {
                    Log.w(TAG, "Release ${release.tagName} has ${release.allAssets.size} binaries " +
                            "but none match '$deviceSlug'. Available: ${release.allAssets.keys}")
                    // Continue to next release — maybe an older one has compatible binary
                }
            }

            Log.w(TAG, "No compatible firmware found for variant '$deviceSlug'")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch releases for variant", e)
            null
        }
    }

    /** Download firmware binary from URL. Returns ByteArray or null on error. */
    suspend fun downloadFirmware(downloadUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/octet-stream")
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true

            if (conn.responseCode != 200) {
                Log.w(TAG, "Download returned ${conn.responseCode}")
                return@withContext null
            }

            val data = conn.inputStream.readBytes()
            conn.disconnect()
            Log.i(TAG, "Downloaded ${data.size} bytes")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download firmware", e)
            null
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun fetchAllReleases(): List<FirmwareRelease>? {
        val url = URL(API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        if (conn.responseCode != 200) {
            Log.w(TAG, "GitHub API returned ${conn.responseCode}")
            return null
        }

        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val releases = JSONArray(json)
        val result = mutableListOf<FirmwareRelease>()
        for (i in 0 until releases.length()) {
            parseRelease(releases.getJSONObject(i))?.let { result.add(it) }
        }
        return result
    }

    /**
     * Extract the variant slug from a firmware binary filename.
     *
     * Expected naming convention: `myweld-{slug}-v{version}.bin`
     * Examples:
     *   - `myweld-lcd2004-v1.0.1.bin` → "lcd2004"
     *   - `myweld-jc3248w535-v1.0.1.bin` → "jc3248w535"
     *   - `myweld-nextion-v1.0.1.bin` → "nextion"
     *   - `firmware.bin` → null (legacy/unknown naming)
     */
    private fun extractVariantSlug(filename: String): String? {
        val match = VARIANT_FILENAME_REGEX.find(filename)
        return match?.groupValues?.get(1)
    }

    private fun parseRelease(json: JSONObject): FirmwareRelease? {
        val tag = json.optString("tag_name", "")
        if (tag.isEmpty()) return null

        // Parse version from tag: "v1.0.1" or "1.0.1"
        val versionStr = tag.removePrefix("v").removePrefix("V")
        val parts = versionStr.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        // Collect ALL .bin assets, mapping variant slug → download URL
        val assets = json.optJSONArray("assets")
        val allBinAssets = mutableMapOf<String, String>()  // slug → url
        var firstBinUrl: String? = null
        var firstBinSlug: String? = null

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".bin")) {
                    val downloadUrl = asset.optString("browser_download_url", "").ifEmpty { null }
                        ?: continue

                    val slug = extractVariantSlug(name)
                    if (slug != null) {
                        allBinAssets[slug] = downloadUrl
                        Log.d(TAG, "  Asset: $name → variant '$slug'")
                    } else {
                        // Generic .bin without variant naming — assign key "_generic"
                        allBinAssets["_generic"] = downloadUrl
                        Log.d(TAG, "  Asset: $name → generic (no variant slug)")
                    }

                    if (firstBinUrl == null) {
                        firstBinUrl = downloadUrl
                        firstBinSlug = slug
                    }
                }
            }
        }

        return FirmwareRelease(
            tagName = tag,
            major = major,
            minor = minor,
            patch = patch,
            name = json.optString("name", tag),
            body = json.optString("body", ""),
            downloadUrl = firstBinUrl,
            publishedAt = json.optString("published_at", ""),
            variantSlug = firstBinSlug,
            allAssets = allBinAssets,
        )
    }

    companion object {
        private const val TAG = "GitHubRelease"
        private const val OWNER = "hisham2630"
        private const val REPO = "MyWeld-ESP32"
        private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases"

        /**
         * Regex to extract variant slug from firmware filename.
         * Matches: myweld-{SLUG}-v{anything}.bin
         * Group 1 = variant slug (e.g., "lcd2004", "jc3248w535", "nextion")
         */
        private val VARIANT_FILENAME_REGEX = Regex("""myweld-([a-z0-9_-]+)-v[\d.]+\.bin""")
    }
}
