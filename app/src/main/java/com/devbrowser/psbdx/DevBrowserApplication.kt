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

/**
 * Application entry point. Lazily initializes the local Room database
 * used for Bookmarks / History, and the SharedPreferences-backed
 * settings store. No analytics, telemetry, or crash reporting SDKs are
 * initialized here or anywhere else in the app.
 */
class DevBrowserApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Intentionally empty beyond lazy init above.
        // No third-party SDK initialization of any kind.
    }
}
