#!/usr/bin/env python3
"""Minimal branding fix: leave identifiers, networking, and core untouched."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
app = root / 'V2rayNG' / 'app' / 'src'
paths = sorted((app / 'main' / 'res').glob('values*/strings.xml'))
paths += [app / 'dev/res/values/strings.xml', app / 'pre_release/res/values/strings.xml', app / 'fdroid/res/values/strings.xml']
for path in paths:
    if not path.exists():
        continue
    before = path.read_text(encoding='utf-8')
    after = before.replace('v2rayNG', 'Gozar')
    if after != before:
        path.write_text(after, encoding='utf-8')
        print('UPDATED:', path.relative_to(root))

manifest = app / 'main/AndroidManifest.xml'
if manifest.exists():
    before = manifest.read_text(encoding='utf-8')
    # Only change the icon attribute of the Quick Settings tile service.
    pattern = r'(<service\b(?=[^>]*android:name="\.service\.QSTileService")[^>]*?android:icon=")@drawable/ic_stat_name(")'
    after, count = re.subn(pattern, r'\g<1>@drawable/ic_stat_gozar\2', before, flags=re.DOTALL)
    if count != 1:
        raise RuntimeError(f'Expected exactly one QSTileService icon, found {count}. No manifest change saved.')
    if not (app / 'main/res/drawable/ic_stat_gozar.xml').exists():
        raise RuntimeError('Gozar notification icon is missing')
    if after != before:
        manifest.write_text(after, encoding='utf-8')
        print('UPDATED:', manifest.relative_to(root))
print('Branding pass completed. Package identifiers and core code were not modified.')
