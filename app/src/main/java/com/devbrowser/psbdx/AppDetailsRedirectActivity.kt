/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Trampoline activity for Android's `SHOW_APP_INFO` hand-off — the
 * "Store" section's "App details" link in Settings > Apps > this app.
 *
 * That link only ever points here for a user who received their current
 * copy of this app through this app's own self-updater (see
 * [com.devbrowser.psbdx.update.PackageInstallerHelper]), which makes
 * this app the recorded installer of that update — exactly the same
 * mechanism Play Store, F-Droid, and Aurora Store use to show their own
 * listing page for apps they installed. If the app was installed some
 * other way (a store, a file manager, ADB), that store's/tool's own
 * "App details" handler is what Settings shows instead — not this one.
 *
 * This does NOT touch or replace the system's own App Info screen:
 * Permissions, Battery usage, Notifications, Storage usage, and the
 * Uninstall control all remain exactly as Android provides them, one
 * screen up, completely unaffected by this. This only supplies the
 * optional "more info about where this came from" destination.
 */
class AppDetailsRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(APP_DETAILS_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        private const val APP_DETAILS_URL = "https://docs.psbdx.com/dev-browser-app-details"
    }
}
