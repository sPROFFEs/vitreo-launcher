# Screenshot provenance

Captured on an Android 15 / API 35 disposable emulator by
[CI run 39026978920](https://github.com/sPROFFEs/vitreo-launcher/actions/runs/39026978920),
source `5cf0e908b6ab16fd430a7c7fbc3518a2a76ace2a`, version 0.5.0 candidate.
The API 35 job passed all 44 instrumentation tests and the optimized Preview smoke.
The API 31 job in that review run failed the app-return synchronization test;
it is not release evidence for the final commit. Final release evidence is in the
release's build-info.json and corresponding CI run.

| File | Capture |
| --- | --- |
| home.png | Optimized Preview default Home |
| settings.png | Optimized Preview Settings |
| category.png | Debug native light category, PixelCopy |
| app-menu.png | Debug native contextual menu, PixelCopy |
| widget-previews.png | Optimized Preview widget catalog |
| app-icon.png | Native installed adaptive-icon mask regression |

Images are unmodified emulator captures. Home-widget regression images in the CI
artifact deliberately use a green instrumentation provider; those are test evidence,
not screenshots of a shipping widget. This provider is never bundled in user APKs.
