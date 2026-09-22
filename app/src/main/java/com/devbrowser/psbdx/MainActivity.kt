/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DevBrowserApplication

        setContent {
            PSBDxDevBrowserTheme {
                Surface(modifier = Modifier) {
                    val viewModel: BrowserViewModel = viewModel(
                        factory = BrowserViewModel.Factory(app.database)
                    )
                    MainScreen(viewModel = viewModel)
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
