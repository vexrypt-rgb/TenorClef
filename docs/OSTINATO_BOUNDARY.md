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

## Current violations (expected pre-Phase 2)

- TenorClef tasks call `getCustomGoalProcess`, `getPathingBehavior`, `getInputOverrideHandler`, etc. directly.
- TenorClef embeds `TungstenBridge` reflection instead of depending solely on Ostinato's backend SPI.
- `AltoClef.initializeBaritoneSettings` mutates Ostinato/Baritone settings from the agent layer (some of this may remain as “agent preferences” injected through MovementEngine config later).

## Phase 2 direction

1. Expand Ostinato `IMovementBackend` → documented **MovementEngine** API (`MovementGoal`, status, path result, failures).
2. Keep `BaritoneMovementBackend` + `TungstenMovementBackend` + hybrid auto-fallback inside Ostinato.
3. Replace TenorClef travel call sites gradually (`GetToBlockTask` / `TungstenGotoTask` first).
4. Leave mining/building on Baritone processes until a later, explicit action-interface phase.

## Version matrix (boundary implications)

| MC | Ostinato | Tungsten |
|----|----------|----------|
| 1.21.11 / 1.21.1 / 1.21 | Tip jars from `Ostinato/dist` | Optional jars on TenorClef 1.21.x modules only |
| 1.16.1 | `Ostinato-1.16.1` / ostinato-1.16.1 jar | **Not supported** — Baritone only |

## Pointers

- TenorClef: `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/OSTINATO_WIRING.md`, `docs/TUNGSTEN_BACKEND.md`
- Ostinato: `docs/SWIM_PORT.md` (+ short `docs/TENORCLEF.md` pointer added in Phase 0)
