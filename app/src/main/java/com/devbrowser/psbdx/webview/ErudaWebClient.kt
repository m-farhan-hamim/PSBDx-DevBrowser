/*
 * PSBDx DevBrowser
 * Copyright (C) 2024 PSBDx DevBrowser Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v3.0 as published
 * by the Free Software Foundation. See /LICENSE for the full text.
 */
package com.devbrowser.psbdx.webview

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * A [WebViewClient] responsible for:
 *  - injecting the locally-bundled Eruda.js developer console into every
 *    page the user navigates to, along with a default viewport meta tag
 *    (on pages that don't already declare one) and a forced console
 *    height, so Eruda's own overlay UI — including the Elements tab's
 *    built-in live editing (double-click to edit text, click to edit
 *    attributes/styles) — behaves consistently instead of getting thrown
 *    off by this app's wide-viewport/overview-mode WebView settings;
 *  - installing the opt-in REST API (fetch/XHR) request blocker;
 *  - applying the per-site third-party-cookie policy on every navigation.
 *
 * Eruda is read from `app/src/main/assets/eruda.min.js` — it is never
 * downloaded from a remote CDN at runtime, satisfying the F-Droid
 * requirement that no executable code be fetched dynamically from
 * unverified sources. The asset retains Eruda's original MIT license
 * header, as required by the MIT License's attribution clause.
 *
 * Injection happens at two points for reliability across different sites:
 *  - `onPageStarted`, via evaluateJavascript once the document exists, so
 *    the console is available even on pages that never fully "finish"
 *    (e.g. long-polling single page apps).
 *  - `onPageFinished`, as a safety net for the common case.
 */
class ErudaWebClient(
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String, String) -> Unit,
    private val isApiBlockingEnabled: () -> Boolean,
    private val isThirdPartyCookiesAllowed: (String) -> Boolean,
    private val erudaHeightPercent: () -> Int
) : WebViewClient() {

    private var erudaSource: String? = null

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
        applyThirdPartyCookiePolicy(view, url)
        injectEruda(view)
        injectApiBlocker(view)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        injectEruda(view)
        injectApiBlocker(view)
        view.evaluateJavascript("document.title") { rawTitle ->
            val title = rawTitle?.trim('"').orEmpty().ifBlank { url }
            onPageFinished(url, title)
        }
    }

    /**
     * Third-party cookies are blocked for every site by default; the user
     * can allow them for a specific host from the site-info panel (tap
     * the lock/warning icon in the address bar). Re-applied on every
     * navigation since [android.webkit.CookieManager]'s setting is not
     * itself host-scoped.
     */
    private fun applyThirdPartyCookiePolicy(view: WebView, url: String) {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, isThirdPartyCookiesAllowed(host))
    }

    /**
     * Loads `assets/eruda.min.js` once (cached in memory for the process
     * lifetime) and evaluates it in the page context, then calls
     * `eruda.init()` guarded so repeated injections on the same page are
     * harmless no-ops. Also ensures a `viewport` meta tag exists — many
     * pages this browser loads have none, which combined with this app's
     * `useWideViewPort`/`loadWithOverviewMode` settings leaves the page
     * (and Eruda's own fixed-position overlay drawn inside it) rendered
     * at an unpredictable initial scale, which is what made touch
     * targets in Eruda's Elements tab (editing text/attributes/styles)
     * feel unresponsive or misaligned — and forces Eruda's own panel to
     * the user's configured height every time it's (re)injected.
     */
    private fun injectEruda(view: WebView) {
        val source = erudaSource ?: loadErudaSource(view).also { erudaSource = it }
        if (source.isBlank()) return

        val script = buildString {
            append("(function(){")
            append(ENSURE_VIEWPORT_JS)
            append("if (window.__psbdxErudaLoaded) { return; }")
            append("window.__psbdxErudaLoaded = true;")
            append(source)
            append(
                "try{ if (window.eruda && !window.__psbdxErudaInit) {" +
                    "window.eruda.init(); window.__psbdxErudaInit = true; } }catch(e){}"
            )
            append("})();")
        }
        view.evaluateJavascript(script, null)
        view.evaluateJavascript(DevWebViewController.buildErudaHeightCssJs(erudaHeightPercent()), null)
    }

    /** Installs (once per page) and toggles the fetch/XHR blocking shim. */
    private fun injectApiBlocker(view: WebView) {
        val script = DevWebViewController.API_BLOCK_INIT_JS +
            "window.__psbdxBlockApi = ${isApiBlockingEnabled()};"
        view.evaluateJavascript(script, null)
    }

    private fun loadErudaSource(view: WebView): String {
        return try {
            view.context.assets.open("eruda.min.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private companion object {
        const val ENSURE_VIEWPORT_JS = """
            if (!document.querySelector('meta[name="viewport"]')) {
              var __psbdxViewport = document.createElement('meta');
              __psbdxViewport.name = 'viewport';
              __psbdxViewport.content = 'width=device-width, initial-scale=1';
              (document.head || document.documentElement).appendChild(__psbdxViewport);
            }
        """
    }
}
