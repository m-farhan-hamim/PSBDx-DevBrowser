/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.devbrowser.psbdx.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub, ready to offer to the user. */
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseUrl: String?
)

/**
 * The outcome of a [UpdateManager.checkForUpdate] call. Deliberately NOT
 * collapsed into a nullable [UpdateInfo] — a failed network call and a
 * genuinely up-to-date app both need to be reported honestly rather than
 * both silently looking like "no update", which is exactly the bug that
 * previously made a failed/blocked GitHub API call show "You're on the
 * latest version" even when a newer release actually existed.
 */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    /** Not due for a check yet (only possible when [force] is false). */
    data object NotDue : UpdateCheckResult
    /** Update checks are disabled for this build, or this install came via F-Droid. */
    data object Disabled : UpdateCheckResult
    /** The GitHub API call, parsing, or asset lookup failed. [reason] is safe to show the user. */
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * Handles the whole direct-APK self-update flow: checking GitHub
 * Releases, comparing versions, downloading the APK, and installing it.
 *
 * This is a single universal APK — there is no separate F-Droid build.
 * [BuildConfig.IS_UPDATE_CHECK_ENABLED] is a manual off-switch (always
 * `true` today), and on top of that, this refuses to check for or
 * download updates at runtime if it detects that this particular
 * install actually came through the F-Droid client — F-Droid requires
 * apps it distributes to never self-update outside of its own
 * mechanism.
 */
class UpdateManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Compile-time flag AND runtime installer check, both must allow it. */
    fun isUpdateCheckAllowed(): Boolean {
        if (!BuildConfig.IS_UPDATE_CHECK_ENABLED) return false
        return !isInstalledViaFDroid()
    }

    private fun isInstalledViaFDroid(): Boolean {
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
        return installer == "org.fdroid.fdroid" || installer == "org.fdroid.fdroid.privileged"
    }

    private fun isCheckDue(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    private fun markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /**
     * Fetches the latest GitHub release and compares it against the
     * running app. Every failure mode gets its own [UpdateCheckResult]
     * case with a specific, loggable reason — nothing is silently
     * treated as "you're up to date" anymore.
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!isUpdateCheckAllowed()) return@withContext UpdateCheckResult.Disabled
        if (!force && !isCheckDue()) return@withContext UpdateCheckResult.NotDue

        val fetchResult = runCatching {
            val connection = URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // GitHub's REST API rejects requests with no User-Agent header
            // (403 Forbidden) — this is a common, easy-to-miss gotcha.
            connection.setRequestProperty("User-Agent", "PSBDx-DevBrowser-UpdateChecker")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                throw HttpStatusException(code, errorBody)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        }
        markChecked()

        val body = fetchResult.getOrElse { error ->
            val reason = when (error) {
                is HttpStatusException -> describeHttpFailure(error)
                else -> "network error (${error.message ?: error.javaClass.simpleName})"
            }
            Log.w(TAG, "Update check failed: $reason", error)
            return@withContext UpdateCheckResult.Failed(reason)
        }

        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: run {
                Log.w(TAG, "Update check failed: couldn't parse GitHub response as JSON")
                return@withContext UpdateCheckResult.Failed("couldn't parse GitHub's response")
            }

        val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
        if (tagName.isBlank()) {
            Log.w(TAG, "Update check failed: release has no tag_name")
            return@withContext UpdateCheckResult.Failed("the latest release has no version tag")
        }

        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    if (apkUrl != null) break
                }
            }
        }
        val downloadUrl = apkUrl ?: run {
            Log.w(TAG, "Update check failed: release $tagName has no .apk asset")
            return@withContext UpdateCheckResult.Failed("release v$tagName has no .apk file attached")
        }

        if (!isNewerVersion(tagName, BuildConfig.VERSION_NAME)) {
            return@withContext UpdateCheckResult.UpToDate
        }

        UpdateCheckResult.Available(
            UpdateInfo(
                version = tagName,
                downloadUrl = downloadUrl,
                releaseUrl = json.optString("html_url").takeIf { it.isNotBlank() }
            )
        )
    }

    private fun describeHttpFailure(error: HttpStatusException): String = when (error.code) {
        403, 429 -> if (error.body.contains("rate limit", ignoreCase = true)) {
            "GitHub API rate limit hit — wait a bit and try again"
        } else {
            "GitHub API refused the request (HTTP ${error.code})"
        }
        404 -> "no releases found for this repository"
        in 500..599 -> "GitHub is having issues (HTTP ${error.code}) — try again later"
        else -> "GitHub API returned HTTP ${error.code}"
    }

    private class HttpStatusException(val code: Int, val body: String) :
        Exception("HTTP $code: $body")

    /** SemVer-style comparison: true if [remote] is a newer version than [current]. */
    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val r = parseSemVer(remote)
        val c = parseSemVer(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parseSemVer(version: String): List<Int> =
        version.substringBefore('-').split('.').mapNotNull { it.trim().toIntOrNull() }

    /**
     * Downloads [url] into this app's private cache directory, reporting
     * 0-100 progress via [onProgress]. Returns the downloaded file, or
     * null on any failure (never throws) — the caller should treat a
     * null result as "download failed, let the user retry".
     */
    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "PSBDx-DevBrowser-UpdateChecker")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val totalSize = connection.contentLength
            val outputDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(outputDir, "psbdx-update.apk")

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            onProgress((downloaded * 100 / totalSize).coerceIn(0, 100))
                        }
                    }
                }
            }
            outputFile
        }.onFailure { Log.w(TAG, "Update download failed", it) }.getOrNull()
    }

    /**
     * Installs a downloaded update APK via [PackageInstallerHelper] so
     * this app becomes its own recorded installer (see that class's doc
     * comment for why that matters). Falls back to the simpler
     * FileProvider `ACTION_VIEW` install intent if the Session API call
     * throws for any reason.
     */
    fun installApk(apkFile: File) {
        runCatching {
            PackageInstallerHelper.installApk(context, apkFile)
        }.onFailure {
            Log.w(TAG, "PackageInstaller session failed, falling back to ACTION_VIEW", it)
            context.startActivity(buildInstallIntent(apkFile))
        }
    }

    /** True if the OS currently lets this app prompt the user to install an APK. */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // granted at install time on pre-8.0, no runtime gate exists
        }
    }

    /** Deep-links to the "Install unknown apps" system settings screen for this app specifically. */
    fun buildUnknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Builds the install intent for a downloaded APK, sharing it via FileProvider as a content:// URI. */
    fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        private const val TAG = "PSBDxUpdateManager"
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/m-farhan-hamim/PSBDx-DevBrowser/releases/latest"
        private const val PREFS_NAME = "psbdx_update"
        private const val KEY_LAST_CHECK = "last_update_check_at"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
