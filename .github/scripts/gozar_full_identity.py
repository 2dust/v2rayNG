#!/usr/bin/env python3
"""Repackage app's first-party Kotlin/Java code from com.v2ray.ang to com.gozar.vpn.
Preserves VPN code, settings data keys, dependency identities, protocol links and legal credits.
Designed for Gozar's feature/gozar-webview-integration branch.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'V2rayNG' / 'app'
SRC = APP / 'src'
OLD = 'com.v2ray.ang'
NEW = 'com.gozar.vpn'
OLD_DIR = SRC / 'main/java/com/v2ray/ang'
NEW_DIR = SRC / 'main/java/com/gozar/vpn'
GRADLE = APP / 'build.gradle.kts'

if not GRADLE.exists():
    sys.exit('ERROR: Android module not found: ' + str(GRADLE))
gradle = GRADLE.read_text(encoding='utf-8')
if 'applicationId = "com.gozar.vpn"' not in gradle:
    sys.exit('ERROR: Step 4B applicationId=com.gozar.vpn must be done first.')
if 'namespace = "com.gozar.vpn"' in gradle and NEW_DIR.is_dir() and not OLD_DIR.exists():
    print('Already migrated. Running validation.')
elif 'namespace = "com.v2ray.ang"' not in gradle or not OLD_DIR.is_dir() or NEW_DIR.exists():
    sys.exit('ERROR: Unexpected source layout; no changes made.')
else:
    # Change narrow, first-party package tokens. Preserve unrelated v2ray references,
    # upstream URLs, xray core identifiers, required software attributions, URL schemes.
    allowed = {'.kt', '.java', '.xml', '.kts', '.pro', '.properties', '.json'}
    candidates = [p for p in APP.rglob('*') if p.is_file() and p.suffix in allowed]
    edits = []
    for path in candidates:
        raw = path.read_bytes()
        try:
            content = raw.decode('utf-8')
        except UnicodeDecodeError:
            continue
        if OLD in content:
            edits.append((path, content.replace(OLD, NEW)))
    if not any(path == GRADLE for path, _ in edits):
        sys.exit('ERROR: Expected Gradle namespace edit missing; no changes made.')
    # Path and new sources must not conflict. Entire first-party tree gets relocated.
    for path, updated in edits:
        path.write_text(updated, encoding='utf-8')
    NEW_DIR.parent.mkdir(parents=True, exist_ok=True)
    OLD_DIR.rename(NEW_DIR)
    # Prune old now-empty directory hierarchy.
    for d in [SRC / 'main/java/com/v2ray', SRC / 'main/java/com']:
        if d.exists() and not any(d.iterdir()):
            d.rmdir()
    print(f'Repackaged {len(edits)} text files; moved code tree to {NEW_DIR.relative_to(ROOT)}')

# Static checks: every Kotlin package/import must refer to the new first-party namespace;
# source path changed and manifest relative class names can resolve with new namespace.
text = GRADLE.read_text(encoding='utf-8')
assert 'namespace = "com.gozar.vpn"' in text
assert 'applicationId = "com.gozar.vpn"' in text
assert NEW_DIR.is_dir() and not OLD_DIR.exists()
kt_files = list(NEW_DIR.rglob('*.kt')) + list(NEW_DIR.rglob('*.java'))
assert len(kt_files) >= 150, f'Unexpected source count: {len(kt_files)}'
for f in kt_files:
    s = f.read_text(encoding='utf-8')
    assert OLD not in s, f'Unmigrated import/package: {f}'
    assert 'package com.gozar.vpn' in s or 'package-info' in f.name, f'Unexpected source package: {f}'
for f in APP.rglob('*.xml'):
    s = f.read_text(encoding='utf-8')
    assert OLD not in s, f'Unmigrated xml class: {f}'
assert (SRC / 'main/java/com/gozar/vpn/ui/main/MainActivity.kt').exists()
assert 'fun openNativeBackup()' in (SRC / 'main/java/com/gozar/vpn/ui/main/MainActivity.kt').read_text(encoding='utf-8')
assert 'id="bkNativeZip"' in (SRC / 'main/assets/index.html').read_text(encoding='utf-8')
print('PASS: New namespace/package paths, declared application ID, shortcuts and migration UI.')
print('NOTICE: Compile and device testing remain REQUIRED; do not publish based on static checks.')
