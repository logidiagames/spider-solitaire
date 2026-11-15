#!/usr/bin/env bash
# Builds the signed release AAB of Spider Solitaire with its own upload key (alias spider-upload,
# keystore spider-upload-key.jks, outside the repository; see signing/README.md). FreeCell's upload
# key belongs to FreeCell and is never used here.
#
# Run it yourself from a terminal (Git Bash on the Windows PC). The first time, when the keystore
# does not exist yet, it creates it with keytool: you choose and type the password (twice), nothing
# is shown. Then it asks for that password once more for the build. The password only goes into
# Gradle's environment; it is never printed or saved. The script refuses to finish if the AAB is not
# signed by the keystore's own certificate or if a native library is not 16 KB-aligned. The AAB is
# copied outside the repository.
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE="${SPIDER_KEYSTORE:-$HOME/Spider-keys/spider-upload-key.jks}"
KEY_ALIAS="spider-upload"
DNAME="CN=Nexus Play Labs, O=Nexus Play Labs, L=Porto, C=PT"
OUTPUT_DIR="${SPIDER_RELEASE_DIR:-$HOME/Downloads/Spider-release-builds}"
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
keytool="$(java_bin)/keytool"; [ -x "$keytool" ] || keytool="$keytool.exe"

[ -f app/google-services.json ] || fail "app/google-services.json is missing (Firebase would be disabled)"
command -v unzip >/dev/null 2>&1 || fail "unzip is needed for the 16 KB check"
command -v node >/dev/null 2>&1 || fail "Node is needed for the 16 KB check"

if [ ! -f "$KEYSTORE" ]; then
    echo "No upload key yet: creating $KEYSTORE (alias $KEY_ALIAS, RSA 4096, $DNAME)."
    echo "Choose a strong password, type it twice, and keep it in your password manager."
    mkdir -p "$(dirname "$KEYSTORE")"
    "$keytool" -genkeypair -keystore "$(native_path "$KEYSTORE")" -alias "$KEY_ALIAS" -keyalg RSA -keysize 4096 \
        -sigalg SHA384withRSA -validity 10000 -dname "$DNAME"
    [ -f "$KEYSTORE" ] || fail "the keystore was not created"
    echo "Upload key created. Keep at least two copies of $KEYSTORE in safe places."
fi

version_code=$(sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts)
version_name=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
[ -n "$version_code" ] && [ -n "$version_name" ] || fail "could not read the version from app/build.gradle.kts"
echo "Building Spider Solitaire $version_name (versionCode $version_code). Play rejects a versionCode it has already received."

read -r -s -p "Keystore password for $KEY_ALIAS: " password
echo
[ -n "$password" ] || fail "no password given"

# The keystore's own certificate: the AAB must be signed by it.
expected=$(SPIDER_STOREPASS="$password" "$keytool" -list -v -keystore "$(native_path "$KEYSTORE")" -alias "$KEY_ALIAS" \
    -storepass:env SPIDER_STOREPASS 2>/dev/null | awk '/SHA256:/ {print $2; exit}') || true
[ -n "$expected" ] || fail "could not read the keystore (wrong password?)"

UPLOAD_KEYSTORE="$(native_path "$KEYSTORE")" \
UPLOAD_KEYSTORE_PASSWORD="$password" \
UPLOAD_KEY_ALIAS="$KEY_ALIAS" \
UPLOAD_KEY_PASSWORD="$password" \
    ./gradlew --no-daemon clean test lintRelease bundleRelease
unset password

aab=app/build/outputs/bundle/release/app-release.aab
[ -f "$aab" ] || fail "no AAB at $aab"

signer=$("$keytool" -printcert -jarfile "$(native_path "$aab")" | awk '/SHA256:/ {print $2; exit}')
[ "$signer" = "$expected" ] || fail "AAB is signed by ${signer:-nothing}, not by $KEY_ALIAS"
echo "Signer OK: $KEY_ALIAS ($(basename "$KEYSTORE")), SHA-256 $signer"

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
target="$OUTPUT_DIR/SpiderSolitaire-v$version_name-$version_code.aab"
cp "$aab" "$target"
echo "Signed AAB: $target"
