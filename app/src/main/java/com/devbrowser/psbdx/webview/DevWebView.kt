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
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Desktop mode user-agent string, modeled after Chrome on Linux. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

/**
 * Holds a reference to the single WebView instance backing the active tab
 * so the ViewModel / toolbar can drive navigation, reload, Eruda toggling,
 * and JS snippet execution without needing a second source of truth.
 */
class DevWebViewController {
    var webView: WebView? = null
        internal set

    var isDesktopMode: Boolean = false
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

    fun loadUrl(url: String) {
        val normalized = normalizeUrlOrSearch(url)
        webView?.loadUrl(normalized)
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

    /** Clears cache, cookies, LocalStorage/SessionStorage and form data. */
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

    companion object {
        private const val ERUDA_TOGGLE_JS =
            "try{ if (window.eruda) { window.eruda._isShow ? window.eruda.hide() : window.eruda.show(); } }catch(e){}"

        internal fun applyUserAgent(webView: WebView, desktop: Boolean) {
            webView.settings.userAgentString = if (desktop) {
                DESKTOP_USER_AGENT
            } else {
                null // restores the system default mobile UA
            }
        }

        /** Basic heuristic: if it looks like a URL, load it; otherwise search. */
        internal fun normalizeUrlOrSearch(input: String): String {
            val trimmed = input.trim()
            val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ")
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                looksLikeUrl -> "https://$trimmed"
                else -> "https://duckduckgo.com/html/?q=${trimmed.replace(" ", "+")}"
            }
        }
    }
}

/**
 * Jetpack Compose wrapper around a raw [WebView], configured for developer
 * use: JavaScript, DOM storage, mixed content, zoom controls, and automatic
 * Eruda.js injection are all wired up here via [ErudaWebClient].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevWebView(
    controller: DevWebViewController,
    startUrl: String,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String, String) -> Unit = { _, _ -> },
    onProgressChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val erudaClient = remember { ErudaWebClient(onPageStarted, onPageFinished) }

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
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = erudaClient
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                controller.webView = this
                DevWebViewController.applyUserAgent(this, controller.isDesktopMode)
                loadUrl(DevWebViewController.normalizeUrlOrSearch(startUrl))
            }
        },
        update = { /* Navigation is driven imperatively via controller */ }
    )
}
