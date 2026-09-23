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
 * Handles the whole direct-APK self-update flow for the "github"
 * distribution flavor: checking GitHub Releases, comparing versions,
 * downloading the APK, and building the install intent.
 *
 * Deliberately does nothing at all for the "fdroid" flavor
 * ([BuildConfig.IS_UPDATE_CHECK_ENABLED] is compiled to `false` there),
 * and additionally refuses to run even in a "github"-flavor build if it
 * detects at runtime that this particular install actually came through
 * the F-Droid client — F-Droid requires apps it distributes to never
 * self-update outside of F-Droid's own mechanism.
 */
class UpdateManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Compile-time flavor flag AND runtime installer check, both must allow it. */
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
     * Fetches the latest GitHub release and returns update info if it's
     * newer than the running app. Returns null if: update checks are
     * disabled for this build/install, a check isn't due yet (unless
     * [force]), the release couldn't be parsed, it has no .apk asset, or
     * it isn't actually newer. Never throws — network/parsing failures
     * are swallowed and treated the same as "no update available".
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!isUpdateCheckAllowed()) return@withContext null
        if (!force && !isCheckDue()) return@withContext null

        val body = runCatching {
            val connection = URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()

        markChecked()
        if (body == null) return@withContext null

        val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
        if (tagName.isBlank()) return@withContext null

        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                if (apkUrl != null) break
            }
        }
        val downloadUrl = apkUrl ?: return@withContext null

        if (!isNewerVersion(tagName, BuildConfig.VERSION_NAME)) return@withContext null

        UpdateInfo(
            version = tagName,
            downloadUrl = downloadUrl,
            releaseUrl = json.optString("html_url").takeIf { it.isNotBlank() }
        )
    }

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
        }.getOrNull()
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
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/m-farhan-hamim/PSBDx-DevBrowser/releases/latest"
        private const val PREFS_NAME = "psbdx_update"
        private const val KEY_LAST_CHECK = "last_update_check_at"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
