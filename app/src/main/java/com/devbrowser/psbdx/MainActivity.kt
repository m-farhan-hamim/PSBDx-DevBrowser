/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devbrowser.psbdx.ui.MainScreen
import com.devbrowser.psbdx.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    // ---------------------------------------------------------------
    // Permission timing, by design, for a side-loaded app's trust and
    // safety: only permissions with an *immediate* browser-wide purpose
    // are requested up front. Everything else (camera, microphone) is
    // requested lazily, at the moment a website actually asks for it —
    // and only after the user has explicitly allowed that specific site
    // from the address bar's Site info panel. No permission dialog ever
    // appears before the user has taken an action that needs it.
    // ---------------------------------------------------------------

    /** Requested immediately at launch: precise location (for "near me" style
     *  browsing/geolocation prompts) and, on Android 13+, notifications. */
    private val requestUpfrontPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op: WebChromeClient/geolocation checks the actual OS grant at request time */ }

    /** Requested lazily: fired only when a site the user has allowed asks for
     *  camera or microphone and the OS permission isn't granted yet. */
    private var pendingRuntimePermissionCallback: ((Boolean) -> Unit)? = null
    private val requestRuntimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingRuntimePermissionCallback?.invoke(granted)
        pendingRuntimePermissionCallback = null
    }

    /** Passed down to the WebView layer; called only when a site actually requests camera/mic. */
    private fun requestRuntimePermission(permission: String, onResult: (Boolean) -> Unit) {
        pendingRuntimePermissionCallback = onResult
        requestRuntimePermissionLauncher.launch(permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val upfrontPermissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        requestUpfrontPermissions.launch(upfrontPermissions.toTypedArray())

        val app = application as DevBrowserApplication

        setContent {
            PSBDxDevBrowserTheme {
                Surface(modifier = Modifier) {
                    val viewModel: BrowserViewModel = viewModel(
                        factory = BrowserViewModel.Factory(app.database, app.settingsRepository)
                    )
                    MainScreen(
                        viewModel = viewModel,
                        requestRuntimePermission = ::requestRuntimePermission
                    )
                }
            }
        }
    }
}

/**
 * Material 3 theme with automatic light/dark following the system setting.
 * Uses dynamic color on Android 12+ where available, otherwise a fixed
 * Material 3 baseline palette — no proprietary theming libraries involved.
 */
@Composable
fun PSBDxDevBrowserTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !darkTheme ->
            dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
