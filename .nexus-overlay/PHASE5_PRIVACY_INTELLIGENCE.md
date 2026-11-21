# Phase 5 — NEXUS Privacy Intelligence

## Goals
1. Per-domain request analytics: host, category counts (ads, trackers, image, video/media, scripts, frames, other), and blocked totals.
2. A bounded real-time privacy event feed: timestamp, domain, resource category, rule/reason where available, action (blocked/allowed), and resource URL redacted to avoid query-string tracking tokens.
3. Media-ad replacement: when a request is identified as an ad and is image/video/media, replace the ad slot with a NEXUS placeholder rather than showing a broken-media icon or empty box.
4. A per-site dashboard and privacy log UI, with clear reset/export controls.
5. Persisted analytics, bounded storage, and privacy-first defaults: no external analytics; no full query strings stored; provide clear history reset.

## Implementation constraints
- Instrument the actual Orbit request interception/decision path. Do not infer a block from page content or from total counters.
- Only classify resources as ad media when the blocking decision is ad-rule based or the host/rule explicitly indicates advertising. Do not replace legitimate images/video merely because they are blocked for unrelated reasons.
- Placeholders should preserve the approximate slot dimensions and include an accessible label such as “NEXUS blocked ad”.
- Keep tracker/script/image/video categories distinct; classify media based on WebView request destination and fallback MIME/extension.
- Feed must be bounded (e.g., most recent 300 events); analytics must be bounded by domain and time (e.g., 30 days).
- Preserve Orbit's engine, allowlisting, tabs, downloads, navigation and build pipeline.

## Build and test acceptance
- CI build succeeds on branch nexus.
- Unit tests cover event classification, URL redaction, counters, bounded storage and placeholder decision logic.
- Device smoke test confirms analytics increments after a known ad-rule block; feed updates without reopening the screen.
- Test image and video ad requests: blocked slot shows NEXUS placeholder; legitimate non-ad media is not replaced.
- Verify private browsing does not persist analytics/events.
