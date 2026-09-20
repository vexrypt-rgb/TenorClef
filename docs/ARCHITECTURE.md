# TenorClef architecture (Phase 0 — current vs target)

Audit date: 2026-09-19 (America/Phoenix). Repo audited: `vexrypt-rgb/TenorClef` (AltoClef fork).
On the Windows machine this tree is `C:\Users\redfa\Documents\MinecraftDev\altoclef`.

Phase 0 was docs-only. Phase 2 adds MovementEngine adapter for 2 travel tasks.
Phase 3 extracts `WorldKnowledge` + `MovementController` facades; AltoClef stays a shim.

## Product intent

| Layer | Role |
|-------|------|
| **TenorClef** | High-level autonomous agent: perception, world model, goals, planning, tasks, evaluation, recovery |
| **Ostinato** | Low-level movement: nav, pathfinding, physics; backends **Baritone** + **Tungsten** |

Target pipeline:

```
Goal → Requirements → Plan → Task → Action Interface → Ostinato MovementEngine → Baritone | Tungsten
```

Incremental refactor of the AltoClef fork — **do not rewrite from scratch**. First real code PR is Phase 2 (MovementEngine API).

## Current architecture

### Entrypoint / god object

- Fabric main entrypoint: `adris.altoclef.AltoClef` (`src/main/resources/fabric.mod.json`).
- `AltoClef.onInitialize()` only registers for `TitleScreenEntryEvent`; mixin `adris.altoclef.mixins.EntryMixin` fires that event → `AltoClef.onInitializeLoad()` (real startup).
- Singleton: `AltoClef.getInstance()` — **~317 call sites** across source (plus many methods still take an `AltoClef mod` argument).
- `AltoClef` constructs and holds: `TaskRunner`, `TrackerManager`, every `TaskChain`, trackers, `BotBehaviour`, Butler, `InputControls` / `SlotHandler`, Baritone settings bootstrap (`initializeBaritoneSettings`), commands.

### Task system

```
TaskRunner.tick()
  └─ selects highest getPriority() among active TaskChain(s)
       └─ SingleTaskChain runs one Task tree
            └─ Task.tick() → onTick() may return a child Task
```

| Type | Package |
|------|---------|
| `TaskRunner` | `adris.altoclef.tasksystem.TaskRunner` |
| `TaskChain` | `adris.altoclef.tasksystem.TaskChain` |
| `Task` | `adris.altoclef.tasksystem.Task` |
| `SingleTaskChain` | `adris.altoclef.chains.SingleTaskChain` |

**Missing:** `TaskResult` / `FailureReason`. Finish = `isFinished()`; failure is stop/interrupt or a higher-priority chain stealing control.

### Chains (constructed in `AltoClef.onInitializeLoad`)

| Chain | Class | Priority notes (approx.) |
|-------|-------|---------------------------|
| User command tasks | `UserTaskChain` | `50` |
| Mob defense | `MobDefenseChain` | threat-scaled (~50–70+) |
| Death / disconnect UI | `DeathMenuChain` | high when screens require action |
| Interaction repair | `PlayerInteractionFixChain` | situational |
| MLG / fall clutch | `MLGBucketFallChain` | up to `100` while falling |
| Unstuck | `UnstuckChain` | `55` when stuck heuristics fire |
| Pre-equip | `PreEquipItemChain` | situational |
| World survival | `WorldSurvivalChain` | `60`–`100` (lava / fire / drown / portal) |
| Food | `FoodChain` | ~`55` when eating needed |

### Trackers

- `EntityTracker`, `BlockScanner`, `SimpleChunkTracker`, `MiscBlockTracker`, `CraftingRecipeTracker`
- Storage: `ItemStorageTracker`, `ContainerSubTracker` (+ inventory sub-trackers under `trackers.storage`)
- Blacklisting helpers under `trackers.blacklisting`
- Manager: `TrackerManager`

### Catalogue

- `adris.altoclef.TaskCatalogue` (~971 lines) — static hardcoded resource name → task factory maps.

