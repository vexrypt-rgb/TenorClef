# Development (Phase 1)

Portable builds for TenorClef. No machine-specific paths in committed `gradle.properties`.

## JDKs

| What you build | JDK | Notes |
| --- | --- | --- |
| TenorClef modules (`1.21.4`, `1.21.11`, `1.16.1`) | **JDK 21** | Root Loom build; `jvmdowngrader` lowers bytecode for older MC |
| Ostinato `main` (MC 1.21.11) and branch `1.21.4` | **JDK 21** | Gradle 8.x / Unimined |
| Ostinato `1.16.1` branch | **JDK 8** | Gradle **4.9** — do not use JDK 21 for that checkout |

Set `JAVA_HOME` (or your IDE Gradle JVM) to JDK 21 before running TenorClef Gradle.

```bat
REM Windows (example — adjust path to your Adoptium/Temurin install)
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
cd C:\Users\redfa\Documents\MinecraftDev\altoclef
gradlew.bat :1.21.11:compileJava -PostinatoJar=..\Ostinato\dist\<jar>
```

```bash
# Linux / macOS / CI
export JAVA_HOME=/usr/lib/jvm/temurin-21   # or whatever your install is
./gradlew :1.21.11:compileJava
```

### Do not commit `org.gradle.java.home`

Phase 1 removed the Windows Adoptium pin from `gradle.properties`. Pinning a local JDK path breaks Linux CI and other machines.

Personal overrides (optional):

1. **Preferred:** `JAVA_HOME` / IDE Gradle JVM = JDK 21.
2. **User-wide:** `~/.gradle/gradle.properties` (or `%USERPROFILE%\.gradle\gradle.properties`):
   ```properties
   org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.12.101-hotspot
   org.gradle.jvmargs=-Xmx8192M
   ```
3. **Repo-local:** copy `gradle.properties.local.example` → `gradle.properties.local` (gitignored). Loaded by `settings.gradle.kts` as extras; for daemon JDK selection still prefer (1) or (2).

## JVM heap

Committed default is `-Xmx2G` (CI-friendly on GitHub-hosted runners).

For heavy local preprocess / multi-version builds, raise the heap in user or local properties:

```properties
org.gradle.jvmargs=-Xmx8192M
```

## Ostinato jars

The committed jar in `libs/` is the only jar a normal build uses. There is no directory scan and no mtime ordering, so a local build and CI compile against the same bytes. Each jar has a `libs/<jar>.sha256`; a mismatch fails configuration.

| Module | Pinned jar |
| --- | --- |
| 26.3 | `libs/baritone-unoptimized-fabric-ostinato-26.3.jar` |
| 1.21.11 | `libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar` |
| 1.21.4 | `libs/baritone-unoptimized-fabric-1.21.4.jar` |
| 1.16.1 | `libs/baritone-unoptimized-fabric-1.16.1.jar` |

To build against a local Ostinato jar instead, pass `-PostinatoJar=<path>` (checksum not checked). `../Ostinato/dist` is never read implicitly. To re-pin, replace the jar and regenerate its `.sha256` in the same commit (`sha256sum <jar> > <jar>.sha256`).

### 1.16.1

Requires an Ostinato Baritone jar (`baritone-unoptimized-fabric-*1.16.1*` or `baritone-unoptimized-fabric-ostinato-1.16.1.jar`). A CI stub is committed as `libs/baritone-unoptimized-fabric-1.16.1.jar`.

Build fresh from Ostinato (JDK **8**):

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato-1.16.1
set JAVA_HOME=<JDK8 home>
gradlew.bat build -Pbaritone.fabric_build
rem then pass -PostinatoJar=<that jar>, or re-pin libs\baritone-unoptimized-fabric-1.16.1.jar and its .sha256
```

### 1.21.11

Uses the committed `libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar` (Ostinato branch `1.21.11`, JDK 21, `./gradlew :fabric:build`), To build against a local Ostinato checkout, pass its jar with `-PostinatoJar`:

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato
set JAVA_HOME=<JDK21 home>
gradlew.bat build
cd ..\altoclef
gradlew.bat :1.21.11:compileJava -PostinatoJar=..\Ostinato\dist\<jar>
```

### 1.21.4

Uses the committed `libs/baritone-unoptimized-fabric-1.21.4.jar` (Ostinato branch `1.21.4`, JDK 21, `./gradlew :fabric:build`).

### Other versions

`1.21.1` down to `1.16.5` are preprocess-only nodes: TenorClef always runs on Ostinato and Ostinato has no build for them, so their compile, jar and test tasks are disabled. They keep upstream Baritone compile-only, only so the preprocessor can resolve types.

## Version directories (Gradle 9+)

`settings.gradle.kts` creates empty `versions/<mc>/` folders when missing so a clean clone can configure. ReplayMod preprocess fills sources on first build.

## Useful commands

```bat
gradlew.bat :1.21.11:compileJava -PostinatoJar=..\Ostinato\dist\<jar>
gradlew.bat :1.21.4:compileJava
gradlew.bat :1.16.1:compileJava
gradlew.bat :1.21.11:test
gradlew.bat :1.21.11:runClient
```

Do not compile while a `runClient` / `@testrun` session is live on the same tree.

## CI (clean runner)

On push/PR to `main`, `.github/workflows/gradle.yml`:

1. **26.3** (required, primary) — JDK 21 for Gradle plus a JDK 25 toolchain, committed `libs/baritone-unoptimized-fabric-ostinato-26.3.jar` (Ostinato `main` release jar), `./gradlew :26.3:compileJava` then `:26.3:test`.
2. **1.21.11** (required) — JDK 21, committed `libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar`, `./gradlew -Pno26 :1.21.11:compileJava` then `:1.21.11:test`.
3. **1.21.4** (required) — JDK 21, committed `libs/baritone-unoptimized-fabric-1.21.4.jar`, `./gradlew :1.21.4:compileJava` then `:1.21.4:test`.
4. **1.16.1** (required) — JDK 21 for TenorClef Gradle, uses committed `libs/baritone-unoptimized-fabric-1.16.1.jar`, `./gradlew :1.16.1:compileJava`.
5. **1.21.11 against Ostinato tip** (`continue-on-error`) — checkout + `:fabric:build` of `vexrypt-rgb/Ostinato@1.21.11`, then `./gradlew :1.21.11:compileJava -PostinatoJar=<tip jar>` **only if** staging succeeded. An early warning that the two repositories have drifted; does **not** block merge.

`Deploy Javadoc` (`.github/workflows/javadoc-publish.yml`) is also **non-blocking** (`continue-on-error`) — generation/deploy failures must not red `main`.

No `org.gradle.java.home` pin.

### Related: Ostinato `1.16.1` Tests

Ostinato branch `1.16.1` runs `.github/workflows/run_tests.yml` on JDK 8. That job has been flaky/red independently of TenorClef. Prefer documenting + `continue-on-error` there until a dedicated JDK8/Gradle 4.9 test fix lands; TenorClef's `:1.16.1:compileJava` remains the required legacy gate.
