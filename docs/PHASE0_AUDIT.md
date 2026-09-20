# Phase 0 audit inventory

Date: 2026-09-19 (America/Phoenix). Branch intent: `docs/phase0-architecture-audit`.

## Repos touched

| Tree | Remote | Ref audited |
|------|--------|-------------|
| TenorClef | `vexrypt-rgb/TenorClef` | `origin/main` @ `feab389` (“Use verified 1.21.1 release target”) + local docs branch |
| Ostinato tip | `vexrypt-rgb/Ostinato` | `main` (1.21.11, swim port commit present) |
| Ostinato 1.16.1 | same | branch `1.16.1` (Gradle 4.9 / JDK 8 lineage) |

Windows paths from the brief were **not** mounted in this environment; audit used Git clones of those remotes.

## Entrypoints

- Fabric: `adris.altoclef.AltoClef` (`fabric.mod.json` → `main`).
- Delayed init: `EntryMixin` → `TitleScreenEntryEvent` → `AltoClef.onInitializeLoad()`.
- Commands: `AltoClefCommands` / `CommandExecutor` (client chat commands).

## Task / TaskChain hierarchy (summary)

- `Task` — hierarchical tick; child via `onTick()` return.
- `TaskChain` — priority competitor registered with `TaskRunner`.
- `SingleTaskChain` — one `mainTask` at a time (`UserTaskChain`, defense, survival, food, …).
- No typed result object (see `TECH_DEBT.md`).

## Tracker list

`BlockScanner`, `CraftingRecipeTracker`, `EntityTracker`, `MiscBlockTracker`, `SimpleChunkTracker`, `Tracker` / `TrackerManager`, storage (`ItemStorageTracker`, `ContainerSubTracker`, …), blacklisting helpers.

## Sample Baritone / Ostinato / Tungsten call sites

| Site | File:line (approx.) | What |
|------|---------------------|------|
| Baritone provider | `AltoClef.java:426-430` | `getClientBaritone()` → `BaritoneAPI.getProvider()` |
| Extra settings | `AltoClef.java:443-444` | `AltoClefSettings.getInstance()` |
| Settings bootstrap | `AltoClef.java:270-313` | parkour off, portal avoid, timeouts reset, … |
| Custom goal | `CustomBaritoneGoalTask.java:182-184` | `getCustomGoalProcess().setGoalAndPath` |
| Force cancel | `CustomBaritoneGoalTask.java:102,200` | `getPathingBehavior().forceCancel()` |
| Explore | `TimeoutWanderTask.java:~224-225` | `getExploreProcess().explore` |
| Follow entity | `GetToEntityTask.java:~153-154` | `setGoalAndPath(GoalFollowEntity)` |
| Destroy + input | `DestroyBlockTask.java:343-417` | `InputOverrideHandler` left-click + custom goal |
| Water escape | `DestroyBlockTask.java:353,400` | returns `GetOutOfWaterTask` |
| Look / world helpers | `LookHelper.java`, `WorldHelper.java` | `BaritoneAPI.getProvider().getPrimaryBaritone()` |
| Pre-equip BSI | `PreEquipItemChain.java:48` | `BlockStateInterface` from primary Baritone |
| Tungsten facade | `movement/TungstenMovement.java` | goto/follow preference AUTO/TUNGSTEN/BARITONE |
| Tungsten reflection | `movement/TungstenBridge.java` | `kaptainwutax.tungsten.*` soft bind |
| `@tgoto` | `commands/TungstenGotoCommand.java` | explicit Tungsten-preferring travel |
| testrun2 combat | `tasks/speedrun/testrun2/combat/AnyWeaponCombatTask.java` | `TungstenHelper.tryPathTo` |

**~60** files import `baritone.*`. **~317** `AltoClef.getInstance()` uses.

## Ostinato tip movement precursor

Already in Ostinato `main` (not wired through TenorClef tasks yet):

- `baritone.api.movement.IMovementBackend`
- `baritone.movement.BaritoneMovementBackend` / `TungstenMovementBackend` / `MovementBackends`
- `Settings.movementBackend` (`baritone` / `tungsten` / `auto`)

## Existing tests

- No formal unit-test source set found for tasksystem/movement.
- In-game: `TestCommand`, `TestRunCommand`, `Testrun2Command`, `CycleTestCommand`.
- CI: `.github/workflows/gradle.yml` (+ javadoc workflow).

## Working build commands

See [`DEPENDENCIES.md`](./DEPENDENCIES.md). Short form:

```bat
:: Ostinato tip
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato && gradlew.bat :fabric:build

:: Ostinato 1.16.1 (JDK 8)
cd C:\Users\redfa\Documents\MinecraftDev\Ostinato-1.16.1 && gradlew.bat build -Pbaritone.fabric_build

:: TenorClef
cd C:\Users\redfa\Documents\MinecraftDev\altoclef
gradlew.bat :1.21.11:compileJava
gradlew.bat :1.16.1:compileJava
```

## Top 10 coupling hotspots (TenorClef → Baritone / Ostinato / Tungsten)

1. `AltoClef.getClientBaritone()` + settings bootstrap — global Baritone ownership.
2. `CustomBaritoneGoalTask` — shared goal/path + cancel/explore interrupt pattern.
3. `DestroyBlockTask` — pathing + `InputOverrideHandler` mining (water-sensitive).
4. `GetToEntityTask` / `TimeoutWanderTask` — follow + explore processes.
5. `PlaceBlockTask` / builder usage — `getBuilderProcess().build`.
6. `MobDefenseChain` / `MLGBucketFallChain` — input overrides under threat/fall.
7. `BotBehaviour` — push/pop Baritone + `AltoClefSettings` state.
8. `util.baritone.*` custom `Goal*` classes — Goal API woven into tasks.
9. `TungstenMovement` / `TungstenBridge` — second travel control plane (1.21.x only).
10. Ostinato jar flatDir wiring in `build.gradle` — binary coupling to sibling `dist/` folders.

## Blockers

### Phase 1 (CI) — **done on branch `build/phase1-stabilize-ci`**

- Removed committed `org.gradle.java.home`; CI-friendly `-Xmx2G`; optional `gradle.properties.local`.
- CI stages Ostinato tip for `1.21.11`; `1.16.1` uses committed `libs/` jar.
- Matrix: `1.21.1` + `1.21.11` + `1.16.1`. Tungsten explicitly optional.
- See `docs/DEVELOPMENT.md`.

### Phase 2 (MovementEngine)

- ~60 Baritone-import files; cannot migrate all at once — pick 1–2 tasks.
- Dual Tungsten entrypoints (TenorClef facade vs Ostinato `IMovementBackend`) must converge.
- No `TaskResult` yet — MovementEngine failures need a place to surface.
- 1.16.1 has **no** Tungsten — Hybrid engine must degrade cleanly.
- Keep mining on Baritone while travel moves behind the API (locked product decision).

## gh auth status (this agent)

`gh auth status` → **not logged in**. Docs commit can be local; push/PR requires credentials on the Windows machine or a logged-in agent.
