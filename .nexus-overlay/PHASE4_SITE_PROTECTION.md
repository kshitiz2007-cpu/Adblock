# Phase 4 — Site Protection Controller / Shield Dashboard

This phase builds on Orbit's existing per-site allowlist (stored as hostnames in Prefs) and ShieldSheet, rather than creating a second adblock engine.

## Delivered overlay
- Replaces the Shield bottom-sheet layout with NEXUS emerald styling.
- Preserves host label, active/disabled state, per-page blocked count, total blocked count, and protection toggle.
- Existing MainActivity toggle persists per-domain allowlisting, updates current tab state, refreshes browser chrome, and reloads the page so the changed protection takes effect.
- NEXUS emerald colors applied to ShieldSheet active/inactive indicator and shield tint.
- Workflow copies the shield dashboard layout and drawable during CI build.

## Upstream behavior retained
- Orbit request interceptor and filter engine.
- Main-frame navigation safety.
- Existing host allowlist checks.
- Page and global blocked-request counters.
- Browser tabs, navigation, history, bookmarks, downloads, and shortcut functionality.

## Verification still required
- Run the NEXUS workflow on branch nexus.
- On device, open a real website, open Shield, confirm host and counts.
- Toggle protection off, reload, verify allowlisting; toggle on, reload, verify protection resumes.
- Confirm shield layout in light and dark modes.

This overlay does not yet add tracker classification or per-domain persisted blocked history; existing engine only provides current-page and global counts. Those require a subsequent engine telemetry change.
