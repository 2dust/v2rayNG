#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Fetches the newest Psiphon server list for the build: the signed, compressed list Psiphon's client
# downloads for itself, from the address the core gives the client. It goes into the app's assets as
# psiphon_servers.dat, where the app copies it into its asset folder beside the geo files, and the
# core hands it to the client at start, so a fresh install has servers before it can reach the list
# on its own; a fetched list is merged on top later. The list is checked here against Psiphon's
# public key, the one the core and the app carry, so a broken or foreign download never ships.
# psiphon_servers.stamp beside it records when the list was published, from the download's
# Last-Modified header, so the app can tell a newer bundled list from an older one.

APP_CONFIG="$__dir/V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt"
KEY_SOURCE="$__dir/aether/aether/src/psiphon.rs"
TARGET="$__dir/V2rayNG/app/src/main/assets/psiphon_servers.dat"
STAMP="$__dir/V2rayNG/app/src/main/assets/psiphon_servers.stamp"

# The first python that runs: on Windows, "python3" may be a store stub that only prints a hint.
PY=""
for candidate in python3 python; do
  if "$candidate" -c "import zlib" >/dev/null 2>&1; then
    PY="$candidate"
    break
  fi
done
if [[ -z "$PY" ]]; then
  echo "python not found"
  exit 1
fi
if [[ ! -f "$KEY_SOURCE" ]]; then
  echo "Aether sources not found at $__dir/aether"
  echo "run: git submodule update --init --recursive"
  exit 1
fi

URL="$(grep -o 'PSIPHON_SERVERS_URL = "[^"]*"' "$APP_CONFIG" | cut -d'"' -f2)"
if [[ -z "$URL" ]]; then
  echo "PSIPHON_SERVERS_URL not found in $APP_CONFIG"
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "[psiphon-servers] fetching $URL"
curl -fsSL --retry 3 --retry-delay 5 --max-time 180 -D "$work/headers" -o "$work/list" "$URL"

# Unpack the package for the check: the key as the core carries it, the entry text and the signature.
"$PY" - "$work" "$KEY_SOURCE" <<'EOF'
import base64, email.utils, hashlib, io, json, re, sys, time, zlib
work, key_source = sys.argv[1], sys.argv[2]
published = None
for line in io.open(work + "/headers", encoding="latin-1"):
    if line.lower().startswith("last-modified:"):
        parsed = email.utils.parsedate_tz(line.split(":", 1)[1].strip())
        if parsed:
            published = email.utils.mktime_tz(parsed)
if published is None:
    published = int(time.time())
    print("[psiphon-servers] no Last-Modified header; the fetch time stands for the publication time")
io.open(work + "/stamp", "w").write(str(published) + chr(10))
print("[psiphon-servers] published %s" % time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime(published)))
source = io.open(key_source, encoding="utf-8").read()
found = re.search(r'SERVER_LIST_SIGNATURE_KEY: &str = concat!\((.*?)\);', source, re.S)
if not found:
    sys.exit("the signing key was not found in " + key_source)
key = "".join(re.findall(r'"([^"]*)"', found.group(1)))
package = json.loads(zlib.decompress(open(work + "/list", "rb").read()))
if base64.b64decode(package["signingPublicKeyDigest"]) != hashlib.sha256(key.encode()).digest():
    sys.exit("the list names another key than the one the core carries")
data = package["data"]
entries = [line for line in data.split("\n") if line.strip()]
if len(entries) < 50:
    sys.exit("only %d server entries in the list; that is not the list" % len(entries))
open(work + "/data.bin", "wb").write(data.encode("utf-8"))
open(work + "/sig.bin", "wb").write(base64.b64decode(package["signature"]))
pem = "-----BEGIN PUBLIC KEY-----\n" + "\n".join(key[i:i + 64] for i in range(0, len(key), 64)) + "\n-----END PUBLIC KEY-----\n"
open(work + "/key.pem", "w").write(pem)
print("[psiphon-servers] %d server entries" % len(entries))
EOF

# The signature: RSA PKCS#1 v1.5 with SHA-256 over the entry text, as the client checks it.
openssl dgst -sha256 -verify "$work/key.pem" -signature "$work/sig.bin" "$work/data.bin"

mkdir -p "$(dirname "$TARGET")"
cp "$work/list" "$TARGET"
cp "$work/stamp" "$STAMP"
echo "[psiphon-servers] staged:"
ls -la "$TARGET" "$STAMP"
