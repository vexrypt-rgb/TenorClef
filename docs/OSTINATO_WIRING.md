# Ostinato wiring

TenorClef uses Ostinato as its Baritone-compatible movement engine on the primary
modern target and on the 1.16.1 legacy target. Build matching revisions locally until
Ostinato is published under a versioned Maven coordinate.

## Compatibility

| TenorClef module | Ostinato source | Status |
| --- | --- | --- |
| `1.21.11` | `main`, Fabric artifact | Primary supported pairing |
| `1.21.1` / `1.21` | API-compatible modern artifact | Build/test pairing |
| `1.16.1` | branch `1.16.1`, Fabric artifact | Legacy pairing |

## Build the modern pairing

1. Clone TenorClef and Ostinato as sibling directories.
2. In the Ostinato directory, run `gradlew.bat :fabric:build` on Windows or
   `./gradlew :fabric:build` on macOS/Linux.
3. Confirm an unoptimized Fabric jar exists in `Ostinato/dist/`.
4. In the TenorClef directory, run `gradlew.bat :1.21.11:build` or
   `./gradlew :1.21.11:build`.

TenorClef detects the newest matching jar under `../Ostinato/dist`. Its Gradle output
prints the selected file as `[altoclef] Ostinato Baritone ...`. Treat that line as the
source of truth for the build; do not leave several unknown Baritone jars in `libs/`.

## Legacy 1.16.1

Check out Ostinato's `1.16.1` branch and build its Fabric artifact first. Place the
result in `../Ostinato/dist/` (or use a sibling `Ostinato-1.16.1/dist/` directory),
then run `gradlew.bat :1.16.1:build` from TenorClef.

## Tungsten

Tungsten is optional and only affects travel/custom-goal movement on the modern target.
Build its Fabric jar from `vendor/tungsten` and place it in TenorClef's `libs/` folder,
or install it beside TenorClef and Ostinato in the Minecraft instance. If absent,
Ostinato falls back to Baritone travel.

## Publishing follow-up

The local-jar arrangement is intentionally temporary. Before publishing a stable
TenorClef release, publish a tagged Ostinato Fabric artifact and replace this file
lookup with a pinned dependency coordinate. That makes an incorrect pairing fail at
dependency resolution rather than at runtime.
