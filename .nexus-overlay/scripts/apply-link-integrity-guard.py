#!/usr/bin/env python3
"""Apply NEXUS Link Integrity Engine v1 to Orbit's WebView navigation client."""
from pathlib import Path

target = Path("app/src/main/java/com/orbit/browser/browser/OrbitWebViewClient.kt")
source = target.read_text()

anchor = "    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {"
if anchor not in source:
    raise SystemExit("Navigation callback anchor not found; refusing to patch")
if "NEXUS Link Integrity Engine v1" in source:
    print("Link Integrity Engine v1 already applied")
    raise SystemExit(0)

replacement = """    // NEXUS Link Integrity Engine v1: detect direct image/emoji destinations
    // commonly used as misleading download-button targets. Navigation is never
    // silently redirected; users can cancel or explicitly continue.
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
            val chain = nexusRecentNavigations.takeLast(4).joinToString("\\n\\u2192 ")
            val details = if (chain.isEmpty()) url else chain + "\\n\\u2192 " + url
            android.app.AlertDialog.Builder(context)
                .setTitle("NEXUS Link Integrity")
                .setMessage("This destination is an image/emoji resource, not a download page.\\n\\nDestination:\\n$url\\n\\nRecent navigation:\\n$details")
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
        // Non-web schemes are handed to the existing external-intent handler.
        if (scheme != null && scheme != "http" && scheme != "https" && scheme != "about") {
            return callbacks.onExternalIntent(uri)
        }
        return false
    }
"""
source = source.replace(anchor, replacement, 1)
target.write_text(source)
print("NEXUS Link Integrity Engine v1 applied")
