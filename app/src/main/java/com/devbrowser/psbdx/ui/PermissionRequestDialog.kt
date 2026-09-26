/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.devbrowser.psbdx.data.SitePermissionType

/**
 * A live Allow/Block prompt shown the first time a site asks for camera,
 * microphone, or location access (browser-level permission requests
 * only appear once per site — see DevWebView's onPermissionRequest /
 * onGeolocationPermissionsShowPrompt for how the decision made here gets
 * remembered so the site isn't asked again).
 */
@Composable
fun PermissionRequestDialog(
    host: String,
    types: List<SitePermissionType>,
    onAllow: () -> Unit,
    onBlock: () -> Unit
) {
    val label = types.joinToString(" and ") { it.label.lowercase() }
    AlertDialog(
        onDismissRequest = onBlock,
        title = { Text(host) },
        text = { Text("This site wants to use your $label.") },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onBlock) { Text("Block") } }
    )
}
