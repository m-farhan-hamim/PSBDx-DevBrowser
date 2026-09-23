/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives the result of a [PackageInstaller] session committed by
 * [PackageInstallerHelper]. `STATUS_PENDING_USER_ACTION` is the normal,
 * expected outcome for a non-privileged app like this one — it means
 * the system needs to show its own "Install this update?" confirmation
 * screen, which this receiver launches exactly as the OS instructs.
 * Nothing is installed without that system-shown confirmation.
 *
 * Registered with `android:exported="false"` in the manifest — safe
 * because only the OS ever fires the exact [android.app.PendingIntent]
 * this app created and handed to `session.commit()`; no other app can
 * trigger this receiver by guessing its action string.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Installed successfully; nothing further to do here.
            }
            else -> {
                // Failed or declined by the user — silently ignore. The
                // update banner reappears on the next 24h check, and the
                // user can just tap "Update" again to retry.
            }
        }
    }
}
