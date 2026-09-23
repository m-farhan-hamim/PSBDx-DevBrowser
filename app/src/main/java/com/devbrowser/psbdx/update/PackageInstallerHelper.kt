/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Installs a downloaded update APK via the [PackageInstaller] Session
 * API rather than a plain `ACTION_VIEW` intent handed to the generic
 * system installer. The system still shows its own install-confirmation
 * UI — nothing here bypasses that user confirmation step — but because
 * *this app* is the one that creates and commits the session, this app
 * becomes the recorded "installer package" for the resulting update.
 *
 * That's what makes Settings > Apps > PSBDx DevBrowser > (Store section)
 * > "App details" meaningfully point back at this app after a
 * self-update, instead of at whichever generic installer UI happened to
 * run the very first install — exactly the same mechanism Play Store,
 * F-Droid, and Aurora Store use to show their own listing page for apps
 * installed through them.
 */
object PackageInstallerHelper {

    const val ACTION_INSTALL_STATUS = "com.devbrowser.psbdx.update.INSTALL_STATUS"

    fun installApk(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        runCatching { params.setAppPackageName(context.packageName) }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.use { s ->
            apkFile.inputStream().use { input ->
                s.openWrite("psbdx_update", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    s.fsync(output)
                }
            }

            val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)

            s.commit(pendingIntent.intentSender)
        }
    }
}
