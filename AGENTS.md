# MusicHud-TuneWeave plugin guide

This worktree is the dedicated Paper/Velocity branch. It contains processor, core, paper and velocity; it does not build Minecraft client mods.

- Java 25 and Groovy Gradle scripts.
- Core protocol and public playback logic are synchronized with the 26.2 client worktree. Keep channel IDs, codecs, capabilities and tests identical when changing that boundary.
- TuneWeave and music credentials are client-owned. Plugins only coordinate public playback; never restore legacy provider endpoints, passwords, Cookies or server-side music-account storage.
- Paper compiles against the existing 1.21.1 API baseline. Runtime support must be validated separately; a successful build is not a version-matrix claim.
- Velocity owns public state across backend switches and does not forward backend packets in the fork namespace. Actual network disconnect invalidates membership.
- Preserve stable session identity, connection identity, stale-result rejection and bounded fragment reassembly.
- Paper uses the player scheduler; pending sends must be invalidated on plugin shutdown or player disconnect.
- Do not remove LGPL licensing or upstream attribution.

## Verification

    .\gradlew.bat core:test paper:build velocity:build --rerun-tasks

Core unit tests run by default; integration-tagged tests are excluded. Velocity includes actual adapter tests. Both build tasks include verifyProtocolJar, which rejects legacy provider/login protocol classes and requires shared registration metadata.

Deploy the unclassified shadow JAR. The -plain and -sources JARs are not deployment artifacts. No changes are automatically committed or pushed.