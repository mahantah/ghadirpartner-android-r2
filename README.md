# Ghadir Partner Android R5.6

راهنمای جاری: [README_R56_FA.md](README_R56_FA.md)

Web-automation-style login, encrypted opt-in remembered credentials, repaired biometric Activity/theme handling, correctly scaled SVG icons, original 512px brand logo, transparent checkout icons and an operational account-scoped message center.

Android source commit `3bfea70677df5ffde34ec3c23ed901fa278931e0` passed builds and lint for both portal and automation, startup/resume and 14 Android API 29 instrumentation tests.
[Build report and APK](https://github.com/mahantah/ghadirpartner-android-r2/actions/runs/38119136226).

Tests use injected API fixtures and do not send real SMS, orders or payments. Physical-device fingerprint/face and live-account acceptance remain unverified. See [API_MATRIX.md](API_MATRIX.md) and [VALIDATION.json](VALIDATION.json).

[Original asset provenance](validation/figma-assets.json) and [brand checksum](validation/brand-source.json) document bundled assets.
