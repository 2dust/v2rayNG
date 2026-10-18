#!/usr/bin/env python3
"""Stage 4A: add local migration export/import to existing Gozar WebView app.
Idempotent, surgical, fails closed if code layout differs. Does NOT change application ID.
"""
from pathlib import Path
root = Path(__file__).resolve().parents[2]
main = root / 'V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt'
html = root / 'V2rayNG/app/src/main/assets/index.html'

def replace_once(s, old, new, title):
    if new in s:
        print(title, 'already present')
        return s
    n=s.count(old)
    if n != 1:
        raise RuntimeError(f'{title}: expected exactly 1 occurrence of anchor, got {n}. No files changed.')
    return s.replace(old, new, 1)

k = main.read_text(encoding='utf-8')
h = html.read_text(encoding='utf-8')

if 'private val exportMigrationDocument =' not in k:
    anchor = '    private val requestCameraPermission ='
    addition = '''    // Gozar one-time migration: SAF keeps credentials in a user-selected local file.
    private var pendingMigrationJson: String? = null
    private val exportMigrationDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            var saved = false
            val payload = pendingMigrationJson
            pendingMigrationJson = null
            if (uri != null && payload != null) {
                saved = try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } != null
                } catch (_: Exception) { false }
            }
            gozarWebView?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('gozar-migration-export'," +
                    "{detail:{ok:" + saved.toString() + "}}))", null
            )
        }

'''
    k=replace_once(k, anchor, addition + anchor,'create document')
# Documents chosen by the user are exposed as content:// URIs through the file picker.
k=replace_once(k, 'settings.allowContentAccess = false', 'settings.allowContentAccess = true', 'open selected backup file')

if 'fun saveMigrationBackup(json: String?)' not in k:
    anchor='''        /** Read clipboard only when the user explicitly taps the import option. */'''
    addition='''        /** Export the WebView backup using Android's system document picker. */
        @JavascriptInterface
        fun saveMigrationBackup(json: String?): String {
            if (json.isNullOrBlank() || json.length > 5_000_000) return "invalid"
            return try {
                val data = JSONObject(json)
                if (data.optString("app") != "gozar" ||
                    data.optInt("migrationVersion") != 1 ||
                    !data.has("storage")) return "invalid"
                runOnUiThread {
                    pendingMigrationJson = json
                    exportMigrationDocument.launch("Gozar-migration-backup.json")
                }
                "pending"
            } catch (_: Exception) { "invalid" }
        }

'''
    k=replace_once(k,anchor,addition+anchor,'bridge method')

export_old = '''click('#bkExport',function(){
  const arr=state.configs.map(function(c){ return {raw:c.raw,name:c.name,src:c.src||''}; });
  const data={app:'gozar',version:1,exportedAt:new Date().toISOString(),configs:arr};
  download('gozar-backup-'+new Date().toISOString().slice(0,10)+'.json',new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));
  log(t('log_bk',{n:state.configs.length}),'ok');
});'''
export_new = '''click('#bkExport',function(){
  // Preserve all Gozar WebView data, including subscriptions and preferences.
  const storage={};
  for(let i=0;i<localStorage.length;i++){
    const key=localStorage.key(i);
    if(key && key.startsWith('gozar.')) storage[key]=localStorage.getItem(key);
  }
  const data={app:'gozar',migrationVersion:1,exportedAt:new Date().toISOString(),storage:storage,
    configs:state.configs.map(function(c){return {raw:c.raw,name:c.name,src:c.src||''};})};
  const json=JSON.stringify(data);
  if(window.GozarAndroid && typeof GozarAndroid.saveMigrationBackup==='function'){
    const result=GozarAndroid.saveMigrationBackup(json);
    if(result!=='pending') toast('تهیه نسخه پشتیبان ناموفق بود','err');
  }else{
    download('Gozar-migration-backup.json',new Blob([json],{type:'application/json'}));
  }
});
window.addEventListener('gozar-migration-export',function(e){
  toast(e.detail&&e.detail.ok?'فایل پشتیبان ذخیره شد':'ذخیره فایل لغو شد یا ناموفق بود',e.detail&&e.detail.ok?'ok':'warn');
});'''
h=replace_once(h,export_old,export_new,'migration export')
import_anchor='''      const arr=Array.isArray(j)?j:(Array.isArray(j.configs)?j.configs:null);'''
import_new='''      // Explicit one-time migration restore. Never delete or overwrite existing configs silently.
      if(j && j.app==='gozar' && j.migrationVersion===1 && j.storage && typeof j.storage==='object'){
        if(!confirm('بازگردانی اطلاعات Gozar از فایل انتخاب‌شده؟ از اطلاعات فعلی ابتدا پشتیبان بگیرید.')) return;
        const keys=Object.keys(j.storage).filter(function(key){ return key.startsWith('gozar.') && typeof j.storage[key]==='string'; });
        if(!keys.includes('gozar.configs.v1')) throw new Error('اطلاعات کانفیگ‌ها در فایل پشتیبان موجود نیست');
        const configs=JSON.parse(j.storage['gozar.configs.v1']);
        if(!Array.isArray(configs)) throw new Error('فایل کانفیگ‌ها معتبر نیست');
        // Validate before any mutation. This schema contains only app-owned WebView keys.
        keys.forEach(function(key){ if(key==='gozar.settings.v1'||key==='gozar.submeta.v1') JSON.parse(j.storage[key]); });
        keys.forEach(function(key){ localStorage.setItem(key,j.storage[key]); });
        toast('اطلاعات بازیابی شد؛ برنامه دوباره بارگذاری می‌شود','ok');
        setTimeout(function(){ location.reload(); },700);
        return;
      }
      const arr=Array.isArray(j)?j:(Array.isArray(j.configs)?j.configs:null);'''
h=replace_once(h,import_anchor,import_new,'migration import')
# All matches and checks succeeded; now write both.
main.write_text(k,encoding='utf-8')
html.write_text(h,encoding='utf-8')
print('4A ready: WebView export/import + Android SAF backup. PACKAGE ID UNCHANGED.')
