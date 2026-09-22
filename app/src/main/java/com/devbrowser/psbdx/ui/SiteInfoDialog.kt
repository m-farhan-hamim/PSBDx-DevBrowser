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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbrowser.psbdx.data.SitePermissionType

@Composable
fun SiteInfoDialog(
    host: String,
    isHttps: Boolean,
    thirdPartyCookiesAllowed: Boolean,
    onThirdPartyCookiesToggle: (Boolean) -> Unit,
    permissionStates: List<Pair<SitePermissionType, Boolean>>,
    onPermissionToggle: (SitePermissionType, Boolean) -> Unit,
    onDeleteCookies: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(host.ifBlank { "Site info" }) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isHttps) Icons.Filled.Lock else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isHttps) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(if (isHttps) "Connection is secure (HTTPS)" else "Connection is not secure (HTTP)")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("Cookies", fontWeight = FontWeight.Bold)
                TextButton(onClick = onDeleteCookies, contentPadding = PaddingValues(0.dp)) {
                    Text("Delete cookies for this site")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow third-party cookies")
                        Text(
                            "Blocked for every site by default. Enable only for this site if it breaks without them.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = thirdPartyCookiesAllowed, onCheckedChange = onThirdPartyCookiesToggle)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("Permissions", fontWeight = FontWeight.Bold)
                Text(
                    "Denied for every site by default.",
                    style = MaterialTheme.typography.bodySmall
                )
                permissionStates.forEach { (type, allowed) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(type.label, modifier = Modifier.weight(1f))
                        Switch(checked = allowed, onCheckedChange = { onPermissionToggle(type, it) })
                    }
                }
            }
        }
    )
}
