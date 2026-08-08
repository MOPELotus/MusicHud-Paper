# Architecture

## Project boundary

MusicHud-TuneWeave is a MusicHud-derived Minecraft project built around TuneWeave. Minecraft code owns UI, HUD rendering, queues, local playback, multiplayer synchronization, and loader integration. TuneWeave owns platform-specific API behavior, authentication flows, provider routing, and normalization of external music data.

```text
MusicHud-TuneWeave
├── Minecraft domain
│   ├── UI / HUD
│   ├── queue and playback
│   ├── client/server synchronization
│   └── Fabric / NeoForge / Paper / Velocity integration
└── TuneWeave contract
    └── provider-specific music services
```

Provider-specific endpoint DTOs and routing rules must not leak back into the Minecraft domain.

## Current 26.2 module map

```text
processor
   └── compile-time registration metadata

core
   ├── protocol payloads and codecs
   ├── shared domain beans and service interfaces
   └── TuneWeave-facing server/client-neutral support

common
   ├── depends on core
   ├── shared Minecraft UI, audio and mixins
   └── client-side TuneWeave services

fabric
   ├── depends on common and core
   └── Fabric lifecycle, networking and packaging

neoforge
   ├── compiles common sources and depends on core
   └── NeoForge lifecycle, networking and packaging
```

`processor`, `core`, `common`, `fabric`, and `neoforge` are the only modules included by this branch's `settings.gradle`.

## Platform model

Fabric and NeoForge are client-mod builds for each supported Minecraft version. Paper and Velocity are server/proxy deployments that must remain independent of the client version matrix.

```text
Client                         Server / Proxy
├── <MC version>               ├── Paper
│   ├── Fabric                 └── Velocity
│   └── NeoForge
└── ...
```

Paper and Velocity code currently lives on dedicated repository branches and is not yet part of this branch's Gradle graph. Bringing those modules into the formal mainline is a separate platform-integration stage; this document does not claim they already build here.

## State and credential invariants

- Playback state is published as an immutable `PlaybackSnapshot` from one synchronization boundary.
- Asynchronous work must carry track/session identity and discard stale results.
- View listeners consume snapshots; direct UI push must not form a second playback-state path.
- Music-platform credentials remain client-owned and must not be placed in Minecraft Server state or logs.
- Server-coordinated client resolution is limited to establishing or refreshing a shared public playback session.

## Dependency direction

```text
platform adapters → common Minecraft behavior → core contracts
                                           │
                                           ▼
                                TuneWeave stable boundary
```

Reverse dependencies from core contracts into Fabric, NeoForge, Paper, Velocity, or provider-specific implementation details are not allowed.
