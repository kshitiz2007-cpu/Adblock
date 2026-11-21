NEXUS Browser branding assets

This phase adds:
- Native Android vector shield ic_nexus_shield.xml
- Reusable wordmark/header layout view_nexus_brand_header.xml

Important: these assets are not yet wired into Orbit's runtime homepage or launcher manifest. Adding files alone will not change an APK. Next integration must import Orbit source, insert the header into the homepage layout, wire the shield into launcher icon resources and manifest, then build and verify the APK.