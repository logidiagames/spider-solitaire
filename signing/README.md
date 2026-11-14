# FreeCell upload key

## Current upload key (since 2026-11-06)

A new upload key was created on the Windows PC on 2026-11-06, because the previous key stayed on the
owner's old Mac. The owner asks Google Play to reset the upload key with this key's certificate; until
Google approves the reset, Play keeps rejecting AABs signed with it.

- Keystore file: `freecell-upload-key-2.jks`, kept outside the repository in `C:\Users\acoel\FreeCell-keys\` (`~/FreeCell-keys/` in Git Bash)
- Alias: `freecell-upload`
- Key: RSA 4096, certificate signature SHA384withRSA, valid from 2026-11-06 to 2054-03-24
- Certificate owner: `CN=Nexus Play Labs, O=Nexus Play Labs, L=Porto, C=PT`
- Certificate SHA-1: `26:9D:FB:15:09:27:50:3C:E6:8C:09:35:2F:6D:DF:FE:42:F0:1B:68`
- Certificate SHA-256: `0E:D9:09:F2:7D:8B:66:F5:60:18:FC:1A:60:09:E7:E2:18:1D:0C:B8:29:43:B6:DC:C1:CA:35:BC:F5:C2:4D:99`
- Public certificate for the reset request: `upload_certificate.pem` (exported to the owner's Desktop; public, but not kept in the repository)
- Password: chosen and typed by the owner; never written in this repository, in a file or in a log. Keep it in the password manager.

Keep at least two copies of the `.jks` in safe places (for example an encrypted USB drive and a
password-protected cloud vault), and the password in the password manager. Losing either means
another upload key reset.

## Signing an AAB

Only after Google has approved the reset. From Git Bash in the repository:

```sh
tools/build_release_aab.sh
```

The script asks for the keystore password (typed by the owner, never shown), passes it to Gradle
through the existing environment variables (`UPLOAD_KEYSTORE`, `UPLOAD_KEYSTORE_PASSWORD`,
`UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`; read by `app/build.gradle.kts`, never written to a
file), checks that the AAB is signed by the SHA-256 above and that the native libraries are 16 KB-aligned, and
copies the AAB to `~/Downloads/FreeCell-release-builds/`. `FREECELL_KEYSTORE` points it at
another copy of the keystore.

## Previous upload key (replaced 2026-11-06)

- File `freecell-upload-key.jks`, alias `freecell-upload`, RSA 4096, created under a different studio name
- Certificate SHA-1 starts `EB:11:72:48:` (as the owner reported); SHA-256 `A0:4C:11:A7:1E:F7:D9:EA:E0:97:7F:0A:3C:0A:9F:C9:0B:51:77:C9:EF:79:A6:30:C4:4B:57:78:01:DA:F1:A6`
- Used for the first AAB (0.4.4, versionCode 9, uploaded 2026-10-22). It stayed on the owner's old
  Mac (`~/Documents/ChatGPT/Freecell 2.0/signing/`, password in the Mac Keychain) and is not
  available any more, so it is being replaced through Google Play's upload key reset.
