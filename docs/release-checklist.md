# Google Play release checklist

## Required before a signed production AAB

- Permanent `applicationId` confirmed as `com.nexusplaylabss.freecell`.
- Confirm app name, version code/version name and supported languages.
- Upload key: the old `freecell-upload` key is already registered by the first upload. Replacing it now needs an upload-key reset request in Play Console (App integrity → App signing); keep keystore and passwords outside Git.
- Enable Play App Signing in the target Play Console app.
- Supply final original launcher icon, feature graphic, phone/tablet screenshots and store copy.
- Privacy policy: https://sites.google.com/view/freecellsolitaireprivacypolicy (opened from Settings → Privacy Policy; support e-mail nexusplaylabss@gmail.com). Use the same URL in the Play Console listing, and complete Data safety / content-rating declarations. There is no Terms of Service page, so the app has no Terms item.
- Test on representative API 24–36 phones and tablets, including low-memory devices.
- Run unit tests, lint and a closed-track test; inspect the Play pre-launch report.
- 16 KB page size: native libraries must be 16 KB-aligned. Pre-checked on the release APK on 2026-10-22 (all `libdatastore_shared_counter.so` OK); verify again on the AAB (`zipalign -c -P 16 -v 4` on the APKs built from it, and the ELF LOAD alignment).
- Data safety: see `docs/DATA_SAFETY.md`.
- versionCode 9 (0.4.4) was already uploaded to Play on 2026-10-22, signed with the old `freecell-upload` key; the next upload uses versionCode 10 (versionName stays 0.4.4). Every later upload needs a higher versionCode.
- Decide whether ads, analytics, crash reporting or billing are in launch scope. If so, implement consent, child-directed-treatment decisions, regional privacy handling and the corresponding Play declarations.

The project targets Android 16 / API 36, matching the Google Play requirement that applies to new mobile apps from 31 August 2026.

