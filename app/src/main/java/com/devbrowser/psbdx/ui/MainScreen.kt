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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devbrowser.psbdx.viewmodel.BrowserViewModel
import com.devbrowser.psbdx.viewmodel.DevPanel
import com.devbrowser.psbdx.webview.DevWebView
import com.devbrowser.psbdx.webview.DevWebViewController

/**
 * Root composable: address bar, WebView, multi-tab switcher, and the
 * developer-tools bottom bar (Eruda toggle, source viewer, snippet
 * runner, network inspector, element picker, storage cleaner).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BrowserViewModel,
    controller: DevWebViewController = remember { DevWebViewController() }
) {
    var addressBarText by remember { mutableStateOf(viewModel.activeTab.url) }
    var loadProgress by remember { mutableStateOf(0) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showStorageClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = addressBarText,
                            onValueChange = { addressBarText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search or type a URL") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                controller.loadUrl(addressBarText)
                            })
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { controller.goBack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { controller.goForward() }) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = { controller.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                        }
                        IconButton(onClick = {
                            viewModel.toggleBookmark(viewModel.activeTab.url, viewModel.activeTab.title)
                        }) {
                            Icon(Icons.Filled.BookmarkBorder, contentDescription = "Bookmark")
                        }
                        IconButton(onClick = { showTabSwitcher = true }) {
                            Text("${viewModel.tabs.size}", modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                )
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        bottomBar = {
            DevToolsBottomBar(
                activePanel = viewModel.activePanel,
                onPanelSelected = { viewModel.showPanel(it) },
                onEruda = { controller.toggleEruda() },
                onClearStorage = { showStorageClearConfirm = true },
                onAbout = { viewModel.setAboutDialogVisible(true) },
                onDesktopToggle = {
                    controller.toggleDesktopMode()
                    viewModel.updateActiveTab(desktopMode = controller.isDesktopMode)
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            DevWebView(
                controller = controller,
                startUrl = viewModel.activeTab.url,
                onPageStarted = { url ->
                    addressBarText = url
                },
                onPageFinished = { url, title ->
                    addressBarText = url
                    viewModel.updateActiveTab(url = url, title = title)
                    viewModel.recordVisit(url, title)
                    controller.webView?.evaluateJavascript(
                        "document.documentElement.outerHTML"
                    ) { html -> viewModel.setLastLoadedSourceHtml(html ?: "") }
                },
                onProgressChanged = { loadProgress = it },
                modifier = Modifier.fillMaxSize()
            )

            when (viewModel.activePanel) {
                DevPanel.SourceViewer -> SourceViewerPanel(
                    html = viewModel.lastLoadedSourceHtml,
                    onClose = { viewModel.showPanel(DevPanel.SourceViewer) }
                )
                DevPanel.SnippetRunner -> SnippetRunnerPanel(
                    snippets = viewModel.snippets.collectAsState().value,
                    onRun = { code -> controller.runSnippet(code) },
                    onSave = { name, code -> viewModel.saveSnippet(name, code) },
                    onDelete = { viewModel.deleteSnippet(it) },
                    onClose = { viewModel.showPanel(DevPanel.SnippetRunner) }
                )
                DevPanel.NetworkInspector -> NetworkInspectorPanel(
                    onClose = { viewModel.showPanel(DevPanel.NetworkInspector) }
                )
                DevPanel.ElementPicker -> {
                    controller.webView?.evaluateJavascript(ELEMENT_PICKER_JS, null)
                    viewModel.showPanel(DevPanel.None)
                }
                DevPanel.None -> Unit
            }

            if (showTabSwitcher) {
                TabSwitcherOverlay(
                    viewModel = viewModel,
                    onDismiss = { showTabSwitcher = false }
                )
            }
        }
    }

    if (viewModel.showAboutDialog) {
        AboutDialog(onDismiss = { viewModel.setAboutDialogVisible(false) })
    }

    if (showStorageClearConfirm) {
        AlertDialog(
            onDismissRequest = { showStorageClearConfirm = false },
            title = { Text("Clear browsing data?") },
            text = { Text("This clears cache, cookies, local storage, and form data for all tabs.") },
            confirmButton = {
                TextButton(onClick = {
                    controller.clearAllStorage()
                    viewModel.clearHistory()
                    showStorageClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showStorageClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DevToolsBottomBar(
    activePanel: DevPanel,
    onPanelSelected: (DevPanel) -> Unit,
    onEruda: () -> Unit,
    onClearStorage: () -> Unit,
    onAbout: () -> Unit,
    onDesktopToggle: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onEruda) {
                Icon(Icons.Filled.Terminal, contentDescription = "Toggle Eruda console")
            }
            IconButton(onClick = { onPanelSelected(DevPanel.SourceViewer) }) {
                Icon(Icons.Filled.Code, contentDescription = "View source")
            }
            IconButton(onClick = { onPanelSelected(DevPanel.SnippetRunner) }) {
                Icon(Icons.Filled.Add, contentDescription = "JS snippet runner")
            }
            IconButton(onClick = { onPanelSelected(DevPanel.NetworkInspector) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Network inspector")
            }
            IconButton(onClick = { onPanelSelected(DevPanel.ElementPicker) }) {
                Icon(Icons.Filled.TouchApp, contentDescription = "Element picker")
            }
            IconButton(onClick = onDesktopToggle) {
                Icon(Icons.Filled.DesktopWindows, contentDescription = "Toggle desktop mode")
            }
            IconButton(onClick = onClearStorage) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear storage")
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Filled.Info, contentDescription = "About")
            }
        }
    }
}

@Composable
private fun SourceViewerPanel(html: String, onClose: () -> Unit) {
    PanelScaffold(title = "Source Viewer", onClose = onClose) {
        Text(
            text = html.ifBlank { "No page loaded yet." },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun SnippetRunnerPanel(
    snippets: List<com.devbrowser.psbdx.data.JsSnippetEntity>,
    onRun: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (com.devbrowser.psbdx.data.JsSnippetEntity) -> Unit,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("console.log('Hello from PSBDx DevBrowser');") }

    PanelScaffold(title = "JS Snippet Runner", onClose = onClose) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Snippet name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("JavaScript") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onRun(code) }) { Text("Run") }
                TextButton(onClick = { onSave(name, code) }) { Text("Save") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Saved snippets", fontFamily = FontFamily.Default)
            LazyColumn {
                items(snippets) { snippet ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snippet.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRun(snippet.code) }) {
                            Icon(Icons.Filled.Terminal, contentDescription = "Run ${snippet.name}")
                        }
                        IconButton(onClick = { onDelete(snippet) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Delete ${snippet.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkInspectorPanel(onClose: () -> Unit) {
    PanelScaffold(title = "Network Inspector", onClose = onClose) {
        Text(
            "Live XHR / Fetch requests are captured by the injected Eruda " +
                "console's Network panel — tap the terminal icon to open it " +
                "for full headers, payloads, and response previews.",
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun PanelScaffold(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        tonalElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            content()
        }
    }
}

@Composable
private fun TabSwitcherOverlay(viewModel: BrowserViewModel, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tabs", style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { viewModel.openNewTab() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New tab")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close tab switcher")
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.tabs) { tab ->
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .height(120.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp).fillMaxSize()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    tab.title,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.closeTab(tab.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close ${tab.title}")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                tab.url,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                viewModel.selectTab(tab.id)
                                onDismiss()
                            }) {
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Injected on demand: highlights the tapped DOM element and reports its
 * tag/id/class in an on-page overlay, giving a lightweight "element
 * picker" without needing a native touch-forwarding bridge.
 */
private const val ELEMENT_PICKER_JS = """
(function() {
  if (window.__psbdxPickerActive) return;
  window.__psbdxPickerActive = true;
  var overlay = document.createElement('div');
  overlay.style.position = 'fixed';
  overlay.style.pointerEvents = 'none';
  overlay.style.border = '2px solid #7c4dff';
  overlay.style.background = 'rgba(124,77,255,0.15)';
  overlay.style.zIndex = 2147483647;
  document.body.appendChild(overlay);
  document.addEventListener('click', function handler(e) {
    var el = e.target;
    var rect = el.getBoundingClientRect();
    overlay.style.left = rect.left + 'px';
    overlay.style.top = rect.top + 'px';
    overlay.style.width = rect.width + 'px';
    overlay.style.height = rect.height + 'px';
    console.log('[PSBDx Element Picker]', el.tagName, el.id, el.className);
    e.preventDefault();
    e.stopPropagation();
  }, true);
})();
"""
