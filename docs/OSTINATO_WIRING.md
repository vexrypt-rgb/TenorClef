# Ostinato wiring

TenorClef currently uses a matching published Baritone artifact for its stable modern
targets. Ostinato is required for the 1.16.1 legacy pairing and is the engine under
development for the experimental 1.21.11 port. Do not use a 1.21.11 Ostinato jar with
a 1.21 or 1.21.1 client.

## Compatibility

| TenorClef module | Ostinato source | Status |
| --- | --- | --- |
| `1.21.1` | Matching published Baritone artifact | Primary supported pairing |
| `1.21` | Matching published Baritone artifact | Maintained pairing |
| `1.21.11` | `main`, Fabric artifact | Experimental; source port does not compile yet |
| `1.16.1` | branch `1.16.1`, Fabric artifact | Legacy pairing |

## Build the stable modern target

Run `gradlew.bat :1.21.1:build` on Windows or `./gradlew :1.21.1:build` on macOS/Linux.
The project resolves a Baritone artifact for Minecraft 1.21.1. This target compiled
successfully on 2026-09-17.

The build intentionally ignores `../Ostinato/dist` for 1.21 and 1.21.1. This prevents
an incompatible 1.21.11 artifact from being selected merely because it is newer.

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

Before making 1.21.11 a stable TenorClef target, finish its source port, publish a
tagged matching Ostinato Fabric artifact, and consume it through a pinned dependency
coordinate. That makes an incorrect pairing fail at dependency resolution rather than
at runtime.
