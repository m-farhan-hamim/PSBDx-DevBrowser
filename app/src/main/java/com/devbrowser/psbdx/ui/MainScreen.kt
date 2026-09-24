/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devbrowser.psbdx.data.SitePermissionType
import com.devbrowser.psbdx.viewmodel.BrowserTab
import com.devbrowser.psbdx.viewmodel.BrowserViewModel
import com.devbrowser.psbdx.viewmodel.DevPanel
import com.devbrowser.psbdx.webview.DevWebView
import com.devbrowser.psbdx.webview.DevWebViewController

/**
 * Root composable, laid out Chrome-style: a single top toolbar holding a
 * security icon, the address bar, the tab-count button, and a three-dot
 * overflow menu carrying every developer tool and setting. There is
 * deliberately no bottom bar — on a small mobile viewport, every extra
 * row of chrome is a row the web page doesn't get.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BrowserViewModel,
    controller: DevWebViewController = remember { DevWebViewController() },
    requestRuntimePermission: (String, (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) }
) {
    var addressBarText by remember { mutableStateOf(viewModel.activeTab.url) }
    var pageTitle by remember { mutableStateOf(viewModel.activeTab.title) }
    var isAddressFocused by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableStateOf(0) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showStorageClearConfirm by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(viewModel.activeTab.url) }
    var isBookmarked by remember { mutableStateOf(false) }

    val bookmarks by viewModel.bookmarks.collectAsState()
    val isLoading = loadProgress in 1..99
    val isHttps = currentUrl.startsWith("https://", ignoreCase = true)
    val currentHost = remember(currentUrl) { runCatching { Uri.parse(currentUrl).host }.getOrNull().orEmpty() }
    val context = LocalContext.current

    // Once a tapped "Update" download finishes, hand the APK straight to
    // the system Package Installer via a PackageInstaller session — the
    // user still sees and confirms that system install prompt themselves.
    LaunchedEffect(viewModel.downloadedUpdateApk) {
        viewModel.downloadedUpdateApk?.let { file ->
            viewModel.installDownloadedApk(file)
            viewModel.clearDownloadedUpdateApk()
        }
    }

    // The app shares a single WebView across every tab, so switching the
    // *active* tab (from the grid switcher, or opening a new tab) needs
    // to explicitly navigate that shared WebView to the newly-selected
    // tab's URL. Skips the very first composition, since DevWebView's
    // own factory already loads the initial tab's URL once on its own.
    var isFirstTabLoad by remember { mutableStateOf(true) }
    LaunchedEffect(viewModel.activeTabId) {
        if (isFirstTabLoad) {
            isFirstTabLoad = false
        } else {
            controller.loadUrl(viewModel.activeTab.url, viewModel.searchEngine.urlTemplate)
        }
    }

    // Shown text: the page title once loaded (Chrome-style), or the URL
    // itself while loading / with no title yet. Tapping the field always
    // switches it to the editable URL, per the trailing focus check below.
    val addressDisplayValue = if (isAddressFocused) addressBarText else pageTitle.ifBlank { addressBarText }

    LaunchedEffect(currentUrl, bookmarks) {
        isBookmarked = bookmarks.any { it.url == currentUrl }
    }

    // System/gesture back navigates the WebView history first, and only
    // falls through to the default activity-finish behavior once there's
    // nowhere left to go back to.
    BackHandler(enabled = canGoBack) { controller.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.setSiteInfoDialogVisible(true) }) {
                                Icon(
                                    if (isHttps) Icons.Filled.Lock else Icons.Filled.Warning,
                                    contentDescription = if (isHttps) "Secure connection" else "Not secure",
                                    tint = if (isHttps) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                            OutlinedTextField(
                                value = addressDisplayValue,
                                onValueChange = { addressBarText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isAddressFocused = it.isFocused },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = addressBarFontSize(addressDisplayValue)
                                ),
                                placeholder = {
                                    Text("Search or type a URL", style = MaterialTheme.typography.bodyMedium)
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (isLoading) controller.stop() else controller.reload()
                                    }) {
                                        Icon(
                                            if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                                            contentDescription = if (isLoading) "Stop" else "Reload"
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    controller.loadUrl(addressBarText, viewModel.searchEngine.urlTemplate)
                                })
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            controller.webView?.let { wv ->
                                if (wv.width > 0 && wv.height > 0) {
                                    val bitmap = runCatching {
                                        Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888).also {
                                            wv.draw(Canvas(it))
                                        }
                                    }.getOrNull()
                                    bitmap?.let { viewModel.updateTabThumbnail(viewModel.activeTabId, it) }
                                }
                            }
                            showTabSwitcher = true
                        }) {
                            Text("${viewModel.tabs.size}")
                        }
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        BrowserOverflowMenu(
                            expanded = showOverflowMenu,
                            onDismiss = { showOverflowMenu = false },
                            isBookmarked = isBookmarked,
                            isDesktopMode = controller.isDesktopMode,
                            onNewTab = { viewModel.openNewTab() },
                            onToggleBookmark = { viewModel.toggleBookmark(currentUrl, viewModel.activeTab.title) },
                            onBack = { controller.goBack() },
                            onForward = { controller.goForward() },
                            onReload = { controller.reload() },
                            onEruda = { controller.toggleEruda() },
                            onSourceViewer = { viewModel.showPanel(DevPanel.SourceViewer) },
                            onSnippetRunner = { viewModel.showPanel(DevPanel.SnippetRunner) },
                            onNetworkInspector = { viewModel.showPanel(DevPanel.NetworkInspector) },
                            onElementPicker = { viewModel.showPanel(DevPanel.ElementPicker) },
                            onDesktopToggle = {
                                controller.toggleDesktopMode()
                                viewModel.updateActiveTab(desktopMode = controller.isDesktopMode)
                            },
                            onClearStorage = { showStorageClearConfirm = true },
                            onSettings = { viewModel.setSettingsDialogVisible(true) },
                            onAbout = { viewModel.setAboutDialogVisible(true) },
                            onLicenses = { viewModel.setLicensesDialogVisible(true) },
                            onHelpDocs = { viewModel.openNewTab(HELP_DOCS_URL) }
                        )
                    }
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val updateInfo = viewModel.updateInfo
                if (updateInfo != null && !viewModel.updateBannerDismissed) {
                    UpdateBanner(
                        updateInfo = updateInfo,
                        downloadProgress = viewModel.updateDownloadProgress,
                        onUpdateClick = {
                            if (viewModel.canRequestPackageInstalls()) {
                                viewModel.downloadUpdate()
                            } else {
                                context.startActivity(viewModel.buildUnknownSourcesSettingsIntent())
                            }
                        },
                        onDismiss = { viewModel.dismissUpdateBanner() }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            DevWebView(
                controller = controller,
                startUrl = viewModel.activeTab.url,
                onPageStarted = { url ->
                    addressBarText = url
                    currentUrl = url
                    pageTitle = ""
                    canGoBack = controller.canGoBack()
                },
                onPageFinished = { url, title ->
                    addressBarText = url
                    currentUrl = url
                    pageTitle = title
                    canGoBack = controller.canGoBack()
                    viewModel.updateActiveTab(url = url, title = title)
                    viewModel.recordVisit(url, title)
                    controller.webView?.evaluateJavascript(
                        "document.documentElement.outerHTML"
                    ) { html -> viewModel.updateLastLoadedSourceHtml(html ?: "") }
                },
                onProgressChanged = { loadProgress = it },
                isApiBlockingEnabled = { viewModel.apiBlockingEnabled },
                isThirdPartyCookiesAllowed = { host -> viewModel.isThirdPartyCookiesAllowed(host) },
                isPermissionAllowed = { host, type -> viewModel.isPermissionAllowed(host, type) },
                requestRuntimePermission = requestRuntimePermission,
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
        AboutDialog(
            onDismiss = { viewModel.setAboutDialogVisible(false) },
            onViewLicenses = {
                viewModel.setAboutDialogVisible(false)
                viewModel.setLicensesDialogVisible(true)
            }
        )
    }

    if (viewModel.showLicensesDialog) {
        LicensesDialog(onDismiss = { viewModel.setLicensesDialogVisible(false) })
    }

    if (viewModel.showSettingsDialog) {
        SettingsDialog(
            currentEngine = viewModel.searchEngine,
            onEngineSelected = { viewModel.updateSearchEngine(it) },
            apiBlockingEnabled = viewModel.apiBlockingEnabled,
            onApiBlockingToggle = {
                viewModel.updateApiBlockingEnabled(it)
                controller.updateApiBlockingEnabled(it)
            },
            onDismiss = { viewModel.setSettingsDialogVisible(false) }
        )
    }

    if (viewModel.showSiteInfoDialog) {
        val permissionStates = SitePermissionType.entries.map { type ->
            type to viewModel.isPermissionAllowed(currentHost, type)
        }
        SiteInfoDialog(
            host = currentHost,
            isHttps = isHttps,
            thirdPartyCookiesAllowed = viewModel.isThirdPartyCookiesAllowed(currentHost),
            onThirdPartyCookiesToggle = { allowed ->
                viewModel.setThirdPartyCookiesAllowed(currentHost, allowed)
                controller.reload()
            },
            permissionStates = permissionStates,
            onPermissionToggle = { type, allowed -> viewModel.setPermissionAllowed(currentHost, type, allowed) },
            onDeleteCookies = { controller.deleteCookiesForCurrentSite() },
            onDismiss = { viewModel.setSiteInfoDialogVisible(false) }
        )
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

/**
 * The three-dot overflow menu, opened from the toolbar next to the tab
 * count button. Every developer tool and browser setting that used to
 * live in a permanent bottom bar now lives here instead, so the page
 * keeps the full viewport height.
 */
