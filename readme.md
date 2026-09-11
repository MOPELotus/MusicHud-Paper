# MusicHud-TuneWeave — Paper / Velocity

Independent LGPL fork of [MusicHud](https://github.com/Etern-34520/MusicHud), derived also from the former MusicHud-Paper work. This is not an official upstream release.

This dedicated branch provides server/proxy coordination for current MusicHud-TuneWeave clients. Music providers, authentication and credentials remain on clients. Plugins do not require a server-side TuneWeave process.

## Build

Java 25 is required. Paper currently compiles against the existing 1.21.1 API baseline; runtime testing on target servers is still required.

    .\gradlew.bat core:test paper:build velocity:build --rerun-tasks

Deploy `paper/build/libs/musichud-tuneweave-paper-<version>.jar` or `velocity/build/libs/musichud-tuneweave-velocity-<version>.jar`. Do not deploy `-plain` or `-sources` artifacts.

## Configuration and ownership

Paper stores its configuration under the new `MusicHud-TuneWeave` plugin directory; Velocity uses its injected `musichud_tuneweave` directory. Old MusicHud directories are neither moved nor deleted. Reapply the public vote-policy setting if needed; do not migrate server-side music credentials. The configurable policy is `pusherVoteAdditionalRate` (0 through 1).

The protocol namespace is `musichud_tuneweave`, with the same core codecs/capabilities as the 26.2 client branch. The proxy owns public playback across backend switches and suppresses backend messages in this namespace to prevent duplicate authority. Actual disconnect removes membership. Large packets use bounded shared fragmentation.

Unit and adapter tests are deterministic and offline-safe when dependencies are cached. Builds and contract tests do not replace LAN, real Paper, proxy switching or client playback acceptance tests.