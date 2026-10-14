# Ostinato wiring

TenorClef always runs on Ostinato. It is only compiled for the Minecraft versions Ostinato
is built for; never load a jar built for one Minecraft version into another.

## Compatibility

| TenorClef module | Ostinato source | Status |
| --- | --- | --- |
| `1.21.11` | Ostinato `main` (MC 1.21.11), staged as `libs/baritone-unoptimized-fabric-ostinato-1.21.11.jar` | Primary; compiles and runs the unit tests in CI |
| `1.21.4` | Ostinato branch `1.21.4`, staged as `libs/baritone-unoptimized-fabric-1.21.4.jar` | Supported; compiles and runs the unit tests in CI |
| `1.16.1` | Ostinato branch `1.16.1`, staged as `libs/baritone-unoptimized-fabric-1.16.1.jar` | Legacy pairing; compiles in CI |
| `26.3` | Ostinato branch `26.3`, release jar staged as `libs/baritone-unoptimized-fabric-ostinato-26.3.jar` | Experimental; built with `-Pwith26` and a JDK 25 toolchain, not in CI |
| `1.21.1` … `1.16.5` | none | Preprocess-only: not compiled, packaged or tested |

The preprocess chain cannot be trimmed (see `settings.gradle.kts`), so the preprocess-only
modules still exist. The preprocessor resolves types on each of them while it remaps sources
down to 1.16.1, so they keep the matching upstream Baritone as a **compile-only** dependency for
that analysis. Nothing built from them is shipped or run.

## Build

Run `gradlew.bat :1.21.11:build` on Windows or `./gradlew :1.21.11:build` on macOS/Linux.

The jars in `libs/` are the unoptimized Fabric jars of an Ostinato release. To refresh one, build the
matching Ostinato branch and copy its `dist/baritone-unoptimized-fabric-*.jar` over the file in `libs/`:

- `1.21.11`: JDK 21, `main`, `./gradlew :fabric:build`. A jar in a sibling `../Ostinato/dist/` is used
  ahead of the one in `libs/`, so a local Ostinato checkout on `main` is picked up without copying anything.
  The newest jar there wins whichever branch it was built from, so clear `../Ostinato/dist/` after
  building another line in that checkout.
- `1.21.4`: JDK 21, branch `1.21.4`, `./gradlew :fabric:build`.
- `1.16.1`: JDK 8, `./gradlew build -Pbaritone.fabric_build`.

## Tungsten

Tungsten is optional and only affects travel/custom-goal movement on the modern target.
Build its Fabric jar from `vendor/tungsten` and place it in TenorClef's `libs/` folder,
or install it beside TenorClef and Ostinato in the Minecraft instance. If absent,
Ostinato falls back to Baritone travel.

## Publishing follow-up

The jars in `libs/` pin each module to an Ostinato release, but nothing stops a jar of another
line being copied over one of them. Consuming Ostinato through a versioned dependency coordinate
would make an incorrect pairing fail at dependency resolution rather than at runtime.
