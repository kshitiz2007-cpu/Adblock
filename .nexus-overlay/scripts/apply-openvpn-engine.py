#!/usr/bin/env python3
"""Install the pinned GPL OpenVPN Android engine and wire it into Orbit."""
from pathlib import Path
import re
import shutil

SOURCE = Path("../openvpn-engine-source/vpnLib")
TARGET = Path("nexus-openvpn")
EXPECTED = ("arm64-v8a", "armeabi-v7a")
REQUIRED_LIBS = ("libopenvpn.so", "libovpnexec.so", "libovpnutil.so")

if not SOURCE.is_dir():
    raise SystemExit("Pinned OpenVPN vpnLib source was not checked out.")
for abi in EXPECTED:
    for lib in REQUIRED_LIBS:
        if not (SOURCE / "src/main/jniLibs" / abi / lib).is_file():
            raise SystemExit(f"Missing OpenVPN native engine binary: {abi}/{lib}")

if TARGET.exists():
    shutil.rmtree(TARGET)
shutil.copytree(SOURCE, TARGET)
(TARGET / "build.gradle").unlink(missing_ok=True)
(TARGET / "build.gradle.kts").write_text("""plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "de.blinkt.openvpn"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
        targetSdk = 34
        consumerProguardFiles("proguard-rules.pro")
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
""")

settings = Path("settings.gradle.kts")
s = settings.read_text()
if 'include(":nexus-openvpn")' not in s:
    if 'include(":app")' not in s:
        raise SystemExit("Orbit settings include anchor not found.")
    settings.write_text(s.replace('include(":app")', 'include(":app")\ninclude(":nexus-openvpn")', 1))

catalog = Path("gradle/libs.versions.toml")
toml = catalog.read_text()
if "android-library =" not in toml:
    if "[plugins]" not in toml:
        toml += "\n[plugins]\n"
    catalog.write_text(toml + '\nandroid-library = { id = "com.android.library", version.ref = "agp" }\n')

root_gradle = Path("build.gradle.kts")
root = root_gradle.read_text()
if "libs.plugins.android.library" not in root:
    anchor = "    alias(libs.plugins.android.application) apply false"
    if anchor not in root:
        raise SystemExit("Root Gradle plugin anchor not found.")
    root_gradle.write_text(root.replace(anchor, anchor + '\n    alias(libs.plugins.android.library) apply false', 1))

app_gradle = Path("app/build.gradle.kts")
app = app_gradle.read_text()
if 'implementation(project(":nexus-openvpn"))' not in app:
    if "dependencies {" not in app:
        raise SystemExit("App dependencies block not found.")
    app = app.replace("dependencies {", 'dependencies {\n    implementation(project(":nexus-openvpn"))', 1)
if "useLegacyPackaging = true" not in app:
    if "    packaging {" not in app:
        raise SystemExit("App packaging block not found.")
    app = app.replace("    packaging {", "    packaging {\n        jniLibs { useLegacyPackaging = true }", 1)
app = app.replace('include("armeabi-v7a", "arm64-v8a", "x86_64")',
                  'include("armeabi-v7a", "arm64-v8a")')
app_gradle.write_text(app)

manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text()
m = re.sub(r'\s*<service\s+android:name="\.ui.NexusVpnService".*?</service>', '', m, flags=re.S)
m = m.replace('    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n', '')
for permission in (
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
    "android.permission.POST_NOTIFICATIONS",
):
    line = f'    <uses-permission android:name="{permission}" />'
    if line not in m:
        m = m.replace("<application", line + "\n\n    <application", 1)

components = [
'''        <activity
            android:name="de.blinkt.openvpn.LaunchVPN"
            android:exported="false"
            android:excludeFromRecents="true" />''',
'''        <service
            android:name="de.blinkt.openvpn.core.OpenVPNService"
            android:exported="false"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:foregroundServiceType="systemExempted">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>''',
'''        <service
            android:name="de.blinkt.openvpn.core.OpenVPNStatusService"
            android:exported="false" />''',
'''        <service
            android:name="de.blinkt.openvpn.core.keepVPNAlive"
            android:permission="android.permission.BIND_JOB_SERVICE"
            android:exported="false" />''',
]
for component in components:
    marker = re.search(r'android:name="([^"]+)"', component).group(1)
    if f'android:name="{marker}"' not in m:
        if "    </application>" not in m:
            raise SystemExit("Manifest application closing tag not found.")
        m = m.replace("    </application>", component + "\n    </application>", 1)
manifest.write_text(m)
Path("app/src/main/java/com/orbit/browser/ui/NexusVpnService.kt").unlink(missing_ok=True)
print("Pinned OpenVPN engine installed with AIDL, native ARM libraries, Gradle module, and real VPN service declarations.")
