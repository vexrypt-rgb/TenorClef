# Ostinato wiring (TenorClef)

Playbook reference: [UnionClef](https://github.com/3ndetz/unionclef) — slim active MC modules,
Loom 1.15.x, `1.21.11` as preferred modern target. TenorClef keeps Ostinato Baritone (UnionClef
dropped Baritone for Tungsten-only; we still need AltoClef Baritone APIs via Ostinato).

## Preferred paths (MC-aligned)

| TenorClef module | Ostinato artifact | Minecraft |
| --- | --- | --- |
| **`1.21.11`** | `../Ostinato/dist/baritone-unoptimized-fabric-*.jar` (exclude `*1.16.1*`) | **1.21.11** |
| **`1.16.1`** | `../Ostinato/dist/baritone-unoptimized-fabric-ostinato-1.16.1.jar` (or `../Ostinato-1.16.1/dist/`) | **1.16.1** |

Both use Ostinato. Do **not** keep MiranCZ/libs as the long-term 1.16.1 path.

### Active Gradle modules (UnionClef-style)

`settings.gradle.kts` includes only: `1.21.11`, `1.21.1`, `1.21`, `1.16.5`, `1.16.1`.
Older `versions/1.17–1.20` trees stay on disk but are not configured (faster Loom).

### Modern (1.21.11)

```bat
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato
gradlew.bat :fabric:build

cd C:\Users\redfa\Documents\MinecraftDev\altoclef
gradlew.bat :1.21.11:compileJava
gradlew.bat :1.21.11:runClient
```

Expect: `[altoclef] Ostinato Baritone for 1.21.11: baritone-unoptimized-fabric-...jar`

### Legacy (1.16.1)

Ostinato branch `1.16.1` (cabaletta 1.16.5 + AltoClef ports):

- `postHandleMultiBlockChange` no-op (`method_30621`)
- `BlockOptionalMeta.drops` try/catch (`minecraft:origin` loot)

```bat
gradlew.bat :1.16.1:compileJava
gradlew.bat :1.16.1:runClient
```

Expect: `[altoclef] Ostinato Baritone for 1.16.1: baritone-unoptimized-fabric-ostinato-1.16.1.jar`

## Fallback

`-Paltoclef.forceMiranczBaritone` — modern modules only.
`1.16.1` requires the Ostinato 1.16.1 jar.

## Tungsten

TenorClef still vendors Tungsten under `libs/` / `vendor/tungsten` (unlike UnionClef's
`include(":tungsten")` subproject). Ostinato `movementBackend` covers travel on 1.21.11.
