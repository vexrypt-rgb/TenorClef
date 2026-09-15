# @schem — schematic builder (not testrun2 / not AA)

```
@schem list
@schem house.schem
@schem house          # prefix match
```

Put Baritone-readable files in `<gameDir>/schematics/`
(`.schem`, `.schematic`, `.litematic`, `.nbt`).

## What it does

1. **KIT** — wood/stone pick, axe, crafting table.
2. **RESERVE** — 64 cobble + 16 dirt. Does not re-gather after every placed block (hysteresis: only top up when cobble &lt; 16).
3. **BUILD** — Baritone `BuilderProcess.build(name, file, feet)`.
4. **T2Brain** — same jump / water / GUI / starve fixes as your other tasks.

Protected items: cobble, dirt, picks, planks, table. That stops the “mine last cobble → pillar with it → mine it again” loop.

## Register

In `AltoClefCommands.init()` add:

```
new BuildSchematicCommand(),
```

(`adris.altoclef.commands.BuildSchematicCommand`)

## Files

- `commands/BuildSchematicCommand.java`
- `tasks/speedrun/testrun2/schematic/SchematicBuildTask.java`
