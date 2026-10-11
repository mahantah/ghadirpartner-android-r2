# Ghadir Partner Android R5.5

راهنمای جاری: [README_R55_FA.md](README_R55_FA.md)

Native Android implementation of Figma page 100:573: day/night portal screens, OTP login, catalog pricing, cart, cash/check/credit settlement, order review, profile, notifications, proforma PDFs and delivered-order serials.

The exact Android source commit `02e3f8e4507789fc217012773d3c9714bc7cd8e5` passed builds and lint for both portal and automation, startup/resume and seven Android API 29 instrumentation tests.
[Build report and APK](https://github.com/mahantah/ghadirpartner-android-r2/actions/runs/38061463799).

Tests use injected API fixtures and do not send real SMS, orders or payments. Real-device biometric/camera and live-account acceptance remain unverified. See [API_MATRIX.md](API_MATRIX.md) and [VALIDATION.json](VALIDATION.json).

Original Figma SVGs and the byte-verified offer image are bundled locally. [Asset provenance](validation/figma-assets.json) records their origin and checksums.
