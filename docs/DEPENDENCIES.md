# Dependencies & build wiring (Phase 0 + Phase 1)

Audit date: 2026-09-19 (America/Phoenix). Phase 1 Gradle/CI updates 2026-09-19 PT.

## Paths (Windows product layout)

| Path | Role |
|------|------|
| `C:\Users\redfa\Documents\MinecraftDev\altoclef` | TenorClef (this repo; remote `vexrypt-rgb/TenorClef`) |
| `C:\Users\redfa\Documents\MinecraftDev\Ostinato` | Ostinato `main` (MC 1.21.11; 1.21.4 on branch `1.21.4`) |
| `C:\Users\redfa\Documents\MinecraftDev\Ostinato-1.16.1` | Ostinato `1.16.1` branch checkout |

This audit used clones of the same GitHub remotes on the agent box.

## TenorClef modules / MC versions

- Root name: `altoclef` (`settings.gradle.kts`); build file per version: `versions/<mc>/` → `../../build.gradle`.
- Preprocess nodes linked in `root.gradle.kts`: **1.21.11 → … → 1.16.1** (full chain required for ReplayMod preprocess even if you mostly build two endpoints).
- `gradle.properties`: `mod_version=0.19`, `loader_version=0.16.7`, Fabric Loom **1.15.5**.
- Compile with **JDK 21**; `jvmdowngrader` lowers bytecode for older MC (`getJavaVersion()` → 21 / 17 / 8 by `mcVersion`).

### Machine-specific JDK — **resolved (Phase 1)**

Removed `org.gradle.java.home` from committed `gradle.properties`. Use:

- `JAVA_HOME` → JDK 21 for TenorClef / Ostinato tip
- Optional `~/.gradle/gradle.properties` or gitignored `gradle.properties.local` (see `docs/DEVELOPMENT.md`)
- Default `org.gradle.jvmargs=-Xmx2G` (CI-friendly); raise locally for heavy builds

## Ostinato jar wiring (`build.gradle`)

Directories probed:

- `../Ostinato/dist`
- `../Ostinato-1.16.1/dist`
- `libs/` (repo-local)

### 1.16.1 (`mcVersion == 11601`)

- Requires an Ostinato Baritone jar matching `baritone-unoptimized-fabric-*1.16.1*` or `baritone-unoptimized-fabric-ostinato-1.16.1.jar`.
- **Hard fail** if missing (no Maven fallback for 1.16.1).

### 1.21.4 (`mcVersion == 12104`)

- Requires `libs/baritone-unoptimized-fabric-1.21.4.jar` (Ostinato branch `1.21.4`). **Hard fail** if missing.

### 26.3 (primary)

- Newest `baritone-unoptimized-fabric-*26.3*.jar` from `../Ostinato/dist`, else the committed `libs/baritone-unoptimized-fabric-ostinato-26.3.jar` (the Ostinato `main` release jar). **Hard fail** if missing. Compiles with a JDK 25 toolchain; `-Pno26` leaves the module out.

### 1.21.11

- Newest non-1.16.1, non-1.21.4, non-26.3 jar from `../Ostinato/dist`, else the committed `libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar` (Ostinato branch `1.21.11`). No Maven fallback.

### Everything else (1.21.1 … 1.16.5)

- Preprocess-only: not compiled or packaged. Upstream `cabaletta:baritone-unoptimized-fabric:<mc>` is `modCompileOnly`, only for the preprocessor's type resolution.

See also existing `docs/OSTINATO_WIRING.md`.

## Ostinato `main` (MC 1.21.11)

| Item | Value |
|------|-------|
| Minecraft | **1.21.11** |
| Java | **21** |
| Gradle wrapper | **8.14.x** (Unimined multi-loader) |
| Movement API | `baritone.api.movement.IMovementEngine`, `MovementBackends` (reached by reflection) |
| Swim | `Settings.swimInWater` + traverse/diagonal sprint-swim (`docs/SWIM_PORT.md`, port of baritone#3988 / #2377) |

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato
gradlew.bat :fabric:build
```

## Ostinato 1.16.1 (`branch 1.16.1`)

| Item | Value |
|------|-------|
| Minecraft | **1.16.1** (Cabaletta 1.16.5-era Fabric toolchain retargeted) |
| Java | **JDK 8** |
| Gradle wrapper | **4.9** |

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato-1.16.1
set JAVA_HOME=<JDK8 home>
gradlew.bat build -Pbaritone.fabric_build
```

Copy `dist/baritone-unoptimized-fabric-*.jar` to `../Ostinato/dist/baritone-unoptimized-fabric-ostinato-1.16.1.jar` (or `altoclef/libs/`).

## Working TenorClef build commands

```bat
cd C:\Users\redfa\Documents\MinecraftDev\altoclef
gradlew.bat :1.21.11:compileJava
gradlew.bat :1.21.11:runClient
gradlew.bat :1.16.1:compileJava
gradlew.bat :1.16.1:runClient
```

Expect `[altoclef] Ostinato Baritone for <version>: <jar name>` on configure.

## CI today (`.github/workflows/gradle.yml`) — **Phase 1**

- JDK 21, `ubuntu-latest`
- Jobs: `:26.3:compileJava` + `:26.3:test` (primary, committed `libs/baritone-unoptimized-fabric-ostinato-26.3.jar`, JDK 25 toolchain), `:1.21.11:compileJava` + `:1.21.11:test` (committed `libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar`), `:1.21.4:compileJava` + `:1.21.4:test`, `:1.16.1:compileJava` (committed `libs/baritone-unoptimized-fabric-1.16.1.jar`), and a non-blocking `:1.21.11:compileJava` against the tip of Ostinato `1.21.11` (checkout/build → `../Ostinato/dist`)
- See `docs/DEVELOPMENT.md` for clean-runner behavior

## Other libraries

Jackson 2.16 (shadow), MixinExtras, `dev.babbaj:nether-pathfinder:1.5`, Fabric API per-MC version map in `build.gradle`.
