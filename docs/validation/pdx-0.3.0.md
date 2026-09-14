# PDX v0.3.0 validation — 2026-09-14

The final `pdx-debug.apk` was built, installed in place, and exercised on the paired RG Rotate. The app is labeled **PDX**, with an original adaptive pixel-letter icon. Android app info was visually inspected; screenshots remain private.

| Check | Result |
|---|---|
| Android build and lint | Passed; 0 lint errors, 30 warnings |
| JVM tests | 32 passed |
| Renderer tests | 3 passed |
| Final RG Rotate instrumentation | 3 passed in 28.008 seconds |
| Paired installation upgrade | Same signing certificate; six encrypted blobs unchanged byte-for-byte after the initial update |
| Real host | Existing SSH key and host verification still authenticate; two real Herdr observers exercised |
| Codex | Actual account, reviewed structured proposals, and same-thread reconnect exercised |
| Voice | Recognition startup and cancellation exercised; no recognized speech submitted |
| HOME | Existing default launcher preserved; switching default launchers was not tested |
| Documentation | Relative Markdown links checked after the move |
| Privacy | Tracked source and uncompressed APK entries scanned; private tools, history backups, design inputs, screenshots, and host details excluded |

The first device pass preceded an adaptive-icon adjustment. The three device suites were run again against the final packaged APK; the result above refers to that final pass. The tests inject controller/touch events and do not replace physical button calibration or hands-on comfort checks. No Herdr task instructions, Enter, Ctrl+C, or approval were sent.

APK SHA-256: `aa18042d346f5baaf5d7b26741a55e49226dc6bc9d4c1aee837b56064fc34c9a`. Machine-readable details are in [validation-summary.json](../../artifacts/validation-summary.json). See [test reproduction](../development/testing.md) and [historical evidence](../../artifacts/README.md).

Only the current conversation persists today. The conversation picker, pinned context, compaction controls, and additional platform implementations remain [roadmap items](../product/roadmap.md).
