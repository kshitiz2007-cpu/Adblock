#!/usr/bin/env python3
"""Apply NEXUS Link Integrity Guard to Orbit's navigation client."""
from pathlib import Path

target = Path("app/src/main/java/com/orbit/browser/browser/OrbitWebViewClient.kt")
source = target.read_text()
start_marker = "    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {"
end_marker = "    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {"
start = source.find(start_marker)
end = source.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("Navigation method boundaries not found; refusing to patch")

replacement = r'''    // NEXUS Link Integrity Guard v3: warn before opening obvious image-resource URLs.
    private var nexusAllowOnceUrl: String? = null
    private val nexusRecentNavigations = ArrayDeque<String>()

    private fun nexusImageDestination(uri: Uri): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        return path.endsWith(".png") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".gif") || path.endsWith(".webp") ||
            path.endsWith(".svg") || path.endsWith(".ico") || path.endsWith(".avif") ||
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
            android.app.AlertDialog.Builder(view.context)
                .setTitle("NEXUS Link Integrity")
                .setMessage("This link points to an image-like resource, not a typical download page.\n\n$url\n\nOpen it anyway?")
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

'''
source = source[:start] + replacement + source[end:]
target.write_text(source)
print("NEXUS Link Integrity Guard v3 applied")
