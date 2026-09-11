# Development

## Requirements

- Java 25
- the repository Gradle wrapper
- network access for the pinned ModernUI fork release in `gradle.properties`

ModernUI-MC is resolved directly from the public [MOPELotus fork releases](https://github.com/MOPELotus/ModernUI-MC/releases).
`modernui_fork_version=26.2-3.13.0.7` pins both loader artifacts. Gradle's exclusive Ivy repository maps these dependencies to GitHub release JARs without a local sibling checkout, credentials, or JitPack build. Local builds and GitHub Actions use the same dependency path. To upgrade, change this property to a published tag with both Fabric and NeoForge universal JARs.

The fork is a compile-only mod dependency, so it is not embedded in MusicHud. Install the matching ModernUI universal JAR when running Minecraft.

## Build

```powershell
.\gradlew.bat fabric:build
.\gradlew.bat neoforge:build
```

The resulting release jars use the predictable form:

```text
musichud-tuneweave-<platform>-<version>+<mc-version>.jar
```

## Tests

```powershell
.\gradlew.bat common:test
```

Default tests are deterministic and exclude tests tagged `integration`. TuneWeave release-manifest and artifact-download tests are opt-in:

```powershell
.\gradlew.bat common:test -PintegrationTests
```

Before declaring a change complete, force execution rather than relying on Gradle cache state:

```powershell
.\gradlew.bat common:test fabric:build neoforge:build --rerun-tasks
```

Changes to shared code must build both loader targets. Parser, protocol, asynchronous state, lifecycle, and external-input changes require permanent regression tests.
