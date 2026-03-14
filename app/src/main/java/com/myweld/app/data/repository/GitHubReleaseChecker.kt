package com.myweld.app.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub releases for the MyWeld firmware repo.
 * Uses the public GitHub REST API (no API key needed for public repos).
 */
class GitHubReleaseChecker {

    companion object {
        private const val TAG = "GitHubRelease"
        private const val OWNER = "hisham2630"
        private const val REPO = "MyWeld-ESP32"
        private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases"
    }

    data class FirmwareRelease(
        val tagName: String,         // e.g., "v1.0.1"
        val major: Int,
        val minor: Int,
        val patch: Int,
        val name: String,            // Release title
        val body: String,            // Changelog / description (markdown)
        val downloadUrl: String?,    // .bin asset URL
        val publishedAt: String,     // ISO date
    ) {
        fun versionString() = "$major.$minor.$patch"

        fun isNewerThan(currentMajor: Int, currentMinor: Int, currentPatch: Int): Boolean {
            if (major != currentMajor) return major > currentMajor
            if (minor != currentMinor) return minor > currentMinor
            return patch > currentPatch
        }
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
            val url = URL(API_URL)
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

            val releases = JSONArray(json)
            for (i in 0 until releases.length()) {
                val release = parseRelease(releases.getJSONObject(i))
                if (release?.downloadUrl != null) return@withContext release
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch releases", e)
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

    private fun parseRelease(json: org.json.JSONObject): FirmwareRelease? {
        val tag = json.optString("tag_name", "")
        if (tag.isEmpty()) return null

        // Parse version from tag: "v1.0.1" or "1.0.1"
        val versionStr = tag.removePrefix("v").removePrefix("V")
        val parts = versionStr.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        // Find .bin asset
        val assets = json.optJSONArray("assets")
        var binUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".bin")) {
                    binUrl = asset.optString("browser_download_url", "").ifEmpty { null }
                    break
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
            downloadUrl = binUrl,
            publishedAt = json.optString("published_at", ""),
        )
    }
}
