# PDX documentation

**PDX — Pocket Dispatch & eXecution** is a handheld interface for supervising remote agents. The current implementation is native Android, with Herdr for agent access and the host's Codex CLI for the optional assistant.

## Use PDX

- [Install, connect, and learn the controls](../README.md)
- [Supported devices and how to report a new handheld](devices/README.md)
- [Herdr connection contract](integrations/herdr.md)
- [Codex account, conversation, and voice](integrations/codex.md)

## Develop PDX

- [Architecture and code boundaries](architecture.md)
- [Product scope and input rules](product/scope.md)
- [Native design and controller behavior](product/design.md)
- [Roadmap and support criteria](product/roadmap.md)
- [Testing and paired-device precautions](development/testing.md)
- [Build, package, and release](development/releases.md)
- [Contributing](../CONTRIBUTING.md)

## Evidence

- [PDX v0.3.0 upgrade and final device tests](validation/pdx-0.3.0.md)

- [RG Rotate measurements and initial validation](devices/rg-rotate.md)
- [Real SSH, Herdr, Codex, and voice validation](validation/real-connection.md)
- [Latest build report](../artifacts/validation-summary.json)
- [Historical build reports](../artifacts/README.md)

Device compatibility and integration compatibility are separate. A successful build does not establish support for a new handheld or a new Herdr/Codex version.
