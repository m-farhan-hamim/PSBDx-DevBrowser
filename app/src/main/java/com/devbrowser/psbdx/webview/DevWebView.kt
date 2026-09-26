/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.webview

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.devbrowser.psbdx.data.SitePermissionType
import java.net.URLEncoder

/**
 * Desktop mode user-agent string, modeled after Chrome on Linux.
 */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

/**
 * Mobile-mode user agent, modeled closely on a real Chrome-for-Android UA
 * string but deliberately omitting the "; wv" WebView marker token that
 * Android's stock system WebView normally inserts.
 *
 * Why: many sites run PWA/"is this installed?" detection heuristics that
 * key off that "wv" token to decide whether the page is being shown
 * inside an embedded WebView versus a real browser tab, and some of those
 * heuristics misfire by treating a "wv" UA as evidence the page is
 * already running as an installed PWA/TWA. Presenting a standard,
 * non-"wv" Chrome mobile UA — which is what this app actually behaves
 * like, since it provides its own full browser chrome (address bar, tabs,
 * menu) rather than a bare installed-app shell — avoids that misdetection.
 */
private const val CHROME_LIKE_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

/** Cookie count and total size (bytes) for a site, shown in the Site info panel. */
data class CookieSummary(val count: Int, val sizeBytes: Int)

/**
 * Holds a reference to the single WebView instance backing the active tab
 * so the ViewModel / toolbar can drive navigation, reload, Eruda toggling,
 * JS snippet execution, API-request blocking, and per-site cookie/
 * permission policy without needing a second source of truth.
 */
class DevWebViewController {
    var webView: WebView? = null
        internal set

    var isDesktopMode: Boolean = false
        private set

    /** Mirrors the user's "Block API requests" developer setting for the current page. */
    var isApiBlockingEnabled: Boolean = false
        private set

    fun goBack() {
        webView?.let { if (it.canGoBack()) it.goBack() }
    }

    fun goForward() {
        webView?.let { if (it.canGoForward()) it.goForward() }
    }

    fun reload() {
        webView?.reload()
    }

