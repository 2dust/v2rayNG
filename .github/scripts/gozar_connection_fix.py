#!/usr/bin/env python3
"""Fix Gozar WebView -> native connection handoff; preserve VPN core and user data.
Run from any working directory. Applies strictly to feature/gozar-webview-integration.
"""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[3]
app = root / 'V2rayNG/app'
html = app / 'src/main/assets/index.html'
main = app / 'src/main/java/com/gozar/vpn/ui/main/MainActivity.kt'
gradle = app / 'build.gradle.kts'
assert gradle.is_file() and 'applicationId = "com.gozar.vpn"' in gradle.read_text(), 'Gozar package mismatch; abort'
assert main.is_file() and html.is_file(), 'Required source files missing; abort'
k = main.read_text(encoding='utf-8')
h = html.read_text(encoding='utf-8')


def once(s, old, new, label):
    if new in s:
        print(label + ': already applied')
        return s
    n = s.count(old)
    if n != 1:
        raise RuntimeError(f'{label}: anchor count {n} (expected 1); no files written')
    return s.replace(old, new, 1)

# Add an explicit non-sensitive diagnostic for the existing native bridge.
old_bridge = '''        @JavascriptInterface
        fun vpnStatus(): String {'''
new_bridge = '''        /** Return a connection-start error to the app UI, without exposing config contents. */
        @JavascriptInterface
        fun connectionError(): String = lastBridgeError.take(240)

        @JavascriptInterface
        fun vpnStatus(): String {'''
k = once(k, old_bridge, new_bridge, 'connection error bridge')

# Handle import rejection without a hidden fallback to previously selected server.
old_invalid = '''                if (result.first <= 0) {
                    lastBridgeError = "کانفیگ قابل واردکردن نیست."
                    return "invalid"
                }'''
new_invalid = '''                if (result.first <= 0) {
                    lastBridgeError = "لینک کانفیگ در موتور اندروید قابل واردکردن نیست."
                    return "invalid"
                }'''
k = once(k, old_invalid, new_invalid, 'import diagnostic')

# WebView's xrayOut() is a preview/export generator, not the canonical input
# for the Android URI parser. Prefer original links for native connection.
old_selected = '''    const json=xrayOut(c);
    if(!json){ toast('این پروتکل در نسخهٔ فعلی پشتیبانی نمی‌شود؛ VLESS، VMess، Trojan یا Shadowsocks را انتخاب کن.','warn'); return; }
    lastStartAt=Date.now();'''
new_selected = '''    const raw=String(c.raw||'').trim();
    const nativeInput=/^(vless|vmess|trojan|ss):\\/\\//i.test(raw) ? raw : xrayOut(c);
    if(!nativeInput){ toast('این پروتکل در نسخهٔ فعلی پشتیبانی نمی‌شود؛ VLESS، VMess، Trojan یا Shadowsocks را انتخاب کن.','warn'); return; }
    lastStartAt=Date.now();'''
h = once(h, old_selected, new_selected, 'raw URI handoff')

old_result = '''      const res=B.startVpn(json);
      if(res==='ask') toast('در پنجرهٔ اندروید، اجازهٔ اتصال VPN را تأیید کن.','warn');
      else if(res==='invalid'){ showError('کانفیگ خالی یا نامعتبر است.'); }
      else if(res==='not_ready'){ showError('هستهٔ VPN آماده نیست.'); }'''
new_result = '''      const res=B.startVpn(nativeInput);
      if(res==='ask') toast('در پنجرهٔ اندروید، اجازهٔ اتصال VPN را تأیید کن.','warn');
      else if(res==='invalid'){
        let why='کانفیگ توسط موتور اندروید پذیرفته نشد.';
        try{ if(B.connectionError) why=B.connectionError()||why; }catch(_){}
        showError(why);
      }
      else if(res==='not_ready'){ showError('هستهٔ VPN آماده نیست.'); }'''
h = once(h, old_result, new_result, 'native rejection surfaced')

old_status = '''      let msg='خطا در راه‌اندازی VPN';
      try{ if(B.coreLog) msg=B.coreLog()||msg; }catch(e){}
      showError(msg); return;'''
new_status = '''      let msg='خطا در راه‌اندازی VPN';
      try{ if(B.connectionError) msg=B.connectionError()||msg; }catch(e){}
      showError(msg); return;'''
h = once(h, old_status, new_status, 'real error in status poll')

old_timeout = '''      vpnOn=false; session.reason='اتصال شروع نشد؛ کانفیگ را بررسی و دوباره تلاش کن.';
      setMode('err'); stopPulseLoop(); setProgress(0);'''
new_timeout = '''      vpnOn=false;
      let msg='سرویس VPN شروع نشد. مجوز VPN و لاگ هسته را بررسی کن.';
      try{ if(B.connectionError) msg=B.connectionError()||msg; }catch(_){}
      showError(msg);'''
h = once(h, old_timeout, new_timeout, 'timeout surfaced')

old_auto = '''        const c=activeCfg(), json=c&&xrayOut(c);
        if(json){ lastStartAt=Date.now(); setMode('busy'); pushParams(); B.startVpn(json); }'''
new_auto = '''        const c=activeCfg(), raw=c&&String(c.raw||'').trim();
        const nativeInput=(raw&&/^(vless|vmess|trojan|ss):\\/\\//i.test(raw)) ? raw : (c&&xrayOut(c));
        if(nativeInput){ lastStartAt=Date.now(); setMode('busy'); pushParams(); B.startVpn(nativeInput); }'''
h = once(h, old_auto, new_auto, 'auto connect consistency')

# Ensure the separate restart action also uses the same source as the main button.
old_restart = '''window.GozarAndroid.startVpn(xrayOut(c));'''
new_restart = '''window.GozarAndroid.startVpn(/^(vless|vmess|trojan|ss):\\/\\//i.test(String(c.raw||''))?c.raw:xrayOut(c));'''
h = once(h, old_restart, new_restart, 'restart consistency')

assert 'applicationId = "com.gozar.vpn"' in gradle.read_text()
assert 'package com.gozar.vpn.ui.main' in k
assert 'id="bkNativeZip"' in h and 'saveMigrationBackup' in k
# Validate both files before touching either one.
main.write_text(k, encoding='utf-8')
html.write_text(h, encoding='utf-8')
print('PASS: raw native config handoff + visible connection errors; backup and app ID unchanged.')
