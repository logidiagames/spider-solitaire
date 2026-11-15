#!/usr/bin/env bash
# Signs an AAB already built by `./gradlew bundleRelease` (unsigned when no upload key is in the
# environment) with Spider's upload key, then runs the same checks as tools/build_release_aab.sh:
# signed by the keystore's own certificate, native libraries 16 KB-aligned, copied outside the
# repository. Use it when Gradle cannot run in the terminal where the owner types the password
# (the Claude desktop app's terminal does not see the Android SDK under AppData\Local).
#
# jarsigner and keytool ask for the keystore password themselves (typed by the owner, never shown).
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE="${SPIDER_KEYSTORE:-$HOME/Spider-keys/spider-upload-key.jks}"
KEY_ALIAS="spider-upload"
OUTPUT_DIR="${SPIDER_RELEASE_DIR:-$HOME/Downloads/Spider-release-builds}"
WINDOWS_JDK="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
[ -d "/c/Program Files/nodejs" ] && export PATH="/c/Program Files/nodejs:$PATH"
fail() { echo "ERROR: $*" >&2; exit 1; }
native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
bin="$WINDOWS_JDK/bin"; [ -d "$bin" ] || bin="$(dirname "$(command -v jarsigner)")"
jarsigner="$bin/jarsigner"; [ -x "$jarsigner" ] || jarsigner="$jarsigner.exe"
keytool="$bin/keytool"; [ -x "$keytool" ] || keytool="$keytool.exe"

[ -f "$KEYSTORE" ] || fail "keystore not found: $KEYSTORE"
unsigned=app/build/outputs/bundle/release/app-release.aab
[ -f "$unsigned" ] || fail "no AAB at $unsigned (run ./gradlew bundleRelease first)"
version_code=$(sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts)
version_name=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
echo "Signing Spider Solitaire $version_name (versionCode $version_code), built $(date -r "$unsigned" '+%Y-%m-%d %H:%M')."

read -r -s -p "Keystore password for $KEY_ALIAS: " password
echo
[ -n "$password" ] || fail "no password given"
expected=$(SPIDER_STOREPASS="$password" "$keytool" -list -v -keystore "$(native_path "$KEYSTORE")" -alias "$KEY_ALIAS" \
    -storepass:env SPIDER_STOREPASS 2>/dev/null | awk '/SHA256:/ {print $2; exit}') || true
[ -n "$expected" ] || fail "could not read the keystore (wrong password?)"

mkdir -p "$OUTPUT_DIR"
target="$OUTPUT_DIR/SpiderSolitaire-v$version_name-$version_code.aab"
SPIDER_STOREPASS="$password" "$jarsigner" -keystore "$(native_path "$KEYSTORE")" -storepass:env SPIDER_STOREPASS \
    -signedjar "$(native_path "$target")" "$(native_path "$unsigned")" "$KEY_ALIAS" >/dev/null
unset password

signer=$("$keytool" -printcert -jarfile "$(native_path "$target")" | awk '/SHA256:/ {print $2; exit}')
[ "$signer" = "$expected" ] || { rm -f "$target"; fail "AAB is signed by ${signer:-nothing}, not by $KEY_ALIAS"; }
echo "Signer OK: $KEY_ALIAS ($(basename "$KEYSTORE")), SHA-256 $signer"

libs=$(mktemp -d)
trap 'rm -rf "$libs"' EXIT
unzip -Z1 "$target" | grep '\.so$' | while read -r name; do unzip -q -o "$target" "$name" -d "$libs"; done
node - "$(native_path "$libs")" <<'JS'
const fs = require('fs'), path = require('path');
const bad = [];
(function walk(dir) {
  for (const entry of fs.readdirSync(dir)) {
    const file = path.join(dir, entry);
    if (fs.statSync(file).isDirectory()) { walk(file); continue; }
    if (!entry.endsWith('.so')) continue;
    const data = fs.readFileSync(file), is64 = data[4] === 2;
    const phoff = is64 ? Number(data.readBigUInt64LE(32)) : data.readUInt32LE(28);
    const size = data.readUInt16LE(is64 ? 54 : 42), count = data.readUInt16LE(is64 ? 56 : 44);
    let smallest = Infinity;
    for (let i = 0; i < count; i++) {
      const at = phoff + i * size;
      if (data.readUInt32LE(at) !== 1) continue; // PT_LOAD
      smallest = Math.min(smallest, is64 ? Number(data.readBigUInt64LE(at + 48)) : data.readUInt32LE(at + 28));
    }
    if (smallest < 16384) bad.push(entry);
  }
})(process.argv[2]);
if (bad.length) { console.error('Not 16 KB-aligned: ' + bad.join(', ')); process.exit(1); }
console.log('16 KB alignment OK');
JS

echo "Signed AAB: $target"