### Movement coupling today

**Path A — direct Baritone (dominant).** Tasks/chains call through `AltoClef.getClientBaritone()` / `BaritoneAPI`:

- `getCustomGoalProcess().setGoalAndPath(...)`
- `getPathingBehavior().forceCancel()` / `isPathing()` / `isSafeToCancel()`
- `getExploreProcess().explore(...)`
- `getBuilderProcess()` / `getMineProcess()`
- `getInputOverrideHandler().setInputForceState(...)`
- Extra hooks: `baritone.altoclef.AltoClefSettings` via `getExtraBaritoneSettings()`

**Path B — Tungsten travel facade (optional).** `adris.altoclef.movement.TungstenMovement` + reflection `TungstenBridge` → `TungstenGotoTask` / `TungstenFollowTask`. Mining intentionally stays on Baritone (`docs/TUNGSTEN_BACKEND.md`).

**Ostinato MovementEngine (Phase 2):** Ostinato tip exposes `IMovementEngine` / `HybridMovementEngine` (built on `IMovementBackend`). TenorClef routes **GetToBlockTask** and **GetToEntityTask** through `adris.altoclef.movement.MovementEngineAdapter` (reflection + CustomGoalProcess fallback). Other ~60 Baritone call sites unchanged. Mining/builder stay on Baritone processes.

### Phase 3 core split (incremental)

| Type | Package | Role |
|------|---------|------|
| `WorldKnowledge` | `adris.altoclef.knowledge` | Read-only facade over existing trackers + player/world |
| `AltoClefWorldKnowledge` | same | Live impl backed by AltoClef getters |
| `MovementController` | `adris.altoclef.control` | Travel ops (go-to / follow / cancel / status) |
| `AdapterMovementController` | same | Thin wrap of Phase 2 `MovementEngineAdapter` |
| `CoreServices` | `adris.altoclef.core` | Bundles knowledge + movement for injection |

**Shim:** `AltoClef.getInstance()` and existing getters still work. New accessors:
`getWorldKnowledge()`, `getMovement()`, `getCoreServices()`.

**Migrated call sites (few):** `CustomBaritoneGoalTask` (GetToBlock path), `GetToEntityTask`,
and a few `GetToBlockTask` world/scanner reads via `WorldKnowledge`.

**Still on AltoClef god object (~317 `getInstance()` sites):** task runner, chains, butler,
slot/input controls, Baritone settings bootstrap, food/mob/MLG chains, catalogue, most tasks.

### Multi-version

ReplayMod preprocess + Fabric Loom: `settings.gradle.kts` includes `1.21.11` … `1.16.1`. Shared sources in `src/main/java`; per-version dirs under `versions/`.

## Target architecture

```
┌──────────────────────────────────────────────┐
│ TenorClef                                     │
│  Goal / Requirements / Planner                │
│  WorldKnowledge (timestamped facts)           │
│  TaskExecutor (TaskResult / FailureReason)    │
│  Recovery + threat controllers                │
│  Action Interface ──────────────────────┐     │
└─────────────────────────────────────────│─────┘
                                          ▼
┌──────────────────────────────────────────────┐
│ Ostinato MovementEngine                       │
│  MovementGoal / Status / PathResult / failures│
│  Hybrid engine → Baritone | Tungsten backends │
└──────────────────────────────────────────────┘
```

Phase 2 should **evolve** Ostinato's `IMovementBackend` into the public MovementEngine surface and migrate 1–2 TenorClef call sites — not invent a second parallel API and not rewrite TenorClef.

## Related docs

- [`DEPENDENCIES.md`](./DEPENDENCIES.md) — Gradle / jars / JDKs
- [`TECH_DEBT.md`](./TECH_DEBT.md) — known debt
- [`PHASE0_AUDIT.md`](./PHASE0_AUDIT.md) — inventory + hotspots
- [`OSTINATO_BOUNDARY.md`](./OSTINATO_BOUNDARY.md) — ownership boundary
- [`ROADMAP.md`](./ROADMAP.md) — phases 0–10
