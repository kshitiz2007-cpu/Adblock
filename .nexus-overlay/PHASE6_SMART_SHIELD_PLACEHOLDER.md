# Phase 6 — Smart Shield compatibility and blocked-image placeholder

## Build overlay changes
- At the Orbit request interception path, blocked image requests now receive a NEXUS-branded SVG placeholder instead of a transparent 1x1 GIF.
- The placeholder is 320x90 with emerald shield branding and an accessible textual label.
- This applies to image resources that Orbit's existing adblock rules block. The interception API does not expose rule-category/reason details in this overlay, so this is not yet limited to confirmed image-ad rules; some blocked tracker pixels/images may also be replaced.
- Existing per-site protection pause/allowlist is retained as the false-positive recovery mechanism: disable protection for the current site and reload to restore compatibility. Re-enable to resume blocking.

## Known limitations / follow-up
- Video/media requests are still blocked using Orbit's normal response; no video-slot placeholder is injected because request interception cannot replace a media element's surrounding HTML safely.
- Rule-reason telemetry, per-domain analytics, and a live privacy event feed are not part of this patch.
- The placeholder's intrinsic size may affect sites whose blocked image slots have no explicit dimensions. Device/page testing is required.
- The CI patch uses fail-fast source-pattern checks; if upstream Orbit changes the target code, the build will stop rather than silently skip the patch.

## Test
Build on branch nexus. Device test blocked image ad, blocked 1x1 tracker, legitimate images, video ad slot, and per-site protection pause/re-enable. Check layout shifts.
