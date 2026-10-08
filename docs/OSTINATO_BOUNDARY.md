# Ostinato ↔ TenorClef boundary (Phase 0)

## Rule of thumb

| Lives in **Ostinato** | Lives in **TenorClef** |
|-----------------------|------------------------|
| Pathfinding, movement execution, physics traversal | Goals, plans, task graphs, catalogue |
| Baritone processes & path executor | World model / knowledge facts |
| Tungsten physics A* backend | Inventory / crafting / container logic |
| Movement backend selection (`movementBackend` / future MovementEngine) | Threat assessment policy, recovery policy |
| Swim/sprint-in-water movement fixes | When to mine vs escape water (task policy) |
| Schematic/builder **mechanics** | What to build and why |
| Input overrides needed to **follow a path** | Input overrides for **gameplay** (eat, shield, MLG decision) |

## Boundary progress (Phase 2)

- **Migrated:** `GetToBlockTask` / `GetToEntityTask` → `MovementEngineAdapter` → Ostinato `IMovementEngine` (fallback to CustomGoalProcess).
- **Still direct:** most other tasks still call `getCustomGoalProcess` / `getPathingBehavior` / `getInputOverrideHandler`.
- TenorClef still embeds `TungstenBridge` for its own TungstenGoto/Follow tasks; hybrid selection increasingly lives in Ostinato.
- `AltoClef.initializeBaritoneSettings` still mutates Baritone settings (agent preferences; later via engine config).

## Phase 2 direction

1. Expand Ostinato `IMovementBackend` → documented **MovementEngine** API (`MovementGoal`, status, path result, failures).
2. Keep `BaritoneMovementBackend` + `TungstenMovementBackend` + hybrid auto-fallback inside Ostinato.
3. Replace TenorClef travel call sites gradually (`GetToBlockTask` / `TungstenGotoTask` first).
4. Leave mining/building on Baritone processes until a later, explicit action-interface phase.

## Version matrix (boundary implications)

| MC module | Ostinato jar (sole source) | Tungsten | CI gate |
|----|----------|----------|---------|
| 1.21.1 / 1.21 | Published Baritone artifact (`../Ostinato/dist` ignored) | Optional | **Required** (compile + unit tests) |
| 1.21.4 | Ostinato `1.21.4` → `libs/baritone-unoptimized-fabric-1.21.4.jar` | Optional | Local only |
| 1.21.11 | Ostinato `main` tip → `../Ostinato/dist` | Optional | Non-blocking |
| 1.16.1 | Ostinato `1.16.1` → `libs/baritone-unoptimized-fabric-1.16.1.jar` (kinematic backend lives here) | Optional (`vendor/tungsten-1.16.1`) | **Required** (compile) |

Never cross jars between rows: each module must resolve only its own row's Ostinato jar.

## Pointers

- TenorClef: `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/OSTINATO_WIRING.md`, `docs/TUNGSTEN_BACKEND.md`
- Ostinato: `docs/SWIM_PORT.md` (+ short `docs/TENORCLEF.md` pointer added in Phase 0)
