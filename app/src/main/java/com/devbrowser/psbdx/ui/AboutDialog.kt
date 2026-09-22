/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement

/**
 * Short "About" summary. Full license text (GPLv3 for the app, MIT for
 * the bundled Eruda console) lives in its own "Licenses" dialog instead
 * — see [LicensesDialog] — reached from the same overflow menu.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, onViewLicenses: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onViewLicenses) { Text("Licenses") }
        },
        title = { Text("About PSBDx DevBrowser") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "PSBDx DevBrowser is a free and open-source developer " +
                        "browser that injects a full web console, DOM " +
                        "inspector, and network logger into any site."
                )
                Text("Version 1.0.0")
            }
        }
    )
}
