# Ostinato wiring (TenorClef)

## What changed

TenorClef `1.21` / `1.21.1` Gradle modules prefer Ostinato's
`baritone-unoptimized-fabric-*.jar` from `../Ostinato/dist` when present.

Fallback: MiranCZ / cabaletta maven (`-Paltoclef.forceMiranczBaritone` forces maven).

`1.16.1` still uses `libs/baritone-unoptimized-fabric-1.16.1.jar` (patched).

## Build

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato
gradlew.bat :fabric:build

cd C:\Users\redfa\Documents\MinecraftDev\altoclef
gradlew.bat :1.21.1:compileJava
```

Expect log line: `[altoclef] Ostinato Baritone for 1.21.1: baritone-unoptimized-fabric-...jar`

## Version caveat

| Project | Minecraft |
| --- | --- |
| Ostinato | **1.21.11** |
| TenorClef newest module | **1.21.1** |

Compile uses Ostinato for `AltoClefSettings` / Baritone APIs. Full in-game load wants matching MC
(add TenorClef `1.21.11` or rebuild Ostinato for `1.21.1`).

## Tungsten

- **Ostinato:** `#set movementBackend auto|tungsten|baritone` (travel only; mining stays Baritone).
- **TenorClef:** existing `TungstenMovement` facade + jar under `libs/` (see `docs/TUNGSTEN_BACKEND.md`).