/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbrowser.psbdx.data.SearchEngineId

/** A small colored-circle monogram used in place of a trademarked search-engine logo. */
@Composable
private fun SearchEngineIcon(engine: SearchEngineId, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(Color(engine.colorHex), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(engine.letter, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SettingsDialog(
    currentEngine: SearchEngineId,
    onEngineSelected: (SearchEngineId) -> Unit,
    apiBlockingEnabled: Boolean,
    onApiBlockingToggle: (Boolean) -> Unit,
    erudaHeightPercent: Int,
    onErudaHeightChange: (Int) -> Unit,
    isCheckingForUpdate: Boolean,
    updateCheckMessage: String?,
    onCheckForUpdatesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Default search engine", fontWeight = FontWeight.Bold)
                SearchEngineId.entries.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineSelected(engine) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = engine == currentEngine, onClick = { onEngineSelected(engine) })
                        SearchEngineIcon(engine)
                        Text(engine.label, modifier = Modifier.weight(1f))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block API requests", fontWeight = FontWeight.Bold)
                        Text(
                            "Blocks fetch() and XMLHttpRequest calls made by pages, so " +
                                "REST/API calls a site makes never reach the network. " +
                                "Normal page loading, images, scripts, and styles are unaffected.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = apiBlockingEnabled, onCheckedChange = onApiBlockingToggle)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("Eruda console height", fontWeight = FontWeight.Bold)
                Text(
                    "How much of the screen the Eruda console takes up when opened: $erudaHeightPercent%.",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = erudaHeightPercent.toFloat(),
                    onValueChange = { onErudaHeightChange(it.toInt()) },
                    valueRange = 10f..90f,
                    steps = 15
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Updates", fontWeight = FontWeight.Bold)
                        Text(
                            "Checks automatically at most once a day. You can also " +
                                "check right now.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (updateCheckMessage != null) {
                            Text(
                                updateCheckMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (isCheckingForUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = onCheckForUpdatesClick) { Text("Check now") }
                    }
                }
            }
        }
    )
}
