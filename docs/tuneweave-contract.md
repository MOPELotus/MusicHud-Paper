# TuneWeave contract

TuneWeave is the only music-service boundary for MusicHud-TuneWeave. Minecraft code consumes normalized operations and entities; it does not select or orchestrate platform implementations.

## Minecraft-facing capabilities

The current client facade is split by domain:

- authentication and client-owned sessions;
- catalog search and entity detail;
- account collections;
- playback source and lyrics resolution;
- playlists and Uni Playlist materialization;
- cloud library, video, podcast, and radio operations.

Concrete endpoints are implementation details of the TuneWeave client services. New Minecraft features should depend on the narrow domain service they need instead of growing a generic endpoint passthrough.

## Ownership

```text
credential/session → client → local TuneWeave
normalized entity  → Minecraft UI/queue
resolved playback  → local player or constrained public-session flow
```

The Minecraft Server may coordinate a client resolve only to establish or refresh the current public `PlaybackSession`. It must not become a general provider worker scheduler, and it must never receive the resolver's credential.

## Failure model direction

Provider-specific status codes should be normalized at the TuneWeave boundary into stable categories such as authentication required, not found, unavailable, no playback source, rate limited, unsupported, or internal failure. UI and network code should not branch on raw provider endpoint responses.
