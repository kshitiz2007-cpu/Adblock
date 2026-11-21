# Phase 3 — Functional Home & Protection Status

## Orbit functionality preserved
Orbit upstream HomeView already provides functional shortcut behavior:
- pinned shortcut list stored in Prefs
- most-visited sites queried asynchronously from BrowserDb
- tap opens the URL
- long-press opens shortcut actions (edit/remove or pin/unpin)
- Add tile launches the shortcut editor

## NEXUS integration overlay
The overlay replaces the home layout and shortcut tile presentation while keeping Orbit's existing View IDs and binding contract. MainActivity continues to set the live status line using engineStatusLine(), which reads AdblockService.state and blockedTotal. The status chip therefore reports the real engine state/count, not a decorative hard-coded number.


## Build pipeline
The nexus-orbit-baseline workflow clones upstream Orbit and overlays the NEXUS drawable/layout files before assembling the APK. Upstream Kotlin remains unchanged, preserving shortcut/database and browser functionality.


## Verify on device
- Add a shortcut; verify it appears and opens.
- Long-press a shortcut; verify edit/remove/pin controls.
- Browse ad-heavy page; confirm blocked count updates.
- Open a new tab; confirm home panel and shortcut data refresh.
- Ensure browser navigation, tabs, history and downloads remain operational.
