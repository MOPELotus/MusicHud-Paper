# Protocol and identifiers

## Current state

The 26.2 branch uses `musichud_tuneweave` for its mod ID, resource namespace, translation prefix, and Minecraft network namespace. The Java package remains `indi.mopelotus.musichud.*`.

MusicHud-TuneWeave and upstream MusicHud must not be installed together because their Java packages still overlap. Both loader manifests reject upstream `music_hud`.

## Configuration and data migration

On first access, known legacy files are copied to independent destinations:

- `config/music_hud-client.toml` → `config/musichud_tuneweave-client.toml`;
- `config/music_hud-server.toml` → `config/musichud_tuneweave-server.toml`;
- `config/music-hud/uni-playlists.json` → `config/musichud-tuneweave/uni-playlists.json`.

Existing destinations win. Legacy files remain untouched. A bounded temporary copy is published using create-new hard-link semantics, so concurrent migration cannot overwrite a destination. Filesystem links escaping the config root and non-regular files are rejected. Unsupported filesystems or migration failures stop loading rather than silently replacing configuration. Temporary files created by this operation are the only files removed.

New process logs use `musichud-tuneweave/logs`. TuneWeave service data, logs, and executable are kept under the project-owned `musichud-tuneweave` directory; this migration does not move a live service database or managed binary. Identifier changes require matching client and server builds; dedicated plugin branches must be updated separately.

## Current protocol shape

Shared codecs and payload contracts live in `core/src/main/java/indi/etern/musichud/network`. Fabric and NeoForge adapt those contracts to their loader networking APIs.

The breaking TuneWeave protocol generation is `2.0.0-alpha`. The handshake now requires all three of the following:

- project identity `musichud-tuneweave`;
- a compatible protocol `Version`;
- all capabilities in `ProtocolInfo.CAPABILITIES`, including public sessions, resource quality, client idle snapshots, idle modes and playback source context.

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

### Resource quality capability

Public playback now requires both `PUBLIC_PLAYBACK_SESSION` and `RESOURCE_QUALITY_METADATA`. The resource codec appends requested and actual quality enum values after backup URLs; server sanitization preserves these non-secret fields. Peers without the new capability must be rejected before playback traffic. Dedicated Paper and Velocity branches need the same handshake and codec update before joining this client build.

### Client idle source snapshots and modes

`CLIENT_IDLE_SOURCE_SNAPSHOT` and `IDLE_SOURCE_PLAY_MODES` are now required. AddToIdlePlaySourceMessage carries the typed source descriptor, mode and normalized client-loaded collection. The server validates/copies it and does not load provider collections by numeric ID. Collection class names accept only Playlist and Album, without reflection. IdlePlaySource descriptors encode RANDOM or SEQUENTIAL; mode changes and source changes must use matching peers. Stable sourceReference is client configuration data; it is not needed in the removal descriptor.

### Playback source context

`PLAYBACK_SOURCE_CONTEXT` is required. MusicDetail now appends PlaybackSource after MusicSourceSelector. Source context describes public collection navigation independently of track identity; it contains no credentials or playable URL. The server preserves the requested source across resolution. Private source context omits collection reference and image and uses a generic name.

### Bounded fragmented transport

All current peers require `FRAGMENTED_PAYLOADS`. Messages above 24,000 encoded bytes use ClientPayloadFragment or ServerPayloadFragment, carrying UUID, original channel, index/count/total and a bounded byte slice. Reassembly is scoped to the connection identity and direction, accepts out-of-order identical pieces, rejects conflicting metadata and nested/unknown channels, expires after 10 seconds, and ignores recently completed replay IDs. Limits: 8 MiB per message, four pending per peer, sixteen pending globally, 32 MiB reserved globally. Disconnect clears associated state. Completed messages use the same domain codec and must have no trailing bytes.

Paper sends through the player scheduler with bounded channel-registration retries. Velocity owns this namespace: client messages are handled by the proxy, backend messages are not forwarded as a second public authority. A backend switch retains proxy membership and triggers state resynchronization; an actual disconnect invalidates the connection identity.

### Resolved track identity

`RESOLVED_TRACK_IDENTITY` is required. MusicResourceInfo appends the resolved TuneWeave track reference after quality metadata. Public-resource sanitization preserves this non-secret identifier. Listening history uses the actual target, bitrate, quality and resource duration; absent resolved identity for Uni/cloud is not guessed. VIVID is appended to the Quality enum, preserving existing ordinal values.
