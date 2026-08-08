# MusicHud-TuneWeave Agent Guide

MusicHud-TuneWeave is an independent MusicHud fork built around the TuneWeave music-service boundary. This branch targets Minecraft 26.2 with Java 25 and contains the Fabric and NeoForge client-mod builds.

## Architecture rules

- Minecraft code owns UI, HUD, queueing, playback, synchronization, and platform integration.
- TuneWeave owns music-provider endpoints, provider routing, authentication implementation, and normalization of external music data.
- Do not add provider-specific API models or endpoint orchestration to Minecraft core.
- Music-platform credentials remain client-owned. Never place passwords, Cookies, tokens, or authorization headers in Minecraft Server packets, state, or logs.
- A server may coordinate client resolution only to establish or refresh the current public playback session; do not build a general client-worker scheduler.
- Preserve immutable playback snapshots, track/session identity, stale-result rejection, and atomic start/stop/switch behavior.
- HomeView must consume the snapshot subscription path; do not reintroduce a separate direct UI-push path.
- External input is untrusted. Parser, protocol, config, manifest, and URL changes require malformed/boundary tests.
- Do not remove the LGPL license or upstream attribution.

See `docs/architecture.md`, `docs/protocol.md`, and `docs/tuneweave-contract.md` before changing boundaries.

## Current modules

```text
processor  compile-time registration metadata
core       platform-independent contracts, codecs, beans and services
common     shared Minecraft UI, audio, mixins and client services
fabric     Fabric lifecycle, networking and packaging
neoforge   NeoForge lifecycle, networking and packaging
```

Only those modules are included by this branch's `settings.gradle`. Paper and Velocity are formal server/proxy targets but currently live on dedicated branches; do not claim they build from this branch.

The Java package and transitional mod/network identifier remain `indi.etern.musichud.*` and `music_hud`. This is not an upstream-compatibility promise. Identifier changes must be atomic with protocol/config/data migration and tests.

## Build and test

```powershell
.\gradlew.bat common:test
.\gradlew.bat fabric:build
.\gradlew.bat neoforge:build
```

Before completion, force relevant tasks to execute:

```powershell
.\gradlew.bat common:test fabric:build neoforge:build --rerun-tasks
```

TuneWeave network/download integration tests are opt-in:

```powershell
.\gradlew.bat common:test -PintegrationTests --rerun-tasks
```

Unit tests run by default. Integration tests are tagged `integration` so normal builds remain deterministic and offline-safe. There is currently no CI, linter, formatter, or typechecker configured.

Expected client artifacts:

```text
musichud-tuneweave-fabric-<version>+<mc-version>.jar
musichud-tuneweave-neoforge-<version>+<mc-version>.jar
```

## Implementation patterns

- Service discovery uses `Environment.Platform.load()` and reflective platform implementations.
- `RegistrationManager` consumes annotation-processor metadata generated for `@RegisterMark` classes.
- `MusicHud.EXECUTOR` is a virtual-thread executor used for audio, API lifecycle, and network work.
- `core` is a plain `java-library`; do not apply Loom to it.
- Fabric and NeoForge package `common` and `core` through their dedicated dependency/shadow configurations.
- Build scripts use Groovy DSL.
- The Fabric run config requires the explicit `fabric.dli.config` override already present in `fabric/build.gradle`.

## Change discipline

For non-trivial changes: audit, plan, implement, run targeted tests, run the full relevant suite, review the diff adversarially, fix findings, then force one final rerun. Do not report cached or previously green tasks as current verification.

Changes to async code must test stale callbacks and rapid state changes. Changes to lifecycle code must test late detach/unsubscribe ordering. Changes to managed files must prove active files and unrelated user files are never deleted.
