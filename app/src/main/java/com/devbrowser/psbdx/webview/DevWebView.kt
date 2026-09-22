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

    fun runSnippet(code: String) {
        webView?.evaluateJavascript(code, null)
    }

    /** Enables or disables the "block REST API requests" developer toggle for the active page. */
    fun setApiBlockingEnabled(enabled: Boolean) {
        isApiBlockingEnabled = enabled
        webView?.evaluateJavascript("window.__psbdxBlockApi = $enabled;", null)
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

    /** Deletes only the cookies belonging to the currently-loaded site's origin. */
    fun deleteCookiesForCurrentSite() {
        webView?.evaluateJavascript(DELETE_SITE_COOKIES_JS, null)
        CookieManager.getInstance().flush()
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
 * Eruda.js injection, opt-in API-request blocking, and per-site
 * third-party-cookie / camera-microphone-location permission gating are
 * all wired up here via [ErudaWebClient] and the [WebChromeClient] below.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevWebView(
    controller: DevWebViewController,
    startUrl: String,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String, String) -> Unit = { _, _ -> },
    onProgressChanged: (Int) -> Unit = {},
    isApiBlockingEnabled: () -> Boolean = { false },
    isThirdPartyCookiesAllowed: (String) -> Boolean = { false },
    isPermissionAllowed: (String, SitePermissionType) -> Boolean = { _, _ -> false },
    modifier: Modifier = Modifier
) {
    val erudaClient = remember {
        ErudaWebClient(
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            isApiBlockingEnabled = isApiBlockingEnabled,
            isThirdPartyCookiesAllowed = isThirdPartyCookiesAllowed
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

                    override fun onPermissionRequest(request: PermissionRequest) {
                        val host = request.origin.host
                        val granted = if (host == null) {
                            emptyArray<String>()
                        } else {
                            request.resources.filter { resource ->
                                val type = when (resource) {
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermissionType.CAMERA
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermissionType.MICROPHONE
                                    else -> null
                                }
                                type != null && isPermissionAllowed(host, type)
                            }.toTypedArray()
                        }
                        if (granted.isNotEmpty()) {
                            request.grant(granted)
                        } else {
                            request.deny()
                        }
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String,
                        callback: GeolocationPermissions.Callback
                    ) {
                        val host = runCatching { Uri.parse(origin).host }.getOrNull()
                        val allowed = host != null && isPermissionAllowed(host, SitePermissionType.LOCATION)
                        callback.invoke(origin, allowed, false)
                    }
                }

                controller.webView = this
                DevWebViewController.applyUserAgent(this, controller.isDesktopMode)
                controller.setApiBlockingEnabled(isApiBlockingEnabled())
                loadUrl(startUrl)
            }
        },
        update = { /* Navigation is driven imperatively via controller */ }
    )
}
