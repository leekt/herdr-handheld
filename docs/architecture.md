# PDX architecture

PDX means **Pocket Dispatch & eXecution**. Execution happens on the existing development host. The handheld owns navigation, reading, input review, voice capture, and the optional Android home experience.

```text
Android handheld
  PdxApp / native Compose screens
  AndroidInputRouter → InputRouter → InputSafety
  ConnectionCoordinator
    ├─ HerdrClient → SshHerdrClient
    │    ├─ real agent inventory / recent output
    │    └─ selected observe or control stream → terminal renderer
    └─ AssistantController → CodexClient
         └─ dedicated Codex conversation / reviewed proposals
                    │
              SshTransport (SSHJ)
                    │
Existing development host
  SSH server
    ├─ existing Herdr CLI → existing Herdr server and agents
    └─ codex app-server --listen stdio:// → model service
```

There is no web dashboard being mirrored. Kotlin exchanges JSON with the headless Codex process. Codex authentication and its full conversation stay on the host; SSH credentials, drafts, and the assistant's recent display cache are encrypted on Android. The assistant requires its SSH host to be reachable.

## Repository map

| Path | Responsibility |
|---|---|
| `app/` | The single Android Gradle module; native product and platform code |
| `terminal-assets/` | Pinned xterm dependencies and the restricted renderer build |
| `fixtures/herdr/` | Versioned, anonymized Herdr wire contracts |
| `fixtures/codex/` | Versioned, anonymized Codex contracts |
| `scripts/` | Local toolchain, packaging, and opt-in contract/SSH checks |
| `docs/product/` | Scope, design, and roadmap |
| `docs/devices/` | Compatibility matrix and per-device measurements |
| `docs/integrations/` | Backend contracts and authentication behavior |
| `docs/development/` | Test and release procedures |
| `docs/validation/`, `artifacts/` | Human-readable evidence and machine-readable reports |
| `licenses/` | Unmodified third-party license texts and dependency inventory |

The Kotlin source packages remain under `dev.herdr.handheld`: `ui`, `input`, `connection`, `herdr`, `assistant`, `ssh`, `terminal`, `storage`, `voice`, and `launcher`. `herdr` contains the measured integration and shared target/state models. That coupling is documented rather than presented as an existing provider-neutral framework. Additional adapters or platform implementations should be introduced with a working use case and contract tests.

## Stable installation identity

PDX replaces the product name Herdr Handheld starting with v0.3.0. Its Android application ID and Activity remain `dev.herdr.handheld` and `.MainActivity`. The Keystore alias, encrypted storage identifiers, preference names, and terminal message bridge also retain their existing values. They are compatibility identifiers, not product branding.

An in-place update signed with the same certificate preserves the paired SSH key, host verification, drafts, controller mapping, and assistant conversation reference. Do not rename these identifiers as a cosmetic cleanup, uninstall the paired app, or assume a newly generated debug signing certificate can update an existing installation. Historical releases and evidence retain their original names.

## Boundaries that survive new devices

- The native input router is the sole controller input path. Read mode is local; entry into remote input is explicit.
- Herdr controls the remote task lifecycle. Disconnecting PDX does not terminate agents.
- Host/session/terminal/agent identity and connection generation are checked before any write. Old proposals or uncertain input are never replayed.
- Codex proposes a small allowlisted set of actions. Native recipient review and terminal ownership remain independent of model memory.
- Device layouts use window constraints, density, and font scale; button labels require actual calibration.

See [product scope](product/scope.md), [device support](devices/README.md), and [integration contracts](README.md).
