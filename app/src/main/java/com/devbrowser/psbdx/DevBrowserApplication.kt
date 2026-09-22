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

/**
 * Application entry point. Lazily initializes the local Room database
 * used for Bookmarks / History. No analytics, telemetry, or crash
 * reporting SDKs are initialized here or anywhere else in the app.
 */
class DevBrowserApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // Intentionally empty beyond lazy DB init above.
        // No third-party SDK initialization of any kind.
    }
}
