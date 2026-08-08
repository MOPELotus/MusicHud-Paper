# Development

## Requirements

- Java 25
- the repository Gradle wrapper
- the ModernUI build/version referenced by `gradle.properties`

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
