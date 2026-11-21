#!/usr/bin/env python3
"""Idempotently prepare Orbit app module for embedded WireGuard tunnel library."""
from pathlib import Path

gradle = Path("app/build.gradle.kts")
text = gradle.read_text()

dependency = '    implementation("com.wireguard.android:tunnel:1.0.20260102")'
if dependency not in text:
    marker = 'dependencies {'
    if marker not in text:
        raise SystemExit("Could not find dependencies block in app/build.gradle.kts")
    text = text.replace(marker, marker + '\n' + dependency, 1)

# WireGuard tunnel module relies on Java 8+ library APIs; enable desugaring.
compile_marker = '    compileOptions {'
if 'isCoreLibraryDesugaringEnabled = true' not in text:
    if compile_marker not in text:
        raise SystemExit("Could not find compileOptions block")
    text = text.replace(compile_marker, compile_marker + '\n        isCoreLibraryDesugaringEnabled = true', 1)

desugar = '    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")'
if desugar not in text:
    text = text.replace('dependencies {', 'dependencies {\n' + desugar, 1)

gradle.write_text(text)
print("WireGuard tunnel dependency + desugaring added (idempotent).")
