# Build and release PDX

PDX continues the existing Android installation. Use JDK 17, Android SDK platform 35 and Build Tools 35.0.0. Android 12/API 31 is the minimum runtime. See [first-run setup](../../README.md) and [tests](testing.md).

## Build and package

```sh
./scripts/env.sh :app:testDebugUnitTest :app:lintDebug
./scripts/package-debug.sh
```

The packaging script installs the pinned terminal dependencies, regenerates the bundled assets, runs renderer tests, and builds the APK. It writes `artifacts/pdx-debug.apk` and `artifacts/pdx-debug.apk.sha256`. Python 3 and Node/npm are required in addition to the Android toolchain. Generated APKs and source archives are ignored by Git.

`app/build/outputs/apk/debug/app-debug.apk` remains the standard Gradle output. Packaging is a copy with the public product name; it does not alter the APK or signing certificate. Keep the same debug signing key for in-place device updates. Optional release signing is described below; private signing material must never be committed.

## Device update

```sh
adb install -r artifacts/pdx-debug.apk
adb shell am start -n dev.herdr.handheld/.MainActivity
```

Do not uninstall a paired installation to work around a signature mismatch. Obtain a build signed with the existing key or explicitly arrange a new pairing. The product name is PDX; the stable package/component identifiers are explained in [architecture](../architecture.md).

## Publish a version

1. Update the Android version code/name, changelog, and the results of checks actually run. Do not copy historical device passes into a new run.
2. Review the staged diff and tracked files for private hostnames/IPs, terminal output, credentials, device serials, account details, and local paths. `.tools/`, local design inputs, and device screenshots stay excluded.
3. Run the build and packaging steps, inspect the final APK label/version/signature, and record its SHA-256 in `artifacts/validation-summary.json`.
4. Commit the reviewed source. Create the source archive from that exact commit, never from the working directory:

   ```sh
   git archive --format=zip --prefix=pdx/ --output=artifacts/pdx-source.zip HEAD
   ```

5. Publish the tag/release with `pdx-debug.apk`, its checksum, `pdx-source.zip`, and the validation JSON. Verify the downloaded assets against the recorded hashes. Keep older tags and release assets intact.

The public Git history contains the reviewed open-source history. Local private history backups are excluded. Never replace it with an earlier private checkout or upload the whole workspace as a source archive.

## v0.4 checks and signing channels

Run `python3 scripts/check.py` for a fresh JVM/renderer/lint/build run plus byte-for-byte consistency of generated terminal assets against Git. The script deletes previous test reports before execution, records subprocess outcomes, parses fresh XML and hashes the built APK. It never infers a physical-device pass from a build. CI runs the same command with read-only repository permissions and actions pinned to commit SHAs. CI uploads only the APK/checksum and sanitized report, not private device logs or app storage.

CI debug builds use the runner's disposable debug certificate. They are test artifacts and cannot update an installation signed by another machine. Published debug updates continue to use the existing maintainer certificate; retain its private keystore outside the repository with a protected backup. Compare the APK certificate before installing. Never solve a mismatch by silently uninstalling a paired app.

Optional production signing is configured through `PDX_SIGNING_STORE`, `PDX_SIGNING_STORE_PASSWORD`, `PDX_SIGNING_ALIAS`, and `PDX_SIGNING_KEY_PASSWORD`. Supply all four through your local secret manager/environment, then run `./scripts/env.sh :app:assembleRelease`. Without them, Gradle's release output is unsigned. No signing key is generated, embedded, uploaded to PR jobs, or rotated by this configuration. A production certificate different from the current debug certificate requires a deliberate migration/re-pairing plan; the existing update channel has not been changed.

For a physical-device report, run `python3 scripts/device-check.py --real-host > /path/to/private-run.log` after checks, then attach its APK-bound results with `python3 scripts/check.py --attach-device-log /path/to/private-run.log --device-label rg-rotate`. Only counts, outcome, log hash and capture time enter the public report. Logs must belong to the tested build; keep prior runs in versioned evidence instead of reusing their pass counts. The script exposes skipped/failed instrumentation rather than treating `OK (n tests)` alone as proof that every test ran.

References: [Android signing and update identity](https://developer.android.com/studio/publish/app-signing), [GitHub Actions security](https://docs.github.com/en/actions/reference/security/secure-use).
