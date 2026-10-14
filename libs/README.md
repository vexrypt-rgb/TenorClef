Place remapped Tungsten Fabric jars here. Gradle picks them up for the compiled
modules that support Tungsten (`1.21.11`, `1.21.4`, and the
preprocess-only 1.21 / 1.21.1 steps).

Ostinato Baritone jars (preferred):

- Prefer `../Ostinato/dist` after building the matching Ostinato branch. See
  [docs/OSTINATO_WIRING.md](../docs/OSTINATO_WIRING.md).
- `baritone-unoptimized-fabric-ostinato-1.21.11.jar` — Ostinato `main` (Minecraft
  1.21.11, Java 21). Primary pairing.
- `baritone-unoptimized-fabric-1.21.4.jar` — Ostinato branch `1.21.4` (Minecraft
  1.21.4, Java 21).
- `baritone-unoptimized-fabric-ostinato-1.16.1.jar` — Ostinato branch `1.16.1`
  (`method_30621` no-op + `AltoClefSettings`).
- Legacy name: `baritone-unoptimized-fabric-1.16.1.jar` — same 1.16.1 lineage;
  TenorClef also accepts this pattern.

Do not drop a 1.21.11 Ostinato jar into a 1.21.4 TenorClef build, or the
reverse.

## Vibe coding / AI use

Large parts of this repository were written or edited with AI assistants
(Claude, Grok, and similar). That is vibe coding: a person set the
direction; a model produced a lot of the text. A green CI run or a
commit message is not proof that a human understood every line.

Read the diff before you run or merge it. Do not treat this as audited
software. File bugs. Do not assume the model already considered your
case.
