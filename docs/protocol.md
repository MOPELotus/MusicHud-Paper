# Protocol and identifiers

## Current state

The 26.2 transition branch still uses the historical `music_hud` mod ID, resource namespace, translation prefix, and Minecraft network namespace. The Java package remains `indi.etern.musichud.*`.

This is an explicit transitional constraint, not an upstream-compatibility promise. MusicHud-TuneWeave and upstream MusicHud must not be installed together while they share an identifier.

## Required identifier migration

The dedicated breaking protocol stage must change these values atomically:

- Fabric and NeoForge mod ID;
- packet/network namespace;
- protocol project identity and version handshake;
- resource and translation namespace where required;
- config filenames and persistent data paths;
- loader dependency table keys and platform metadata.

That change must include migration tests and must either safely migrate known configuration/data fields or document an intentional breaking migration. A partial rename is forbidden because it could make incompatible peers appear compatible or silently hide user configuration.

## Current protocol shape

Shared codecs and payload contracts live in `core/src/main/java/indi/etern/musichud/network`. Fabric and NeoForge adapt those contracts to their loader networking APIs. Compatibility is currently checked with the project `Version` value; formal project identity, protocol version, and capability negotiation remain work for the protocol stage.

Credentials, authorization headers, passwords, Cookies, and refresh tokens are not valid protocol payload content for the future public playback design.
