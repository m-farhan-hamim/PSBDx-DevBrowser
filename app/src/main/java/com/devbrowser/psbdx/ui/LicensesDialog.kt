/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dedicated "Licenses" section, separate from the short About summary,
 * satisfying both:
 *  - the GPLv3 requirement that the app itself display appropriate legal
 *    notices, and
 *  - the MIT License's requirement that Eruda's copyright/permission
 *    notice be reproduced wherever the software is redistributed.
 */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Licenses") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PSBDx DevBrowser", fontWeight = FontWeight.Bold)
                Text(
                    "Copyright (C) 2024 PSBDx DevBrowser Contributors\n\n" +
                        "This program is free software: you can redistribute it " +
                        "and/or modify it under the terms of the GNU General " +
                        "Public License as published by the Free Software " +
                        "Foundation, either version 3 of the License, or (at " +
                        "your option) any later version.\n\n" +
                        "This program is distributed in the hope that it will " +
                        "be useful, but WITHOUT ANY WARRANTY; without even the " +
                        "implied warranty of MERCHANTABILITY or FITNESS FOR A " +
                        "PARTICULAR PURPOSE. See the GNU General Public " +
                        "License for more details.\n\n" +
                        "The full license text is bundled with this app's " +
                        "source code as /LICENSE, and at " +
                        "https://www.gnu.org/licenses/gpl-3.0.html"
                )

                HorizontalDivider()

                Text("Eruda", fontWeight = FontWeight.Bold)
                Text(
                    "https://github.com/liriliri/eruda\n" +
                        "Copyright (c) 2016-present liriliri\n\n" +
                        "Permission is hereby granted, free of charge, to any " +
                        "person obtaining a copy of this software and " +
                        "associated documentation files (the \"Software\"), to " +
                        "deal in the Software without restriction, including " +
                        "without limitation the rights to use, copy, modify, " +
                        "merge, publish, distribute, sublicense, and/or sell " +
                        "copies of the Software, subject to the following " +
                        "conditions:\n\n" +
                        "The above copyright notice and this permission " +
                        "notice shall be included in all copies or " +
                        "substantial portions of the Software.\n\n" +
                        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY " +
                        "OF ANY KIND, EXPRESS OR IMPLIED. See the MIT License " +
                        "for full details."
                )
            }
        }
    )
}
