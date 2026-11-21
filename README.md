# NEXUS AdBlock

An Android ad-blocker project targeting Samsung Internet. The initial scaffold includes a filter-list/rule engine and a Compose dashboard. Samsung Internet Content Blocker integration is a separate compatibility layer and is not yet wired into this scaffold; blocking inside Samsung Internet requires its supported content-blocker API and compatible rule export.


## Goals
- Host/domain rules and custom allow/block rules
- Filter-list refresh architecture
- Counters and protection dashboard
- Cosmetic blocking rules (planned)
- Samsung Internet integration (planned; verify API/library against current Samsung docs before implementing)

## Build
Open this repository in Android Studio (recent version), sync Gradle, then run the `app` module on Android 8.0+.

## Status
This is a starter project—not yet a complete Samsung Internet blocker. No claim of blocking all ads, images, or video is made until Samsung Internet integration and test coverage are implemented.

## License
MIT (see LICENSE).
