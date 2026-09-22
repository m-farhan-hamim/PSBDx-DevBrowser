/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** A default search engine option. Icons are drawn as plain colored monograms in the UI. */
enum class SearchEngineId(val label: String, val urlTemplate: String, val letter: String, val colorHex: Long) {
    GOOGLE("Google", "https://www.google.com/search?q=%s", "G", 0xFF4285F4),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/html/?q=%s", "D", 0xFFDE5833),
    BING("Bing", "https://www.bing.com/search?q=%s", "B", 0xFF00897B),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s", "B", 0xFFFB542B);

    companion object {
        val Default = GOOGLE
    }
}

/** Per-site permission types the browser can grant or block for a web origin. */
enum class SitePermissionType(val prefKey: String, val label: String) {
    CAMERA("camera", "Camera"),
    MICROPHONE("microphone", "Microphone"),
    LOCATION("location", "Location")
}

/**
 * Lightweight SharedPreferences-backed settings store. Everything here is
 * local to the device only — nothing is synced or transmitted anywhere.
 *
 * Defaults are deliberately conservative:
 *  - Third-party cookies are blocked for every site unless explicitly
 *    allowed for that specific host.
 *  - Camera/Microphone/Location are denied for every site unless
 *    explicitly allowed for that specific host.
 *  - API request blocking is off by default (it's an opt-in developer tool).
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSearchEngine(): SearchEngineId {
        val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngineId.Default.name)
        return runCatching { SearchEngineId.valueOf(name ?: SearchEngineId.Default.name) }
            .getOrDefault(SearchEngineId.Default)
    }

    fun setSearchEngine(engine: SearchEngineId) {
        prefs.edit { putString(KEY_SEARCH_ENGINE, engine.name) }
    }

    fun isApiBlockingEnabled(): Boolean = prefs.getBoolean(KEY_BLOCK_API_REQUESTS, false)

    fun setApiBlockingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BLOCK_API_REQUESTS, enabled) }
    }

    /** Third-party cookies are blocked by default; this is the per-site override. */
    fun isThirdPartyCookiesAllowed(host: String): Boolean =
        prefs.getBoolean(thirdPartyCookieKey(host), false)

    fun setThirdPartyCookiesAllowed(host: String, allowed: Boolean) {
        prefs.edit { putBoolean(thirdPartyCookieKey(host), allowed) }
    }

    /** Site permissions (camera/mic/location) are denied by default; this is the per-site override. */
    fun isPermissionAllowed(host: String, type: SitePermissionType): Boolean =
        prefs.getBoolean(permissionKey(host, type), false)

    fun setPermissionAllowed(host: String, type: SitePermissionType, allowed: Boolean) {
        prefs.edit { putBoolean(permissionKey(host, type), allowed) }
    }

    private fun thirdPartyCookieKey(host: String) = "third_party_cookies_$host"
    private fun permissionKey(host: String, type: SitePermissionType) = "perm_${type.prefKey}_$host"

    companion object {
        private const val PREFS_NAME = "psbdx_settings"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_BLOCK_API_REQUESTS = "block_api_requests"
    }
}
