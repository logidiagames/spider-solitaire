# Spider Solitaire upload key

Spider Solitaire has its own upload key. FreeCell's upload key belongs to FreeCell and is never used
here.

- Keystore file: `spider-upload-key.jks`, kept outside the repository in `C:\Users\acoel\Spider-keys\`
  (`~/Spider-keys/` in Git Bash)
- Alias: `spider-upload`
- Key: RSA 4096, signature SHA384withRSA, valid 10,000 days
- Certificate owner: `CN=Nexus Play Labs, O=Nexus Play Labs, L=Porto, C=PT`
- Password: chosen and typed by the owner; never written in this repository, in a file or in a log.
  Keep it in the password manager.

`tools/build_release_aab.sh` creates the key the first time it runs (keytool asks the owner for the
password). Google Play registers it as the upload key with the first AAB uploaded to the new app
(Play App Signing keeps the app signing key).

Keep at least two copies of the `.jks` in safe places (for example an encrypted USB drive and a
password-protected cloud vault), and the password in the password manager. Losing either means an
upload key reset through Google Play support.

## Signing an AAB

From Git Bash in the repository:

```sh
tools/build_release_aab.sh
```

The script asks for the keystore password (typed by the owner, never shown), passes it to Gradle
through the environment variables read by `app/build.gradle.kts` (`UPLOAD_KEYSTORE`,
`UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`; never written to a file),
runs the tests and lint, checks that the AAB is signed by the keystore's certificate and that the
native libraries are 16 KB-aligned, and copies the AAB to `~/Downloads/Spider-release-builds/`.
`SPIDER_KEYSTORE` points it at another copy of the keystore.
