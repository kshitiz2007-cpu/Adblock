# NEXUS Browser — Orbit-based development branch

This branch (`nexus`) is the workspace for evolving NEXUS into a standalone Android browser based on Orbit.

## Upstream
- Repository: https://github.com/egeeuzun/orbit
- Upstream branch: `master`
- Upstream project description: Android WebView browser with embedded Brave adblock-rust, uBlock Origin filter lists, and page translation.

## Direction
- Keep Orbit's browser and native ad-blocking implementation as the foundation.
- Replace Orbit branding with NEXUS branding (green shield, green/purple dark theme).
- Retain the upstream ad-block engine and avoid duplicating it with the old NEXUS filter parser.
- Adapt filter-list defaults and settings to the NEXUS product experience.
- Add a NEXUS protection dashboard, protection toggle, and Balanced / Strict / Custom presets after the upstream base is imported.
- Preserve upstream license notices and review the upstream license before redistributing or producing builds.

## Migration stages
1. **Workspace branch created** — this branch is isolated from `main` and the old Samsung Content Blocker project.
2. Import Orbit source into this branch, retaining upstream attribution and license files.
3. Confirm a clean baseline build from the imported source.
4. Apply NEXUS package/app name, icon, colors, and UI branding.
5. Wire NEXUS presets and list configuration to Orbit's existing engine.
6. Build APK and test browsing, ad blocking, filter updates, tabs, downloads, and crashes on-device.

## Current status
Only the workspace branch and this migration plan are committed so far. Orbit's source code has **not yet been imported**, and no NEXUS Browser APK has been built from this branch.
