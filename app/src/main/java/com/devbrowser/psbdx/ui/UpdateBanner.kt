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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbrowser.psbdx.update.UpdateInfo

/**
 * A dismissible infobar for the direct-APK self-update flow. Shows a
 * plain "Update"/"Dismiss" pair while idle, and a progress bar once the
 * user has tapped Update and a download is running.
 */
@Composable
fun UpdateBanner(
    updateInfo: UpdateInfo,
    downloadProgress: Int?,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Version ${updateInfo.version} is available", fontWeight = FontWeight.Bold)
                if (downloadProgress != null) {
                    Text(
                        "Downloading… $downloadProgress%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                } else {
                    Text(
                        "Downloads directly from GitHub Releases; you'll confirm the install.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (downloadProgress == null) {
                TextButton(onClick = onUpdateClick) { Text("Update") }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss update notice")
                }
            }
        }
    }
}
