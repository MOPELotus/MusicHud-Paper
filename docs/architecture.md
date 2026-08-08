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

## Public playback session

The queue stores stable TuneWeave resource identity and requester ownership. It does not persist a CDN URL. Immediately before playback, the server asks clients to resolve that queue item in this order:

```text
requester / owner
        ↓ unavailable or rejected
other connected clients in stable UUID order
        ↓
canonical MusicDetail + lyrics + MusicResourceInfo
        ↓ server validation and header sanitization
PlaybackSession(sessionId, sequence, revision, startTime)
        ↓
all listeners consume the same resource and timeline
```

The resolve request is a narrow protocol operation, not a general client-worker API. The response cannot carry a TuneWeave credential. `PlaybackSession` carries the canonical `MusicDetail` (including its `LyricInfo`), the sanitized `MusicResourceInfo`, the authoritative start time, a monotonic server sequence, and revision identity. The sequence orders track/stop events; revision orders resource refreshes within one session.

A local decoder or download failure only changes local player status. It reports the affected `(sessionId, revision)` to the server and does not clear `NowPlayingInfo`. Once the server's failure threshold is reached, it resolves a replacement resource, retains the original session ID and timeline, increments the revision, and broadcasts the refreshed session.

Shared URLs are limited to HTTP(S). Both the server acceptance path and the public-session client download path reject loopback, private, link-local, multicast, DNS-rebinding targets, and unsafe redirect targets. Only a small non-credential request-header allowlist survives server sanitization. This restriction is scoped to server-shared playback; an explicitly local/direct playback source retains the caller's local-network semantics.

## Dependency direction

```text
platform adapters → common Minecraft behavior → core contracts
                                           │
                                           ▼
                                TuneWeave stable boundary
```

Reverse dependencies from core contracts into Fabric, NeoForge, Paper, Velocity, or provider-specific implementation details are not allowed.

The public playback path depends on the provider-neutral `IClientMusicService.resolvePublicPlayback` boundary and `ServerPlayerRegistry`. Server membership is established by the 2.0 handshake and is independent of music-platform login state. Provider login, search, catalog reads, and account mutations are absent from the Minecraft network protocol and remain client-owned TuneWeave operations.
