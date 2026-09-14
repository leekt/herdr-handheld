# Build evidence

- `validation-summary.json`: results for the current packaged APK; only checks actually run for that build are claimed.
- `pdx-debug.apk.sha256`: checksum of the current PDX debug APK.
- `history/v0.2.0/`: original Herdr Handheld v0.2.0 report and checksum, preserved under the old artifact name.
- `history/v0.1.0/validation.json`: initial implementation report; includes historical synthetic test flows.
- `contract-probe.json`: sanitized isolated Herdr CLI contract probe.

APKs and source ZIPs are available as release assets and ignored in Git. Private screenshots, terminal captures, and local tools are never release assets. Historical evidence is not retroactively relabeled as a PDX test run.