    fun stop() {
        webView?.stopLoading()
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    fun canGoForward(): Boolean = webView?.canGoForward() == true

    fun currentUrl(): String? = webView?.url

    /** Loads a URL as-is, or runs it through [searchTemplate] if it doesn't look like a URL. */
    fun loadUrl(input: String, searchTemplate: String) {
        webView?.loadUrl(normalizeUrlOrSearch(input, searchTemplate))
    }

    fun toggleDesktopMode() {
        val wv = webView ?: return
        isDesktopMode = !isDesktopMode
        applyUserAgent(wv, isDesktopMode)
        wv.reload()
    }

    fun toggleEruda() {
        webView?.evaluateJavascript(ERUDA_TOGGLE_JS, null)
    }

    /** Live-applies a new Eruda console height (10-90%) to the current page immediately. */
    fun setErudaHeightPercent(percent: Int) {
        webView?.evaluateJavascript(buildErudaHeightCssJs(percent), null)
    }

    fun runSnippet(code: String) {
        webView?.evaluateJavascript(code, null)
    }

    /** Enables or disables the "block REST API requests" developer toggle for the active page. */
    fun updateApiBlockingEnabled(enabled: Boolean) {
        isApiBlockingEnabled = enabled
        webView?.evaluateJavascript("window.__psbdxBlockApi = $enabled;", null)
    }

    /**
     * Re-applies the algorithmic-darkening setting to match the app's
     * current light/dark theme, so pages that use the
     * `prefers-color-scheme` media query see the same theme the rest of
     * the app is in. Safe to call even when the installed WebView is too
     * old to support this — it's a no-op in that case.
     */
    fun applyDarkMode(isDarkTheme: Boolean) {
        val wv = webView ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, isDarkTheme)
        }
    }

    /** Clears cache, cookies, LocalStorage/SessionStorage and form data for every site. */
    fun clearAllStorage() {
        val wv = webView ?: return
        wv.clearCache(true)
        wv.clearHistory()
        wv.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        wv.evaluateJavascript(
            "try{localStorage.clear();sessionStorage.clear();}catch(e){}",
            null
        )
    }

    /**
     * Deletes the cookies belonging to the currently-loaded site, using
     * the native [CookieManager] rather than `document.cookie` JS. This
     * matters because `document.cookie` can never see or delete
     * HttpOnly cookies (very common for session/login cookies) — those
     * are only reachable through the native cookie store, which is what
     * this does instead. Also runs the previous JS-based sweep, in case
     * a cookie was set with an unusual path this doesn't catch.
     */
    fun deleteCookiesForCurrentSite() {
        val wv = webView ?: return
        val url = wv.url ?: return
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(url)

        cookieString?.split(";")?.forEach { pair ->
            val name = pair.substringBefore("=").trim()
            if (name.isBlank()) return@forEach
            val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
            cookieManager.setCookie(url, "$expired; path=/")
            if (host != null) {
                cookieManager.setCookie(url, "$expired; path=/; domain=$host")
                cookieManager.setCookie(url, "$expired; path=/; domain=.$host")
            }
        }

        wv.evaluateJavascript(DELETE_SITE_COOKIES_JS, null)
        cookieManager.flush()
    }

    /** Cookie count and total size (bytes) for the currently-loaded site, for display in Site info. */
    fun cookieSummaryForCurrentSite(): CookieSummary {
        val url = webView?.url ?: return CookieSummary(0, 0)
        val cookieString = CookieManager.getInstance().getCookie(url)
        if (cookieString.isNullOrBlank()) return CookieSummary(0, 0)
        val entries = cookieString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        return CookieSummary(count = entries.size, sizeBytes = cookieString.toByteArray(Charsets.UTF_8).size)
    }

    companion object {
        private const val ERUDA_TOGGLE_JS =
            "try{ if (window.eruda) { window.eruda._isShow ? window.eruda.hide() : window.eruda.show(); } }catch(e){}"

        private const val DELETE_SITE_COOKIES_JS = """
            (function(){
              document.cookie.split(';').forEach(function(c){
                var eqPos = c.indexOf('=');
                var name = (eqPos > -1 ? c.substr(0, eqPos) : c).trim();
                if (!name) return;
                document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
                document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=' + location.hostname;
              });
            })();
        """

        /**
         * Installed once per page load, before Eruda. Wraps `fetch` and
         * `XMLHttpRequest` so that, while `window.__psbdxBlockApi` is true,
         * every REST-style call a page makes (fetch calls and XHR
         * send()) is silently rejected/no-opped instead of reaching the
         * network. Static resources (images, scripts, stylesheets, the
         * page itself) are untouched — only script-initiated fetch/XHR
         * calls are affected.
         */
        internal const val API_BLOCK_INIT_JS = """
            (function(){
              if (window.__psbdxApiBlockInstalled) return;
              window.__psbdxApiBlockInstalled = true;
              var origFetch = window.fetch;
              if (origFetch) {
                window.fetch = function() {
                  if (window.__psbdxBlockApi) {
                    return Promise.reject(new Error('Blocked by PSBDx DevBrowser: API request blocking is enabled'));
                  }
                  return origFetch.apply(this, arguments);
                };
              }
              var origOpen = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function() {
                this.__psbdxBlocked = !!window.__psbdxBlockApi;
                return origOpen.apply(this, arguments);
              };
              var origSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.send = function() {
                if (this.__psbdxBlocked) { return; }
                return origSend.apply(this, arguments);
              };
            })();
        """

        /**
         * Forces Eruda's own dev-tools panel to a specific viewport-height
         * percentage via an injected `!important` stylesheet, since Eruda
         * otherwise sets its own inline height (from the user dragging its
         * resize handle, persisted per-origin in that page's localStorage)
         * which would otherwise vary unpredictably from site to site.
         */
        internal fun buildErudaHeightCssJs(percent: Int): String {
            val clamped = percent.coerceIn(10, 90)
            return """
                (function(){
                  var style = document.getElementById('__psbdx_eruda_height_style');
                  if (!style) {
                    style = document.createElement('style');
                    style.id = '__psbdx_eruda_height_style';
                    document.head.appendChild(style);
                  }
                  style.textContent = '#eruda .eruda-dev-tools{height:${clamped}vh !important;}';
                })();
            """
        }

        internal fun applyUserAgent(webView: WebView, desktop: Boolean) {
            webView.settings.userAgentString = if (desktop) {
                DESKTOP_USER_AGENT
            } else {
                CHROME_LIKE_MOBILE_USER_AGENT
            }
        }

        /** Basic heuristic: if it looks like a URL, load it; otherwise search using [searchTemplate]. */
        internal fun normalizeUrlOrSearch(input: String, searchTemplate: String): String {
            val trimmed = input.trim()
            val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ")
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                looksLikeUrl -> "https://$trimmed"
                else -> searchTemplate.replace("%s", URLEncoder.encode(trimmed, "UTF-8"))
            }
        }
    }
}

