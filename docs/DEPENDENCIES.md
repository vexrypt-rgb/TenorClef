# Dependencies & build wiring (Phase 0 + Phase 1)

Audit date: 2026-09-19 (America/Phoenix). Phase 1 Gradle/CI updates 2026-09-19 PT.

## Paths (Windows product layout)

| Path | Role |
|------|------|
| `C:\Users\redfa\Documents\MinecraftDev\altoclef` | TenorClef (this repo; remote `vexrypt-rgb/TenorClef`) |
| `C:\Users\redfa\Documents\MinecraftDev\Ostinato` | Ostinato tip (`main`, modern MC) |
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
- **Tungsten is not attached** on this module.

### 1.21 / 1.21.1 / 1.21.11

- Prefer newest non-1.16.1 jar from `../Ostinato/dist` (else `libs/`).
- Fallback: Maven `cabaletta:baritone-unoptimized-fabric:<mc>` (or `-Paltoclef.development` local flatDir).
- Escape hatch: `-Paltoclef.forceMiranczBaritone`.
- **Tungsten optional:** `libs/tungsten*.jar` or `vendor/tungsten/build/libs`; if absent, log and Baritone-only travel.

See also existing `docs/OSTINATO_WIRING.md`.

## Tungsten vendor pin

`vendor/TUNGSTEN_PIN.txt`:

- Repo: `https://github.com/3ndetz/Tungsten`
- Branch: `altoclef-compat`
- Commit: `5cb12ad65c0e045aa5a02d017c98df64eeda6d40`
- Treat as **GPL-3.0** for distribution.

**Fact for Phase 0:** Tungsten is a **1.21.x-only** classpath concern. 1.16.1 = Ostinato Baritone only (`mover=baritone` expected).

## Ostinato tip (`main`)

| Item | Value |
|------|-------|
| Minecraft | **1.21.11** |
| Java | **21** |
| Gradle wrapper | **8.14.x** (Unimined multi-loader) |
| Movement precursor | `baritone.api.movement.IMovementBackend`, `MovementBackends`, setting `movementBackend` |
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
| Tungsten | **Not present** in this lineage |

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
- Jobs: `:1.21.1:compileJava` (primary), `:1.21.11:compileJava` (checkout/build Ostinato tip → `../Ostinato/dist`), `:1.16.1:compileJava` (committed `libs/baritone-unoptimized-fabric-1.16.1.jar`)
- Tungsten **optional** — never required for a green job
- See `docs/DEVELOPMENT.md` for clean-runner behavior

## Other libraries

Jackson 2.16 (shadow), MixinExtras, `dev.babbaj:nether-pathfinder:1.5`, Fabric API per-MC version map in `build.gradle`.
