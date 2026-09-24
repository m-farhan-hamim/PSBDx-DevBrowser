/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devbrowser.psbdx.data.AppDatabase
import com.devbrowser.psbdx.data.BookmarkEntity
import com.devbrowser.psbdx.data.HistoryEntity
import com.devbrowser.psbdx.data.JsSnippetEntity
import com.devbrowser.psbdx.data.SearchEngineId
import com.devbrowser.psbdx.data.SettingsRepository
import com.devbrowser.psbdx.data.SitePermissionType
import com.devbrowser.psbdx.update.UpdateInfo
import com.devbrowser.psbdx.update.UpdateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** A single browser tab's minimal state. The WebView itself lives in the UI layer. */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "https://www.google.com",
    val isDesktopMode: Boolean = false,
    /** A captured snapshot of the page for the tab-grid preview card; null until first captured. */
    val thumbnail: Bitmap? = null
)

sealed interface DevPanel {
    data object None : DevPanel
    data object SourceViewer : DevPanel
    data object SnippetRunner : DevPanel
    data object NetworkInspector : DevPanel
    data object ElementPicker : DevPanel
}

class BrowserViewModel(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    init {
        checkForUpdates()
    }

    // ---------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------
    var tabs by mutableStateOf(listOf(BrowserTab()))
        private set

    var activeTabId by mutableStateOf(tabs.first().id)
        private set

    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    fun openNewTab(url: String = "https://www.google.com") {
        val tab = BrowserTab(url = url)
        tabs = tabs + tab
        activeTabId = tab.id
    }

    fun closeTab(tabId: String) {
        val remaining = tabs.filterNot { it.id == tabId }
        tabs = remaining.ifEmpty { listOf(BrowserTab()) }
        if (activeTabId == tabId) {
            activeTabId = tabs.first().id
        }
    }

    fun selectTab(tabId: String) {
        activeTabId = tabId
    }

    fun closeAllTabs() {
        tabs = listOf(BrowserTab())
        activeTabId = tabs.first().id
    }

    fun updateTabThumbnail(tabId: String, bitmap: Bitmap) {
        tabs = tabs.map { tab -> if (tab.id == tabId) tab.copy(thumbnail = bitmap) else tab }
    }

    fun updateActiveTab(url: String? = null, title: String? = null, desktopMode: Boolean? = null) {
        tabs = tabs.map { tab ->
            if (tab.id != activeTabId) return@map tab
            tab.copy(
                url = url ?: tab.url,
                title = title ?: tab.title,
                isDesktopMode = desktopMode ?: tab.isDesktopMode
            )
        }
    }

    // ---------------------------------------------------------------
    // Dev tools panel state (opened from the overflow menu now, not a
    // permanent bottom bar, so the WebView keeps the full viewport)
    // ---------------------------------------------------------------
    var activePanel by mutableStateOf<DevPanel>(DevPanel.None)
        private set

    fun showPanel(panel: DevPanel) {
        activePanel = if (activePanel == panel) DevPanel.None else panel
    }

    // ---------------------------------------------------------------
    // Dialogs
    // ---------------------------------------------------------------
    var showAboutDialog by mutableStateOf(false)
        private set

    fun setAboutDialogVisible(visible: Boolean) {
        showAboutDialog = visible
    }

    var showLicensesDialog by mutableStateOf(false)
        private set

    fun setLicensesDialogVisible(visible: Boolean) {
        showLicensesDialog = visible
    }

    var showSettingsDialog by mutableStateOf(false)
        private set

    fun setSettingsDialogVisible(visible: Boolean) {
        showSettingsDialog = visible
    }

    var showSiteInfoDialog by mutableStateOf(false)
        private set

    fun setSiteInfoDialogVisible(visible: Boolean) {
        showSiteInfoDialog = visible
    }

    var lastLoadedSourceHtml by mutableStateOf("")
        private set

    fun updateLastLoadedSourceHtml(html: String) {
        lastLoadedSourceHtml = html
    }

    // ---------------------------------------------------------------
    // Settings: default search engine + "block API requests" dev toggle
    // ---------------------------------------------------------------
    var searchEngine by mutableStateOf(settingsRepository.getSearchEngine())
        private set

    fun updateSearchEngine(engine: SearchEngineId) {
        searchEngine = engine
        settingsRepository.setSearchEngine(engine)
    }

    var apiBlockingEnabled by mutableStateOf(settingsRepository.isApiBlockingEnabled())
        private set

    fun updateApiBlockingEnabled(enabled: Boolean) {
        apiBlockingEnabled = enabled
        settingsRepository.setApiBlockingEnabled(enabled)
    }

    // ---------------------------------------------------------------
    // Per-site privacy & permissions (surfaced from the address-bar
    // lock/warning icon's site-info panel)
    // ---------------------------------------------------------------
    fun isThirdPartyCookiesAllowed(host: String): Boolean =
        settingsRepository.isThirdPartyCookiesAllowed(host)

    fun setThirdPartyCookiesAllowed(host: String, allowed: Boolean) {
        settingsRepository.setThirdPartyCookiesAllowed(host, allowed)
    }

    fun isPermissionAllowed(host: String, type: SitePermissionType): Boolean =
        settingsRepository.isPermissionAllowed(host, type)

    fun setPermissionAllowed(host: String, type: SitePermissionType, allowed: Boolean) {
        settingsRepository.setPermissionAllowed(host, type, allowed)
    }

    // ---------------------------------------------------------------
    // Self-update (GitHub Releases direct-APK flow — "github" flavor
    // only; compiled out entirely for "fdroid", see UpdateManager)
    // ---------------------------------------------------------------
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    var updateBannerDismissed by mutableStateOf(false)
        private set

    /** Null when not downloading; 0-100 while a download is in progress. */
    var updateDownloadProgress by mutableStateOf<Int?>(null)
        private set

    var downloadedUpdateApk by mutableStateOf<File?>(null)
        private set

    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            val info = updateManager.checkForUpdate(force)
            if (info != null) {
                updateInfo = info
                updateBannerDismissed = false
            }
        }
    }

    fun dismissUpdateBanner() {
        updateBannerDismissed = true
    }

    fun downloadUpdate() {
        val info = updateInfo ?: return
        viewModelScope.launch {
            updateDownloadProgress = 0
            val file = updateManager.downloadApk(info.downloadUrl) { percent -> updateDownloadProgress = percent }
            updateDownloadProgress = null
            downloadedUpdateApk = file
        }
    }

    fun clearDownloadedUpdateApk() {
        downloadedUpdateApk = null
    }

    fun canRequestPackageInstalls(): Boolean = updateManager.canRequestPackageInstalls()

    fun buildUnknownSourcesSettingsIntent(): android.content.Intent =
        updateManager.buildUnknownSourcesSettingsIntent()

    fun installDownloadedApk(apkFile: File) {
        updateManager.installApk(apkFile)
    }

    // ---------------------------------------------------------------
    // Persistence-backed streams
    // ---------------------------------------------------------------
    val bookmarks: StateFlow<List<BookmarkEntity>> =
        database.bookmarkDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> =
        database.historyDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snippets: StateFlow<List<JsSnippetEntity>> =
        database.jsSnippetDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recordVisit(url: String, title: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            database.historyDao().insert(HistoryEntity(title = title.ifBlank { url }, url = url))
        }
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            if (database.bookmarkDao().isBookmarked(url)) {
                database.bookmarkDao().deleteByUrl(url)
            } else {
                database.bookmarkDao().insert(BookmarkEntity(title = title.ifBlank { url }, url = url))
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { database.bookmarkDao().delete(bookmark) }
    }

    fun clearHistory() {
        viewModelScope.launch { database.historyDao().clearAll() }
    }

    fun saveSnippet(name: String, code: String) {
        if (name.isBlank() || code.isBlank()) return
        viewModelScope.launch {
            database.jsSnippetDao().insert(JsSnippetEntity(name = name, code = code))
        }
    }

    fun deleteSnippet(snippet: JsSnippetEntity) {
        viewModelScope.launch { database.jsSnippetDao().delete(snippet) }
    }

    class Factory(
        private val database: AppDatabase,
        private val settingsRepository: SettingsRepository,
        private val updateManager: UpdateManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BrowserViewModel::class.java))
            return BrowserViewModel(database, settingsRepository, updateManager) as T
        }
    }
}