/**
 * Jetpack Compose wrapper around a raw [WebView], configured for developer
 * use: JavaScript, DOM storage, mixed content, zoom controls, automatic
 * Eruda.js injection, opt-in API-request blocking, live light/dark theme
 * matching, and per-site third-party-cookie / camera-microphone-location
 * permission gating are all wired up here via [ErudaWebClient] and the
 * [WebChromeClient] below.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevWebView(
    controller: DevWebViewController,
    startUrl: String,
    isDarkTheme: Boolean,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String, String) -> Unit = { _, _ -> },
    onProgressChanged: (Int) -> Unit = {},
    isApiBlockingEnabled: () -> Boolean = { false },
    isThirdPartyCookiesAllowed: (String) -> Boolean = { false },
    isPermissionAllowed: (String, SitePermissionType) -> Boolean = { _, _ -> false },
    hasPermissionDecision: (String, SitePermissionType) -> Boolean = { _, _ -> false },
    onPermissionRequestNeeded: (
        host: String,
        types: List<SitePermissionType>,
        resolve: (Map<SitePermissionType, Boolean>) -> Unit
    ) -> Unit = { _, _, resolve -> resolve(emptyMap()) },
    requestRuntimePermission: (String, (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) },
    erudaHeightPercent: () -> Int = { 30 },
    modifier: Modifier = Modifier
) {
    val erudaClient = remember {
        ErudaWebClient(
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            isApiBlockingEnabled = isApiBlockingEnabled,
            isThirdPartyCookiesAllowed = isThirdPartyCookiesAllowed,
            erudaHeightPercent = erudaHeightPercent
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    allowContentAccess = true
                    setGeolocationEnabled(true)
                }
                CookieManager.getInstance().setAcceptCookie(true)
                // Third-party cookies are blocked by default; ErudaWebClient
                // re-applies the correct per-site value on every navigation.
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                webViewClient = erudaClient
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }

                    /**
                     * Camera/microphone are never requested from the OS at app
                     * launch. The first time a site asks for one of these and
                     * this app has no saved decision for that site yet, this
                     * surfaces an actual Allow/Block prompt to the user (via
                     * [onPermissionRequestNeeded], which the UI layer turns
                     * into a real dialog) instead of silently denying it —
                     * the user's choice is then remembered for that site, so
                     * later requests are applied automatically without asking
                     * again.
                     */
                    override fun onPermissionRequest(request: PermissionRequest) {
                        val host = request.origin.host
                        if (host == null) {
                            request.deny()
                            return
                        }

                        val requestedTypes = request.resources.mapNotNull { resource ->
                            when (resource) {
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermissionType.CAMERA
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermissionType.MICROPHONE
                                else -> null
                            }
                        }.distinct()

                        if (requestedTypes.isEmpty()) {
                            request.deny()
                            return
                        }

                        fun resolveWithDecisions(decisions: Map<SitePermissionType, Boolean>) {
                            val approvedResources = requestedTypes.mapNotNull { type ->
                                if (decisions[type] != true) return@mapNotNull null
                                when (type) {
                                    SitePermissionType.CAMERA ->
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE to android.Manifest.permission.CAMERA
                                    SitePermissionType.MICROPHONE ->
                                        PermissionRequest.RESOURCE_AUDIO_CAPTURE to android.Manifest.permission.RECORD_AUDIO
                                    SitePermissionType.LOCATION -> null
                                }
                            }
                            if (approvedResources.isEmpty()) {
                                request.deny()
                                return
                            }

                            val grantedResources = mutableListOf<String>()
                            var remaining = approvedResources.size
                            fun finish() {
                                if (grantedResources.isEmpty()) request.deny() else request.grant(grantedResources.toTypedArray())
                            }
                            approvedResources.forEach { (resource, osPermission) ->
                                val alreadyGranted = ContextCompat.checkSelfPermission(context, osPermission) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (alreadyGranted) {
                                    grantedResources.add(resource)
                                    remaining--
                                    if (remaining == 0) finish()
                                } else {
                                    requestRuntimePermission(osPermission) { granted ->
                                        if (granted) grantedResources.add(resource)
                                        remaining--
                                        if (remaining == 0) finish()
                                    }
                                }
                            }
                        }

                        if (requestedTypes.all { hasPermissionDecision(host, it) }) {
                            resolveWithDecisions(requestedTypes.associateWith { isPermissionAllowed(host, it) })
                        } else {
                            onPermissionRequestNeeded(host, requestedTypes) { decisions -> resolveWithDecisions(decisions) }
                        }
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String,
                        callback: GeolocationPermissions.Callback
                    ) {
                        val host = runCatching { Uri.parse(origin).host }.getOrNull()
                        if (host == null) {
                            callback.invoke(origin, false, false)
                            return
                        }
                        if (hasPermissionDecision(host, SitePermissionType.LOCATION)) {
                            callback.invoke(origin, isPermissionAllowed(host, SitePermissionType.LOCATION), false)
                        } else {
                            onPermissionRequestNeeded(host, listOf(SitePermissionType.LOCATION)) { decisions ->
                                callback.invoke(origin, decisions[SitePermissionType.LOCATION] == true, false)
                            }
                        }
                    }
                }

                controller.webView = this
                DevWebViewController.applyUserAgent(this, controller.isDesktopMode)
                controller.updateApiBlockingEnabled(isApiBlockingEnabled())
                controller.applyDarkMode(isDarkTheme)
                loadUrl(startUrl)
            }
        },
        update = { controller.applyDarkMode(isDarkTheme) }
    )
}