@Composable
private fun BrowserOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isBookmarked: Boolean,
    isDesktopMode: Boolean,
    onNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onEruda: () -> Unit,
    onSourceViewer: () -> Unit,
    onSnippetRunner: () -> Unit,
    onNetworkInspector: () -> Unit,
    onElementPicker: () -> Unit,
    onDesktopToggle: () -> Unit,
    onClearStorage: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onLicenses: () -> Unit,
    onHelpDocs: () -> Unit
) {
    fun wrap(action: () -> Unit): () -> Unit = { action(); onDismiss() }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("New tab") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = wrap(onNewTab))
        DropdownMenuItem(
            text = { Text(if (isBookmarked) "Remove bookmark" else "Add bookmark") },
            leadingIcon = { Icon(if (isBookmarked) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd, null) },
            onClick = wrap(onToggleBookmark)
        )
        androidx.compose.material3.HorizontalDivider()
        DropdownMenuItem(text = { Text("Back") }, onClick = wrap(onBack))
        DropdownMenuItem(text = { Text("Forward") }, onClick = wrap(onForward))
        DropdownMenuItem(text = { Text("Reload") }, onClick = wrap(onReload))
        androidx.compose.material3.HorizontalDivider()
        DropdownMenuItem(text = { Text("Eruda console") }, onClick = wrap(onEruda))
        DropdownMenuItem(text = { Text("View page source") }, onClick = wrap(onSourceViewer))
        DropdownMenuItem(text = { Text("JS snippet runner") }, onClick = wrap(onSnippetRunner))
        DropdownMenuItem(text = { Text("Network inspector") }, onClick = wrap(onNetworkInspector))
        DropdownMenuItem(text = { Text("Element picker") }, onClick = wrap(onElementPicker))
        DropdownMenuItem(
            text = { Text(if (isDesktopMode) "Switch to mobile site" else "Desktop site") },
            onClick = wrap(onDesktopToggle)
        )
        androidx.compose.material3.HorizontalDivider()
        DropdownMenuItem(text = { Text("Clear browsing data") }, onClick = wrap(onClearStorage))
        DropdownMenuItem(text = { Text("Settings") }, onClick = wrap(onSettings))
        DropdownMenuItem(text = { Text("About") }, onClick = wrap(onAbout))
        DropdownMenuItem(text = { Text("Licenses") }, onClick = wrap(onLicenses))
        DropdownMenuItem(text = { Text("Help & Docs") }, onClick = wrap(onHelpDocs))
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
                            Icon(Icons.Filled.Refresh, contentDescription = "Run ${snippet.name}")
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
                "console's Network panel — open Eruda from the menu for full " +
                "headers, payloads, and response previews.",
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
    var searchQuery by remember { mutableStateOf("") }
    var isGridMode by remember { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val filteredTabs = remember(viewModel.tabs, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.tabs
        } else {
            viewModel.tabs.filter {
                it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: new tab, tab count + layout toggle, overflow menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = {
                        viewModel.openNewTab()
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "New tab")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${viewModel.tabs.size}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp)
                        )
                        IconButton(onClick = { isGridMode = !isGridMode }) {
                            Icon(
                                if (isGridMode) Icons.Filled.GridView else Icons.Filled.ViewList,
                                contentDescription = "Toggle tab layout"
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Tab options")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Close all tabs") },
                            onClick = {
                                viewModel.closeAllTabs()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Close switcher") },
                            onClick = {
                                showOverflowMenu = false
                                onDismiss()
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search your tabs") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isGridMode) 2 else 1),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredTabs, key = { it.id }) { tab ->
                    TabGridCard(
                        tab = tab,
                        onSelect = {
                            viewModel.selectTab(tab.id)
                            onDismiss()
                        },
                        onClose = { viewModel.closeTab(tab.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabGridCard(tab: BrowserTab, onSelect: () -> Unit, onClose: () -> Unit) {
    val host = remember(tab.url) { runCatching { Uri.parse(tab.url).host }.getOrNull().orEmpty() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabFavicon(host)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    tab.title.ifBlank { host.ifBlank { "New Tab" } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close ${tab.title}",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val thumbnail = tab.thumbnail
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        host.ifBlank { "No preview yet" },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TabFavicon(host: String) {
    val letter = host.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = remember(host) { faviconColorFor(host) }
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val FAVICON_PALETTE = listOf(
    Color(0xFF4285F4), Color(0xFFDE5833), Color(0xFF00897B),
    Color(0xFFFB542B), Color(0xFF7C4DFF), Color(0xFFEF6C00)
)

private fun faviconColorFor(host: String): Color {
    if (host.isBlank()) return FAVICON_PALETTE.first()
    val idx = host.hashCode().let { if (it < 0) -it else it } % FAVICON_PALETTE.size
    return FAVICON_PALETTE[idx]
}

/** Opened as a normal new tab from the overflow menu's "Help & Docs" item — never used to
 *  hijack any system UI surface (see the app's README for the App details / installer notes). */
private const val HELP_DOCS_URL = "https://docs.psbdx.com/dev-browser-app-details"


private fun addressBarFontSize(text: String): androidx.compose.ui.unit.TextUnit {
    val length = text.length
    return when {
        length <= 25 -> 15.sp
        length <= 40 -> 14.sp
        length <= 60 -> 13.sp
        length <= 90 -> 12.sp
        else -> 11.sp
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
