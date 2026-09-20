# Development (Phase 1)

Portable builds for TenorClef. No machine-specific paths in committed `gradle.properties`.

## JDKs

| What you build | JDK | Notes |
| --- | --- | --- |
| TenorClef tip / modern modules (`1.21.1`, `1.21.11`, …) | **JDK 21** | Root Loom build; `jvmdowngrader` lowers bytecode for older MC |
| Ostinato tip (`main`, MC 1.21.11) | **JDK 21** | Gradle 8.x / Unimined |
| Ostinato `1.16.1` branch | **JDK 8** | Gradle **4.9** — do not use JDK 21 for that checkout |

Set `JAVA_HOME` (or your IDE Gradle JVM) to JDK 21 before running TenorClef Gradle.

```bat
REM Windows (example — adjust path to your Adoptium/Temurin install)
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
cd C:\Users\redfa\Documents\MinecraftDev\altoclef
gradlew.bat :1.21.1:compileJava
```

```bash
# Linux / macOS / CI
export JAVA_HOME=/usr/lib/jvm/temurin-21   # or whatever your install is
./gradlew :1.21.1:compileJava
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

TenorClef searches (first hit wins by newest mtime):

| Layout | Path |
| --- | --- |
| Sibling tip (Windows product layout) | `../Ostinato/dist/` |
| Sibling 1.16.1 checkout | `../Ostinato-1.16.1/dist/` |
| In-tree (CI) | `Ostinato/dist/`, `Ostinato-1.16.1/dist/` |
| Repo libs | `libs/` |

### 1.16.1

Requires an Ostinato Baritone jar (`baritone-unoptimized-fabric-*1.16.1*` or `baritone-unoptimized-fabric-ostinato-1.16.1.jar`). A CI stub is committed as `libs/baritone-unoptimized-fabric-1.16.1.jar`.

Build fresh from Ostinato (JDK **8**):

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato-1.16.1
set JAVA_HOME=<JDK8 home>
gradlew.bat build -Pbaritone.fabric_build
copy dist\baritone-unoptimized-fabric-*.jar ..\Ostinato\dist\baritone-unoptimized-fabric-ostinato-1.16.1.jar
```

### 1.21.11

Prefers a tip Ostinato fabric jar from `../Ostinato/dist` (or `libs/baritone-unoptimized-fabric-ostinato*.jar`).
There is **no** published Maven Baritone for 1.21.11 — without a staged jar, Gradle still configures other modules, but `:1.21.11:compileJava` will not link Baritone until the jar is present.

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato
set JAVA_HOME=<JDK21 home>
gradlew.bat build
cd ..\altoclef
gradlew.bat :1.21.11:compileJava
```

### 1.21.1 (primary release)

Uses the published matching Baritone artifact (ignores tip Ostinato jars so a 1.21.11 jar cannot contaminate the build).

## Tungsten (optional)

Travel backend only on 1.21 / 1.21.1 / 1.21.11. Place `tungsten*.jar` in `libs/` or build `vendor/tungsten`. **CI and local builds must succeed without Tungsten** (Baritone fallback). See `docs/TUNGSTEN_BACKEND.md`.

## Version directories (Gradle 9+)

`settings.gradle.kts` creates empty `versions/<mc>/` folders when missing so a clean clone can configure. ReplayMod preprocess fills sources on first build.

## Useful commands

```bat
gradlew.bat :1.21.1:compileJava
gradlew.bat :1.21.11:compileJava
gradlew.bat :1.16.1:compileJava
gradlew.bat :1.21.1:runClient
```

Do not compile while a `runClient` / `@testrun` session is live on the same tree.

## CI (clean runner)

On push/PR to `main`, `.github/workflows/gradle.yml`:

1. **1.21.1** — JDK 21, `./gradlew :1.21.1:compileJava` (Maven Baritone; no Ostinato checkout).
2. **1.21.11** — JDK 21, checkout + build `vexrypt-rgb/Ostinato@main`, stage jars to `../Ostinato/dist` (and `libs/`), then `./gradlew :1.21.11:compileJava`. Ostinato tip build **must** succeed (no Maven Baritone for 1.21.11).
3. **1.16.1** — JDK 21 for TenorClef Gradle, uses committed `libs/baritone-unoptimized-fabric-1.16.1.jar`, `./gradlew :1.16.1:compileJava`.

Tungsten is never required. No `org.gradle.java.home` pin.
