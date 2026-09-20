# Tech debt (Phase 0 + Phase 1 updates)

Audit date: 2026-09-19 (America/Phoenix). Phase 1 CI/Gradle notes updated 2026-09-19 PT. Evidence-based; no invented APIs.

## AltoClef god object + singleton

- `AltoClef` is still the central service locator (chains, trackers, Baritone, Butler, UI, settings).
- `AltoClef.getInstance()` ≈ **317** hits; many tasks also take `AltoClef mod` — dual access patterns.
- Comment at `getInstance()` acknowledges future injection (“refactor codebase to use this instead of passing an argument around”) but the god object remains.

## Missing TaskResult / FailureReason

- `Task` exposes `isFinished()`, `stop` / `interrupt`, debug strings — not typed outcomes.
- Callers cannot distinguish “succeeded” / “unreachable” / “interrupted by survival” / “backend failed” without ad-hoc checks.
- Blocks Phase 4 (structured failure) and honest recovery (Phase 6).

## TaskCatalogue hardcoding

- `TaskCatalogue.java` ~971 lines of static `mine(...)` / `simple(...)` / wood tables.
- Resource identity, dimension constraints, and task graphs are code, not data.
- TODO at ~L747: “Do I really need this?” on a helper — signalling catalogue sprawl.

## Survival / defense TODOs (in-tree)

- `MobDefenseChain`: shield optimisation TODO; “refactor … more reliable for all mobs”.
- `FoodChain`: FIXME should check if currently fighting.
- `UserTaskChain`: FIXME on pausing ownership.
- `WorldSurvivalChain` handles drown/lava/fire/portal with priority bands but remains a special-case chain, not a unified threat model (Phase 8).
- Root `TODO.md`: lava escape, endermen eye contact, piglin rules, bastion avoidance.

## Baritone / Tungsten leakage into TenorClef

- **~60** Java files `import baritone...`.
- Travel helpers exist (`TungstenMovement`) but most movement/construction/speedrun tasks still speak Baritone process APIs directly.
- Duplicate control planes: TenorClef Tungsten facade **and** Ostinato tip `IMovementBackend` / `movementBackend` setting — risk of divergent switches until Phase 2 unifies on Ostinato MovementEngine.

## Version / build debt

- ~~`org.gradle.java.home` hard-coded to a Windows Adoptium JDK 21 path.~~ **Fixed in Phase 1** — removed; use `JAVA_HOME` / `~/.gradle/gradle.properties` / optional `gradle.properties.local` (see `docs/DEVELOPMENT.md`).
- ~~CI version matrix ≠ Ostinato endpoints.~~ **Fixed in Phase 1** — CI compiles `1.21.1` (primary), `1.21.11` (Ostinato tip staged), `1.16.1` (committed `libs/` jar).
- Dual Ostinato lineages (Gradle 4.9 + JDK8 vs Gradle 8 + JDK21) remain; documented in `docs/DEVELOPMENT.md`.
- Tungsten jar often absent from `libs/` — soft fallback remains; **CI explicitly optional** (jobs must pass without Tungsten). Proving the Tungsten path in CI is still open.

## Tests / CI gaps

- No `src/test` unit suite for tasksystem / movement facades.
- “Tests” in-repo are mostly **in-game commands** (`TestCommand`, `TestRunCommand`, `Testrun2Command`, `CycleTestCommand`).
- ~~CI does not stage Ostinato for 1.16.1.~~ **Fixed in Phase 1** — committed `libs/baritone-unoptimized-fabric-1.16.1.jar`; tip Ostinato built in the `1.21.11` job.

## Water / swim (recent, relevant)

Documented in `docs/WATER_MINE_FIX.md` + Ostinato `docs/SWIM_PORT.md`:

- TenorClef: `DestroyBlockTask` must not left-click while water-bobbing; escape via `GetOutOfWaterTask`; CollectWaterBucket prefers shore.
- Ostinato: port baritone#3988 sprint-swim / curb #2377 bob (`swimInWater`).
- Reminder: 1.16.1 remains Baritone-only for movement backend.

## Overnight / process notes

- `OVERNIGHT_NOTES.md`: do not compile while `@testrun` client is live; combat sword-preference patches pending apply cycles.
- Combat docs leftovers / vendor dirt should stay out of architecture PRs unless explicitly in scope.
