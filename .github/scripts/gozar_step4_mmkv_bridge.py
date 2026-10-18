#!/usr/bin/env python3
"""Gozar stage 4: expose EXISTING native MMKV ZIP backup screen in Gozar WebView.
No package ID, VPN core, or storage schema modifications.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[3]
main = root / 'V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt'
html = root / 'V2rayNG/app/src/main/assets/index.html'


def replace_once(text, before, after, label):
    if after in text:
        print(f'{label}: already applied')
        return text
    if text.count(before) != 1:
        raise RuntimeError(f'{label}: expected one anchor, found {text.count(before)}')
    return text.replace(before, after, 1)

k = main.read_text(encoding='utf-8')
h = html.read_text(encoding='utf-8')

k = replace_once(k,
    'import android.Manifest\n',
    'import android.Manifest\nimport android.content.Intent\nimport com.v2ray.ang.ui.backup.BackupActivity\n',
    'native screen imports')

bridge_anchor = '    private inner class GozarBridge {\n'
bridge = '''    private inner class GozarBridge {

        /** Open the app's existing ZIP/MMKV export/restore screen, on UI thread. */
        @JavascriptInterface
        fun openNativeBackup(): String {
            runOnUiThread {
                try {
                    startActivity(Intent(this@MainActivity, BackupActivity::class.java))
                } catch (_: Exception) {
                    // A missing native screen must not disturb the running VPN.
                }
            }
            return "pending"
        }
'''
k = replace_once(k, bridge_anchor, bridge, 'bridge entry point')

row_anchor = '''          <div class="srow"><div class="st"><b data-i18n="bk_copy"></b>'''
row_new = '''          <div class="srow"><div class="st"><b>پشتیبان‌گیری کامل اندروید (ZIP)</b><small>انتقال کانفیگ‌ها و تنظیمات بومی؛ فایل محرمانه است</small></div>
            <button class="btn" id="bkNativeZip" type="button"><i data-lucide="archive"></i>باز کردن</button></div>
''' + row_anchor
h = replace_once(h, row_anchor, row_new, 'native backup button')

js_anchor = "click('#bkCopy',function(){"
js_new = '''click('#bkNativeZip',function(){
  if(!(window.GozarAndroid && typeof GozarAndroid.openNativeBackup === 'function')) {
    toast('پشتیبان ZIP فقط در برنامه اندروید در دسترس است','warn'); return;
  }
  GozarAndroid.openNativeBackup();
});
''' + js_anchor
h = replace_once(h, js_anchor, js_new, 'native backup click')

# Refuse half-complete patch: both outputs are written only after all anchors match.
assert 'com.gozar.vpn' not in k, 'Unexpected package edit in MainActivity'
main.write_text(k, encoding='utf-8')
html.write_text(h, encoding='utf-8')
print('ZIP/MMKV backup screen exposed. Package ID and VPN code untouched.')
