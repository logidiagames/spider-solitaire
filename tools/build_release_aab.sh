#!/usr/bin/env bash
# Builds the signed release AAB with the upload key (alias freecell-upload, keystore
# freecell-upload-key-2.jks since 2026-11-06; see signing/README.md).
#
# Run it yourself from a terminal: Git Bash on the Windows PC, or a macOS terminal. It asks for the
# keystore password, which you type (nothing is shown); on macOS it first tries the Keychain
# (account freecell-upload). The password only goes into Gradle's environment; it is never printed
# or saved. The script refuses to finish if the AAB is not signed by the registered key or if a
# native library is not 16 KB-aligned. The AAB is copied outside the repository.
#
# Google Play accepts AABs signed with this key only after it approves the upload key reset
# requested with this key's certificate (upload_certificate.pem).
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE="${FREECELL_KEYSTORE:-$HOME/FreeCell-keys/freecell-upload-key-2.jks}"
KEY_ALIAS="freecell-upload"
# SHA-256 of the upload certificate (new key, created 2026-11-06 for the upload key reset).
EXPECTED_SIGNER="0E:D9:09:F2:7D:8B:66:F5:60:18:FC:1A:60:09:E7:E2:18:1D:0C:B8:29:43:B6:DC:C1:CA:35:BC:F5:C2:4D:99"
OUTPUT_DIR="${FREECELL_RELEASE_DIR:-$HOME/Downloads/FreeCell-release-builds}"
WINDOWS_JDK="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "$WINDOWS_JDK" ]; then JAVA_HOME="$(cygpath -w "$WINDOWS_JDK")"
    else JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; fi
fi
export JAVA_HOME
[ -d "/c/Program Files/nodejs" ] && export PATH="/c/Program Files/nodejs:$PATH"

fail() { echo "ERROR: $*" >&2; exit 1; }
# A path Java can open (Windows form under Git Bash).
native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
java_bin() { if command -v cygpath >/dev/null 2>&1; then printf '%s/bin' "$(cygpath -u "$JAVA_HOME")"; else printf '%s/bin' "$JAVA_HOME"; fi; }

[ -f "$KEYSTORE" ] || fail "keystore not found: $KEYSTORE"
[ -f app/google-services.json ] || fail "app/google-services.json is missing (Firebase would be disabled)"
command -v unzip >/dev/null 2>&1 || fail "unzip is needed for the 16 KB check"
command -v node >/dev/null 2>&1 || fail "Node is needed for the 16 KB check"

version_code=$(sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts)
version_name=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
[ -n "$version_code" ] && [ -n "$version_name" ] || fail "could not read the version from app/build.gradle.kts"
echo "Building FreeCell $version_name (versionCode $version_code). Play rejects a versionCode it has already received."

password=""
if command -v security >/dev/null 2>&1; then
    password=$(security find-generic-password -a "$KEY_ALIAS" -w 2>/dev/null || true)
fi
if [ -z "$password" ]; then
    read -r -s -p "Keystore password for $KEY_ALIAS: " password
    echo
fi
[ -n "$password" ] || fail "no password given"

UPLOAD_KEYSTORE="$(native_path "$KEYSTORE")" \
UPLOAD_KEYSTORE_PASSWORD="$password" \
UPLOAD_KEY_ALIAS="$KEY_ALIAS" \
UPLOAD_KEY_PASSWORD="$password" \
    ./gradlew --no-daemon clean test lintRelease bundleRelease
unset password

aab=app/build/outputs/bundle/release/app-release.aab
[ -f "$aab" ] || fail "no AAB at $aab"

keytool="$(java_bin)/keytool"; [ -x "$keytool" ] || keytool="$keytool.exe"
signer=$("$keytool" -printcert -jarfile "$(native_path "$aab")" | awk '/SHA256:/ {print $2; exit}')
[ "$signer" = "$EXPECTED_SIGNER" ] || fail "AAB is signed by ${signer:-nothing}, not by the registered upload key"
echo "Signer OK: freecell-upload (freecell-upload-key-2.jks)."

libs=$(mktemp -d)
trap 'rm -rf "$libs"' EXIT
unzip -Z1 "$aab" | grep '\.so$' | while read -r name; do unzip -q -o "$aab" "$name" -d "$libs"; done
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
    console.log(`${path.relative(process.argv[2], file)}: LOAD alignment ${smallest}`);
    if (smallest < 16384) bad.push(entry);
  }
})(process.argv[2]);
if (bad.length) { console.error('Not 16 KB-aligned: ' + bad.join(', ')); process.exit(1); }
console.log('16 KB alignment OK');
JS

mkdir -p "$OUTPUT_DIR"
target="$OUTPUT_DIR/FreeCell-v$version_name-$version_code.aab"
cp "$aab" "$target"
echo "Signed AAB: $target"
