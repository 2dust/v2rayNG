#!/usr/bin/env python3
"""Change only the Android application ID for Gozar migration pilot.
Keep Kotlin namespace, manifest components and VPN logic unchanged.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
gradle = root / 'V2rayNG/app/build.gradle.kts'
manifest = root / 'V2rayNG/app/src/main/AndroidManifest.xml'
main = root / 'V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt'
html = root / 'V2rayNG/app/src/main/assets/index.html'

for file in (gradle, manifest, main, html):
    if not file.is_file():
        raise SystemExit(f'Missing required file: {file}')

g = gradle.read_text(encoding='utf-8')
m = manifest.read_text(encoding='utf-8')
k = main.read_text(encoding='utf-8')
h = html.read_text(encoding='utf-8')
old = 'applicationId = "com.v2ray.ang"'
new = 'applicationId = "com.gozar.vpn"'

if 'namespace = "com.v2ray.ang"' not in g:
    raise SystemExit('Unexpected Kotlin namespace; aborting.')
if 'fun openNativeBackup()' not in k or 'fun saveMigrationBackup(' not in k:
    raise SystemExit('Missing tested migration bridge; aborting.')
if 'id="bkNativeZip"' not in h or 'migrationVersion:1' not in h:
    raise SystemExit('Missing tested WebView migration controls; aborting.')
if '${applicationId}.cache' not in m:
    raise SystemExit('FileProvider must use applicationId placeholder; aborting.')
if 'android:name=".ui.main.MainActivity"' not in m:
    raise SystemExit('Unexpected manifest component names; aborting.')

if new in g and old not in g:
    print('Already applied: com.gozar.vpn')
    raise SystemExit(0)
if g.count(old) != 1 or new in g:
    raise SystemExit('Unexpected or ambiguous applicationId; aborting.')

gradle.write_text(g.replace(old, new, 1), encoding='utf-8')
print('Changed applicationId only: com.v2ray.ang -> com.gozar.vpn')
print('Kotlin namespace and VPN source unchanged.')
