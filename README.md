# NEXUS AdBlock

Android filter provider for Samsung Internet, using Adblock Plus-compatible filter syntax.

## Implemented in this scaffold
- Exported Samsung Internet ContentProvider with read-only `openFile()` filter stream.
- Samsung content-blocker permission and API_1.0 manifest metadata.
- Filter refresh button in the Compose dashboard.
- Download sources: EasyList, EasyPrivacy, and AdGuard Base filter.
- Filter lists are combined into app-private `nexus_filters.txt`; successful updates replace the local file, while all-source failure preserves the last file.
- Starter rules are used before list downloads succeed.
- Samsung content-blocker update broadcast is sent after successful refresh.

## Important status
This is an implementation step, not a verified release. I have not run an Android Gradle build or tested on a Samsung device in this environment. Samsung Internet integration must be verified using its developer testing mode and then submitted to Samsung for validation. The provider's filter file contract and the current Samsung registration process should be checked during device validation.

## Build
Open in Android Studio, sync Gradle, and run the app. On first run, use **Update filter lists** while online. The update can be large and is performed on an IO coroutine.

## Samsung Internet testing
Samsung's developer guide describes a developer-only content blocker testing mode. For development, on your own test device, follow the current Samsung developer instructions. Do not instruct regular users to enable developer mode.

## Filter sources
- EasyList: https://easylist.to/easylist/easylist.txt
- EasyPrivacy: https://easylist.to/easylist/easyprivacy.txt
- AdGuard Base: https://filters.adtidy.org/extension/ublock/filters/2.txt

Filter lists are maintained by their respective authors. Review each list's licensing and attribution requirements before distributing a bundled copy. NEXUS currently downloads lists at runtime rather than bundling them.

## Limits
Content blockers cannot promise to remove every ad. First-party ads, video-stream ads, site-specific anti-adblock, or unsupported filter syntax can evade filtering or break page functionality. The protection toggle in this prototype is dashboard state only; it does not yet enable/disable Samsung Internet's content blocker itself.

## License
The app scaffold is MIT-licensed. Filter lists remain under their own terms.