#!/usr/bin/env python3
"""Install the NEXUS VPN profile setup screen into Orbit. Fails closed on drift."""
from pathlib import Path

main = Path("app/src/main/java/com/orbit/browser/ui/MainActivity.kt")
manifest = Path("app/src/main/AndroidManifest.xml")
overlay_activity = Path("../.nexus-overlay/app/src/main/java/com/orbit/browser/ui/VpnActivity.kt")
activity_target = Path("app/src/main/java/com/orbit/browser/ui/VpnActivity.kt")

src = main.read_text()
marker = "NEXUS VPN profile screen overlay"
if marker not in src:
    menu_anchor = "            MenuSheet.Item(\n                ID_NEW_TAB, R.drawable.ic_add, getString(R.string.new_tab)),"
    if menu_anchor not in src:
        raise SystemExit("Menu item insertion anchor not found; aborting.")
    src = src.replace(menu_anchor, "            // NEXUS VPN profile screen overlay\n            MenuSheet.Item(ID_NEXUS_VPN, R.drawable.ic_shield, \"NEXUS VPN\"),\n" + menu_anchor, 1)
    handler_anchor = "                ID_SHIELD -> showShieldDialog()"
    if handler_anchor not in src:
        raise SystemExit("Menu action anchor not found; aborting.")
    src = src.replace(handler_anchor, handler_anchor + "\n                ID_NEXUS_VPN -> startActivity(Intent(this, VpnActivity::class.java))", 1)
    const_anchor = "        const val ID_PRINT = 14"
    if const_anchor not in src:
        raise SystemExit("Constant insertion anchor not found; aborting.")
    src = src.replace(const_anchor, const_anchor + "\n        const val ID_NEXUS_VPN = 15", 1)
    main.write_text(src)

manifest_src = manifest.read_text()
activity_decl = '        <activity android:name=".ui.VpnActivity" android:exported="false" android:label="NEXUS VPN" />'
if activity_decl not in manifest_src:
    app_anchor = '    <application'
    idx = manifest_src.find(app_anchor)
    if idx < 0:
        raise SystemExit("Manifest application anchor not found.")
    # Insert activity declaration inside application block before its first activity.
    first_activity = '        <activity'
    idx = manifest_src.find(first_activity, idx)
    if idx < 0:
        raise SystemExit("Manifest activity anchor not found.")
    manifest_src = manifest_src[:idx] + activity_decl + "\n\n" + manifest_src[idx:]
    manifest.write_text(manifest_src)

if not activity_target.exists():
    if not overlay_activity.exists():
        raise SystemExit("VPN activity overlay source not found.")
    activity_target.parent.mkdir(parents=True, exist_ok=True)
    activity_target.write_text(overlay_activity.read_text())
print("NEXUS VPN profile screen overlay applied")
