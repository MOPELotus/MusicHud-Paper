# Release model

## Artifact names

Client artifacts use one machine-predictable convention:

```text
musichud-tuneweave-fabric-<version>+<mc-version>.jar
musichud-tuneweave-neoforge-<version>+<mc-version>.jar
```

Future server artifacts should use the same project prefix:

```text
musichud-tuneweave-paper-<version>.jar
musichud-tuneweave-velocity-<version>.jar
```

Paper and Velocity are server/proxy outputs, not entries in the Minecraft-version × client-loader matrix.

## Source of truth

`gradle.properties` is currently the build version source for this branch. Release automation must derive filenames and publishing metadata from one version source and verify each jar before upload.

## Current automation status

This branch does not yet contain the required GitHub Actions CI or release workflow. Until that stage is implemented, a local build is verification evidence only and must not be described as reproducible CI.

The release stage must add clean-checkout builds, tests, artifact metadata checks, checksums, GitHub Releases, and optional Modrinth/CurseForge publishing through repository secrets.
