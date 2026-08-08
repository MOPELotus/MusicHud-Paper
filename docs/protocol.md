# Protocol and identifiers

## Current state

The 26.2 transition branch still uses the historical `music_hud` mod ID, resource namespace, translation prefix, and Minecraft network namespace. The Java package remains `indi.etern.musichud.*`.

This is an explicit transitional constraint, not an upstream-compatibility promise. MusicHud-TuneWeave and upstream MusicHud must not be installed together while they share an identifier.

## Remaining identifier migration

The dedicated breaking protocol stage must change these values atomically:

- Fabric and NeoForge mod ID;
- packet/network namespace;
- resource and translation namespace where required;
- config filenames and persistent data paths;
- loader dependency table keys and platform metadata.

That change must include migration tests and must either safely migrate known configuration/data fields or document an intentional breaking migration. A partial rename is forbidden because it could make incompatible peers appear compatible or silently hide user configuration.

## Current protocol shape

Shared codecs and payload contracts live in `core/src/main/java/indi/etern/musichud/network`. Fabric and NeoForge adapt those contracts to their loader networking APIs.

The breaking TuneWeave protocol generation is `2.0.0-alpha`. The handshake now requires all three of the following:

- project identity `musichud-tuneweave`;
- a compatible protocol `Version`;
- capability `PUBLIC_PLAYBACK_SESSION`.

An upstream MusicHud peer, a pre-2.0 peer, or a peer without the public-session contract is rejected before player membership or queue state is initialized. The handshake exposes protocol capabilities only; music-platform discovery and credentials remain client-owned TuneWeave concerns.

Public playback uses these contracts:

```text
S2C ResolvePlaybackRequestMessage
C2S ResolvePlaybackResultMessage
S2C SwitchMusicMessage(PlaybackSession)
S2C SyncCurrentPlayingMessage(PlaybackSession)
C2S PlaybackResourceFailureMessage(sessionId, revision)
```

Each authoritative track or stop event receives a monotonic server `sequence`. A resource refresh preserves `sessionId`, `sequence`, and timeline while increasing `revision`, so asynchronous client receivers can discard stale updates deterministically.

`GetMusicResourceRequest` and `GetMusicResourceResponse` were removed. Listeners never resolve the active public track through their own account.

Credentials, authorization headers, passwords, cookies, and refresh tokens are not valid protocol payload content. Resolver-supplied media headers are reduced to `Accept`, `Accept-Language`, `Origin`, `Referer`, and `User-Agent` before a session becomes authoritative.

The 2.0 generation has no server account/login protocol and no generic provider endpoint bridge. Search, collection materialization, subscription changes, login, and other provider operations execute through the client's TuneWeave boundary. The Minecraft protocol contains only connection negotiation, public playback coordination, queue/idle-source state, and synchronization messages.
