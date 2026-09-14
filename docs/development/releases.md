# Build and release PDX

PDX v0.3.0 continues the existing Android installation. Use JDK 17, Android SDK platform 35 and Build Tools 35.0.0. Android 12/API 31 is the minimum runtime. See [first-run setup](../../README.md) and [tests](testing.md).

## Build and package

```sh
./scripts/env.sh :app:testDebugUnitTest :app:lintDebug
./scripts/package-debug.sh
```

The packaging script installs the pinned terminal dependencies, regenerates the bundled assets, runs renderer tests, and builds the APK. It writes `artifacts/pdx-debug.apk` and `artifacts/pdx-debug.apk.sha256`. Python 3 and Node/npm are required in addition to the Android toolchain. Generated APKs and source archives are ignored by Git.

`app/build/outputs/apk/debug/app-debug.apk` remains the standard Gradle output. Packaging is a copy with the public product name; it does not alter the APK or signing certificate. Keep the same debug signing key for in-place device updates. Release signing is not configured, and private signing material must never be committed.

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
