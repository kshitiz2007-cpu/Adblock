#!/usr/bin/env python3
"""Apply the NEXUS Link Integrity Guard to the Orbit WebView client."""
from pathlib import Path

target = Path("app/src/main/java/com/orbit/browser/browser/OrbitWebViewClient.kt")
source = target.read_text()

anchor = "    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {"
if anchor not in source:
    raise SystemExit("Navigation callback anchor not found; refusing to patch")

if "NEXUS Link Integrity Guard" in source:
    print("Link Integrity Guard already applied")
    raise SystemExit(0)

replacement = """    // NEXUS Link Integrity Guard: ask before opening a top-level URL that looks
    // like a raw image/emoji asset. This protects against download buttons that
    // unexpectedly land on image assets without silently breaking navigation.
    private fun isRawImageDestination(uri: Uri): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val host = uri.host.orEmpty().lowercase()
        val imageExt = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".avif")
        if (imageExt.any { path.endsWith(it) }) return true
        if (path.contains("/emoji/") || path.contains("/emojis/")) return true
        if (host == "s.w.org" && path.contains("/images/")) return true
        return false
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (request.isForMainFrame && isRawImageDestination(uri)) {
            android.app.AlertDialog.Builder(context)
                .setTitle("NEXUS Link Integrity")
                .setMessage("This link opens a raw image or emoji resource, not a typical download page. Destination: " + uri.toString())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open anyway") { _, _ -> view.post { view.loadUrl(uri.toString()) } }
                .show()
            return true
        }
"""
source = source.replace(anchor, replacement, 1)
target.write_text(source)
print("Link Integrity Guard applied")
