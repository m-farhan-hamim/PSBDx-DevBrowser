/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx

import android.app.Application
import com.devbrowser.psbdx.data.AppDatabase
import com.devbrowser.psbdx.data.SettingsRepository
import com.devbrowser.psbdx.update.UpdateManager

/**
 * Application entry point. Lazily initializes the local Room database
 * used for Bookmarks / History, the SharedPreferences-backed settings
 * store, and the self-update manager (a no-op for the "fdroid" build
 * flavor). No analytics, telemetry, or crash reporting SDKs are
 * initialized here or anywhere else in the app.
 */
class DevBrowserApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val updateManager: UpdateManager by lazy { UpdateManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Intentionally empty beyond lazy init above.
        // No third-party SDK initialization of any kind.
    }
}
