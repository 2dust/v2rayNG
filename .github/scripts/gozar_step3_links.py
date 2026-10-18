#!/usr/bin/env python3
"""Step 3: patch only the two old app release/home links, preserving rest of AppConfig.kt."""
from pathlib import Path
p = Path('V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt')
if not p.is_file():
    raise SystemExit(f'Cannot find file: {p}. Run from repository root.')
s = p.read_text(encoding='utf-8')
replacements = {
    'const val APP_URL = "$GITHUB_URL/2dust/v2rayNG"':
    'const val APP_URL = "$GITHUB_URL/Mohammadhosseini11/-Gozar"',
    'const val APP_API_URL = "https://api.github.com/repos/2dust/v2rayNG/releases"':
    'const val APP_API_URL = "https://api.github.com/repos/Mohammadhosseini11/-Gozar/releases"',
}
for before, after in replacements.items():
    if before in s:
        assert s.count(before) == 1, f'Unexpected multiple occurrences: {before}'
        s = s.replace(before, after)
    elif after not in s:
        raise SystemExit(f'Expected original line not found: {before}')
p.write_text(s, encoding='utf-8')
print('Gozar app website and release API updated; all other constants preserved.')
