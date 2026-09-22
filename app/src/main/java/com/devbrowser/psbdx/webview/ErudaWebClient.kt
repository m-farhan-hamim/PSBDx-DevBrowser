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
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * A [WebViewClient] responsible for injecting the locally-bundled Eruda.js
 * developer console into every page the user navigates to.
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
    private val onPageFinished: (String, String) -> Unit
) : WebViewClient() {

    private var erudaSource: String? = null

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
        injectEruda(view)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        injectEruda(view)
        view.evaluateJavascript("document.title") { rawTitle ->
            val title = rawTitle?.trim('"').orEmpty().ifBlank { url }
            onPageFinished(url, title)
        }
    }

    /**
     * Loads `assets/eruda.min.js` once (cached in memory for the process
     * lifetime) and evaluates it in the page context, then calls
     * `eruda.init()` guarded so repeated injections on the same page are
     * harmless no-ops.
     */
    private fun injectEruda(view: WebView) {
        val source = erudaSource ?: loadErudaSource(view).also { erudaSource = it }
        if (source.isBlank()) return

        val script = buildString {
            append("(function(){")
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
    }

    private fun loadErudaSource(view: WebView): String {
        return try {
            view.context.assets.open("eruda.min.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
}
