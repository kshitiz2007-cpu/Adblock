#!/usr/bin/env python3
"""Apply a conservative NEXUS link integrity guard to Orbit's navigation client."""
from pathlib import Path

target = Path("app/src/main/java/com/orbit/browser/browser/OrbitWebViewClient.kt")
source = target.read_text()
anchor = "    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {"
if anchor not in source:
    raise SystemExit("Navigation callback anchor not found; refusing to patch")
if "NEXUS Link Integrity Guard v2" in source:
    print("Link Integrity Guard v2 already applied")
    raise SystemExit(0)

replacement = r"""    // NEXUS Link Integrity Guard v2: flag direct image-resource navigations
    // that commonly masquerade as download links. Users may explicitly proceed.
    private var nexusAllowOnceUrl: String? = null
    private val nexusRecentNavigations = ArrayDeque<String>()

    private fun nexusImageDestination(uri: Uri): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        val extensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".avif")
        return extensions.any { path.endsWith(it) } ||
            path.contains("/emoji/") || path.contains("/emojis/") ||
            (host == "s.w.org" && path.contains("/images/"))
    }

    private fun nexusRecordNavigation(url: String) {
        if (nexusRecentNavigations.lastOrNull() == url) return
        nexusRecentNavigations.addLast(url)
        while (nexusRecentNavigations.size > 8) nexusRecentNavigations.removeFirst()
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val url = uri.toString()
        if (request.isForMainFrame && nexusAllowOnceUrl == url) {
            nexusAllowOnceUrl = null
            nexusRecordNavigation(url)
            return false
        }
        if (request.isForMainFrame && nexusImageDestination(uri)) {
            val chain = nexusRecentNavigations.takeLast(4).joinToString("\n→ ")
            val details = if (chain.isEmpty()) url else chain + "\n→ " + url
            android.app.AlertDialog.Builder(view.context)
                .setTitle("NEXUS Link Integrity")
                .setMessage("This destination looks like an image/emoji resource, not a download page.\n\nDestination:\n$url\n\nRecent navigation:\n$details")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open anyway") { _, _ ->
                    nexusAllowOnceUrl = url
                    view.post { view.loadUrl(url) }
                }
                .show()
            return true
        }
        if (request.isForMainFrame) nexusRecordNavigation(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https" && scheme != "about") {
            return callbacks.onExternalIntent(uri)
        }
        return false
    }
"""
source = source.replace(anchor, replacement, 1)
target.write_text(source)
print("NEXUS Link Integrity Guard v2 applied")
